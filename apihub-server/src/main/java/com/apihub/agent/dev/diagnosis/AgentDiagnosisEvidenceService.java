package com.apihub.agent.dev.diagnosis;

import com.apihub.agent.common.ErrorCode;
import com.apihub.agent.common.TraceContext;
import com.apihub.agent.exception.BusinessException;
import com.apihub.agent.model.dto.AgentDiagnoseRequest;
import com.apihub.agent.model.dto.QueryAlertEventsRequest;
import com.apihub.agent.model.dto.QueryApiCallStatsRequest;
import com.apihub.agent.model.dto.QueryApiDocsRequest;
import com.apihub.agent.model.dto.QueryApiInfoRequest;
import com.apihub.agent.model.dto.QueryCampusEventsRequest;
import com.apihub.agent.model.dto.QueryGatewayLogsRequest;
import com.apihub.agent.model.dto.QueryRateLimitRuleRequest;
import com.apihub.agent.model.tool.ToolContext;
import com.apihub.agent.model.tool.ToolResult;
import com.apihub.agent.model.vo.AgentDiagnoseResponseVO;
import com.apihub.agent.model.vo.AgentEvidenceItemVO;
import com.apihub.agent.model.vo.AgentReportDetailVO;
import com.apihub.agent.service.ToolService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentDiagnosisEvidenceService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String SOURCE = "AgentDiagnosisEvidenceV1";

    private final ToolService toolService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentDiagnosisEvidenceService(ToolService toolService, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.toolService = toolService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentDiagnoseResponseVO diagnose(AgentDiagnoseRequest request, Long requestUserId, String requestId) {
        long started = System.nanoTime();
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "request body is required");
        }

        String apiCode = normalizeApiCode(request.getApiCode());
        if (!StringUtils.hasText(apiCode)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "apiCode is required");
        }
        LocalDateTime startTime = parseRequiredTime(request.getStartTime(), "startTime");
        LocalDateTime endTime = parseRequiredTime(request.getEndTime(), "endTime");
        if (startTime.isAfter(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "startTime must be before or equal to endTime");
        }
        String diagnosisMode = normalizeMode(request.getDiagnosisMode());
        Long userId = requestUserId == null ? 1L : requestUserId;
        Map<String, Object> user = findActiveUser(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found: " + userId);
        }

        boolean forceRebuild = request.getForceRebuild() == null || Boolean.TRUE.equals(request.getForceRebuild());
        int deletedOldReports = forceRebuild ? deleteOldReports(apiCode, startTime, endTime, request.getScenarioRunId(), request.getAlertId(), diagnosisMode) : 0;

        String traceId = TraceContext.getTraceId();
        Long sessionId = createSession(user, traceId, apiCode, startTime, endTime, request, diagnosisMode, deletedOldReports);
        ToolContext context = toolService.buildContext(userId, requestId);
        context.setSessionId(sessionId);
        context.setTraceId(traceId);
        context.setSource("DEV_AGENT_DIAGNOSIS");

        List<ToolResult> toolResults = runTools(apiCode, startTime, endTime, context);
        DiagnosisDecision decision = decide(apiCode, toolResults);
        List<AgentEvidenceItemVO> evidenceItems = mergeEvidence(toolResults);
        Long reportId = insertReport(sessionId, traceId, userId, apiCode, startTime, endTime, request,
                diagnosisMode, decision, evidenceItems, toolResults.size(), elapsedMs(started));
        int persistedEvidenceCount = insertEvidenceItems(sessionId, traceId, reportId, evidenceItems);
        jdbcTemplate.update(
                "UPDATE agent_report SET evidence_count = ?, tool_call_count = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                persistedEvidenceCount,
                toolResults.size(),
                reportId
        );
        updateSession(sessionId, "SUCCESS", elapsedMs(started), apiCode, reportId, decision);

        AgentDiagnoseResponseVO response = new AgentDiagnoseResponseVO();
        response.setReportId(reportId);
        response.setSessionId(sessionId);
        response.setTraceId(traceId);
        response.setApiCode(apiCode);
        response.setApiName(findApiName(toolResults));
        response.setStartTime(FORMATTER.format(startTime));
        response.setEndTime(FORMATTER.format(endTime));
        response.setScenarioRunId(request.getScenarioRunId());
        response.setAlertId(request.getAlertId());
        response.setDiagnosisMode(diagnosisMode);
        response.setStatus("COMPLETED");
        response.setRiskLevel(decision.riskLevel());
        response.setSummary(decision.summary());
        response.setRootCause(decision.rootCause());
        response.setRecommendation(decision.recommendation());
        response.setEvidenceCount(persistedEvidenceCount);
        response.setToolCallCount(toolResults.size());
        response.setLatencyMs(elapsedMs(started));
        response.setItems(evidenceItems);
        return response;
    }

    public AgentReportDetailVO getReport(Long reportId) {
        if (reportId == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "reportId is required");
        }
        Map<String, Object> report;
        try {
            report = jdbcTemplate.queryForMap(
                    """
                    SELECT id, report_code, session_id, trace_id, report_type, title, summary,
                           risk_level, content_md, created_by, evidence_count, tool_call_count,
                           duration_ms, status, error_code, error_message, generated_at,
                           extra_info, remark, created_at, updated_at
                    FROM agent_report
                    WHERE id = ?
                    """,
                    reportId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "report not found: " + reportId);
        }

        AgentReportDetailVO detail = new AgentReportDetailVO();
        detail.setReport(toReportMap(report));
        detail.setEvidenceItems(queryEvidenceItems(reportId));
        detail.setToolCallTraces(queryToolCallTraces(
                ((Number) report.get("session_id")).longValue(),
                String.valueOf(report.get("trace_id"))
        ));
        return detail;
    }

    private List<ToolResult> runTools(String apiCode, LocalDateTime startTime, LocalDateTime endTime, ToolContext context) {
        String start = FORMATTER.format(startTime);
        String end = FORMATTER.format(endTime);
        List<ToolResult> results = new ArrayList<>();

        QueryApiInfoRequest apiInfo = new QueryApiInfoRequest();
        apiInfo.setApiCode(apiCode);
        apiInfo.setIncludeRateLimit(true);
        apiInfo.setIncludeConsumerApps(true);
        results.add(toolService.queryApiInfoWithTrace(apiInfo, context));

        QueryApiCallStatsRequest stats = new QueryApiCallStatsRequest();
        stats.setApiCode(apiCode);
        stats.setStartTime(start);
        stats.setEndTime(end);
        results.add(toolService.queryApiCallStatsWithTrace(stats, context));

        QueryAlertEventsRequest alerts = new QueryAlertEventsRequest();
        alerts.setApiCode(apiCode);
        alerts.setStartTime(start);
        alerts.setEndTime(end);
        alerts.setStatus("OPEN");
        alerts.setLimit(20);
        results.add(toolService.queryAlertEventsWithTrace(alerts, context));

        QueryGatewayLogsRequest logs = new QueryGatewayLogsRequest();
        logs.setApiCode(apiCode);
        logs.setStartTime(start);
        logs.setEndTime(end);
        logs.setLimit(20);
        results.add(toolService.queryGatewayLogsWithTrace(logs, context));

        QueryRateLimitRuleRequest rateLimit = new QueryRateLimitRuleRequest();
        rateLimit.setApiCode(apiCode);
        rateLimit.setIncludeInactive(false);
        results.add(toolService.queryRateLimitRuleWithTrace(rateLimit, context));

        QueryCampusEventsRequest campusEvents = new QueryCampusEventsRequest();
        campusEvents.setApiCode(apiCode);
        campusEvents.setStartTime(start);
        campusEvents.setEndTime(end);
        campusEvents.setIncludeRelatedApis(true);
        campusEvents.setLimit(20);
        results.add(toolService.queryCampusEventsWithTrace(campusEvents, context));

        QueryApiDocsRequest docs = new QueryApiDocsRequest();
        docs.setApiCode(apiCode);
        docs.setKeyword(keywordFor(apiCode));
        docs.setLimit(5);
        results.add(toolService.queryApiDocsWithTrace(docs, context));

        return results;
    }

    private DiagnosisDecision decide(String apiCode, List<ToolResult> toolResults) {
        Map<String, Object> stats = dataOf(toolResults, "queryApiCallStats");
        Map<String, Object> alerts = dataOf(toolResults, "queryAlertEvents");

        double failRate = doubleValue(stats.get("failRate"));
        long rateLimitCount = longValue(firstNonNull(stats.get("totalRateLimitedCount"), stats.get("rateLimitCount")));
        int maxP95 = intValue(stats.get("maxP95LatencyMs"));
        long error5xx = longValue(firstNonNull(stats.get("total5xxCount"), stats.get("error5xxCount")));
        int openAlertCount = intValue(alerts.get("openAlertCount"));
        String highestSeverity = stringValue(alerts.get("highestSeverity"));
        Set<String> alertTypes = alertTypes(alerts);

        String riskLevel = "NORMAL";
        String summary = apiCode + " current window has no obvious deterministic anomaly.";
        String rootCause = "No active alert or threshold breach was detected in the selected window.";
        String recommendation = "Continue observation and keep the current API gateway and statistics monitoring enabled.";

        if (alertTypes.contains("HIGH_RATE_LIMIT") || rateLimitCount > 0) {
            riskLevel = criticalIf(highestSeverity, failRate, maxP95, "WARNING");
            summary = apiCode + " shows rate-limit pressure in the selected diagnosis window.";
            rootCause = "Peak requests are concentrated enough to trigger rate-limit policy or 429 gateway responses.";
            recommendation = "Review rate-limit thresholds, queue hints, caching, async handling, and temporary capacity settings.";
        } else if (alertTypes.contains("HIGH_FAILURE_RATE") || failRate >= 0.10d) {
            riskLevel = failRate >= 0.30d ? "CRITICAL" : "WARNING";
            summary = apiCode + " shows an elevated failure rate in the selected diagnosis window.";
            rootCause = "The observed failure rate crossed the deterministic diagnosis threshold.";
            recommendation = "Inspect gateway status codes, error-code distribution, caller concentration, and recent release/config changes.";
        } else if (alertTypes.contains("HIGH_LATENCY") || maxP95 >= 1000) {
            riskLevel = maxP95 >= 3000 ? "CRITICAL" : "WARNING";
            summary = apiCode + " shows elevated latency in the selected diagnosis window.";
            rootCause = "P95 latency is above the deterministic diagnosis threshold.";
            recommendation = "Check downstream dependencies, database queries, cache hit rate, and concurrent load.";
        } else if (alertTypes.contains("HIGH_5XX") || error5xx >= 3) {
            riskLevel = "WARNING";
            summary = apiCode + " shows downstream/server error signals in the selected diagnosis window.";
            rootCause = "5xx gateway samples or alert events indicate backend dependency instability.";
            recommendation = "Check upstream service health, timeout settings, retry policy, and circuit-breaker behavior.";
        } else if ("AUTH_LOGIN".equals(apiCode) && alertTypes.contains("AUTH_FAILURE_SPIKE")) {
            riskLevel = criticalIf(highestSeverity, failRate, maxP95, "WARNING");
            summary = "AUTH_LOGIN shows concentrated authentication failures.";
            rootCause = "Authentication failures may come from expired tokens, authorization configuration, or client parameter errors.";
            recommendation = "Check authorization configuration, token expiry policy, error prompts, and client retry behavior.";
        } else if (openAlertCount > 0) {
            riskLevel = criticalIf(highestSeverity, failRate, maxP95, "WARNING");
            summary = apiCode + " has active alert events in the selected diagnosis window.";
            rootCause = "Active alert evidence exists, but no specialized deterministic diagnosis rule had higher priority.";
            recommendation = "Review the alert_event evidence and correlate it with gateway logs and hourly stats.";
        }

        return new DiagnosisDecision(riskLevel, summary, rootCause, recommendation, alertTypes);
    }

    private List<AgentEvidenceItemVO> mergeEvidence(List<ToolResult> toolResults) {
        List<AgentEvidenceItemVO> items = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (ToolResult result : toolResults) {
            if (result.getEvidenceItems() == null || result.getEvidenceItems().isEmpty()) {
                items.add(emptyEvidence(result));
                continue;
            }
            for (Object raw : result.getEvidenceItems()) {
                if (!(raw instanceof Map<?, ?> evidence)) {
                    continue;
                }
                AgentEvidenceItemVO item = toEvidenceItem(result, evidence);
                String key = item.getEvidenceType() + "|" + item.getSourceTool() + "|" + item.getSourceRef();
                if (keys.add(key)) {
                    items.add(item);
                }
            }
        }
        items.sort(Comparator.comparing(AgentEvidenceItemVO::getEvidenceType).thenComparing(AgentEvidenceItemVO::getTitle));
        return items;
    }

    private AgentEvidenceItemVO toEvidenceItem(ToolResult result, Map<?, ?> evidence) {
        String sourceType = stringValue(evidence.get("sourceType"));
        String evidenceType = normalizeEvidenceType(result.getToolName(), stringValue(evidence.get("evidenceType")), sourceType);
        String sourceId = stringValue(evidence.get("sourceId"));
        AgentEvidenceItemVO item = new AgentEvidenceItemVO();
        item.setEvidenceType(evidenceType);
        item.setSourceTool(result.getToolName());
        item.setSourceRef(StringUtils.hasText(sourceType) && StringUtils.hasText(sourceId) ? sourceType + ":" + sourceId : sourceId);
        item.setTitle(truncate(stringValue(evidence.get("title")), 240));
        item.setContent(truncate(maskSensitive(stringValue(firstNonNull(evidence.get("summary"), evidence.get("quote")))), 1600));
        item.setScore(score(evidence.get("score")));
        Map<String, Object> extra = new LinkedHashMap<>();
        evidence.forEach((key, value) -> extra.put(String.valueOf(key), value));
        extra.put("evidenceType", evidenceType);
        extra.put("sourceTool", result.getToolName());
        item.setExtraInfo(extra);
        return item;
    }

    private AgentEvidenceItemVO emptyEvidence(ToolResult result) {
        AgentEvidenceItemVO item = new AgentEvidenceItemVO();
        item.setEvidenceType(normalizeEvidenceType(result.getToolName(), "", ""));
        item.setSourceTool(result.getToolName());
        item.setSourceRef(result.getToolName() + ":empty");
        item.setTitle(result.getToolName() + " returned no evidence");
        item.setContent(result.getSummary());
        item.setScore(null);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("empty", true);
        extra.put("success", result.isSuccess());
        extra.put("errorCode", result.getErrorCode());
        extra.put("evidenceType", item.getEvidenceType());
        item.setExtraInfo(extra);
        return item;
    }

    private Long createSession(Map<String, Object> user, String traceId, String apiCode, LocalDateTime startTime,
                               LocalDateTime endTime, AgentDiagnoseRequest request, String diagnosisMode,
                               int deletedOldReports) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sessionCode = "sess_diag_ev_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO agent_session (
                      session_code, trace_id, user_id, user_type, session_type, title, workflow_name,
                      status, duration_ms, retry_count, last_event_seq, started_at, finished_at,
                      extra_info, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'DIAGNOSIS', ?, 'agent_diagnosis_evidence_v1',
                      'RUNNING', 0, 0, 0, CURRENT_TIMESTAMP, NULL,
                      ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, sessionCode);
            ps.setString(2, traceId);
            ps.setLong(3, ((Number) user.get("id")).longValue());
            ps.setString(4, String.valueOf(user.get("user_type")));
            ps.setString(5, "Agent Diagnosis Evidence v1 - " + apiCode);
            ps.setString(6, toJson(extraInfo(apiCode, startTime, endTime, request, diagnosisMode, deletedOldReports)));
            return ps;
        }, keyHolder);
        return keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
    }

    private Long insertReport(Long sessionId, String traceId, Long userId, String apiCode, LocalDateTime startTime,
                              LocalDateTime endTime, AgentDiagnoseRequest request, String diagnosisMode,
                              DiagnosisDecision decision, List<AgentEvidenceItemVO> evidenceItems,
                              int toolCallCount, long durationMs) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String reportCode = "RPT_DIAG_EV_" + UUID.randomUUID().toString().replace("-", "");
        String content = buildMarkdown(apiCode, startTime, endTime, request, decision, evidenceItems, toolCallCount);
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO agent_report (
                      report_code, session_id, trace_id, report_type, title, summary, risk_level, content_md,
                      created_by, evidence_count, tool_call_count, duration_ms, status, error_code, error_message,
                      generated_at, extra_info, remark, created_at, updated_at
                    ) VALUES (?, ?, ?, 'DIAGNOSIS', ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', NULL, NULL,
                      CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, reportCode);
            ps.setLong(2, sessionId);
            ps.setString(3, traceId);
            ps.setString(4, "Agent Diagnosis Evidence v1 - " + apiCode);
            ps.setString(5, truncate(decision.summary(), 900));
            ps.setString(6, decision.riskLevel());
            ps.setString(7, content);
            ps.setLong(8, userId);
            ps.setInt(9, evidenceItems.size());
            ps.setInt(10, toolCallCount);
            ps.setLong(11, durationMs);
            ps.setString(12, toJson(extraInfo(apiCode, startTime, endTime, request, diagnosisMode, 0)));
            ps.setString(13, "No-LLM deterministic Agent Diagnosis Evidence v1");
            return ps;
        }, keyHolder);
        return keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
    }

    private int insertEvidenceItems(Long sessionId, String traceId, Long reportId, List<AgentEvidenceItemVO> items) {
        int count = 0;
        for (AgentEvidenceItemVO item : items) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        """
                        INSERT INTO evidence_item (
                          session_id, trace_id, report_id, source_type, source_id, title, content,
                          confidence, status, extra_info, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)
                        """,
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setLong(1, sessionId);
                ps.setString(2, traceId);
                ps.setLong(3, reportId);
                ps.setString(4, sourceType(item.getEvidenceType()));
                ps.setString(5, truncate(item.getSourceRef(), 120));
                ps.setString(6, truncate(item.getTitle(), 240));
                ps.setString(7, truncate(item.getContent(), 2000));
                if (item.getScore() == null) {
                    ps.setBigDecimal(8, null);
                } else {
                    ps.setBigDecimal(8, BigDecimal.valueOf(Math.max(0d, Math.min(1d, item.getScore()))));
                }
                ps.setString(9, toJson(item.getExtraInfo()));
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                item.setEvidenceId(keyHolder.getKey().longValue());
            }
            count++;
        }
        return count;
    }

    private void updateSession(Long sessionId, String status, long durationMs, String apiCode, Long reportId,
                               DiagnosisDecision decision) {
        jdbcTemplate.update(
                """
                UPDATE agent_session
                SET status = ?, duration_ms = ?, last_event_seq = ?, finished_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP, extra_info = ?
                WHERE id = ?
                """,
                status,
                durationMs,
                7,
                toJson(Map.of("source", SOURCE, "apiCode", apiCode, "reportId", reportId,
                        "riskLevel", decision.riskLevel(), "alertTypes", decision.alertTypes())),
                sessionId
        );
    }

    private int deleteOldReports(String apiCode, LocalDateTime startTime, LocalDateTime endTime, String scenarioRunId,
                                 Long alertId, String diagnosisMode) {
        List<Long> reportIds = jdbcTemplate.query(
                """
                SELECT id FROM agent_report
                WHERE JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.source')) = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.apiCode')) = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.startTime')) = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.endTime')) = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.diagnosisMode')) = ?
                  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.scenarioRunId')), '') = ?
                  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.alertId')), '') = ?
                """,
                (rs, rowNum) -> rs.getLong("id"),
                SOURCE,
                apiCode,
                FORMATTER.format(startTime),
                FORMATTER.format(endTime),
                diagnosisMode,
                scenarioRunId == null ? "" : scenarioRunId,
                alertId == null ? "" : String.valueOf(alertId)
        );
        for (Long reportId : reportIds) {
            jdbcTemplate.update("DELETE FROM evidence_item WHERE report_id = ?", reportId);
            jdbcTemplate.update("DELETE FROM agent_report WHERE id = ?", reportId);
        }
        return reportIds.size();
    }

    private List<AgentEvidenceItemVO> queryEvidenceItems(Long reportId) {
        return jdbcTemplate.query(
                """
                SELECT id, source_type, source_id, title, content, confidence, extra_info
                FROM evidence_item
                WHERE report_id = ? AND status = 'ACTIVE'
                ORDER BY id ASC
                """,
                (rs, rowNum) -> {
                    Map<String, Object> extraInfo = parseJsonMap(rs.getString("extra_info"));
                    AgentEvidenceItemVO item = new AgentEvidenceItemVO();
                    item.setEvidenceId(rs.getLong("id"));
                    item.setEvidenceType(stringValue(firstNonNull(extraInfo.get("evidenceType"), rs.getString("source_type"))));
                    item.setSourceTool(stringValue(extraInfo.get("sourceTool")));
                    item.setSourceRef(rs.getString("source_id"));
                    item.setTitle(rs.getString("title"));
                    item.setContent(rs.getString("content"));
                    BigDecimal confidence = rs.getBigDecimal("confidence");
                    item.setScore(confidence == null ? null : confidence.doubleValue());
                    item.setExtraInfo(extraInfo);
                    return item;
                },
                reportId
        );
    }

    private List<Map<String, Object>> queryToolCallTraces(Long sessionId, String traceId) {
        return jdbcTemplate.query(
                """
                SELECT id, session_id, trace_id, span_id, tool_name, tool_type, latency_ms,
                       success, error_code, error_message, status, created_at
                FROM tool_call_trace
                WHERE session_id = ? AND trace_id = ?
                ORDER BY id ASC
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("sessionId", rs.getLong("session_id"));
                    row.put("traceId", rs.getString("trace_id"));
                    row.put("spanId", rs.getString("span_id"));
                    row.put("toolName", rs.getString("tool_name"));
                    row.put("toolType", rs.getString("tool_type"));
                    row.put("latencyMs", rs.getInt("latency_ms"));
                    row.put("success", rs.getInt("success") == 1);
                    row.put("errorCode", rs.getString("error_code"));
                    row.put("errorMessage", rs.getString("error_message"));
                    row.put("status", rs.getString("status"));
                    row.put("createdAt", formatTime(rs.getTimestamp("created_at")));
                    return row;
                },
                sessionId,
                traceId
        );
    }

    private Map<String, Object> toReportMap(Map<String, Object> row) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportId", row.get("id"));
        report.put("reportCode", row.get("report_code"));
        report.put("sessionId", row.get("session_id"));
        report.put("traceId", row.get("trace_id"));
        report.put("reportType", row.get("report_type"));
        report.put("title", row.get("title"));
        report.put("summary", row.get("summary"));
        report.put("riskLevel", row.get("risk_level"));
        report.put("contentMd", row.get("content_md"));
        report.put("createdBy", row.get("created_by"));
        report.put("evidenceCount", row.get("evidence_count"));
        report.put("toolCallCount", row.get("tool_call_count"));
        report.put("durationMs", row.get("duration_ms"));
        report.put("status", row.get("status"));
        report.put("generatedAt", formatTime(row.get("generated_at")));
        report.put("extraInfo", parseJsonMap(stringValue(row.get("extra_info"))));
        return report;
    }

    private String buildMarkdown(String apiCode, LocalDateTime startTime, LocalDateTime endTime,
                                 AgentDiagnoseRequest request, DiagnosisDecision decision,
                                 List<AgentEvidenceItemVO> evidenceItems, int toolCallCount) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Agent Diagnosis Evidence v1\n\n");
        builder.append("- API: ").append(apiCode).append("\n");
        builder.append("- Window: ").append(FORMATTER.format(startTime)).append(" ~ ").append(FORMATTER.format(endTime)).append("\n");
        builder.append("- Mode: ").append(normalizeMode(request.getDiagnosisMode())).append("\n");
        builder.append("- Risk Level: ").append(decision.riskLevel()).append("\n\n");
        builder.append("## Summary\n").append(decision.summary()).append("\n\n");
        builder.append("## Root Cause\n").append(decision.rootCause()).append("\n\n");
        builder.append("## Recommendation\n").append(decision.recommendation()).append("\n\n");
        builder.append("## Evidence\n");
        for (AgentEvidenceItemVO item : evidenceItems.stream().limit(12).toList()) {
            builder.append("- [").append(item.getEvidenceType()).append("] ")
                    .append(item.getTitle()).append(" - ").append(item.getContent()).append("\n");
        }
        builder.append("\n## Trace\nTool calls: ").append(toolCallCount).append("\n");
        builder.append("\nNo LLM, RAG, frontend, notification, or auto-fix was used in this deterministic diagnosis.\n");
        return builder.toString();
    }

    private Map<String, Object> extraInfo(String apiCode, LocalDateTime startTime, LocalDateTime endTime,
                                          AgentDiagnoseRequest request, String diagnosisMode, int deletedOldReports) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("source", SOURCE);
        extra.put("apiCode", apiCode);
        extra.put("startTime", FORMATTER.format(startTime));
        extra.put("endTime", FORMATTER.format(endTime));
        extra.put("scenarioRunId", request.getScenarioRunId());
        extra.put("alertId", request.getAlertId());
        extra.put("diagnosisMode", diagnosisMode);
        extra.put("forceRebuild", request.getForceRebuild());
        extra.put("deletedOldReports", deletedOldReports);
        extra.put("noLlm", true);
        extra.put("noRag", true);
        return extra;
    }

    private Map<String, Object> findActiveUser(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, user_type FROM sys_user WHERE id = ? AND status = 'ACTIVE'",
                userId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private LocalDateTime parseRequiredTime(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, field + " is required");
        }
        try {
            return LocalDateTime.parse(value.trim(), FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, field + " format must be yyyy-MM-dd HH:mm:ss");
        }
    }

    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return "DETERMINISTIC";
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (!"DETERMINISTIC".equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "diagnosisMode must be DETERMINISTIC");
        }
        return normalized;
    }

    private String normalizeApiCode(String apiCode) {
        return StringUtils.hasText(apiCode) ? apiCode.trim().toUpperCase(Locale.ROOT) : null;
    }

    private Map<String, Object> dataOf(List<ToolResult> results, String toolName) {
        for (ToolResult result : results) {
            if (toolName.equals(result.getToolName()) && result.getData() instanceof Map<?, ?> map) {
                Map<String, Object> data = new LinkedHashMap<>();
                map.forEach((key, value) -> data.put(String.valueOf(key), value));
                return data;
            }
        }
        return Map.of();
    }

    private Set<String> alertTypes(Map<String, Object> alertData) {
        Set<String> types = new LinkedHashSet<>();
        Object alerts = alertData.get("alerts");
        if (alerts instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> alert) {
                    String type = stringValue(firstNonNull(alert.get("alertType"), alert.get("eventType")));
                    if (StringUtils.hasText(type)) {
                        types.add(type);
                    }
                }
            }
        }
        return types;
    }

    private String normalizeEvidenceType(String toolName, String existingType, String sourceType) {
        if ("queryApiInfo".equals(toolName)) {
            return "API_INFO";
        }
        if ("queryApiCallStats".equals(toolName)) {
            return "API_CALL_STAT";
        }
        if ("queryAlertEvents".equals(toolName)) {
            return "ALERT_EVENT";
        }
        if ("queryGatewayLogs".equals(toolName)) {
            return "GATEWAY_LOG_SAMPLE";
        }
        if ("queryRateLimitRule".equals(toolName)) {
            return "RATE_LIMIT_RULE";
        }
        if ("queryCampusEvents".equals(toolName)) {
            return "CAMPUS_EVENT";
        }
        if ("queryApiDocs".equals(toolName)) {
            return "API_DOC";
        }
        if (StringUtils.hasText(existingType)) {
            return existingType;
        }
        return StringUtils.hasText(sourceType) ? sourceType.toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private String sourceType(String evidenceType) {
        return switch (evidenceType) {
            case "API_INFO" -> "API";
            case "API_CALL_STAT" -> "STAT";
            case "ALERT_EVENT" -> "ALERT";
            case "GATEWAY_LOG_SAMPLE" -> "LOG";
            case "RATE_LIMIT_RULE" -> "RULE";
            case "CAMPUS_EVENT" -> "EVENT";
            case "API_DOC" -> "DOC";
            default -> "DOC";
        };
    }

    private String keywordFor(String apiCode) {
        return switch (apiCode) {
            case "AUTH_LOGIN" -> "signature";
            case "LECTURE_REGISTER" -> "rate";
            case "LIBRARY_BORROW" -> "timeout";
            case "VENUE_RESERVE" -> "idempotency";
            default -> null;
        };
    }

    private String findApiName(List<ToolResult> toolResults) {
        Object name = dataOf(toolResults, "queryApiInfo").get("apiName");
        return stringValue(name);
    }

    private String criticalIf(String highestSeverity, double failRate, int maxP95, String fallback) {
        return "CRITICAL".equals(highestSeverity) || failRate >= 0.30d || maxP95 >= 3000 ? "CRITICAL" : fallback;
    }

    private Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    private Double score(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String maskSensitive(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)token", "tok***")
                .replaceAll("(?i)secret", "sec***")
                .replaceAll("(?i)password", "pass***");
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String formatTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return FORMATTER.format(timestamp.toLocalDateTime());
        }
        return value == null ? null : String.valueOf(value);
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private record DiagnosisDecision(
            String riskLevel,
            String summary,
            String rootCause,
            String recommendation,
            Set<String> alertTypes
    ) {
    }
}
