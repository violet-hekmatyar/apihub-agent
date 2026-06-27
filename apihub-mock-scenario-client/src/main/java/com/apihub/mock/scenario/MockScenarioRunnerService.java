package com.apihub.mock.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
class MockScenarioRunnerService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ScenarioCatalog catalog;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Set<String> stopSignals = ConcurrentHashMap.newKeySet();
    private final String defaultGatewayBaseUrl;
    private final String campusApiBaseUrl;

    MockScenarioRunnerService(JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper,
                              ScenarioCatalog catalog,
                              @Value("${apihub.gateway.base-url:http://localhost:8080}") String defaultGatewayBaseUrl,
                              @Value("${apihub.mock-campus-api.base-url:http://localhost:8091}") String campusApiBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.catalog = catalog;
        this.defaultGatewayBaseUrl = defaultGatewayBaseUrl;
        this.campusApiBaseUrl = campusApiBaseUrl;
    }

    List<ScenarioProfileView> profiles() {
        return catalog.views();
    }

    ScenarioRunResponse start(ScenarioStartRequest request) {
        String profileCode = normalize(firstNonBlank(request.profileCode, "NORMAL_DAILY_INSPECTION"));
        String mode = normalize(firstNonBlank(request.mode, "FAST_DEMO"));
        if (catalog.profile(profileCode) == null) {
            throw new IllegalArgumentException("unknown profileCode: " + profileCode);
        }
        String runId = "mock_" + profileCode.toLowerCase(Locale.ROOT) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int duration = catalog.durationSeconds(profileCode, mode);
        String gatewayBaseUrl = firstNonBlank(request.targetGatewayBaseUrl, defaultGatewayBaseUrl).replaceAll("/+$", "");
        long seed = request.randomSeed == null ? System.currentTimeMillis() : request.randomSeed;
        double rpsScale = request.rpsScale == null || request.rpsScale <= 0 ? 1.0 : request.rpsScale;
        jdbcTemplate.update("""
                INSERT INTO mock_scenario_run (
                  scenario_run_id, profile_code, mode, status, target_gateway_base_url,
                  duration_seconds, random_seed, rps_scale, start_time, extra_json
                ) VALUES (?, ?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?)
                """, runId, profileCode, mode, gatewayBaseUrl, duration, seed, rpsScale,
                Timestamp.valueOf(LocalDateTime.now()), toJson(Map.of("includeTrafficSamples", Boolean.TRUE.equals(request.includeTrafficSamples))));
        executor.submit(() -> runScenario(runId, profileCode, mode, gatewayBaseUrl, duration, seed, rpsScale));
        return status(runId);
    }

    ScenarioRunResponse status(String scenarioRunId) {
        Map<String, Object> run = jdbcTemplate.queryForList("SELECT * FROM mock_scenario_run WHERE scenario_run_id = ? LIMIT 1", scenarioRunId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("scenario run not found: " + scenarioRunId));
        ScenarioRunResponse response = new ScenarioRunResponse();
        response.scenarioRunId = scenarioRunId;
        response.profileCode = String.valueOf(run.get("profile_code"));
        response.mode = String.valueOf(run.get("mode"));
        response.status = String.valueOf(run.get("status"));
        response.targetGatewayBaseUrl = String.valueOf(run.get("target_gateway_base_url"));
        response.durationSeconds = ((Number) run.get("duration_seconds")).intValue();
        response.totalRequestCount = ((Number) run.get("total_request_count")).intValue();
        response.successCount = ((Number) run.get("success_count")).intValue();
        response.failCount = ((Number) run.get("fail_count")).intValue();
        response.elapsedSeconds = elapsedSeconds(run.get("start_time"), run.get("end_time"));
        response.currentPhaseCode = catalog.phase(response.profileCode, response.mode, response.elapsedSeconds).phaseCode();
        response.statusCodeDistribution = distribution("gateway_response_status", scenarioRunId);
        response.apiDistribution = distribution("api_code", scenarioRunId);
        return response;
    }

    ScenarioRunResponse stop(String scenarioRunId) {
        stopSignals.add(scenarioRunId);
        jdbcTemplate.update("UPDATE mock_scenario_run SET status = 'STOPPING' WHERE scenario_run_id = ? AND status = 'RUNNING'", scenarioRunId);
        return status(scenarioRunId);
    }

    Map<String, Object> senderSummary(String scenarioRunId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scenarioRunId", scenarioRunId);
        summary.put("senderRequestCount", count("SELECT COUNT(*) FROM mock_scenario_client_request_log WHERE scenario_run_id = ?", scenarioRunId));
        summary.put("senderResponseCount", count("SELECT COUNT(*) FROM mock_scenario_client_request_log WHERE scenario_run_id = ? AND gateway_response_status IS NOT NULL", scenarioRunId));
        summary.put("successCount", count("SELECT COUNT(*) FROM mock_scenario_client_request_log WHERE scenario_run_id = ? AND success = 1", scenarioRunId));
        summary.put("failCount", count("SELECT COUNT(*) FROM mock_scenario_client_request_log WHERE scenario_run_id = ? AND success = 0", scenarioRunId));
        summary.put("apiDistribution", distribution("api_code", scenarioRunId));
        summary.put("phaseDistribution", distribution("phase_code", scenarioRunId));
        summary.put("statusCodeDistribution", distribution("gateway_response_status", scenarioRunId));
        summary.put("mockScenarioDistribution", distribution("mock_scenario", scenarioRunId));
        return summary;
    }

    Map<String, Object> reconciliationSummary(String scenarioRunId) {
        Map<String, Object> sender = senderSummary(scenarioRunId);
        Map<String, Object> gateway = gatewaySummary(scenarioRunId);
        Map<String, Object> upstream = upstreamSummary(scenarioRunId);
        int senderRequestCount = intValue(sender.get("senderRequestCount"));
        int gatewayReceivedCount = intValue(gateway.getOrDefault("gatewayReceivedCount", 0));
        int gatewayForwardedCount = intValue(gateway.getOrDefault("gatewayForwardedCount", gatewayReceivedCount));
        int upstreamReceivedCount = intValue(upstream.getOrDefault("upstreamReceivedCount", 0));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarioRunId", scenarioRunId);
        result.put("senderRequestCount", senderRequestCount);
        result.put("gatewayReceivedCount", gatewayReceivedCount);
        result.put("gatewayForwardedCount", gatewayForwardedCount);
        result.put("upstreamReceivedCount", upstreamReceivedCount);
        result.put("senderResponseCount", sender.get("senderResponseCount"));
        result.put("gatewayBlockedCount", Math.max(0, gatewayReceivedCount - gatewayForwardedCount));
        result.put("upstreamErrorCount", upstream.getOrDefault("errorCount", 0));
        result.put("matchedRequestCount", Math.min(gatewayReceivedCount, upstreamReceivedCount));
        result.put("mismatchCount", Math.abs(senderRequestCount - gatewayReceivedCount) + Math.abs(gatewayForwardedCount - upstreamReceivedCount));
        result.put("gatewaySummaryAvailable", gateway.getOrDefault("gatewaySummaryAvailable", false));
        return result;
    }

    private void runScenario(String runId, String profileCode, String mode, String gatewayBaseUrl, int duration, long seed, double rpsScale) {
        Random random = new Random(seed);
        int sequence = 0;
        List<Future<?>> pendingRequests = new ArrayList<>();
        try {
            long runStart = System.nanoTime();
            for (int elapsed = 0; elapsed < duration && !stopSignals.contains(runId); elapsed++) {
                PhaseSpec phase = catalog.phase(profileCode, mode, elapsed);
                int calls = Math.max(1, (int) Math.round(phase.targetRps() * rpsScale));
                for (int i = 0; i < calls; i++) {
                    sequence++;
                    PlannedCall call = planCall(runId, profileCode, mode, gatewayBaseUrl, phase, sequence, random);
                    pendingRequests.add(executor.submit(() -> sendOne(call)));
                }
                long targetElapsedMillis = (elapsed + 1) * 1000L;
                long actualElapsedMillis = (System.nanoTime() - runStart) / 1_000_000L;
                if (actualElapsedMillis < targetElapsedMillis) {
                    Thread.sleep(targetElapsedMillis - actualElapsedMillis);
                }
            }
            for (Future<?> pendingRequest : pendingRequests) {
                pendingRequest.get();
            }
            String status = stopSignals.remove(runId) ? "STOPPED" : "COMPLETED";
            finishRun(runId, status);
        } catch (Exception e) {
            jdbcTemplate.update("UPDATE mock_scenario_run SET status = 'FAILED', end_time = ?, extra_json = JSON_OBJECT('error', ?) WHERE scenario_run_id = ?",
                    Timestamp.valueOf(LocalDateTime.now()), truncate(e.getMessage(), 400), runId);
        }
    }

    private PlannedCall planCall(String runId, String profileCode, String mode, String gatewayBaseUrl, PhaseSpec phase, int sequence, Random random) {
        String apiCode = pick(phase.apiWeights(), random);
        String mockScenario = pick(catalog.scenarios(profileCode, phase.phaseCode(), apiCode), random);
        String appCode = catalog.callerAppCode(apiCode);
        String requestId = "req_" + runId + "_" + sequence;
        String url = gatewayBaseUrl + "/api/dev/gateway/invoke";
        return new PlannedCall(runId, requestId, profileCode, mode, phase.phaseCode(), apiCode, appCode, mockScenario, url, sequence);
    }

    private void sendOne(PlannedCall call) {
        long started = System.nanoTime();
        Integer status = null;
        String code = null;
        String traceId = null;
        boolean success = false;
        String error = null;
        try {
            Map<String, Object> body = gatewayBody(call.runId(), call.profileCode(), call.mode(), call.phaseCode(),
                    call.apiCode(), call.appCode(), call.mockScenario(), call.sequence());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(call.url()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Request-Id", call.requestId())
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            Map<String, Object> parsed = parse(response.body());
            Map<String, Object> data = asMap(parsed.get("data"));
            success = Boolean.TRUE.equals(data.get("success"));
            code = data.get("upstreamCode") == null ? null : String.valueOf(data.get("upstreamCode"));
            traceId = data.get("traceId") == null ? null : String.valueOf(data.get("traceId"));
        } catch (Exception e) {
            error = truncate(e.getMessage(), 512);
        }
        int latencyMs = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000L);
        insertRequestLog(call.runId(), call.requestId(), traceId, call.profileCode(), call.mode(), call.phaseCode(),
                call.apiCode(), call.appCode(), call.mockScenario(), call.url(), status, code, latencyMs, success, error);
        jdbcTemplate.update("""
                UPDATE mock_scenario_run
                SET total_request_count = total_request_count + 1,
                    success_count = success_count + ?,
                    fail_count = fail_count + ?
                WHERE scenario_run_id = ?
                """, success ? 1 : 0, success ? 0 : 1, call.runId());
    }

    private record PlannedCall(String runId, String requestId, String profileCode, String mode, String phaseCode,
                               String apiCode, String appCode, String mockScenario, String url, int sequence) {
    }

    private Map<String, Object> gatewayBody(String runId, String profileCode, String mode, String phaseCode,
                                            String apiCode, String appCode, String mockScenario, int sequence) {
        Map<String, Object> scenarioContext = new LinkedHashMap<>();
        scenarioContext.put("scenarioRunId", runId);
        scenarioContext.put("scenarioId", profileCode);
        scenarioContext.put("scenarioKey", mode);
        scenarioContext.put("phase", phaseCode);
        scenarioContext.put("sequenceNo", sequence);
        Map<String, Object> clientInfo = new LinkedHashMap<>();
        clientInfo.put("clientIp", "10.88.0." + ((sequence % 200) + 1));
        clientInfo.put("userAgent", "apihub-mock-scenario-client/1.0");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apiCode", apiCode);
        body.put("appCode", appCode);
        body.put("mockScenario", mockScenario);
        body.put("timeoutMs", 5000);
        body.put("clientInfo", clientInfo);
        body.put("scenarioContext", scenarioContext);
        body.put("queryParams", Map.of("studentId", "stu_" + sequence));
        body.put("body", Map.of("requestNo", "biz_" + sequence));
        return body;
    }

    private void insertRequestLog(String runId, String requestId, String traceId, String profileCode, String mode,
                                  String phaseCode, String apiCode, String appCode, String mockScenario, String url,
                                  Integer status, String code, int latencyMs, boolean success, String error) {
        jdbcTemplate.update("""
                INSERT INTO mock_scenario_client_request_log (
                  scenario_run_id, request_id, trace_id, profile_code, mode, phase_code, api_code,
                  caller_app_code, mock_scenario, target_gateway_url, send_time, gateway_response_status,
                  gateway_response_code, gateway_latency_ms, success, error_message, extra_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, runId, requestId, traceId, profileCode, mode, phaseCode, apiCode, appCode, mockScenario, url,
                Timestamp.valueOf(LocalDateTime.now()), status, code, latencyMs, success ? 1 : 0, error, "{}");
    }

    private void finishRun(String runId, String status) {
        jdbcTemplate.update("UPDATE mock_scenario_run SET status = ?, end_time = ? WHERE scenario_run_id = ?",
                status, Timestamp.valueOf(LocalDateTime.now()), runId);
    }

    private Map<String, Object> gatewaySummary(String scenarioRunId) {
        try {
            String baseUrl = String.valueOf(jdbcTemplate.queryForMap("SELECT target_gateway_base_url FROM mock_scenario_run WHERE scenario_run_id = ?", scenarioRunId).get("target_gateway_base_url"));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/api/dev/gateway/scenario-runs/" + scenarioRunId + "/gateway-summary"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            Map<String, Object> response = parse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
            return asMap(response.get("data"));
        } catch (Exception e) {
            return new LinkedHashMap<>(Map.of("gatewaySummaryAvailable", false, "note", "Gateway summary endpoint is not available yet."));
        }
    }

    private Map<String, Object> upstreamSummary(String scenarioRunId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(campusApiBaseUrl.replaceAll("/+$", "") + "/api/mock-campus/scenario-runs/" + scenarioRunId + "/upstream-summary"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            Map<String, Object> response = parse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
            return asMap(response.get("data"));
        } catch (Exception e) {
            return new LinkedHashMap<>(Map.of("upstreamSummaryAvailable", false, "note", "Mock campus summary endpoint is not available yet."));
        }
    }

    private String pick(List<WeightedItem> items, Random random) {
        int total = items.stream().mapToInt(WeightedItem::weight).sum();
        int roll = random.nextInt(total) + 1;
        int sum = 0;
        for (WeightedItem item : items) {
            sum += item.weight();
            if (roll <= sum) {
                return item.code();
            }
        }
        return items.get(items.size() - 1).code();
    }

    private Map<String, Integer> distribution(String column, String scenarioRunId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + column + " AS k, COUNT(*) AS c FROM mock_scenario_client_request_log WHERE scenario_run_id = ? GROUP BY " + column,
                scenarioRunId);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get("k")), ((Number) row.get("c")).intValue());
        }
        return result;
    }

    private int count(String sql, String scenarioRunId) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, scenarioRunId);
        return value == null ? 0 : value;
    }

    private int elapsedSeconds(Object start, Object end) {
        if (start == null) {
            return 0;
        }
        long startMs;
        if (start instanceof java.sql.Timestamp timestamp) {
            startMs = timestamp.getTime();
        } else if (start instanceof LocalDateTime localDateTime) {
            startMs = Timestamp.valueOf(localDateTime).getTime();
        } else {
            return 0;
        }
        long endMs;
        if (end instanceof java.sql.Timestamp timestamp) {
            endMs = timestamp.getTime();
        } else if (end instanceof LocalDateTime localDateTime) {
            endMs = Timestamp.valueOf(localDateTime).getTime();
        } else {
            endMs = System.currentTimeMillis();
        }
        return (int) Math.max(0, (endMs - startMs) / 1000);
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
