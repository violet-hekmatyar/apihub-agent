package com.apihub.mockprovider.scenario;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ScenarioRunService {

    private static final int MAX_PLANNED_REQUESTS = 5000;
    private static final int MAX_CONCURRENCY = 50;
    private static final int MAX_SAMPLE_LIMIT = 500;
    private static final DateTimeFormatter RUN_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ScenarioRunProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService schedulerExecutor = Executors.newFixedThreadPool(4);
    private final Map<String, RunControl> runningRuns = new ConcurrentHashMap<>();

    public ScenarioRunService(JdbcTemplate jdbcTemplate, ScenarioRunProperties properties, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> createRun(ScenarioRunRequest request) {
        NormalizedRun run = normalize(request);
        insertRun(run);
        RunControl control = new RunControl();
        runningRuns.put(run.scenarioRunId(), control);
        schedulerExecutor.submit(() -> executeRun(run, control));
        return Map.of(
                "scenarioRunId", run.scenarioRunId(),
                "scenarioId", run.scenarioId(),
                "status", "RUNNING",
                "statusUrl", "/mock-provider/scenario-runs/" + run.scenarioRunId(),
                "resultUrl", "/mock-provider/scenario-runs/" + run.scenarioRunId() + "/result",
                "sampleCallsUrl", "/mock-provider/scenario-runs/" + run.scenarioRunId() + "/sample-calls"
        );
    }

    public Map<String, Object> getRun(String scenarioRunId) {
        validateScenarioRunId(scenarioRunId);
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT scenario_run_id, scenario_id, status, target_gateway_base_url,
                                   logical_duration_seconds, time_scale, ramp_up_seconds, steady_seconds,
                                   ramp_down_seconds, base_rps, peak_rps, max_concurrency, random_seed,
                                   total_planned_requests, total_sent_requests, success_count, fail_count,
                                   started_at, finished_at, error_message, created_at, updated_at
                            FROM scenario_run
                            WHERE scenario_run_id = ?
                            """,
                    (rs, rowNum) -> runMap(rs),
                    scenarioRunId);
        } catch (EmptyResultDataAccessException e) {
            throw new ScenarioRunNotFoundException("scenario run not found: " + scenarioRunId);
        }
    }

    public Map<String, Object> getResult(String scenarioRunId) {
        validateScenarioRunId(scenarioRunId);
        Map<String, Object> run = getRun(scenarioRunId);
        String summaryJson = jdbcTemplate.queryForObject(
                "SELECT result_summary FROM scenario_run WHERE scenario_run_id = ?",
                String.class,
                scenarioRunId);
        if (StringUtils.hasText(summaryJson)) {
            try {
                run.put("resultSummary", objectMapper.readValue(summaryJson, MAP_TYPE));
            } catch (JsonProcessingException e) {
                run.put("resultSummary", Map.of("parseError", true));
            }
        } else {
            run.put("resultSummary", Map.of(
                    "totalPlannedRequests", run.get("totalPlannedRequests"),
                    "totalSentRequests", run.get("totalSentRequests"),
                    "successCount", run.get("successCount"),
                    "failCount", run.get("failCount")
            ));
        }
        return run;
    }

    public List<Map<String, Object>> listSamples(String scenarioRunId, int limit) {
        validateScenarioRunId(scenarioRunId);
        ensureExists(scenarioRunId);
        int actualLimit = Math.max(1, Math.min(limit, MAX_SAMPLE_LIMIT));
        return jdbcTemplate.query("""
                        SELECT scenario_run_id, sequence_no, api_code, app_code, mock_scenario,
                               phase, trace_id, request_id, gateway_log_id, upstream_status,
                               latency_ms, success, error_code, called_at, extra_info
                        FROM scenario_call_sample
                        WHERE scenario_run_id = ?
                        ORDER BY sequence_no ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> sampleMap(rs),
                scenarioRunId,
                actualLimit);
    }

    public Map<String, Object> cancelRun(String scenarioRunId) {
        validateScenarioRunId(scenarioRunId);
        Map<String, Object> run = getRun(scenarioRunId);
        String status = String.valueOf(run.get("status"));
        if ("RUNNING".equals(status) || "PENDING".equals(status)) {
            RunControl control = runningRuns.get(scenarioRunId);
            if (control != null) {
                control.cancelled = true;
            }
            jdbcTemplate.update("""
                            UPDATE scenario_run
                            SET status = 'CANCELLED', finished_at = NOW(), error_message = 'scenario run cancelled by request'
                            WHERE scenario_run_id = ?
                            """,
                    scenarioRunId);
            status = "CANCELLED";
        }
        return Map.of("scenarioRunId", scenarioRunId, "status", status);
    }

    private void executeRun(NormalizedRun run, RunControl control) {
        ExecutorService workers = Executors.newFixedThreadPool(run.maxConcurrency());
        CompletionService<CallOutcome> completionService = new ExecutorCompletionService<>(workers);
        Semaphore semaphore = new Semaphore(run.maxConcurrency());
        FullSummary summary = new FullSummary(run.totalPlannedRequests());
        List<SampleCandidate> candidates = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Future<CallOutcome>> futures = new ArrayList<>();
        Instant started = Instant.now();
        long maxAllowedMillis = Math.round((run.logicalDurationSeconds() / run.timeScale()) * 1500) + 60_000L;
        boolean timedOut = false;

        try {
            List<CallPlan> plans = buildPlan(run);
            int completed = 0;
            for (CallPlan plan : plans) {
                if (control.cancelled || elapsedMillis(started) > maxAllowedMillis) {
                    timedOut = !control.cancelled;
                    break;
                }
                sleepUntil(started, plan.targetOffsetMillis());
                if (control.cancelled || elapsedMillis(started) > maxAllowedMillis) {
                    timedOut = !control.cancelled;
                    break;
                }
                Future<CallOutcome> future = completionService.submit(() -> {
                    semaphore.acquire();
                    try {
                        GatewayCallResult result = callGateway(run, plan);
                        return new CallOutcome(plan, result);
                    } finally {
                        semaphore.release();
                    }
                });
                futures.add(future);
                completed += drainCompleted(completionService, summary, candidates);
                if (summary.sent.get() % 10 == 0 && summary.sent.get() > 0) {
                    updateProgress(run.scenarioRunId(), summary.sent.get(), summary.success.get(), summary.fail.get());
                }
            }

            while (completed < futures.size()) {
                if (control.cancelled || elapsedMillis(started) > maxAllowedMillis) {
                    timedOut = !control.cancelled;
                    break;
                }
                Future<CallOutcome> future = completionService.poll(2, TimeUnit.SECONDS);
                if (future == null) {
                    continue;
                }
                completed++;
                CallOutcome outcome = future.get();
                summary.record(outcome);
                candidates.add(new SampleCandidate(outcome.plan(), outcome.result()));
                if (completed % 10 == 0 || completed == futures.size()) {
                    updateProgress(run.scenarioRunId(), summary.sent.get(), summary.success.get(), summary.fail.get());
                }
            }

            if (control.cancelled) {
                cancelFutures(futures);
                updateCancelled(run.scenarioRunId());
                return;
            }
            if (timedOut) {
                cancelFutures(futures);
                updateFailed(run.scenarioRunId(), "scenario run exceeded max allowed duration");
                return;
            }

            updateProgress(run.scenarioRunId(), summary.sent.get(), summary.success.get(), summary.fail.get());
            insertSelectedSamples(run.scenarioRunId(), selectSamples(candidates, run.sampleLimit()));
            updateCompleted(run.scenarioRunId(), summary.toMap());
        } catch (Exception e) {
            updateFailed(run.scenarioRunId(), e.getMessage());
        } finally {
            workers.shutdownNow();
            runningRuns.remove(run.scenarioRunId());
        }
    }

    private GatewayCallResult callGateway(NormalizedRun run, CallPlan call) throws IOException, InterruptedException {
        String traceId = newTraceId();
        String requestId = "req_" + run.scenarioRunId() + "_" + call.sequenceNo();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("apiCode", call.apiCode());
        payload.put("appCode", call.appCode());
        payload.put("mockScenario", call.mockScenario());
        payload.put("timeoutMs", 3000);
        payload.put("clientInfo", Map.of("clientIp", "127.0.0.1", "userAgent", "scenario-runner/1.0"));
        payload.put("scenarioContext", Map.of(
                "scenarioRunId", run.scenarioRunId(),
                "scenarioId", run.scenarioId(),
                "phase", call.phase(),
                "sequenceNo", call.sequenceNo()
        ));
        if (isGetApi(call.apiCode())) {
            payload.put("queryParams", queryParams(call.apiCode()));
        } else {
            payload.put("body", body(call.apiCode(), call.appCode()));
        }

        long started = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(run.targetGatewayBaseUrl() + "/api/dev/gateway/invoke"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Trace-Id", traceId)
                .header("X-Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - started;
        Map<String, Object> wrapper = objectMapper.readValue(response.body(), MAP_TYPE);
        Object dataObj = wrapper.get("data");
        Map<String, Object> data = dataObj instanceof Map<?, ?> dataMap ? castMap(dataMap) : Map.of();
        boolean success = Boolean.TRUE.equals(data.get("success"));
        Integer upstreamStatus = intValue(data.get("upstreamStatus"));
        Long gatewayLogId = longValue(data.get("gatewayLogId"));
        String responseTraceId = stringValue(data.get("traceId"), traceId);
        String responseRequestId = stringValue(data.get("requestId"), requestId);
        String errorCode = stringValue(data.get("errorCode"), null);
        return new GatewayCallResult(success, upstreamStatus, gatewayLogId, responseTraceId, responseRequestId, errorCode, (int) elapsed);
    }

    private NormalizedRun normalize(ScenarioRunRequest request) {
        if (request == null || !StringUtils.hasText(request.getScenarioId())) {
            throw new IllegalArgumentException("scenarioId is required");
        }
        String scenarioId = request.getScenarioId().trim().toUpperCase(Locale.ROOT);
        ScenarioDefinition definition = ScenarioCatalog.find(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("unsupported scenarioId: " + scenarioId));
        ScenarioRunRequest.LoadProfile input = request.getLoadProfile() == null ? new ScenarioRunRequest.LoadProfile() : request.getLoadProfile();
        int rampUp = nonNegative(input.getRampUpSeconds(), 0, "rampUpSeconds");
        int steady = nonNegative(input.getSteadySeconds(), 60, "steadySeconds");
        int rampDown = nonNegative(input.getRampDownSeconds(), 0, "rampDownSeconds");
        int duration = positive(input.getLogicalDurationSeconds(), rampUp + steady + rampDown, "logicalDurationSeconds");
        if (rampUp + steady + rampDown != duration) {
            throw new IllegalArgumentException("rampUpSeconds + steadySeconds + rampDownSeconds must equal logicalDurationSeconds");
        }
        double timeScale = doubleValue(input.getTimeScale(), 1.0);
        double baseRps = doubleValue(input.getBaseRps(), 1.0);
        double peakRps = doubleValue(input.getPeakRps(), 1.0);
        int maxConcurrency = positive(input.getMaxConcurrency(), 1, "maxConcurrency");
        if (timeScale < 1 || timeScale > 3600) {
            throw new IllegalArgumentException("timeScale must be between 1 and 3600");
        }
        if (baseRps < 0) {
            throw new IllegalArgumentException("baseRps must be >= 0");
        }
        if (peakRps <= 0 || peakRps < baseRps) {
            throw new IllegalArgumentException("peakRps must be > 0 and >= baseRps");
        }
        if (maxConcurrency > MAX_CONCURRENCY) {
            throw new IllegalArgumentException("maxConcurrency must be <= " + MAX_CONCURRENCY);
        }
        int planned = plannedRequests(rampUp, steady, rampDown, baseRps, peakRps);
        if (planned < 1) {
            throw new IllegalArgumentException("totalPlannedRequests must be > 0");
        }
        if (planned > MAX_PLANNED_REQUESTS) {
            throw new IllegalArgumentException("totalPlannedRequests exceeds " + MAX_PLANNED_REQUESTS);
        }
        int sampleLimit = nonNegative(request.getSampleLimit(), 20, "sampleLimit");
        if (sampleLimit > MAX_SAMPLE_LIMIT) {
            throw new IllegalArgumentException("sampleLimit must be <= " + MAX_SAMPLE_LIMIT);
        }
        String targetGateway = StringUtils.hasText(request.getTargetGatewayBaseUrl()) ? request.getTargetGatewayBaseUrl().trim() : properties.getBaseUrl();
        validateGatewayUrl(targetGateway);
        return new NormalizedRun(newRunId(), scenarioId, targetGateway.replaceAll("/+$", ""), duration, timeScale,
                rampUp, steady, rampDown, baseRps, peakRps, maxConcurrency, input.getRandomSeed(), planned,
                sampleLimit, request.getNote(), definition);
    }

    private List<CallPlan> buildPlan(NormalizedRun run) {
        Random random = new Random(run.randomSeed() == null ? System.nanoTime() : run.randomSeed());
        List<CallPlan> plans = new ArrayList<>(run.totalPlannedRequests());
        int sequence = 1;
        sequence = addPhasePlans(run, random, plans, sequence, "RAMP_UP", 0, run.rampUpSeconds(),
                plannedRequests(run.rampUpSeconds(), 0, 0, run.baseRps(), run.peakRps()));
        sequence = addPhasePlans(run, random, plans, sequence, "STEADY", run.rampUpSeconds(), run.steadySeconds(),
                plannedRequests(0, run.steadySeconds(), 0, run.baseRps(), run.peakRps()));
        addPhasePlans(run, random, plans, sequence, "RAMP_DOWN", run.rampUpSeconds() + run.steadySeconds(), run.rampDownSeconds(),
                plannedRequests(0, 0, run.rampDownSeconds(), run.baseRps(), run.peakRps()));
        plans.sort(Comparator.comparingLong(CallPlan::targetOffsetMillis).thenComparingInt(CallPlan::sequenceNo));
        return plans;
    }

    private int addPhasePlans(NormalizedRun run, Random random, List<CallPlan> plans, int sequenceStart,
            String phase, int phaseStartSecond, int phaseSeconds, int count) {
        if (phaseSeconds <= 0 || count <= 0) {
            return sequenceStart;
        }
        int sequence = sequenceStart;
        for (int i = 0; i < count; i++) {
            double logicalOffset = phaseStartSecond + ((double) i / count) * phaseSeconds;
            long targetOffsetMillis = Math.round(logicalOffset * 1000 / run.timeScale());
            ScenarioDefinition.ApiWeight api = pick(run.definition().apiWeights(), random);
            ScenarioDefinition.MockScenarioWeight mockScenario = pick(api.mockScenarios(), random);
            String appCode = ScenarioCatalog.DEFAULT_APP_CODES.get(api.apiCode());
            plans.add(new CallPlan(sequence++, targetOffsetMillis, phase, api.apiCode(), appCode, mockScenario.mockScenario()));
        }
        return sequence;
    }

    private static <T> T pick(List<T> weightedItems, Random random) {
        int total = 0;
        for (T item : weightedItems) {
            total += item instanceof ScenarioDefinition.ApiWeight api ? api.weight() : ((ScenarioDefinition.MockScenarioWeight) item).weight();
        }
        int value = random.nextInt(Math.max(total, 1));
        int current = 0;
        for (T item : weightedItems) {
            current += item instanceof ScenarioDefinition.ApiWeight api ? api.weight() : ((ScenarioDefinition.MockScenarioWeight) item).weight();
            if (value < current) {
                return item;
            }
        }
        return weightedItems.get(weightedItems.size() - 1);
    }

    private void insertRun(NormalizedRun run) {
        jdbcTemplate.update("""
                        INSERT INTO scenario_run (
                          scenario_run_id, scenario_id, status, target_gateway_base_url,
                          logical_duration_seconds, time_scale, ramp_up_seconds, steady_seconds,
                          ramp_down_seconds, base_rps, peak_rps, max_concurrency, random_seed,
                          total_planned_requests, total_sent_requests, success_count, fail_count,
                          started_at, extra_info
                        ) VALUES (?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, NOW(), ?)
                        """,
                run.scenarioRunId(), run.scenarioId(), run.targetGatewayBaseUrl(), run.logicalDurationSeconds(),
                BigDecimal.valueOf(run.timeScale()), run.rampUpSeconds(), run.steadySeconds(), run.rampDownSeconds(),
                BigDecimal.valueOf(run.baseRps()), BigDecimal.valueOf(run.peakRps()), run.maxConcurrency(),
                run.randomSeed(), run.totalPlannedRequests(), toJson(Map.of("note", run.note() == null ? "" : run.note())));
    }

    private void insertSelectedSamples(String scenarioRunId, List<SampleCandidate> samples) {
        for (SampleCandidate sample : samples) {
            CallPlan call = sample.plan();
            GatewayCallResult result = sample.result();
            jdbcTemplate.update("""
                            INSERT INTO scenario_call_sample (
                              scenario_run_id, sequence_no, api_code, app_code, mock_scenario,
                              phase, trace_id, request_id, gateway_log_id, upstream_status,
                              latency_ms, success, error_code, called_at, extra_info
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?)
                            """,
                    scenarioRunId, call.sequenceNo(), call.apiCode(), call.appCode(), call.mockScenario(), call.phase(),
                    result.traceId(), result.requestId(), result.gatewayLogId(), result.upstreamStatus(), result.latencyMs(),
                    result.success() ? 1 : 0, result.errorCode(), toJson(Map.of("source", "scenario-runner-v1")));
        }
    }

    private List<SampleCandidate> selectSamples(List<SampleCandidate> candidates, int sampleLimit) {
        if (sampleLimit <= 0 || candidates.isEmpty()) {
            return List.of();
        }
        List<SampleCandidate> selected = new ArrayList<>();
        int failureBudget = Math.max(1, sampleLimit / 2);
        for (SampleCandidate candidate : candidates.stream().filter(c -> !c.result().success()).toList()) {
            addIfRoom(selected, candidate, sampleLimit);
            if (selected.size() >= failureBudget) {
                break;
            }
        }
        for (String phase : List.of("RAMP_UP", "STEADY", "RAMP_DOWN")) {
            candidates.stream().filter(c -> phase.equals(c.plan().phase())).findFirst()
                    .ifPresent(c -> addIfRoom(selected, c, sampleLimit));
        }
        for (String apiCode : ScenarioCatalog.DEFAULT_APP_CODES.keySet()) {
            candidates.stream().filter(c -> apiCode.equals(c.plan().apiCode())).findFirst()
                    .ifPresent(c -> addIfRoom(selected, c, sampleLimit));
        }
        for (SampleCandidate candidate : candidates.stream().filter(c -> !c.result().success()).toList()) {
            addIfRoom(selected, candidate, sampleLimit);
            if (selected.size() >= sampleLimit) {
                break;
            }
        }
        for (SampleCandidate candidate : candidates) {
            addIfRoom(selected, candidate, sampleLimit);
            if (selected.size() >= sampleLimit) {
                break;
            }
        }
        selected.sort(Comparator.comparingInt(c -> c.plan().sequenceNo()));
        return selected;
    }

    private static void addIfRoom(List<SampleCandidate> selected, SampleCandidate candidate, int sampleLimit) {
        if (selected.size() >= sampleLimit || selected.stream().anyMatch(c -> c.plan().sequenceNo() == candidate.plan().sequenceNo())) {
            return;
        }
        selected.add(candidate);
    }

    private void updateProgress(String scenarioRunId, int sent, int success, int fail) {
        jdbcTemplate.update("""
                        UPDATE scenario_run
                        SET total_sent_requests = ?, success_count = ?, fail_count = ?
                        WHERE scenario_run_id = ?
                        """, sent, success, fail, scenarioRunId);
    }

    private int drainCompleted(CompletionService<CallOutcome> completionService, FullSummary summary,
            List<SampleCandidate> candidates) throws Exception {
        int drained = 0;
        Future<CallOutcome> future;
        while ((future = completionService.poll()) != null) {
            drained++;
            CallOutcome outcome = future.get();
            summary.record(outcome);
            candidates.add(new SampleCandidate(outcome.plan(), outcome.result()));
        }
        return drained;
    }

    private void updateCompleted(String scenarioRunId, Map<String, Object> summary) {
        jdbcTemplate.update("""
                        UPDATE scenario_run
                        SET status = 'COMPLETED', finished_at = NOW(), result_summary = ?
                        WHERE scenario_run_id = ?
                        """, toJson(summary), scenarioRunId);
    }

    private void updateFailed(String scenarioRunId, String message) {
        jdbcTemplate.update("""
                        UPDATE scenario_run
                        SET status = 'FAILED', finished_at = NOW(), error_message = ?
                        WHERE scenario_run_id = ?
                        """, truncate(message, 1024), scenarioRunId);
    }

    private void updateCancelled(String scenarioRunId) {
        jdbcTemplate.update("""
                        UPDATE scenario_run
                        SET status = 'CANCELLED', finished_at = NOW(), error_message = 'scenario run cancelled by request'
                        WHERE scenario_run_id = ?
                        """, scenarioRunId);
    }

    private Map<String, Object> runMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scenarioRunId", rs.getString("scenario_run_id"));
        map.put("scenarioId", rs.getString("scenario_id"));
        map.put("status", rs.getString("status"));
        map.put("totalPlannedRequests", rs.getInt("total_planned_requests"));
        map.put("totalSentRequests", rs.getInt("total_sent_requests"));
        map.put("successCount", rs.getInt("success_count"));
        map.put("failCount", rs.getInt("fail_count"));
        map.put("startedAt", stringTime(rs, "started_at"));
        map.put("finishedAt", stringTime(rs, "finished_at"));
        map.put("errorMessage", rs.getString("error_message"));
        map.put("loadProfile", Map.of(
                "targetGatewayBaseUrl", rs.getString("target_gateway_base_url"),
                "logicalDurationSeconds", rs.getInt("logical_duration_seconds"),
                "timeScale", rs.getBigDecimal("time_scale"),
                "rampUpSeconds", rs.getInt("ramp_up_seconds"),
                "steadySeconds", rs.getInt("steady_seconds"),
                "rampDownSeconds", rs.getInt("ramp_down_seconds"),
                "baseRps", rs.getBigDecimal("base_rps"),
                "peakRps", rs.getBigDecimal("peak_rps"),
                "maxConcurrency", rs.getInt("max_concurrency")
        ));
        return map;
    }

    private Map<String, Object> sampleMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scenarioRunId", rs.getString("scenario_run_id"));
        map.put("sequenceNo", rs.getInt("sequence_no"));
        map.put("apiCode", rs.getString("api_code"));
        map.put("appCode", rs.getString("app_code"));
        map.put("mockScenario", rs.getString("mock_scenario"));
        map.put("phase", rs.getString("phase"));
        map.put("traceId", rs.getString("trace_id"));
        map.put("requestId", rs.getString("request_id"));
        map.put("gatewayLogId", rs.getObject("gateway_log_id"));
        map.put("upstreamStatus", rs.getObject("upstream_status"));
        map.put("latencyMs", rs.getObject("latency_ms"));
        map.put("success", rs.getInt("success") == 1);
        map.put("errorCode", rs.getString("error_code"));
        map.put("calledAt", stringTime(rs, "called_at"));
        return map;
    }

    private void ensureExists(String scenarioRunId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scenario_run WHERE scenario_run_id = ?", Integer.class, scenarioRunId);
        if (count == null || count == 0) {
            throw new ScenarioRunNotFoundException("scenario run not found: " + scenarioRunId);
        }
    }

    static void validateScenarioRunId(String scenarioRunId) {
        if (!StringUtils.hasText(scenarioRunId) || scenarioRunId.contains("{") || scenarioRunId.contains("}")
                || !scenarioRunId.matches("^sr_[0-9A-Za-z_\\-]{8,64}$")) {
            throw new IllegalArgumentException("invalid scenarioRunId format");
        }
    }

    private static boolean isGetApi(String apiCode) {
        return "COURSE_TODAY".equals(apiCode) || "LECTURE_LIST".equals(apiCode)
                || "CAMPUS_NOTICE".equals(apiCode) || "LIBRARY_BORROW".equals(apiCode);
    }

    private static Map<String, Object> queryParams(String apiCode) {
        return switch (apiCode) {
            case "COURSE_TODAY" -> Map.of("studentNo", "2023001001", "date", "2026-06-19");
            case "LECTURE_LIST" -> Map.of("date", "2026-06-19");
            case "CAMPUS_NOTICE" -> Map.of("category", "exam");
            case "LIBRARY_BORROW" -> Map.of("studentNo", "2023001001");
            default -> Map.of();
        };
    }

    private static Map<String, Object> body(String apiCode, String appCode) {
        return switch (apiCode) {
            case "AUTH_LOGIN" -> Map.of("appCode", appCode, "studentNo", "2023001001", "timestamp", "2026-06-19T12:00:00", "nonce", "mock_nonce_runner", "signature", "mock_signature_valid");
            case "LECTURE_REGISTER" -> Map.of("lectureId", "lec_20260619_ai_001", "studentNo", "2023001001", "idempotencyKey", "idem_runner");
            case "VENUE_RESERVE" -> Map.of("venueId", "venue_report_hall_201", "studentNo", "2023001001", "reserveDate", "2026-06-20", "timeRange", "19:00-21:00", "idempotencyKey", "idem_venue_runner");
            default -> Map.of();
        };
    }

    private static int plannedRequests(int rampUp, int steady, int rampDown, double baseRps, double peakRps) {
        return (int) Math.round(((baseRps + peakRps) / 2.0 * rampUp) + (peakRps * steady) + ((peakRps + baseRps) / 2.0 * rampDown));
    }

    private static int positive(Integer value, int defaultValue, String field) {
        int result = value == null ? defaultValue : value;
        if (result <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
        return result;
    }

    private static int nonNegative(Integer value, int defaultValue, String field) {
        int result = value == null ? defaultValue : value;
        if (result < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
        return result;
    }

    private static double doubleValue(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    private static String stringTime(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toString();
    }

    private static String newRunId() {
        return "sr_" + RUN_ID_TIME.format(LocalDateTime.now()) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static long elapsedMillis(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }

    private static void sleepUntil(Instant started, long targetOffsetMillis) throws InterruptedException {
        long sleepMillis = targetOffsetMillis - elapsedMillis(started);
        if (sleepMillis > 0) {
            Thread.sleep(sleepMillis);
        }
    }

    private static void cancelFutures(List<Future<CallOutcome>> futures) {
        for (Future<CallOutcome> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private static void validateGatewayUrl(String targetGateway) {
        try {
            URI uri = URI.create(targetGateway);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("targetGatewayBaseUrl must be an http or https URL");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("targetGatewayBaseUrl must be an http or https URL");
        }
    }

    @PreDestroy
    public void shutdown() {
        schedulerExecutor.shutdownNow();
    }

    private record NormalizedRun(String scenarioRunId, String scenarioId, String targetGatewayBaseUrl,
                                 int logicalDurationSeconds, double timeScale, int rampUpSeconds,
                                 int steadySeconds, int rampDownSeconds, double baseRps, double peakRps,
                                 int maxConcurrency, Long randomSeed, int totalPlannedRequests,
                                 int sampleLimit, String note, ScenarioDefinition definition) {
    }

    private record CallPlan(int sequenceNo, long targetOffsetMillis, String phase, String apiCode, String appCode, String mockScenario) {
    }

    private record GatewayCallResult(boolean success, Integer upstreamStatus, Long gatewayLogId,
                                     String traceId, String requestId, String errorCode, int latencyMs) {
    }

    private record CallOutcome(CallPlan plan, GatewayCallResult result) {
    }

    private record SampleCandidate(CallPlan plan, GatewayCallResult result) {
    }

    private static class RunControl {
        private volatile boolean cancelled;
    }

    private static class FullSummary {
        private final int totalPlannedRequests;
        private final AtomicInteger sent = new AtomicInteger();
        private final AtomicInteger success = new AtomicInteger();
        private final AtomicInteger fail = new AtomicInteger();
        private final Map<String, AtomicInteger> apiDistribution = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> statusDistribution = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> mockScenarioDistribution = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> phaseDistribution = new ConcurrentHashMap<>();
        private final List<Integer> latencies = java.util.Collections.synchronizedList(new ArrayList<>());

        private FullSummary(int totalPlannedRequests) {
            this.totalPlannedRequests = totalPlannedRequests;
        }

        private void record(CallOutcome outcome) {
            sent.incrementAndGet();
            if (outcome.result().success()) {
                success.incrementAndGet();
            } else {
                fail.incrementAndGet();
            }
            increment(apiDistribution, outcome.plan().apiCode());
            increment(statusDistribution, String.valueOf(outcome.result().upstreamStatus()));
            increment(mockScenarioDistribution, outcome.plan().mockScenario());
            increment(phaseDistribution, outcome.plan().phase());
            latencies.add(outcome.result().latencyMs());
        }

        private Map<String, Object> toMap() {
            return Map.of(
                    "totalPlannedRequests", totalPlannedRequests,
                    "totalSentRequests", sent.get(),
                    "successCount", success.get(),
                    "failCount", fail.get(),
                    "apiDistribution", plain(apiDistribution),
                    "statusDistribution", plain(statusDistribution),
                    "mockScenarioDistribution", plain(mockScenarioDistribution),
                    "phaseDistribution", plain(phaseDistribution),
                    "latencySummary", latencySummary()
            );
        }

        private Map<String, Object> latencySummary() {
            List<Integer> copy;
            synchronized (latencies) {
                copy = new ArrayList<>(latencies);
            }
            copy.sort(Integer::compareTo);
            int count = copy.size();
            int max = count == 0 ? 0 : copy.get(count - 1);
            int avg = count == 0 ? 0 : (int) Math.round(copy.stream().mapToInt(Integer::intValue).average().orElse(0));
            int p95 = count == 0 ? 0 : copy.get(Math.min(count - 1, (int) Math.ceil(count * 0.95) - 1));
            return Map.of("count", count, "avgLatencyMs", avg, "maxLatencyMs", max, "p95LatencyMs", p95);
        }

        private static void increment(Map<String, AtomicInteger> map, String key) {
            map.computeIfAbsent(key == null ? "UNKNOWN" : key, ignored -> new AtomicInteger()).incrementAndGet();
        }

        private static Map<String, Integer> plain(Map<String, AtomicInteger> source) {
            Map<String, Integer> result = new LinkedHashMap<>();
            source.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.put(entry.getKey(), entry.getValue().get()));
            return result;
        }
    }
}
