package com.apihub.agent.service;

import com.apihub.agent.model.dto.QueryAlertEventsRequest;
import com.apihub.agent.model.dto.QueryApiCallStatsRequest;
import com.apihub.agent.model.dto.QueryApiDocsRequest;
import com.apihub.agent.model.dto.QueryApiInfoRequest;
import com.apihub.agent.model.dto.QueryCampusEventsRequest;
import com.apihub.agent.model.dto.QueryGatewayLogsRequest;
import com.apihub.agent.model.dto.QueryRateLimitRuleRequest;
import com.apihub.agent.model.dto.ToolChainEvalRunRequest;
import com.apihub.agent.model.tool.ToolContext;
import com.apihub.agent.model.tool.ToolResult;
import com.apihub.agent.model.vo.ToolChainEvalResultVO;
import com.apihub.agent.model.vo.ToolChainEvalStepVO;
import com.apihub.agent.model.vo.ToolChainScenarioVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
public class ToolChainEvalService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_START_TIME = "2026-06-19 00:00:00";
    private static final String DEFAULT_END_TIME = "2026-06-19 23:59:59";

    private final ToolService toolService;
    private final Map<String, ScenarioDefinition> scenarios;

    public ToolChainEvalService(ToolService toolService) {
        this.toolService = toolService;
        this.scenarios = buildScenarios();
    }

    public List<ToolChainScenarioVO> listScenarios() {
        return scenarios.values().stream().map(this::toScenarioVO).toList();
    }

    public ToolChainEvalResultVO run(ToolChainEvalRunRequest request, Long userId, String requestId) {
        long started = System.nanoTime();
        String scenarioCode = normalizeCode(request == null ? null : request.getScenarioCode());
        ScenarioDefinition scenario = scenarios.get(scenarioCode);
        if (scenario == null) {
            ToolChainEvalResultVO result = baseFailure(scenarioCode, "SCENARIO_NOT_FOUND", "scenario not found: " + scenarioCode);
            result.setLatencyMs(elapsedMs(started));
            return result;
        }

        String startTime = StringUtils.hasText(request.getStartTime()) ? request.getStartTime().trim() : DEFAULT_START_TIME;
        String endTime = StringUtils.hasText(request.getEndTime()) ? request.getEndTime().trim() : DEFAULT_END_TIME;
        if (!isValidTimeRange(startTime, endTime)) {
            ToolChainEvalResultVO result = baseFailure(scenarioCode, "INVALID_ARGUMENT", "startTime must be before or equal to endTime");
            result.setScenarioName(scenario.name());
            result.setApiCode(resolveApiCode(request, scenario));
            result.setStartTime(startTime);
            result.setEndTime(endTime);
            result.setLatencyMs(elapsedMs(started));
            return result;
        }

        String apiCode = resolveApiCode(request, scenario);
        ToolContext context = toolService.buildContext(userId, requestId);
        ToolChainEvalResultVO result = new ToolChainEvalResultVO();
        result.setSuccess(true);
        result.setScenarioCode(scenario.code());
        result.setScenarioName(scenario.name());
        result.setApiCode(apiCode);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setTraceId(context.getTraceId());

        List<ToolChainEvalStepVO> steps = new ArrayList<>();
        List<Object> mergedEvidence = new ArrayList<>();
        Set<String> evidenceKeys = new LinkedHashSet<>();
        Map<String, Object> aggregate = new LinkedHashMap<>();

        int stepNo = 1;
        for (StepDefinition step : scenario.steps()) {
            ToolResult toolResult = step.runner().apply(new StepInput(apiCode, startTime, endTime, context));
            ToolChainEvalStepVO stepVO = buildStep(stepNo, step, toolResult);
            steps.add(stepVO);
            mergeEvidence(mergedEvidence, evidenceKeys, toolResult.getEvidenceItems());
            mergeAggregate(aggregate, toolResult);
            if (!toolResult.isSuccess()) {
                break;
            }
            stepNo++;
        }

        int passed = (int) steps.stream().filter(ToolChainEvalStepVO::isSuccess).count();
        int failed = steps.size() - passed;
        result.setSteps(steps);
        result.setStepCount(steps.size());
        result.setPassedStepCount(passed);
        result.setFailedStepCount(failed);
        result.setSuccess(failed == 0);
        result.setMergedEvidenceItems(mergedEvidence);
        result.setApiName((String) aggregate.get("apiName"));
        result.setRiskLevel(resolveRiskLevel(scenario, aggregate, failed));
        result.setRiskReasons(buildRiskReasons(scenario, aggregate, failed));
        result.setTemplateConclusion(buildConclusion(scenario, aggregate, failed));
        if (failed > 0) {
            ToolChainEvalStepVO failedStep = steps.get(steps.size() - 1);
            result.setErrorCode(failedStep.getErrorCode());
            result.setErrorMessage(failedStep.getSummary());
        }
        result.setLatencyMs(elapsedMs(started));
        return result;
    }

    private Map<String, ScenarioDefinition> buildScenarios() {
        Map<String, ScenarioDefinition> map = new LinkedHashMap<>();
        map.put("AUTH_LOGIN_403_DIAG", new ScenarioDefinition(
                "AUTH_LOGIN_403_DIAG",
                "Unified login 403 diagnosis chain",
                "AUTH_LOGIN",
                "Validate an authentication or signature failure evidence chain.",
                List.of(
                        new StepDefinition("queryApiInfo", "Confirm API metadata and access boundary.", input -> queryApiInfo(input.apiCode(), input.context())),
                        new StepDefinition("queryApiCallStats", "Check call volume, failure rate, and latency.", input -> queryApiCallStats(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryGatewayLogs", "Inspect HTTP 403 gateway log samples.", input -> queryGatewayLogs(input.apiCode(), input.startTime(), input.endTime(), 403, "signature", input.context())),
                        new StepDefinition("queryAlertEvents", "Check active alert events for the API.", input -> queryAlertEvents(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryApiDocs", "Retrieve signature and 403 troubleshooting documentation.", input -> queryApiDocs(input.apiCode(), "signature", null, input.context()))
                )
        ));
        map.put("LECTURE_REGISTER_PEAK", new ScenarioDefinition(
                "LECTURE_REGISTER_PEAK",
                "Lecture registration peak warning chain",
                "LECTURE_REGISTER",
                "Validate business-event-driven peak risk analysis.",
                List.of(
                        new StepDefinition("queryApiInfo", "Confirm API metadata and ownership.", input -> queryApiInfo(input.apiCode(), input.context())),
                        new StepDefinition("queryCampusEvents", "Find campus business events related to this API.", input -> queryCampusEvents(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryApiCallStats", "Check traffic, latency, and rate-limit counts.", input -> queryApiCallStats(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryGatewayLogs", "Inspect rate-limit gateway log samples.", input -> queryGatewayLogs(input.apiCode(), input.startTime(), input.endTime(), 429, "rate", input.context())),
                        new StepDefinition("queryRateLimitRule", "Review active rate-limit rules.", input -> queryRateLimitRule(input.apiCode(), input.context())),
                        new StepDefinition("queryAlertEvents", "Check alert event severity and status.", input -> queryAlertEvents(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryApiDocs", "Retrieve peak and rate-limit guidance.", input -> queryApiDocs(input.apiCode(), "rate", null, input.context()))
                )
        ));
        map.put("VENUE_RESERVE_IDEMPOTENCY", new ScenarioDefinition(
                "VENUE_RESERVE_IDEMPOTENCY",
                "Venue reservation duplicate request and idempotency risk chain",
                "VENUE_RESERVE",
                "Validate duplicate submit, reservation conflict, and idempotency risk analysis.",
                List.of(
                        new StepDefinition("queryApiInfo", "Confirm API metadata and write-risk profile.", input -> queryApiInfo(input.apiCode(), input.context())),
                        new StepDefinition("queryApiCallStats", "Check traffic, failures, and rate limits.", input -> queryApiCallStats(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryGatewayLogs", "Inspect duplicate request and idempotency log samples.", input -> queryGatewayLogs(input.apiCode(), input.startTime(), input.endTime(), null, "duplicate", input.context())),
                        new StepDefinition("queryCampusEvents", "Find reservation-open campus event context.", input -> queryCampusEvents(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryAlertEvents", "Check duplicate-submit alert context.", input -> queryAlertEvents(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryApiDocs", "Retrieve idempotency documentation.", input -> queryApiDocs(input.apiCode(), "idempotency", null, input.context()))
                )
        ));
        map.put("LIBRARY_BORROW_DEPENDENCY", new ScenarioDefinition(
                "LIBRARY_BORROW_DEPENDENCY",
                "Library borrow downstream dependency exception chain",
                "LIBRARY_BORROW",
                "Validate downstream dependency timeout and 5xx analysis.",
                List.of(
                        new StepDefinition("queryApiInfo", "Confirm API metadata and dependency profile.", input -> queryApiInfo(input.apiCode(), input.context())),
                        new StepDefinition("queryApiCallStats", "Check 5xx and latency indicators.", input -> queryApiCallStats(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryGatewayLogs", "Inspect dependency timeout gateway logs.", input -> queryGatewayLogs(input.apiCode(), input.startTime(), input.endTime(), null, "timeout", input.context())),
                        new StepDefinition("queryAlertEvents", "Check downstream dependency alerts.", input -> queryAlertEvents(input.apiCode(), input.startTime(), input.endTime(), input.context())),
                        new StepDefinition("queryApiDocs", "Retrieve dependency timeout troubleshooting guidance.", input -> queryApiDocs(input.apiCode(), "timeout", null, input.context()))
                )
        ));
        return map;
    }

    private ToolResult queryApiInfo(String apiCode, ToolContext context) {
        QueryApiInfoRequest request = new QueryApiInfoRequest();
        request.setApiCode(apiCode);
        request.setIncludeRateLimit(true);
        request.setIncludeConsumerApps(true);
        return toolService.queryApiInfoWithTrace(request, context);
    }

    private ToolResult queryApiCallStats(String apiCode, String startTime, String endTime, ToolContext context) {
        QueryApiCallStatsRequest request = new QueryApiCallStatsRequest();
        request.setApiCode(apiCode);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        return toolService.queryApiCallStatsWithTrace(request, context);
    }

    private ToolResult queryGatewayLogs(String apiCode, String startTime, String endTime, Integer httpStatus, String keyword, ToolContext context) {
        QueryGatewayLogsRequest request = new QueryGatewayLogsRequest();
        request.setApiCode(apiCode);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setHttpStatus(httpStatus);
        request.setKeyword(keyword);
        request.setLimit(20);
        return toolService.queryGatewayLogsWithTrace(request, context);
    }

    private ToolResult queryRateLimitRule(String apiCode, ToolContext context) {
        QueryRateLimitRuleRequest request = new QueryRateLimitRuleRequest();
        request.setApiCode(apiCode);
        request.setIncludeInactive(false);
        return toolService.queryRateLimitRuleWithTrace(request, context);
    }

    private ToolResult queryAlertEvents(String apiCode, String startTime, String endTime, ToolContext context) {
        QueryAlertEventsRequest request = new QueryAlertEventsRequest();
        request.setApiCode(apiCode);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setLimit(20);
        return toolService.queryAlertEventsWithTrace(request, context);
    }

    private ToolResult queryCampusEvents(String apiCode, String startTime, String endTime, ToolContext context) {
        QueryCampusEventsRequest request = new QueryCampusEventsRequest();
        request.setApiCode(apiCode);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setIncludeRelatedApis(true);
        request.setLimit(20);
        return toolService.queryCampusEventsWithTrace(request, context);
    }

    private ToolResult queryApiDocs(String apiCode, String keyword, String docType, ToolContext context) {
        QueryApiDocsRequest request = new QueryApiDocsRequest();
        request.setApiCode(apiCode);
        request.setKeyword(keyword);
        request.setDocType(docType);
        request.setLimit(5);
        return toolService.queryApiDocsWithTrace(request, context);
    }

    private ToolChainEvalStepVO buildStep(int stepNo, StepDefinition step, ToolResult result) {
        ToolChainEvalStepVO stepVO = new ToolChainEvalStepVO();
        stepVO.setStepNo(stepNo);
        stepVO.setToolName(step.toolName());
        stepVO.setPurpose(step.purpose());
        stepVO.setSuccess(result.isSuccess());
        stepVO.setSummary(result.getSummary());
        stepVO.setErrorCode(result.getErrorCode());
        stepVO.setLatencyMs(result.getLatencyMs());
        stepVO.setEvidenceCount(result.getEvidenceItems() == null ? 0 : result.getEvidenceItems().size());
        stepVO.setKeyData(extractKeyData(result));
        return stepVO;
    }

    private Map<String, Object> extractKeyData(ToolResult result) {
        Map<String, Object> keyData = new LinkedHashMap<>();
        if (!(result.getData() instanceof Map<?, ?> data)) {
            return keyData;
        }
        putIfPresent(keyData, "apiCode", data.get("apiCode"));
        putIfPresent(keyData, "apiName", data.get("apiName"));
        putIfPresent(keyData, "status", data.get("status"));
        putIfPresent(keyData, "riskLevel", data.get("riskLevel"));
        putIfPresent(keyData, "totalCallCount", data.get("totalCallCount"));
        putIfPresent(keyData, "failRate", data.get("failRate"));
        putIfPresent(keyData, "maxP95LatencyMs", data.get("maxP95LatencyMs"));
        putIfPresent(keyData, "totalMatched", data.get("totalMatched"));
        putIfPresent(keyData, "returnedCount", data.get("returnedCount"));
        putIfPresent(keyData, "statusDistribution", data.get("statusDistribution"));
        putIfPresent(keyData, "topErrorMessages", data.get("topErrorMessages"));
        putIfPresent(keyData, "ruleCount", data.get("ruleCount"));
        putIfPresent(keyData, "activeRuleCount", data.get("activeRuleCount"));
        putIfPresent(keyData, "highestSeverity", data.get("highestSeverity"));
        putIfPresent(keyData, "openAlertCount", data.get("openAlertCount"));
        putIfPresent(keyData, "relatedApiCodes", data.get("relatedApiCodes"));
        putIfPresent(keyData, "searchMode", data.get("searchMode"));
        putIfPresent(keyData, "docTypeDistribution", data.get("docTypeDistribution"));
        return keyData;
    }

    private void mergeAggregate(Map<String, Object> aggregate, ToolResult result) {
        if (!(result.getData() instanceof Map<?, ?> data)) {
            return;
        }
        copyIfAbsent(aggregate, "apiName", data.get("apiName"));
        copyIfAbsent(aggregate, "statsRiskLevel", data.get("riskLevel"));
        copyIfAbsent(aggregate, "failRate", data.get("failRate"));
        copyIfAbsent(aggregate, "maxP95LatencyMs", data.get("maxP95LatencyMs"));
        copyIfAbsent(aggregate, "openAlertCount", data.get("openAlertCount"));
        copyIfAbsent(aggregate, "highestSeverity", data.get("highestSeverity"));
        copyIfAbsent(aggregate, "relatedApiCodes", data.get("relatedApiCodes"));
        copyIfAbsent(aggregate, "statusDistribution", data.get("statusDistribution"));
    }

    private void mergeEvidence(List<Object> mergedEvidence, Set<String> evidenceKeys, List<Object> evidenceItems) {
        if (evidenceItems == null) {
            return;
        }
        for (Object item : evidenceItems) {
            if (!(item instanceof Map<?, ?> evidence)) {
                continue;
            }
            String key = evidence.get("evidenceType") + "|" + evidence.get("sourceType") + "|" + evidence.get("sourceId");
            if (!evidenceKeys.add(key)) {
                continue;
            }
            Map<String, Object> next = new LinkedHashMap<>();
            evidence.forEach((mapKey, mapValue) -> next.put(String.valueOf(mapKey), mapValue));
            if (next.get("quote") instanceof String quote && quote.length() > 220) {
                next.put("quote", quote.substring(0, 220) + "...");
            }
            mergedEvidence.add(next);
        }
    }

    private String resolveRiskLevel(ScenarioDefinition scenario, Map<String, Object> aggregate, int failed) {
        if (failed > 0) {
            return "UNKNOWN";
        }
        String highestSeverity = (String) aggregate.get("highestSeverity");
        if ("CRITICAL".equals(highestSeverity) || "HIGH".equals(highestSeverity)) {
            return "HIGH";
        }
        Object statsRiskLevel = aggregate.get("statsRiskLevel");
        if (statsRiskLevel != null) {
            return String.valueOf(statsRiskLevel);
        }
        if ("LECTURE_REGISTER_PEAK".equals(scenario.code()) || "VENUE_RESERVE_IDEMPOTENCY".equals(scenario.code())) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> buildRiskReasons(ScenarioDefinition scenario, Map<String, Object> aggregate, int failed) {
        List<String> reasons = new ArrayList<>();
        if (failed > 0) {
            reasons.add("tool chain stopped because a required step failed");
            return reasons;
        }
        if (aggregate.get("highestSeverity") != null) {
            reasons.add("alert severity: " + aggregate.get("highestSeverity"));
        }
        if (aggregate.get("failRate") != null) {
            reasons.add("observed fail rate: " + aggregate.get("failRate"));
        }
        if (aggregate.get("maxP95LatencyMs") != null) {
            reasons.add("observed max P95 latency: " + aggregate.get("maxP95LatencyMs") + "ms");
        }
        if (aggregate.get("relatedApiCodes") != null) {
            reasons.add("related APIs from campus event: " + aggregate.get("relatedApiCodes"));
        }
        if (reasons.isEmpty()) {
            reasons.add("scenario " + scenario.code() + " completed with deterministic tool evidence");
        }
        return reasons;
    }

    private String buildConclusion(ScenarioDefinition scenario, Map<String, Object> aggregate, int failed) {
        if (failed > 0) {
            return "The deterministic tool chain did not complete. Review the failed step and its error code before using this scenario as an Agent evidence baseline.";
        }
        return switch (scenario.code()) {
            case "AUTH_LOGIN_403_DIAG" -> "AUTH_LOGIN shows a 403-related diagnostic chain. Check signature rules, timestamp, nonce, token expiry, and caller configuration first. If gateway logs concentrate on one caller, prioritize that caller configuration. This is a diagnostic suggestion, not an automatic fix.";
            case "LECTURE_REGISTER_PEAK" -> "LECTURE_REGISTER risk is consistent with a lecture signup business peak. Observe call volume, P95, 429 logs, duplicate requests, and rate-limit thresholds. Related AUTH_LOGIN and LECTURE_LIST APIs should be watched together. This is business peak risk context, not a single-endpoint fault conclusion.";
            case "VENUE_RESERVE_IDEMPOTENCY" -> "VENUE_RESERVE requires attention to duplicate submit and idempotency. If logs show duplicate request, reservation conflict, or missing idempotency key hints, check retry behavior, idempotency keys, business locks, and conflict handling. No automatic remediation is performed.";
            case "LIBRARY_BORROW_DEPENDENCY" -> "LIBRARY_BORROW evidence points to downstream dependency timeout risk. Check 5xx rate, gateway timeout logs, downstream service health, timeout settings, and degrade behavior before writing an incident report.";
            default -> "Tool chain completed with deterministic evidence. This is not a formal Agent report.";
        };
    }

    private ToolChainScenarioVO toScenarioVO(ScenarioDefinition scenario) {
        ToolChainScenarioVO vo = new ToolChainScenarioVO();
        vo.setScenarioCode(scenario.code());
        vo.setScenarioName(scenario.name());
        vo.setDefaultApiCode(scenario.defaultApiCode());
        vo.setPurpose(scenario.purpose());
        vo.setToolSequence(scenario.steps().stream().map(StepDefinition::toolName).toList());
        return vo;
    }

    private ToolChainEvalResultVO baseFailure(String scenarioCode, String errorCode, String errorMessage) {
        ToolChainEvalResultVO result = new ToolChainEvalResultVO();
        result.setSuccess(false);
        result.setScenarioCode(scenarioCode);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        result.setRiskLevel("UNKNOWN");
        result.setTemplateConclusion(errorMessage);
        return result;
    }

    private String resolveApiCode(ToolChainEvalRunRequest request, ScenarioDefinition scenario) {
        return StringUtils.hasText(request == null ? null : request.getApiCode())
                ? request.getApiCode().trim().toUpperCase()
                : scenario.defaultApiCode();
    }

    private boolean isValidTimeRange(String startTime, String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime, FORMATTER);
            LocalDateTime end = LocalDateTime.parse(endTime, FORMATTER);
            return !start.isAfter(end);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void copyIfAbsent(Map<String, Object> target, String key, Object value) {
        if (value != null && !target.containsKey(key)) {
            target.put(key, value);
        }
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private record ScenarioDefinition(String code, String name, String defaultApiCode, String purpose,
                                      List<StepDefinition> steps) {
    }

    private record StepDefinition(String toolName, String purpose, Function<StepInput, ToolResult> runner) {
    }

    private record StepInput(String apiCode, String startTime, String endTime, ToolContext context) {
    }
}
