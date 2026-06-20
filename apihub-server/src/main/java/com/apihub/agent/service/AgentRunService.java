package com.apihub.agent.service;

import com.apihub.agent.common.TraceContext;
import com.apihub.agent.model.dto.AgentRunRequest;
import com.apihub.agent.model.dto.ToolChainEvalRunRequest;
import com.apihub.agent.model.vo.AgentRunResultVO;
import com.apihub.agent.model.vo.ToolChainEvalResultVO;
import com.apihub.agent.model.vo.ToolChainEvalStepVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AgentRunService {

    private static final String DEFAULT_START_TIME = "2026-06-19 00:00:00";
    private static final String DEFAULT_END_TIME = "2026-06-19 23:59:59";

    private final ToolChainEvalService toolChainEvalService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentRunService(ToolChainEvalService toolChainEvalService, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.toolChainEvalService = toolChainEvalService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public AgentRunResultVO run(AgentRunRequest request, Long requestUserId, String requestId) {
        long started = System.nanoTime();
        String traceId = TraceContext.getTraceId();
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
        Long userId = requestUserId == null ? 1L : requestUserId;
        Map<String, Object> user = findActiveUser(userId);
        if (user == null) {
            AgentRunResultVO result = failure(runId, traceId, "USER_NOT_FOUND", "user not found: " + userId);
            result.setLatencyMs(elapsedMs(started));
            return result;
        }

        String scenarioCode = resolveScenarioCode(request);
        if (!StringUtils.hasText(scenarioCode)) {
            AgentRunResultVO result = failure(runId, traceId, "SCENARIO_NOT_MATCHED", "scenarioCode or recognizable apiCode/question is required");
            result.setQuestion(request == null ? null : request.getQuestion());
            result.setStatus("FAILED");
            result.setLatencyMs(elapsedMs(started));
            return result;
        }

        Long sessionId = ensureAgentSession(request, user, traceId, runId, scenarioCode);
        if (sessionId == null) {
            AgentRunResultVO result = failure(runId, traceId, "SESSION_NOT_FOUND", "session not found: " + (request == null ? null : request.getSessionId()));
            result.setStatus("FAILED");
            result.setLatencyMs(elapsedMs(started));
            return result;
        }

        String question = resolveQuestion(request, scenarioCode);
        insertMessage(sessionId, "USER", question, 1, eventData("runId", runId, "scenarioCode", scenarioCode));

        ToolChainEvalRunRequest evalRequest = new ToolChainEvalRunRequest();
        evalRequest.setScenarioCode(scenarioCode);
        evalRequest.setApiCode(request == null ? null : request.getApiCode());
        evalRequest.setStartTime(StringUtils.hasText(request == null ? null : request.getStartTime()) ? request.getStartTime() : DEFAULT_START_TIME);
        evalRequest.setEndTime(StringUtils.hasText(request == null ? null : request.getEndTime()) ? request.getEndTime() : DEFAULT_END_TIME);
        ToolChainEvalResultVO eval = toolChainEvalService.run(evalRequest, userId, requestId);

        AgentRunResultVO result = buildResult(runId, sessionId, question, eval, started);
        String finalAnswer = buildFinalAnswer(eval);
        result.setFinalAnswer(finalAnswer);
        if (!eval.isSuccess()) {
            result.setStatus("FAILED");
            result.setErrorCode(eval.getErrorCode());
            result.setErrorMessage(eval.getErrorMessage());
        }

        Long reportId = insertReport(result, userId);
        result.setReportId(reportId);
        int evidenceCount = insertEvidenceItems(result, reportId);
        updateReportEvidenceCount(reportId, evidenceCount);
        insertMessage(sessionId, "ASSISTANT", finalAnswer, 2, eventData(
                "runId", runId,
                "reportId", reportId,
                "status", result.getStatus()
        ));
        updateSession(sessionId, result, started);
        return result;
    }

    public SseEmitter stream(AgentRunRequest request, Long userId, String requestId, String traceId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        CompletableFuture.runAsync(() -> {
            try {
                TraceContext.setTraceId(traceId);
                send(emitter, "stage", eventData("stage", "build_context"));
                String scenarioCode = resolveScenarioCode(request);
                send(emitter, "stage", eventData("stage", "select_scenario", "scenarioCode", scenarioCode));
                send(emitter, "stage", eventData("stage", "run_tool_chain"));
                AgentRunResultVO result = run(request, userId, requestId);
                send(emitter, "agent_start", eventData(
                        "runId", result.getRunId(),
                        "sessionId", result.getSessionId(),
                        "scenarioCode", result.getScenarioCode(),
                        "apiCode", result.getApiCode(),
                        "traceId", result.getTraceId()
                ));
                for (ToolChainEvalStepVO step : result.getSteps()) {
                    send(emitter, "tool_step", step);
                }
                send(emitter, "stage", eventData("stage", "merge_evidence"));
                for (Object evidence : firstItems(result.getEvidenceItems(), 10)) {
                    send(emitter, "evidence", evidence);
                }
                send(emitter, "risk", eventData(
                        "riskLevel", result.getRiskLevel(),
                        "riskReasons", result.getRiskReasons()
                ));
                send(emitter, "stage", eventData("stage", "generate_template_answer"));
                send(emitter, "answer", eventData("finalAnswer", result.getFinalAnswer()));
                send(emitter, "stage", eventData("stage", "persist_result"));
                if (!result.isSuccess()) {
                    send(emitter, "error", eventData(
                            "runId", result.getRunId(),
                            "status", result.getStatus(),
                            "errorCode", result.getErrorCode(),
                            "errorMessage", result.getErrorMessage()
                    ));
                }
                send(emitter, "stage", eventData("stage", "completed"));
                send(emitter, "done", eventData(
                        "runId", result.getRunId(),
                        "sessionId", result.getSessionId(),
                        "reportId", result.getReportId(),
                        "status", result.getStatus(),
                        "latencyMs", result.getLatencyMs()
                ));
                emitter.complete();
            } catch (Exception e) {
                try {
                    send(emitter, "error", eventData("status", "FAILED", "errorCode", "AGENT_RUN_FAILED", "errorMessage", "agent run failed"));
                } catch (Exception ignored) {
                    // Ignore secondary SSE failure.
                }
                emitter.complete();
            } finally {
                TraceContext.clear();
            }
        });
        return emitter;
    }

    private String resolveScenarioCode(AgentRunRequest request) {
        if (request != null && StringUtils.hasText(request.getScenarioCode())) {
            return request.getScenarioCode().trim().toUpperCase();
        }
        String apiCode = request == null ? "" : stringValue(request.getApiCode()).toUpperCase();
        String question = request == null ? "" : stringValue(request.getQuestion()).toLowerCase();
        if ("AUTH_LOGIN".equals(apiCode) || containsAny(question, "登录", "403", "认证", "签名", "token", "auth")) {
            return "AUTH_LOGIN_403_DIAG";
        }
        if ("LECTURE_REGISTER".equals(apiCode) || containsAny(question, "讲座", "报名", "高峰", "限流", "429", "lecture")) {
            return "LECTURE_REGISTER_PEAK";
        }
        if ("VENUE_RESERVE".equals(apiCode) || containsAny(question, "场地", "预约", "重复", "幂等", "冲突", "idempotency")) {
            return "VENUE_RESERVE_IDEMPOTENCY";
        }
        if ("LIBRARY_BORROW".equals(apiCode) || containsAny(question, "图书", "借阅", "超时", "下游", "dependency", "timeout")) {
            return "LIBRARY_BORROW_DEPENDENCY";
        }
        return null;
    }

    private AgentRunResultVO buildResult(String runId, Long sessionId, String question, ToolChainEvalResultVO eval, long started) {
        AgentRunResultVO result = new AgentRunResultVO();
        result.setSuccess(eval.isSuccess());
        result.setRunId(runId);
        result.setSessionId(sessionId);
        result.setScenarioCode(eval.getScenarioCode());
        result.setScenarioName(eval.getScenarioName());
        result.setApiCode(eval.getApiCode());
        result.setApiName(eval.getApiName());
        result.setQuestion(question);
        result.setStartTime(eval.getStartTime());
        result.setEndTime(eval.getEndTime());
        result.setStatus(eval.isSuccess() ? "COMPLETED" : "FAILED");
        result.setSteps(eval.getSteps());
        result.setEvidenceItems(eval.getMergedEvidenceItems());
        result.setRiskLevel(eval.getRiskLevel());
        result.setRiskReasons(eval.getRiskReasons());
        result.setErrorCode(eval.getErrorCode());
        result.setErrorMessage(eval.getErrorMessage());
        result.setTraceId(eval.getTraceId());
        result.setLatencyMs(elapsedMs(started));
        return result;
    }

    private String buildFinalAnswer(ToolChainEvalResultVO eval) {
        String conclusion = StringUtils.hasText(eval.getTemplateConclusion())
                ? eval.getTemplateConclusion()
                : "The deterministic Agent skeleton could not complete the selected scenario.";
        return "以下分析基于 API-HUB Agent 的工具链结果生成。\n\n"
                + conclusion
                + "\n\n当前版本未调用大模型，结论来自确定性规则和 Tool 证据。";
    }

    private Long ensureAgentSession(AgentRunRequest request, Map<String, Object> user, String traceId, String runId, String scenarioCode) {
        if (request != null && request.getSessionId() != null) {
            List<Long> existing = jdbcTemplate.query(
                    "SELECT id FROM agent_session WHERE id = ?",
                    (rs, rowNum) -> rs.getLong("id"),
                    request.getSessionId()
            );
            if (existing.isEmpty()) {
                return null;
            }
            jdbcTemplate.update(
                    """
                    UPDATE agent_session
                    SET trace_id = ?, status = 'RUNNING', workflow_name = 'agent_run_skeleton',
                        updated_at = CURRENT_TIMESTAMP, extra_info = ?
                    WHERE id = ?
                    """,
                    traceId,
                    toJson(eventData("runId", runId, "scenarioCode", scenarioCode, "reuseSession", true)),
                    request.getSessionId()
            );
            return request.getSessionId();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sessionCode = "sess_agent_run_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO agent_session (
                      session_code, trace_id, user_id, user_type, session_type, title, workflow_name,
                      status, duration_ms, retry_count, last_event_seq, started_at, finished_at,
                      extra_info, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'DIAGNOSIS', ?, 'agent_run_skeleton',
                      'RUNNING', 0, 0, 0, CURRENT_TIMESTAMP, NULL,
                      ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, sessionCode);
            ps.setString(2, traceId);
            ps.setLong(3, ((Number) user.get("id")).longValue());
            ps.setString(4, String.valueOf(user.get("user_type")));
            ps.setString(5, "Agent Run Skeleton - " + scenarioCode);
            ps.setString(6, toJson(eventData("runId", runId, "scenarioCode", scenarioCode)));
            return ps;
        }, keyHolder);
        return keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
    }

    private void insertMessage(Long sessionId, String role, String content, int order, Map<String, Object> extraInfo) {
        jdbcTemplate.update(
                """
                INSERT INTO agent_message (session_id, message_role, content, message_order, status, extra_info, created_at)
                VALUES (?, ?, ?, ?, 'SUCCESS', ?, CURRENT_TIMESTAMP)
                """,
                sessionId,
                role,
                content,
                order,
                toJson(extraInfo)
        );
    }

    private Long insertReport(AgentRunResultVO result, Long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String reportCode = "RPT_AGENT_RUN_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO agent_report (
                      report_code, session_id, trace_id, report_type, title, summary, risk_level, content_md,
                      created_by, evidence_count, tool_call_count, duration_ms, status, error_code, error_message,
                      generated_at, extra_info, remark, created_at, updated_at
                    ) VALUES (?, ?, ?, 'DIAGNOSIS', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, reportCode);
            ps.setLong(2, result.getSessionId());
            ps.setString(3, result.getTraceId());
            ps.setString(4, "Agent Run Skeleton - " + result.getScenarioCode());
            ps.setString(5, truncate(result.getFinalAnswer(), 900));
            ps.setString(6, StringUtils.hasText(result.getRiskLevel()) ? result.getRiskLevel() : "LOW");
            ps.setString(7, buildContentMarkdown(result));
            ps.setLong(8, userId);
            ps.setInt(9, result.getEvidenceItems() == null ? 0 : result.getEvidenceItems().size());
            ps.setInt(10, result.getSteps() == null ? 0 : result.getSteps().size());
            ps.setLong(11, result.getLatencyMs());
            ps.setString(12, result.isSuccess() ? "SUCCESS" : "FAILED");
            ps.setString(13, result.getErrorCode());
            ps.setString(14, result.getErrorMessage());
            ps.setString(15, toJson(eventData(
                    "runId", stringValue(result.getRunId()),
                    "scenarioCode", stringValue(result.getScenarioCode()),
                    "apiCode", stringValue(result.getApiCode()),
                    "noLlm", true
            )));
            ps.setString(16, "No-LLM deterministic Agent Run skeleton");
            return ps;
        }, keyHolder);
        return keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
    }

    private int insertEvidenceItems(AgentRunResultVO result, Long reportId) {
        if (result.getEvidenceItems() == null || reportId == null) {
            return 0;
        }
        int count = 0;
        for (Object item : result.getEvidenceItems()) {
            if (!(item instanceof Map<?, ?> evidence)) {
                continue;
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO evidence_item (
                      session_id, trace_id, report_id, source_type, source_id, title, content,
                      confidence, status, extra_info, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)
                    """,
                    result.getSessionId(),
                    result.getTraceId(),
                    reportId,
                    normalizeEvidenceSourceType(evidence.get("sourceType"), evidence.get("evidenceType")),
                    stringValue(evidence.get("sourceId")),
                    truncate(stringValue(evidence.get("title")), 240),
                    truncate(maskSensitive(stringValue(firstNonNull(evidence.get("summary"), evidence.get("quote")))), 1200),
                    confidence(evidence.get("score")),
                    toJson(evidence)
            );
            count++;
        }
        return count;
    }

    private void updateReportEvidenceCount(Long reportId, int evidenceCount) {
        if (reportId != null) {
            jdbcTemplate.update("UPDATE agent_report SET evidence_count = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", evidenceCount, reportId);
        }
    }

    private void updateSession(Long sessionId, AgentRunResultVO result, long started) {
        jdbcTemplate.update(
                """
                UPDATE agent_session
                SET status = ?, error_code = ?, error_message = ?, duration_ms = ?, last_event_seq = ?,
                    finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, extra_info = ?
                WHERE id = ?
                """,
                result.isSuccess() ? "SUCCESS" : "FAILED",
                result.getErrorCode(),
                result.getErrorMessage(),
                elapsedMs(started),
                result.getSteps() == null ? 0 : result.getSteps().size(),
                toJson(eventData("runId", result.getRunId(), "scenarioCode", result.getScenarioCode(), "apiCode", result.getApiCode())),
                sessionId
        );
    }

    private Map<String, Object> findActiveUser(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, user_type FROM sys_user WHERE id = ? AND status = 'ACTIVE'",
                userId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AgentRunResultVO failure(String runId, String traceId, String errorCode, String errorMessage) {
        AgentRunResultVO result = new AgentRunResultVO();
        result.setSuccess(false);
        result.setRunId(runId);
        result.setTraceId(traceId);
        result.setStatus("FAILED");
        result.setRiskLevel("UNKNOWN");
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        result.setFinalAnswer(errorMessage);
        return result;
    }

    private String resolveQuestion(AgentRunRequest request, String scenarioCode) {
        if (request != null && StringUtils.hasText(request.getQuestion())) {
            return request.getQuestion().trim();
        }
        return "Run deterministic Agent skeleton for " + scenarioCode;
    }

    private String buildContentMarkdown(AgentRunResultVO result) {
        StringBuilder builder = new StringBuilder();
        builder.append("## Final Answer\n").append(result.getFinalAnswer()).append("\n\n");
        builder.append("## Scenario\n").append(result.getScenarioCode()).append(" / ").append(result.getApiCode()).append("\n\n");
        builder.append("## Evidence Count\n").append(result.getEvidenceItems() == null ? 0 : result.getEvidenceItems().size()).append("\n");
        return builder.toString();
    }

    private String normalizeEvidenceSourceType(Object sourceType, Object evidenceType) {
        String type = stringValue(sourceType).toLowerCase();
        if (type.contains("gateway_log")) {
            return "LOG";
        }
        if (type.contains("rate_limit")) {
            return "RULE";
        }
        if (type.contains("alert")) {
            return "ALERT";
        }
        if (type.contains("campus")) {
            return "EVENT";
        }
        if (type.contains("rag")) {
            return "DOC";
        }
        String evidence = stringValue(evidenceType);
        return StringUtils.hasText(evidence) ? truncate(evidence, 64) : "DOC";
    }

    private BigDecimal confidence(Object score) {
        if (score instanceof Number number) {
            double value = Math.max(0.0, Math.min(1.0, number.doubleValue()));
            return BigDecimal.valueOf(value);
        }
        return null;
    }

    private void send(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private List<Object> firstItems(List<Object> items, int max) {
        if (items == null || items.size() <= max) {
            return items == null ? List.of() : items;
        }
        return new ArrayList<>(items.subList(0, max));
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    private Map<String, Object> eventData(Object... pairs) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            data.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return data;
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
