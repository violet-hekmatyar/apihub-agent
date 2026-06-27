package com.apihub.agent.dev.reportworkbench;

import com.apihub.agent.common.ErrorCode;
import com.apihub.agent.common.TraceContext;
import com.apihub.agent.dev.diagnosis.AgentDiagnosisEvidenceService;
import com.apihub.agent.dev.llm.LlmDiagnosisResult;
import com.apihub.agent.dev.llm.LlmDiagnosisOrchestrator;
import com.apihub.agent.dev.reportworkbench.MonitorReportWorkbenchDtos.LatestAnomalyRequest;
import com.apihub.agent.dev.reportworkbench.MonitorReportWorkbenchDtos.MonitorEventReportRequest;
import com.apihub.agent.dev.reportworkbench.MonitorReportWorkbenchDtos.RangeReportRequest;
import com.apihub.agent.dev.reportworkbench.MonitorReportWorkbenchDtos.WorkbenchReportResponse;
import com.apihub.agent.exception.BusinessException;
import com.apihub.agent.model.dto.AgentDiagnoseRequest;
import com.apihub.agent.model.vo.AgentDiagnoseResponseVO;
import com.apihub.agent.model.vo.AgentEvidenceItemVO;
import com.apihub.agent.model.vo.AgentReportDetailVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MonitorReportWorkbenchService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String SOURCE = "MonitorReportWorkbenchV1";
    private static final String DATA_BOUNDARY_NOTE = "说明：本报告基于本地模拟场景与开发环境数据生成，不代表真实线上用户影响。";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AgentDiagnosisEvidenceService diagnosisService;
    private final LlmDiagnosisOrchestrator llmOrchestrator;
    private final ReportWorkbenchHtmlRenderer htmlRenderer;

    public MonitorReportWorkbenchService(JdbcTemplate jdbcTemplate,
                                         ObjectMapper objectMapper,
                                         AgentDiagnosisEvidenceService diagnosisService,
                                         LlmDiagnosisOrchestrator llmOrchestrator,
                                         ReportWorkbenchHtmlRenderer htmlRenderer) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.diagnosisService = diagnosisService;
        this.llmOrchestrator = llmOrchestrator;
        this.htmlRenderer = htmlRenderer;
    }

    @Transactional
    public WorkbenchReportResponse fromMonitorEvent(MonitorEventReportRequest request, Long userId, String requestId) {
        if (request == null || !StringUtils.hasText(request.getMonitorEventId())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "monitorEventId is required");
        }
        Map<String, Object> event = findMonitorEvent(request.getMonitorEventId());
        if (event == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "monitor event not found: " + request.getMonitorEventId());
        }
        List<Map<String, Object>> snapshots = findSnapshots(request.getMonitorEventId());
        Map<String, Object> alert = findAlertEvent(longValue(event.get("alert_event_id")));
        String apiCode = normalize(stringValue(event.get("api_code")));
        if (!StringUtils.hasText(apiCode)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "monitor event apiCode is empty");
        }
        LocalDateTime start = firstTime(event, "context_start_time", "window_start_time", "first_trigger_time");
        LocalDateTime end = firstTime(event, "resolved_time", "last_trigger_time", "window_end_time");
        if (start == null) {
            start = LocalDateTime.now().minusMinutes(15);
        }
        if (end == null || !end.isAfter(start)) {
            end = start.plusMinutes(5);
        }

        AgentDiagnoseRequest diagnoseRequest = new AgentDiagnoseRequest();
        diagnoseRequest.setApiCode(apiCode);
        diagnoseRequest.setStartTime(format(start));
        diagnoseRequest.setEndTime(format(end));
        diagnoseRequest.setScenarioRunId(stringValue(map(event.get("extra")).get("scenarioRunId")));
        diagnoseRequest.setAlertId(longValue(event.get("alert_event_id")));
        diagnoseRequest.setDiagnosisMode("DETERMINISTIC");
        diagnoseRequest.setForceRebuild(request.getForceRebuild() == null || Boolean.TRUE.equals(request.getForceRebuild()));

        AgentDiagnoseResponseVO diagnosis = diagnosisService.diagnose(
                diagnoseRequest,
                userId == null ? 1L : userId,
                StringUtils.hasText(requestId) ? requestId : "report-workbench-" + UUID.randomUUID()
        );
        AgentReportDetailVO detail = diagnosisService.getReport(diagnosis.getReportId());
        LlmRunResult llm = maybeRunLlm(diagnosis.getReportId(), Boolean.TRUE.equals(request.getIncludeLlm()),
                Boolean.TRUE.equals(request.getIncludePrompt()), diagnosis.getRiskLevel());
        WorkbenchPayload payload = buildIncidentPayload(detail, diagnosis, event, snapshots, alert, llm);
        updateReportExtra(diagnosis.getReportId(), payload, llm, Map.of(
                "sourceType", "MONITOR_EVENT",
                "monitorEventId", request.getMonitorEventId(),
                "deterministicReportId", diagnosis.getReportId()
        ));
        return response(diagnosis.getReportId(), payload, llm, "INCIDENT_ANALYSIS", request.getMonitorEventId(), apiCode, start, end);
    }

    public WorkbenchReportResponse analyzeLatestAnomaly(LatestAnomalyRequest request, Long userId, String requestId) {
        LatestAnomalyRequest actual = request == null ? new LatestAnomalyRequest() : request;
        Map<String, Object> event = latestAnomaly();
        if (event == null) {
            WorkbenchReportResponse response = new WorkbenchReportResponse();
            response.setStatus("NO_ANOMALY");
            response.setMessage("No FIRING/COOLDOWN/RESOLVED passive monitor anomaly found. Use analyze-range to generate a periodic health summary.");
            response.setLlmStatus("SKIPPED");
            response.setReportType("INCIDENT_ANALYSIS");
            response.setDisplayStatus("NORMAL");
            response.setStatusLabel(statusLabel("NORMAL"));
            response.setColorLevel(colorLevel("NORMAL"));
            return response;
        }
        MonitorEventReportRequest delegate = new MonitorEventReportRequest();
        delegate.setMonitorEventId(stringValue(event.get("monitor_event_id")));
        delegate.setIncludeLlm(actual.getIncludeLlm());
        delegate.setIncludePrompt(actual.getIncludePrompt());
        delegate.setForceRebuild(actual.getForceRebuild());
        return fromMonitorEvent(delegate, userId, requestId);
    }

    @Transactional
    public WorkbenchReportResponse analyzeRange(RangeReportRequest request, Long userId, String requestId) {
        if (request == null) {
            request = new RangeReportRequest();
        }
        TimeRange range = resolveRange(request);
        String apiCode = normalize(request.getApiCode());
        Map<String, Object> stats = aggregateGatewayLogs(range.start(), range.end(), apiCode);
        List<Map<String, Object>> businessCodes = businessCodeDistribution(range.start(), range.end(), apiCode);
        List<Map<String, Object>> monitorEvents = monitorEvents(range.start(), range.end(), apiCode);
        List<Map<String, Object>> alertEvents = alertEvents(range.start(), range.end(), apiCode);
        String displayStatus = rangeStatus(stats, monitorEvents, alertEvents);
        if (!Boolean.TRUE.equals(request.getIncludeNormalSummary()) && "NORMAL".equals(displayStatus)
                && number(stats.get("total_count")) == 0) {
            WorkbenchReportResponse response = new WorkbenchReportResponse();
            response.setStatus("NO_DATA");
            response.setMessage("No gateway logs in selected range. Set includeNormalSummary=true to persist a normal summary.");
            response.setLlmStatus("SKIPPED");
            response.setReportType("PERIODIC_HEALTH_SUMMARY");
            response.setDisplayStatus("UNKNOWN");
            response.setStatusLabel(statusLabel("UNKNOWN"));
            response.setColorLevel(colorLevel("UNKNOWN"));
            return response;
        }

        Long sessionId = createWorkbenchSession(userId == null ? 1L : userId, apiCode, range, requestId);
        Long reportId = insertRangeReport(sessionId, userId == null ? 1L : userId, apiCode, range, stats,
                monitorEvents, alertEvents, displayStatus);
        int evidenceCount = insertRangeEvidence(sessionId, reportId, stats, monitorEvents, alertEvents, businessCodes);
        jdbcTemplate.update("UPDATE agent_report SET evidence_count = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                evidenceCount, reportId);
        AgentReportDetailVO detail = diagnosisService.getReport(reportId);
        LlmRunResult llm = maybeRunLlm(reportId, Boolean.TRUE.equals(request.getIncludeLlm()),
                Boolean.TRUE.equals(request.getIncludePrompt()), displayStatus);
        WorkbenchPayload payload = buildRangePayload(detail, reportId, apiCode, range, stats, businessCodes,
                monitorEvents, alertEvents, displayStatus, llm);
        updateReportExtra(reportId, payload, llm, Map.of(
                "sourceType", "RANGE",
                "range", StringUtils.hasText(request.getRange()) ? request.getRange() : "custom",
                "startTime", format(range.start()),
                "endTime", format(range.end())
        ));
        return response(reportId, payload, llm, "PERIODIC_HEALTH_SUMMARY", null, apiCode, range.start(), range.end());
    }

    public List<Map<String, Object>> recentReports(Integer limit) {
        int actualLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        return jdbcTemplate.queryForList("""
                SELECT id AS reportId, report_code AS reportCode, report_type AS reportType, title, summary,
                       risk_level AS riskLevel, status, generated_at AS generatedAt, extra_info AS extraInfo
                FROM agent_report
                WHERE JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.source')) = ?
                ORDER BY generated_at DESC, id DESC
                LIMIT ?
                """, SOURCE, actualLimit).stream().map(this::parseReportRow).toList();
    }

    public AgentReportDetailVO reportDetail(Long reportId) {
        return diagnosisService.getReport(reportId);
    }

    public String renderHtml(Long reportId) {
        return htmlRenderer.render(diagnosisService.getReport(reportId));
    }

    private WorkbenchPayload buildIncidentPayload(AgentReportDetailVO detail,
                                                  AgentDiagnoseResponseVO diagnosis,
                                                  Map<String, Object> event,
                                                  List<Map<String, Object>> snapshots,
                                                  Map<String, Object> alert,
                                                  LlmRunResult llm) {
        Map<String, Object> report = detail.getReport();
        String displayStatus = normalizeStatus(diagnosis.getRiskLevel());
        String apiCode = diagnosis.getApiCode();
        String callerAppCode = stringValue(event.get("caller_app_code"));
        LocalDateTime start = parseTime(diagnosis.getStartTime());
        LocalDateTime end = parseTime(diagnosis.getEndTime());
        List<Map<String, Object>> evidenceList = evidenceFromDetail(detail);
        Map<String, Object> metricFacts = incidentMetrics(event, snapshots, evidenceList);
        List<Map<String, Object>> rules = monitorRulesFromEvent(event, evidenceList);
        List<Map<String, Object>> businessCodes = businessCodesFromSnapshots(snapshots, evidenceList);

        Map<String, Object> html = baseHtmlJson("INCIDENT_ANALYSIS", reportCode(report), "异常分析报告",
                targetLabel(apiCode, callerAppCode), start, end,
                llm.modelName(), displayStatus, statusSummary(displayStatus, diagnosis.getSummary()));
        html.put("analysisScope", Map.of(
                "apiCode", apiCode,
                "apiName", stringValue(report.get("apiName")),
                "callerAppCode", callerAppCode,
                "callerAppName", "",
                "currentWindow", formatRange(start, end),
                "referenceWindow", referenceWindow(event)
        ));
        html.put("metricCheckup", metricFacts.get("metricCheckup"));
        html.put("monitorRuleAssessments", rules);
        html.put("businessCodeDistribution", businessCodes);
        html.put("eventTimeline", incidentTimeline(event, start, end));
        html.put("diagnosisSummary", diagnosisSummary(diagnosis.getSummary(), displayStatus, apiCode));
        html.put("operationRecommendations", recommendations(displayStatus, evidenceList));
        html.put("evidenceList", evidenceList);
        html.put("dataBoundaryNote", DATA_BOUNDARY_NOTE);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("reportType", "INCIDENT_ANALYSIS");
        context.put("analysisTrigger", Map.of(
                "triggerType", "MONITOR_EVENT",
                "monitorEventId", stringValue(event.get("monitor_event_id")),
                "requestedAt", format(LocalDateTime.now())
        ));
        context.put("analysisScope", html.get("analysisScope"));
        context.put("deterministicReport", Map.of(
                "reportId", diagnosis.getReportId(),
                "riskLevel", displayStatus,
                "summary", diagnosis.getSummary()
        ));
        context.put("monitorEvent", compactMap(event, "alert_type", "event_status", "first_trigger_time", "last_trigger_time", "resolved_time"));
        context.put("alertEvent", alert == null ? Map.of() : compactMap(alert, "id", "event_type", "severity", "title", "resolved", "status"));
        context.put("metricCheckupFacts", html.get("metricCheckup"));
        context.put("monitorRuleFacts", rules);
        context.put("businessCodeFacts", businessCodes);
        context.put("timelineFacts", html.get("eventTimeline"));
        context.put("evidenceList", evidenceList);
        context.put("constraints", Map.of("environment", "development_simulation", "dataBoundaryNote", DATA_BOUNDARY_NOTE));
        return new WorkbenchPayload(context, html, validateHtmlJson(html));
    }

    private WorkbenchPayload buildRangePayload(AgentReportDetailVO detail,
                                               Long reportId,
                                               String apiCode,
                                               TimeRange range,
                                               Map<String, Object> stats,
                                               List<Map<String, Object>> businessCodeRows,
                                               List<Map<String, Object>> monitorEvents,
                                               List<Map<String, Object>> alertEvents,
                                               String displayStatus,
                                               LlmRunResult llm) {
        Map<String, Object> report = detail.getReport();
        List<Map<String, Object>> evidenceList = evidenceFromDetail(detail);
        List<Map<String, Object>> metricCheckup = rangeMetrics(stats, displayStatus, evidenceList);
        List<Map<String, Object>> ruleAssessments = rangeRuleAssessments(stats, monitorEvents, displayStatus, evidenceList);
        List<Map<String, Object>> businessCodes = businessCodeRows.stream()
                .map(row -> businessCodeItem(row, displayStatus, evidenceList))
                .toList();

        Map<String, Object> html = baseHtmlJson("PERIODIC_HEALTH_SUMMARY", reportCode(report), "周期健康巡检报告",
                StringUtils.hasText(apiCode) ? apiCode : "全部核心 API", range.start(), range.end(),
                llm.modelName(), displayStatus, rangeStatusSummary(displayStatus, stats, monitorEvents));
        html.put("analysisScope", Map.of(
                "apiCode", StringUtils.hasText(apiCode) ? apiCode : "ALL",
                "apiName", "",
                "callerAppCode", "",
                "callerAppName", "",
                "currentWindow", formatRange(range.start(), range.end()),
                "referenceWindow", ""
        ));
        html.put("metricCheckup", metricCheckup);
        html.put("monitorRuleAssessments", ruleAssessments);
        html.put("businessCodeDistribution", businessCodes);
        html.put("eventTimeline", rangeTimeline(range, monitorEvents, alertEvents));
        html.put("diagnosisSummary", rangeSummary(displayStatus, stats, monitorEvents, alertEvents, apiCode));
        html.put("operationRecommendations", recommendations(displayStatus, evidenceList));
        html.put("evidenceList", evidenceList);
        html.put("dataBoundaryNote", DATA_BOUNDARY_NOTE);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("reportType", "PERIODIC_HEALTH_SUMMARY");
        context.put("analysisTrigger", Map.of("triggerType", "RANGE", "requestedAt", format(LocalDateTime.now())));
        context.put("analysisScope", html.get("analysisScope"));
        context.put("deterministicReport", Map.of("reportId", reportId, "riskLevel", displayStatus, "summary", report.get("summary")));
        context.put("metricCheckupFacts", metricCheckup);
        context.put("monitorRuleFacts", ruleAssessments);
        context.put("businessCodeFacts", businessCodes);
        context.put("timelineFacts", html.get("eventTimeline"));
        context.put("evidenceList", evidenceList);
        context.put("constraints", Map.of("environment", "development_simulation", "dataBoundaryNote", DATA_BOUNDARY_NOTE));
        return new WorkbenchPayload(context, html, validateHtmlJson(html));
    }

    private Map<String, Object> baseHtmlJson(String reportType,
                                             String reportCode,
                                             String reportTypeLabel,
                                             String target,
                                             LocalDateTime start,
                                             LocalDateTime end,
                                             String modelName,
                                             String status,
                                             String statusSummary) {
        Map<String, Object> html = new LinkedHashMap<>();
        html.put("reportType", reportType);
        html.put("reportHeader", Map.of(
                "reportCode", reportCode,
                "reportTypeLabel", reportTypeLabel,
                "generatedAt", format(LocalDateTime.now()),
                "analysisTarget", target,
                "timeRange", formatRange(start, end),
                "modelName", modelName,
                "dataSources", List.of("passive_monitor_event", "passive_alert_snapshot", "alert_event", "gateway_log", "agent_report", "evidence_item")
        ));
        html.put("displayStatus", Map.of(
                "status", status,
                "statusLabel", statusLabel(status),
                "colorLevel", colorLevel(status),
                "statusSummary", statusSummary
        ));
        return html;
    }

    private LlmRunResult maybeRunLlm(Long reportId, boolean includeLlm, boolean includePrompt, String deterministicStatus) {
        if (!includeLlm) {
            return new LlmRunResult("SKIPPED", "deterministic-only", null, null);
        }
        String status = normalizeStatus(deterministicStatus);
        if ("WATCH".equals(status)) {
            return new LlmRunResult("SKIPPED", "deterministic-only", null, "existing LLM diagnosis validator does not accept WATCH");
        }
        try {
            LlmDiagnosisResult result = llmOrchestrator.runDashScope(reportId, includePrompt);
            String llmStatus = result.isSuccess() && !result.isFallbackUsed() ? "SUCCESS" : "FALLBACK";
            String model = StringUtils.hasText(result.getModel()) ? result.getModel() : "dashscope";
            return new LlmRunResult(llmStatus, model, result, result.getFallbackReason());
        } catch (Exception e) {
            return new LlmRunResult("FALLBACK", "deterministic-only", null, e.getMessage());
        }
    }

    private void updateReportExtra(Long reportId, WorkbenchPayload payload, LlmRunResult llm, Map<String, Object> metadata) {
        Map<String, Object> report = jdbcTemplate.queryForList("SELECT extra_info FROM agent_report WHERE id = ?", reportId)
                .stream().findFirst().map(row -> parseJsonMap(row.get("extra_info"))).orElse(new LinkedHashMap<>());
        report.put("source", SOURCE);
        report.put("reportWorkbenchVersion", "v1");
        report.put("reportType", stringValue(payload.htmlJson().get("reportType")));
        report.put("analysisContextJson", payload.contextJson());
        report.put("htmlRenderableJson", payload.htmlJson());
        report.put("validationWarnings", payload.validationWarnings());
        report.put("llmStatus", llm.status());
        report.put("llmModel", llm.modelName());
        report.put("llmFallbackReason", llm.fallbackReason());
        report.put("llmReportJson", llm.result());
        report.put("dataBoundaryNote", DATA_BOUNDARY_NOTE);
        report.put("createdAt", format(LocalDateTime.now()));
        report.putAll(metadata);
        String reportType = stringValue(payload.htmlJson().get("reportType"));
        String displayStatus = stringValue(map(payload.htmlJson().get("displayStatus")).get("status"));
        jdbcTemplate.update("""
                UPDATE agent_report
                SET report_type = ?, risk_level = ?, extra_info = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, reportType, displayStatus, toJson(report), reportId);
    }

    private WorkbenchReportResponse response(Long reportId,
                                             WorkbenchPayload payload,
                                             LlmRunResult llm,
                                             String reportType,
                                             String monitorEventId,
                                             String apiCode,
                                             LocalDateTime start,
                                             LocalDateTime end) {
        Map<String, Object> header = map(payload.htmlJson().get("reportHeader"));
        Map<String, Object> display = map(payload.htmlJson().get("displayStatus"));
        WorkbenchReportResponse response = new WorkbenchReportResponse();
        response.setReportId(reportId);
        response.setReportCode(stringValue(header.get("reportCode")));
        response.setReportType(reportType);
        response.setHtmlUrl("/api/dev/report-workbench/reports/" + reportId + "/html");
        response.setLlmStatus(llm.status());
        response.setLlmModel(llm.modelName());
        response.setDisplayStatus(stringValue(display.get("status")));
        response.setStatusLabel(stringValue(display.get("statusLabel")));
        response.setColorLevel(stringValue(display.get("colorLevel")));
        response.setSummary(stringValue(display.get("statusSummary")));
        response.setMonitorEventId(monitorEventId);
        response.setApiCode(apiCode);
        response.setStartTime(format(start));
        response.setEndTime(format(end));
        response.setAnalysisContextJson(payload.contextJson());
        response.setHtmlRenderableJson(payload.htmlJson());
        response.setLlmResult(llm.result());
        response.setValidationWarnings(payload.validationWarnings());
        return response;
    }

    private Map<String, Object> aggregateGatewayLogs(LocalDateTime start, LocalDateTime end, String apiCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(CASE WHEN l.http_status BETWEEN 200 AND 399 THEN 1 ELSE 0 END),0) AS success_count,
                       COALESCE(SUM(CASE WHEN l.http_status >= 400 THEN 1 ELSE 0 END),0) AS fail_count,
                       COALESCE(SUM(CASE WHEN l.http_status BETWEEN 400 AND 499 THEN 1 ELSE 0 END),0) AS error_4xx_count,
                       COALESCE(SUM(CASE WHEN l.http_status >= 500 THEN 1 ELSE 0 END),0) AS error_5xx_count,
                       COALESCE(SUM(CASE WHEN l.http_status = 429 OR l.error_code = 'RATE_LIMITED' THEN 1 ELSE 0 END),0) AS rate_limit_count,
                       COALESCE(SUM(CASE WHEN l.http_status IN (401,403) THEN 1 ELSE 0 END),0) AS auth_failure_count,
                       COALESCE(SUM(CASE WHEN l.error_code LIKE '%TIMEOUT%' THEN 1 ELSE 0 END),0) AS timeout_count,
                       COALESCE(AVG(l.latency_ms),0) AS avg_latency_ms,
                       COALESCE(MAX(l.latency_ms),0) AS max_latency_ms
                FROM gateway_log l
                LEFT JOIN api_endpoint api ON api.id = l.api_id
                WHERE l.request_time >= ? AND l.request_time < ?
                """);
        List<Object> params = new ArrayList<>(List.of(Timestamp.valueOf(start), Timestamp.valueOf(end)));
        if (StringUtils.hasText(apiCode)) {
            sql.append(" AND api.api_code = ?");
            params.add(apiCode);
        }
        return jdbcTemplate.queryForList(sql.toString(), params.toArray()).stream().findFirst().orElse(Map.of());
    }

    private List<Map<String, Object>> businessCodeDistribution(LocalDateTime start, LocalDateTime end, String apiCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(JSON_UNQUOTE(JSON_EXTRACT(l.extra_info, '$.businessCode')),
                                JSON_UNQUOTE(JSON_EXTRACT(l.extra_info, '$.upstreamCode')),
                                l.error_code,
                                CASE WHEN l.http_status BETWEEN 200 AND 399 THEN 'OK' ELSE CONCAT('HTTP_', l.http_status) END) AS businessCode,
                       COUNT(*) AS count
                FROM gateway_log l
                LEFT JOIN api_endpoint api ON api.id = l.api_id
                WHERE l.request_time >= ? AND l.request_time < ?
                """);
        List<Object> params = new ArrayList<>(List.of(Timestamp.valueOf(start), Timestamp.valueOf(end)));
        if (StringUtils.hasText(apiCode)) {
            sql.append(" AND api.api_code = ?");
            params.add(apiCode);
        }
        sql.append(" GROUP BY businessCode ORDER BY count DESC LIMIT 10");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        long total = rows.stream().mapToLong(row -> number(row.get("count"))).sum();
        for (Map<String, Object> row : rows) {
            row.put("ratio", ratio(number(row.get("count")), total));
        }
        return rows;
    }

    private List<Map<String, Object>> monitorEvents(LocalDateTime start, LocalDateTime end, String apiCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM passive_monitor_event
                WHERE first_trigger_time >= ? AND first_trigger_time < ?
                """);
        List<Object> params = new ArrayList<>(List.of(Timestamp.valueOf(start), Timestamp.valueOf(end)));
        if (StringUtils.hasText(apiCode)) {
            sql.append(" AND api_code = ?");
            params.add(apiCode);
        }
        sql.append(" ORDER BY first_trigger_time DESC LIMIT 100");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray()).stream().map(this::parseMonitorRow).toList();
    }

    private List<Map<String, Object>> alertEvents(LocalDateTime start, LocalDateTime end, String apiCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.* FROM alert_event e
                LEFT JOIN api_endpoint api ON api.id = e.api_id
                WHERE e.start_time >= ? AND e.start_time < ?
                """);
        List<Object> params = new ArrayList<>(List.of(Timestamp.valueOf(start), Timestamp.valueOf(end)));
        if (StringUtils.hasText(apiCode)) {
            sql.append(" AND api.api_code = ?");
            params.add(apiCode);
        }
        sql.append(" ORDER BY e.start_time DESC LIMIT 100");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray()).stream().map(this::parseAlertRow).toList();
    }

    private Long createWorkbenchSession(Long userId, String apiCode, TimeRange range, String requestId) {
        Map<String, Object> user = findUser(userId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String traceId = TraceContext.getTraceId();
        String sessionCode = "sess_workbench_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO agent_session (
                      session_code, trace_id, user_id, user_type, session_type, title, workflow_name,
                      status, duration_ms, retry_count, last_event_seq, started_at, finished_at,
                      extra_info, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'DIAGNOSIS', ?, 'monitor_report_workbench_v1',
                      'SUCCESS', 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionCode);
            ps.setString(2, traceId);
            ps.setLong(3, userId);
            ps.setString(4, stringValue(user.getOrDefault("user_type", "DEMO")));
            ps.setString(5, "Monitor Report Workbench - " + (StringUtils.hasText(apiCode) ? apiCode : "ALL"));
            ps.setString(6, toJson(Map.of(
                    "source", SOURCE,
                    "requestId", StringUtils.hasText(requestId) ? requestId : "",
                    "apiCode", StringUtils.hasText(apiCode) ? apiCode : "ALL",
                    "startTime", format(range.start()),
                    "endTime", format(range.end())
            )));
            return ps;
        }, keyHolder);
        return keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
    }

    private Long insertRangeReport(Long sessionId,
                                   Long userId,
                                   String apiCode,
                                   TimeRange range,
                                   Map<String, Object> stats,
                                   List<Map<String, Object>> monitorEvents,
                                   List<Map<String, Object>> alertEvents,
                                   String displayStatus) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String traceId = TraceContext.getTraceId();
        String reportCode = "RPT_MONITOR_" + UUID.randomUUID().toString().replace("-", "");
        String target = StringUtils.hasText(apiCode) ? apiCode : "ALL";
        String summary = rangeStatusSummary(displayStatus, stats, monitorEvents);
        String content = "# Monitor Report Workbench\n\n"
                + "- reportType: PERIODIC_HEALTH_SUMMARY\n"
                + "- target: " + target + "\n"
                + "- window: " + formatRange(range.start(), range.end()) + "\n"
                + "- summary: " + summary + "\n"
                + "- passiveMonitorEvents: " + monitorEvents.size() + "\n"
                + "- alertEvents: " + alertEvents.size() + "\n";
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO agent_report (
                      report_code, session_id, trace_id, report_type, title, summary, risk_level, content_md,
                      created_by, evidence_count, tool_call_count, duration_ms, status, error_code, error_message,
                      generated_at, extra_info, remark, created_at, updated_at
                    ) VALUES (?, ?, ?, 'PERIODIC_HEALTH_SUMMARY', ?, ?, ?, ?, ?, 0, 0, 0, 'SUCCESS', NULL, NULL,
                      CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, reportCode);
            ps.setLong(2, sessionId);
            ps.setString(3, traceId);
            ps.setString(4, "Monitor Report Workbench - " + target);
            ps.setString(5, truncate(summary, 900));
            ps.setString(6, displayStatus);
            ps.setString(7, content);
            ps.setLong(8, userId);
            ps.setString(9, toJson(Map.of("source", SOURCE, "reportType", "PERIODIC_HEALTH_SUMMARY")));
            ps.setString(10, "Monitor Report Workbench v1 deterministic range summary");
            return ps;
        }, keyHolder);
        return keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
    }

    private int insertRangeEvidence(Long sessionId,
                                    Long reportId,
                                    Map<String, Object> stats,
                                    List<Map<String, Object>> monitorEvents,
                                    List<Map<String, Object>> alertEvents,
                                    List<Map<String, Object>> businessCodes) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of(
                "sourceType", "GATEWAY_LOG",
                "sourceId", "range-aggregate",
                "title", "Gateway aggregate metrics",
                "content", "total=" + number(stats.get("total_count")) + ", fail=" + number(stats.get("fail_count"))
                        + ", rateLimit=" + number(stats.get("rate_limit_count")) + ", avgLatencyMs=" + decimal(stats.get("avg_latency_ms")),
                "confidence", 1.0,
                "extraInfo", stats
        ));
        if (!businessCodes.isEmpty()) {
            items.add(Map.of(
                    "sourceType", "GATEWAY_LOG",
                    "sourceId", "business-code-distribution",
                    "title", "Business code distribution",
                    "content", "top business code=" + stringValue(businessCodes.get(0).get("businessCode"))
                            + ", count=" + number(businessCodes.get(0).get("count")),
                    "confidence", 1.0,
                    "extraInfo", Map.of("businessCodeDistribution", businessCodes)
            ));
        }
        for (Map<String, Object> event : monitorEvents.stream().limit(5).toList()) {
            items.add(Map.of(
                    "sourceType", "PASSIVE_MONITOR_EVENT",
                    "sourceId", stringValue(event.get("monitor_event_id")),
                    "title", "Passive monitor event " + stringValue(event.get("alert_type")),
                    "content", "risk=" + stringValue(event.get("risk_level")) + ", status=" + stringValue(event.get("event_status"))
                            + ", requestCount=" + number(event.get("request_count")),
                    "confidence", 1.0,
                    "extraInfo", event
            ));
        }
        if (!alertEvents.isEmpty()) {
            items.add(Map.of(
                    "sourceType", "ALERT_EVENT",
                    "sourceId", "range-alert-events",
                    "title", "Alert events in range",
                    "content", "alertEventCount=" + alertEvents.size(),
                    "confidence", 1.0,
                    "extraInfo", Map.of("alertEvents", alertEvents)
            ));
        }

        String traceId = TraceContext.getTraceId();
        int count = 0;
        for (Map<String, Object> item : items) {
            jdbcTemplate.update("""
                    INSERT INTO evidence_item (
                      session_id, trace_id, report_id, source_type, source_id, title, content,
                      confidence, status, extra_info, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)
                    """,
                    sessionId,
                    traceId,
                    reportId,
                    item.get("sourceType"),
                    truncate(stringValue(item.get("sourceId")), 120),
                    truncate(stringValue(item.get("title")), 240),
                    truncate(stringValue(item.get("content")), 2000),
                    item.get("confidence"),
                    toJson(item.get("extraInfo")));
            count++;
        }
        return count;
    }

    private List<Map<String, Object>> evidenceFromDetail(AgentReportDetailVO detail) {
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (AgentEvidenceItemVO item : detail.getEvidenceItems()) {
            Map<String, Object> extra = item.getExtraInfo() == null ? Map.of() : item.getExtraInfo();
            String evidenceId = "EVID-" + String.format("%03d", index++);
            result.add(new LinkedHashMap<>(Map.of(
                    "evidenceId", evidenceId,
                    "evidenceType", stringValue(item.getEvidenceType()),
                    "evidenceTypeLabel", evidenceTypeLabel(item.getEvidenceType()),
                    "source", stringValue(item.getSourceRef()),
                    "keyMetric", truncate(firstNonBlank(stringValue(extra.get("metric")), item.getTitle(), "metric evidence"), 160),
                    "currentValue", firstNonBlank(stringValue(extra.get("currentValue")), ""),
                    "referenceOrThreshold", firstNonBlank(stringValue(extra.get("threshold")), stringValue(extra.get("baseline")), ""),
                    "deviationValue", firstNonBlank(stringValue(extra.get("delta")), stringValue(extra.get("deviation")), "见证据内容"),
                    "assessmentLabel", "",
                    "relatedConclusion", truncate(firstNonBlank(item.getContent(), item.getTitle()), 220)
            )));
        }
        if (result.isEmpty()) {
            result.add(new LinkedHashMap<>(Map.of(
                    "evidenceId", "EVID-001",
                    "evidenceType", "REPORT",
                    "evidenceTypeLabel", "报告摘要（REPORT）",
                    "source", "agent_report",
                    "keyMetric", "deterministic summary",
                    "currentValue", "",
                    "referenceOrThreshold", "",
                    "deviationValue", "见报告摘要",
                    "assessmentLabel", "",
                    "relatedConclusion", "deterministic report generated"
            )));
        }
        return result;
    }

    private Map<String, Object> incidentMetrics(Map<String, Object> event,
                                                List<Map<String, Object>> snapshots,
                                                List<Map<String, Object>> evidence) {
        String evidenceId = firstEvidenceId(evidence);
        List<Map<String, Object>> metrics = new ArrayList<>();
        metrics.add(metric("请求数", numberText(event.get("request_count")), numberText(event.get("baseline_request_count")),
                signed(number(event.get("request_count")) - number(event.get("baseline_request_count"))), normalizeStatus(event.get("risk_level")), evidenceId));
        metrics.add(metric("错误率", percent(doubleValue(event.get("error_rate"))), percent(doubleValue(event.get("baseline_error_rate"))),
                pp(doubleValue(event.get("error_rate")) - doubleValue(event.get("baseline_error_rate"))), normalizeStatus(event.get("risk_level")), evidenceId));
        metrics.add(metric("限流数量", numberText(event.get("rate_limit_count")), "0",
                signed(number(event.get("rate_limit_count"))), normalizeStatus(event.get("risk_level")), evidenceId));
        metrics.add(metric("P95 延迟", numberText(event.get("p95_latency_ms")) + "ms", "", "见监测窗口", normalizeStatus(event.get("risk_level")), evidenceId));
        return Map.of("metricCheckup", metrics);
    }

    private List<Map<String, Object>> rangeMetrics(Map<String, Object> stats, String status, List<Map<String, Object>> evidence) {
        String evidenceId = firstEvidenceId(evidence);
        long total = number(stats.get("total_count"));
        long fail = number(stats.get("fail_count"));
        long rateLimit = number(stats.get("rate_limit_count"));
        double failRate = total == 0 ? 0d : (double) fail / total;
        List<Map<String, Object>> metrics = new ArrayList<>();
        metrics.add(metric("请求总数", String.valueOf(total), "", signed(total), status, evidenceId));
        metrics.add(metric("成功数", numberText(stats.get("success_count")), "", signed(number(stats.get("success_count"))), status, evidenceId));
        metrics.add(metric("失败数", String.valueOf(fail), "", signed(fail), status, evidenceId));
        metrics.add(metric("错误率", percent(failRate), "10.00%", pp(failRate - 0.10d), status, evidenceId));
        metrics.add(metric("限流数量", String.valueOf(rateLimit), "0", signed(rateLimit), rateLimit > 0 ? "WATCH" : status, evidenceId));
        metrics.add(metric("平均延迟", decimal(stats.get("avg_latency_ms")) + "ms", "", "见聚合值", status, evidenceId));
        return metrics;
    }

    private Map<String, Object> metric(String name, String current, String reference, String change, String status, String evidenceId) {
        String displayStatus = normalizeStatus(status);
        return new LinkedHashMap<>(Map.of(
                "metricName", name,
                "currentWindowValue", current,
                "referenceWindowValue", reference,
                "changeValue", change,
                "assessmentStatus", displayStatus,
                "displayStatus", statusLabel(displayStatus),
                "colorLevel", colorLevel(displayStatus),
                "evidenceIds", List.of(evidenceId)
        ));
    }

    private List<Map<String, Object>> monitorRulesFromEvent(Map<String, Object> event, List<Map<String, Object>> evidence) {
        String alertType = firstNonBlank(stringValue(event.get("alert_type")), "PASSIVE_MONITOR_EVENT");
        String displayStatus = normalizeStatus(event.get("risk_level"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleName", alertType);
        row.put("ruleDisplayName", ruleLabel(alertType, displayStatus));
        row.put("metricName", ruleMetric(alertType));
        row.put("currentValue", ruleCurrentValue(alertType, event));
        row.put("thresholdValue", thresholdText(alertType));
        row.put("deviationValue", ruleDeviation(alertType, event));
        row.put("deviationType", "ABSOLUTE_DELTA");
        row.put("assessmentStatus", displayStatus);
        row.put("assessmentLabel", ruleConclusion(alertType, displayStatus));
        row.put("colorLevel", colorLevel(displayStatus));
        row.put("triggered", true);
        row.put("evidenceIds", List.of(firstEvidenceId(evidence)));
        return List.of(row);
    }

    private List<Map<String, Object>> rangeRuleAssessments(Map<String, Object> stats,
                                                           List<Map<String, Object>> monitorEvents,
                                                           String displayStatus,
                                                           List<Map<String, Object>> evidence) {
        long total = number(stats.get("total_count"));
        long fail = number(stats.get("fail_count"));
        long rateLimit = number(stats.get("rate_limit_count"));
        double failRate = total == 0 ? 0d : (double) fail / total;
        double rateLimitRate = total == 0 ? 0d : (double) rateLimit / total;
        String evidenceId = firstEvidenceId(evidence);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rule("HIGH_ERROR_RATE", "错误率", percent(failRate), "10.00%", pp(failRate - 0.10d),
                failRate >= 0.10d ? "WARNING" : displayStatus, evidenceId));
        rows.add(rule("HIGH_RATE_LIMIT", "限流比例", percent(rateLimitRate), "5.00%", pp(rateLimitRate - 0.05d),
                rateLimitRate >= 0.05d ? "WARNING" : rateLimit > 0 ? "WATCH" : displayStatus, evidenceId));
        rows.add(rule("TRAFFIC_SPIKE", "监测事件数", String.valueOf(monitorEvents.size()), "0",
                signed(monitorEvents.size()), monitorEvents.isEmpty() ? displayStatus : "WARNING", evidenceId));
        return rows;
    }

    private Map<String, Object> rule(String ruleName, String metric, String current, String threshold, String deviation,
                                     String status, String evidenceId) {
        String displayStatus = normalizeStatus(status);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleName", ruleName);
        row.put("ruleDisplayName", ruleLabel(ruleName, displayStatus));
        row.put("metricName", metric);
        row.put("currentValue", current);
        row.put("thresholdValue", threshold);
        row.put("deviationValue", deviation);
        row.put("deviationType", "ABSOLUTE_DELTA");
        row.put("assessmentStatus", displayStatus);
        row.put("assessmentLabel", ruleConclusion(ruleName, displayStatus));
        row.put("colorLevel", colorLevel(displayStatus));
        row.put("triggered", "WARNING".equals(displayStatus));
        row.put("evidenceIds", List.of(evidenceId));
        return row;
    }

    private List<Map<String, Object>> businessCodesFromSnapshots(List<Map<String, Object>> snapshots, List<Map<String, Object>> evidence) {
        Map<String, Long> merged = new LinkedHashMap<>();
        for (Map<String, Object> snapshot : snapshots) {
            Object distribution = firstNonNull(snapshot.get("business_code_distribution"),
                    map(snapshot.get("extra")).get("businessCodeDistribution"),
                    map(snapshot.get("extra")).get("business_code_distribution"));
            if (distribution instanceof Map<?, ?> source) {
                for (Map.Entry<?, ?> entry : source.entrySet()) {
                    merged.merge(String.valueOf(entry.getKey()), number(entry.getValue()), Long::sum);
                }
            }
        }
        if (merged.isEmpty()) {
            return List.of(businessCodeItem(Map.of("businessCode", "UNKNOWN", "count", 0L, "ratio", "0.00%"),
                    "UNKNOWN", evidence));
        }
        long total = merged.values().stream().mapToLong(Long::longValue).sum();
        return merged.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .map(entry -> businessCodeItem(Map.of("businessCode", entry.getKey(), "count", entry.getValue(),
                        "ratio", ratio(entry.getValue(), total)), "WARNING", evidence))
                .toList();
    }

    private Map<String, Object> businessCodeItem(Map<String, Object> row, String status, List<Map<String, Object>> evidence) {
        String code = normalizeBusinessCode(stringValue(row.get("businessCode")));
        String displayStatus = "OK".equals(code) ? "NORMAL" : normalizeStatus(status);
        return new LinkedHashMap<>(Map.of(
                "businessCode", code,
                "businessCodeLabel", businessCodeLabel(code),
                "description", businessCodeDescription(code),
                "count", number(row.get("count")),
                "ratio", firstNonBlank(stringValue(row.get("ratio")), "0.00%"),
                "assessmentStatus", displayStatus,
                "displayStatus", statusLabel(displayStatus),
                "evidenceIds", List.of(firstEvidenceId(evidence))
        ));
    }

    private List<Map<String, Object>> incidentTimeline(Map<String, Object> event, LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(timeline(start, start, "上下文开始", "进入当前分析窗口。"));
        rows.add(timeline(toTime(event.get("first_trigger_time")), start, "首次触发", "Passive Monitor 记录 " + stringValue(event.get("alert_type")) + "。"));
        rows.add(timeline(toTime(event.get("last_trigger_time")), start, "最后触发", "监测事件最后一次刷新。"));
        if (toTime(event.get("resolved_time")) != null) {
            rows.add(timeline(toTime(event.get("resolved_time")), start, "恢复确认", "监测事件已进入恢复状态。"));
        }
        rows.add(timeline(end, start, "报告生成", "生成 Workbench 分析报告。"));
        return rows;
    }

    private List<Map<String, Object>> rangeTimeline(TimeRange range, List<Map<String, Object>> monitorEvents, List<Map<String, Object>> alertEvents) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(timeline(range.start(), range.start(), "窗口开始", "开始聚合 API 网关运行事实。"));
        monitorEvents.stream().limit(3).forEach(event -> rows.add(timeline(toTime(event.get("first_trigger_time")),
                range.start(), "监测事件", stringValue(event.get("alert_type")) + " / " + stringValue(event.get("event_status")))));
        if (!alertEvents.isEmpty()) {
            rows.add(timeline(toTime(alertEvents.get(0).get("start_time")), range.start(), "告警记录", "本窗口存在 alert_event 记录。"));
        }
        rows.add(timeline(range.end(), range.start(), "报告生成", "完成周期健康巡检报告。"));
        return rows;
    }

    private Map<String, Object> timeline(LocalDateTime time, LocalDateTime start, String phase, String description) {
        LocalDateTime actual = time == null ? LocalDateTime.now() : time;
        long minutes = start == null ? 0 : Math.max(0, Duration.between(start, actual).toMinutes());
        return new LinkedHashMap<>(Map.of(
                "absoluteTime", format(actual),
                "relativeTime", "T+" + minutes + "m",
                "phase", phase,
                "description", description
        ));
    }

    private List<String> diagnosisSummary(String summary, String status, String apiCode) {
        List<String> result = new ArrayList<>();
        result.add(firstNonBlank(summary, apiCode + " selected window has been analyzed by deterministic diagnosis."));
        if ("WARNING".equals(status)) {
            result.add("证据显示当前窗口存在需要关注的指标偏差；本报告只确认开发环境已落库事实，不推断真实线上用户影响。");
        } else {
            result.add("当前窗口未形成更高等级展示状态，建议结合后续窗口继续观察趋势。");
        }
        return result.stream().limit(3).toList();
    }

    private List<String> rangeSummary(String status,
                                      Map<String, Object> stats,
                                      List<Map<String, Object>> monitorEvents,
                                      List<Map<String, Object>> alertEvents,
                                      String apiCode) {
        String target = StringUtils.hasText(apiCode) ? apiCode : "全部核心 API";
        long total = number(stats.get("total_count"));
        long fail = number(stats.get("fail_count"));
        List<String> result = new ArrayList<>();
        result.add(target + " 在所选窗口内共记录 " + total + " 次网关请求，失败 " + fail + " 次，展示状态为 " + statusLabel(status) + "。");
        result.add("窗口内 passive monitor 事件数为 " + monitorEvents.size() + "，alert_event 记录数为 " + alertEvents.size() + "。");
        if ("NORMAL".equals(status)) {
            result.add("当前巡检未发现需要升级处理的异常信号，建议保留该窗口作为后续对照。");
        }
        return result.stream().limit(3).toList();
    }

    private List<Map<String, Object>> recommendations(String status, List<Map<String, Object>> evidence) {
        String evidenceId = firstEvidenceId(evidence);
        List<Map<String, Object>> rows = new ArrayList<>();
        if ("WARNING".equals(status)) {
            rows.add(recommendation("P1", "Evidence " + evidenceId,
                    "复核对应时间窗内的限流、错误率和调用方集中度，确认是否需要调整讲座报名等高峰场景的排队、重试提示和重复提交控制。",
                    List.of(evidenceId)));
            rows.add(recommendation("P2", "指标偏差和业务码分布",
                    "保留当前窗口与对照窗口的请求样本，便于后续按 API、调用方和业务码继续追踪。",
                    List.of(evidenceId)));
        } else if ("WATCH".equals(status)) {
            rows.add(recommendation("P2", "Evidence " + evidenceId,
                    "继续观察后续窗口，重点关注限流数量、错误率和延迟是否持续上升。",
                    List.of(evidenceId)));
        } else {
            rows.add(recommendation("P3", "Evidence " + evidenceId,
                    "保持当前监测配置，保留该窗口作为后续健康巡检对照样本。",
                    List.of(evidenceId)));
        }
        return rows.stream().limit(5).toList();
    }

    private Map<String, Object> recommendation(String priority, String basis, String action, List<String> evidenceIds) {
        return new LinkedHashMap<>(Map.of(
                "priority", priority,
                "basisMetricOrEvidence", basis,
                "operationRecommendation", action,
                "evidenceIds", evidenceIds,
                "knowledgeRefs", List.of()
        ));
    }

    private List<String> validateHtmlJson(Map<String, Object> html) {
        List<String> warnings = new ArrayList<>();
        Set<String> required = Set.of("reportType", "reportHeader", "displayStatus", "analysisScope", "metricCheckup",
                "monitorRuleAssessments", "businessCodeDistribution", "eventTimeline", "diagnosisSummary",
                "operationRecommendations", "evidenceList", "dataBoundaryNote");
        for (String key : required) {
            if (!html.containsKey(key)) {
                warnings.add("missing field: " + key);
            }
        }
        String status = stringValue(map(html.get("displayStatus")).get("status"));
        if (!Set.of("NORMAL", "WATCH", "WARNING", "UNKNOWN").contains(status)) {
            warnings.add("displayStatus.status is invalid: " + status);
        }
        String color = stringValue(map(html.get("displayStatus")).get("colorLevel"));
        if (!Set.of("GREEN", "BLUE", "YELLOW", "GRAY").contains(color)) {
            warnings.add("displayStatus.colorLevel is invalid: " + color);
        }
        String rendered = toJson(html);
        for (String forbidden : List.of("CRITICAL", "Root Cause Hypotheses", "Postmortem", "Incident Review", "生成方式", "命中", "未命中", "一句话结论")) {
            if (rendered.contains(forbidden)) {
                warnings.add("forbidden display term found: " + forbidden);
            }
        }
        return warnings;
    }

    private String rangeStatus(Map<String, Object> stats, List<Map<String, Object>> monitorEvents, List<Map<String, Object>> alertEvents) {
        long total = number(stats.get("total_count"));
        long fail = number(stats.get("fail_count"));
        long rateLimit = number(stats.get("rate_limit_count"));
        boolean warningEvent = monitorEvents.stream().anyMatch(row -> "WARNING".equals(normalizeStatus(row.get("risk_level"))));
        if (warningEvent || !alertEvents.isEmpty() || (total > 0 && ((double) fail / total >= 0.10d || (double) rateLimit / total >= 0.05d))) {
            return "WARNING";
        }
        if (rateLimit > 0 || fail > 0 || total == 0) {
            return "WATCH";
        }
        return "NORMAL";
    }

    private String statusSummary(String status, String fallback) {
        if (StringUtils.hasText(fallback)) {
            return fallback;
        }
        return switch (status) {
            case "NORMAL" -> "当前窗口未发现需要升级处理的异常信号。";
            case "WATCH" -> "当前窗口存在需要继续观察的指标变化。";
            case "WARNING" -> "当前窗口存在需要关注的监测信号。";
            default -> "当前窗口数据不足或状态未知。";
        };
    }

    private String rangeStatusSummary(String status, Map<String, Object> stats, List<Map<String, Object>> monitorEvents) {
        return "所选窗口请求总数 " + number(stats.get("total_count"))
                + "，失败数 " + number(stats.get("fail_count"))
                + "，监测事件数 " + monitorEvents.size()
                + "，展示状态为 " + statusLabel(status) + "。";
    }

    private Map<String, Object> findMonitorEvent(String monitorEventId) {
        return jdbcTemplate.queryForList("SELECT * FROM passive_monitor_event WHERE monitor_event_id = ? LIMIT 1", monitorEventId)
                .stream().findFirst().map(this::parseMonitorRow).orElse(null);
    }

    private List<Map<String, Object>> findSnapshots(String monitorEventId) {
        return jdbcTemplate.queryForList("""
                SELECT * FROM passive_alert_snapshot
                WHERE monitor_event_id = ?
                ORDER BY snapshot_time ASC
                """, monitorEventId).stream().map(this::parseSnapshotRow).toList();
    }

    private Map<String, Object> findAlertEvent(Long alertEventId) {
        if (alertEventId == null) {
            return null;
        }
        return jdbcTemplate.queryForList("SELECT * FROM alert_event WHERE id = ? LIMIT 1", alertEventId)
                .stream().findFirst().map(this::parseAlertRow).orElse(null);
    }

    private Map<String, Object> latestAnomaly() {
        return jdbcTemplate.queryForList("""
                SELECT * FROM passive_monitor_event
                WHERE event_status IN ('FIRING', 'COOLDOWN', 'RESOLVED')
                ORDER BY CASE WHEN risk_level = 'WARNING' THEN 0 ELSE 1 END, first_trigger_time DESC
                LIMIT 1
                """).stream().findFirst().map(this::parseMonitorRow).orElse(null);
    }

    private Map<String, Object> findUser(Long userId) {
        Map<String, Object> user = jdbcTemplate.queryForList("SELECT * FROM user WHERE id = ? AND status = 'ACTIVE' LIMIT 1", userId)
                .stream().findFirst().orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found: " + userId);
        }
        return user;
    }

    private TimeRange resolveRange(RangeReportRequest request) {
        LocalDateTime end = parseOptionalTime(request.getEndTime());
        LocalDateTime start = parseOptionalTime(request.getStartTime());
        if (end == null) {
            end = LocalDateTime.now();
        }
        if (start == null) {
            String range = StringUtils.hasText(request.getRange()) ? request.getRange().trim().toLowerCase(Locale.ROOT) : "24h";
            start = switch (range) {
                case "1h" -> end.minusHours(1);
                case "7d" -> end.minusDays(7);
                case "24h" -> end.minusHours(24);
                default -> throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "range must be 1h, 24h, 7d or custom startTime/endTime");
            };
        }
        if (!start.isBefore(end)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "startTime must be before endTime");
        }
        return new TimeRange(start, end);
    }

    private Map<String, Object> parseReportRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("extraInfo", parseJsonMap(row.get("extraInfo")));
        Map<String, Object> extra = map(result.get("extraInfo"));
        if (StringUtils.hasText(stringValue(extra.get("reportType")))) {
            result.put("reportType", extra.get("reportType"));
        }
        Map<String, Object> html = map(extra.get("htmlRenderableJson"));
        Map<String, Object> display = map(html.get("displayStatus"));
        if (StringUtils.hasText(stringValue(display.get("status")))) {
            result.put("riskLevel", display.get("status"));
        }
        result.put("htmlUrl", "/api/dev/report-workbench/reports/" + row.get("reportId") + "/html");
        result.put("llmStatus", extra.get("llmStatus"));
        result.put("sourceType", extra.get("sourceType"));
        return result;
    }

    private Map<String, Object> parseMonitorRow(Map<String, Object> row) {
        Map<String, Object> parsed = new LinkedHashMap<>(row);
        parsed.put("extra", parseJsonMap(row.get("extra_json")));
        return parsed;
    }

    private Map<String, Object> parseSnapshotRow(Map<String, Object> row) {
        Map<String, Object> parsed = new LinkedHashMap<>(row);
        for (String key : List.of("business_code_distribution", "status_code_distribution", "sample_request_ids", "threshold_snapshot", "extra_json")) {
            if (parsed.containsKey(key)) {
                parsed.put(key, parseJsonMap(parsed.get(key)));
            }
        }
        parsed.put("extra", parsed.getOrDefault("extra_json", Map.of()));
        return parsed;
    }

    private Map<String, Object> parseAlertRow(Map<String, Object> row) {
        Map<String, Object> parsed = new LinkedHashMap<>(row);
        parsed.put("extra", parseJsonMap(row.get("extra_info")));
        return parsed;
    }

    private Map<String, Object> parseJsonMap(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), MAP_TYPE);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> map(Object value) {
        return parseJsonMap(value);
    }

    private Map<String, Object> compactMap(Map<String, Object> source, String... keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) {
            result.put(key, source.get(key));
        }
        return result;
    }

    private String reportCode(Map<String, Object> report) {
        return firstNonBlank(stringValue(report.get("reportCode")), "RPT-" + report.get("reportId"));
    }

    private LocalDateTime firstTime(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            LocalDateTime time = toTime(source.get(key));
            if (time != null) {
                return time;
            }
        }
        return null;
    }

    private LocalDateTime parseOptionalTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return parseTime(text);
    }

    private LocalDateTime parseTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), FORMATTER);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(text.trim());
            } catch (Exception ignored) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid time: " + text);
            }
        }
    }

    private LocalDateTime toTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return parseTime(text);
        }
        return null;
    }

    private String format(LocalDateTime time) {
        return time == null ? "" : FORMATTER.format(time);
    }

    private String formatRange(LocalDateTime start, LocalDateTime end) {
        return format(start) + " ~ " + format(end);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeStatus(Object raw) {
        String value = stringValue(raw).toUpperCase(Locale.ROOT);
        if ("NORMAL".equals(value) || "WATCH".equals(value) || "WARNING".equals(value) || "UNKNOWN".equals(value)) {
            return value;
        }
        if ("CRITICAL".equals(value)) {
            return "WARNING";
        }
        return StringUtils.hasText(value) ? "WARNING" : "UNKNOWN";
    }

    private String statusLabel(String status) {
        return switch (normalizeStatus(status)) {
            case "NORMAL" -> "正常（NORMAL）";
            case "WATCH" -> "关注（WATCH）";
            case "WARNING" -> "警告（WARNING）";
            default -> "未知（UNKNOWN）";
        };
    }

    private String colorLevel(String status) {
        return switch (normalizeStatus(status)) {
            case "NORMAL" -> "GREEN";
            case "WATCH" -> "BLUE";
            case "WARNING" -> "YELLOW";
            default -> "GRAY";
        };
    }

    private String evidenceTypeLabel(String type) {
        String actual = firstNonBlank(type, "EVIDENCE");
        return actual + "（" + actual + "）";
    }

    private String targetLabel(String apiCode, String callerAppCode) {
        if (StringUtils.hasText(callerAppCode)) {
            return apiCode + "，调用方：" + callerAppCode;
        }
        return apiCode;
    }

    private String referenceWindow(Map<String, Object> event) {
        LocalDateTime start = toTime(event.get("context_start_time"));
        LocalDateTime end = toTime(event.get("context_end_time"));
        if (start == null || end == null) {
            return "";
        }
        return formatRange(start, end);
    }

    private String ruleLabel(String ruleName, String status) {
        return switch (ruleName) {
            case "HIGH_RATE_LIMIT" -> "限流比例（HIGH_RATE_LIMIT）";
            case "HIGH_ERROR_RATE" -> "错误率（HIGH_ERROR_RATE）";
            case "TRAFFIC_SPIKE" -> "流量突增（TRAFFIC_SPIKE）";
            case "HIGH_5XX_RATE" -> "服务端错误（HIGH_5XX_RATE）";
            case "AUTH_FAILURE_SPIKE" -> "认证失败（AUTH_FAILURE_SPIKE）";
            case "HIGH_LATENCY" -> "延迟升高（HIGH_LATENCY）";
            default -> ruleName + "（" + normalizeStatus(status) + "）";
        };
    }

    private String ruleConclusion(String ruleName, String status) {
        if ("NORMAL".equals(status)) {
            return "正常（NORMAL）";
        }
        if ("WATCH".equals(status)) {
            return ruleMetric(ruleName) + "关注（WATCH）";
        }
        if ("WARNING".equals(status)) {
            return ruleMetric(ruleName) + "升高（WARNING）";
        }
        return "未知（UNKNOWN）";
    }

    private String ruleMetric(String ruleName) {
        return switch (ruleName) {
            case "HIGH_RATE_LIMIT" -> "限流比例";
            case "HIGH_ERROR_RATE" -> "错误率";
            case "TRAFFIC_SPIKE" -> "请求量";
            case "HIGH_5XX_RATE" -> "5xx 比例";
            case "AUTH_FAILURE_SPIKE" -> "认证失败数";
            case "HIGH_LATENCY" -> "P95 延迟";
            default -> "监测指标";
        };
    }

    private String ruleCurrentValue(String ruleName, Map<String, Object> event) {
        return switch (ruleName) {
            case "HIGH_RATE_LIMIT" -> percent(doubleValue(event.get("rate_limit_rate")));
            case "HIGH_ERROR_RATE" -> percent(doubleValue(event.get("error_rate")));
            case "AUTH_FAILURE_SPIKE" -> numberText(event.get("auth_failure_count"));
            case "HIGH_LATENCY" -> numberText(event.get("p95_latency_ms")) + "ms";
            default -> numberText(event.get("request_count"));
        };
    }

    private String thresholdText(String ruleName) {
        return switch (ruleName) {
            case "HIGH_RATE_LIMIT" -> "5.00%";
            case "HIGH_ERROR_RATE" -> "10.00%";
            case "HIGH_LATENCY" -> "1000ms";
            case "TRAFFIC_SPIKE" -> "对照区间";
            default -> "规则阈值";
        };
    }

    private String ruleDeviation(String ruleName, Map<String, Object> event) {
        return switch (ruleName) {
            case "HIGH_RATE_LIMIT" -> pp(doubleValue(event.get("rate_limit_rate")) - 0.05d);
            case "HIGH_ERROR_RATE" -> pp(doubleValue(event.get("error_rate")) - 0.10d);
            case "TRAFFIC_SPIKE" -> signed(number(event.get("request_count")) - number(event.get("baseline_request_count")));
            case "HIGH_LATENCY" -> signed(number(event.get("p95_latency_ms")) - 1000) + "ms";
            default -> "见证据";
        };
    }

    private String normalizeBusinessCode(String value) {
        if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value)) {
            return "UNKNOWN";
        }
        String trimmed = value.trim();
        return switch (trimmed) {
            case "0", "200", "OK" -> "OK";
            case "429" -> "RATE_LIMITED";
            case "401", "403" -> "TOKEN_EXPIRED";
            case "504" -> "DOWNSTREAM_TIMEOUT";
            default -> trimmed.toUpperCase(Locale.ROOT);
        };
    }

    private String businessCodeLabel(String code) {
        return switch (code) {
            case "OK" -> "正常（OK）";
            case "RATE_LIMITED" -> "限流（RATE_LIMITED）";
            case "DUPLICATE_REQUEST" -> "重复提交（DUPLICATE_REQUEST）";
            case "SIGNATURE_MISMATCH" -> "签名异常（SIGNATURE_MISMATCH）";
            case "TOKEN_EXPIRED" -> "登录态过期（TOKEN_EXPIRED）";
            case "SOLD_OUT" -> "名额已满（SOLD_OUT）";
            case "DOWNSTREAM_TIMEOUT" -> "下游超时（DOWNSTREAM_TIMEOUT）";
            case "COURSE_SYSTEM_TIMEOUT" -> "课表系统超时（COURSE_SYSTEM_TIMEOUT）";
            case "UPSTREAM_INTERNAL_ERROR" -> "上游内部错误（UPSTREAM_INTERNAL_ERROR）";
            default -> code;
        };
    }

    private String businessCodeDescription(String code) {
        return switch (code) {
            case "OK" -> "正常返回";
            case "RATE_LIMITED" -> "达到限流策略";
            case "DUPLICATE_REQUEST" -> "请求重复提交";
            case "SIGNATURE_MISMATCH" -> "签名不匹配";
            case "TOKEN_EXPIRED" -> "token 或登录态过期";
            case "SOLD_OUT" -> "业务资源售罄";
            case "DOWNSTREAM_TIMEOUT", "COURSE_SYSTEM_TIMEOUT" -> "下游依赖超时";
            case "UPSTREAM_INTERNAL_ERROR" -> "上游返回内部错误";
            default -> "未配置固定说明";
        };
    }

    private String firstEvidenceId(List<Map<String, Object>> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "EVID-001";
        }
        return stringValue(evidence.get(0).getOrDefault("evidenceId", "EVID-001"));
    }

    private String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100d);
    }

    private String pp(double value) {
        return String.format(Locale.ROOT, "%+.2fpp", value * 100d);
    }

    private String ratio(long count, long total) {
        return total <= 0 ? "0.00%" : String.format(Locale.ROOT, "%.2f%%", (double) count * 100d / total);
    }

    private String signed(long value) {
        return String.format(Locale.ROOT, "%+d", value);
    }

    private String decimal(Object value) {
        return String.format(Locale.ROOT, "%.2f", doubleValue(value));
    }

    private String numberText(Object value) {
        return String.valueOf(number(value));
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (Exception e) {
            return 0L;
        }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0d;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0d;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {
    }

    private record WorkbenchPayload(Map<String, Object> contextJson,
                                    Map<String, Object> htmlJson,
                                    List<String> validationWarnings) {
    }

    private record LlmRunResult(String status, String modelName, Object result, String fallbackReason) {
    }
}
