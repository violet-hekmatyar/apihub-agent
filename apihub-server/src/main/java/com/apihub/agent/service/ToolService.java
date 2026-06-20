package com.apihub.agent.service;

import com.apihub.agent.common.TraceContext;
import com.apihub.agent.model.dto.QueryApiCallStatsRequest;
import com.apihub.agent.model.dto.QueryApiInfoRequest;
import com.apihub.agent.model.dto.QueryGatewayLogsRequest;
import com.apihub.agent.model.dto.QueryRateLimitRuleRequest;
import com.apihub.agent.model.tool.ToolContext;
import com.apihub.agent.model.tool.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ToolService {

    private static final String TOOL_QUERY_API_INFO = "queryApiInfo";
    private static final String TOOL_QUERY_API_CALL_STATS = "queryApiCallStats";
    private static final String TOOL_QUERY_GATEWAY_LOGS = "queryGatewayLogs";
    private static final String TOOL_QUERY_RATE_LIMIT_RULE = "queryRateLimitRule";
    private static final String TOOL_TYPE_LOCAL = "LOCAL";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ToolService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ToolContext buildContext(Long requestUserId, String requestId) {
        Long userId = requestUserId == null ? 1L : requestUserId;
        Map<String, Object> user = findActiveUser(userId);
        if (user == null) {
            ToolContext context = new ToolContext();
            context.setTraceId(TraceContext.getTraceId());
            context.setUserId(userId);
            context.setUserType("UNKNOWN");
            context.setRequestTime(LocalDateTime.now());
            context.setRequestId(requestId);
            context.setSource("DEV_TOOL_API");
            return context;
        }

        ToolContext context = new ToolContext();
        context.setTraceId(TraceContext.getTraceId());
        context.setUserId(((Number) user.get("id")).longValue());
        context.setUserType((String) user.get("user_type"));
        context.setRequestTime(LocalDateTime.now());
        context.setRequestId(requestId);
        context.setSource("DEV_TOOL_API");
        return context;
    }

    public ToolResult queryApiInfo(QueryApiInfoRequest request, ToolContext context) {
        long started = System.nanoTime();
        String spanId = newSpanId(TOOL_QUERY_API_INFO);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("apiCode", request == null ? null : request.getApiCode());
        input.put("includeRateLimit", request != null && Boolean.TRUE.equals(request.getIncludeRateLimit()));
        input.put("includeConsumerApps", request != null && Boolean.TRUE.equals(request.getIncludeConsumerApps()));

        ToolResult result;
        try {
            if (!isKnownUser(context)) {
                result = failure(TOOL_QUERY_API_INFO, "Current demo user does not exist.", "USER_NOT_FOUND",
                        "user not found: " + context.getUserId(), context, spanId, started);
                return result;
            }
            String apiCode = normalizeApiCode(request == null ? null : request.getApiCode());
            if (!StringUtils.hasText(apiCode)) {
                result = failure(TOOL_QUERY_API_INFO, "apiCode is required.", "INVALID_ARGUMENT",
                        "apiCode is required", context, spanId, started);
                return result;
            }
            Map<String, Object> api = findApi(apiCode);
            if (api == null) {
                result = failure(TOOL_QUERY_API_INFO, "API was not found.", "API_NOT_FOUND",
                        "api not found: " + apiCode, context, spanId, started);
                return result;
            }
            if (!canAccessApi(context, ((Number) api.get("id")).longValue())) {
                result = failure(TOOL_QUERY_API_INFO, "Current user has no permission to query this API.",
                        "PERMISSION_DENIED", "permission denied for api: " + apiCode, context, spanId, started);
                return result;
            }

            Map<String, Object> data = buildApiInfoData(api);
            if (request != null && Boolean.TRUE.equals(request.getIncludeRateLimit())) {
                data.put("rateLimitRules", queryRateLimitRules(((Number) api.get("id")).longValue()));
            } else {
                data.put("rateLimitRules", List.of());
            }
            if (request != null && Boolean.TRUE.equals(request.getIncludeConsumerApps())) {
                data.put("consumerApps", queryConsumerApps(((Number) api.get("id")).longValue()));
            } else {
                data.put("consumerApps", List.of());
            }

            result = ToolResult.success(TOOL_QUERY_API_INFO, "API info query completed for " + apiCode + ".",
                    data, context, spanId, elapsedMs(started));
            return result;
        } catch (Exception e) {
            result = failure(TOOL_QUERY_API_INFO, "API info query failed.", "DATA_SOURCE_ERROR",
                    "data source error", context, spanId, started);
            return result;
        } finally {
            // Trace is written in a second pass so both success and business failures are persisted.
            // The local variable is persisted by wrapping each return through traceAndReturn.
        }
    }

    public ToolResult queryApiInfoWithTrace(QueryApiInfoRequest request, ToolContext context) {
        ToolResult result = queryApiInfo(request, context);
        writeTrace(context, result, request);
        return result;
    }

    public ToolResult queryApiCallStatsWithTrace(QueryApiCallStatsRequest request, ToolContext context) {
        ToolResult result = queryApiCallStats(request, context);
        writeTrace(context, result, request);
        return result;
    }

    public ToolResult queryGatewayLogsWithTrace(QueryGatewayLogsRequest request, ToolContext context) {
        ToolResult result = queryGatewayLogs(request, context);
        writeTrace(context, result, request);
        return result;
    }

    public ToolResult queryRateLimitRuleWithTrace(QueryRateLimitRuleRequest request, ToolContext context) {
        ToolResult result = queryRateLimitRule(request, context);
        writeTrace(context, result, request);
        return result;
    }

    private ToolResult queryApiCallStats(QueryApiCallStatsRequest request, ToolContext context) {
        long started = System.nanoTime();
        String spanId = newSpanId(TOOL_QUERY_API_CALL_STATS);
        try {
            if (!isKnownUser(context)) {
                return failure(TOOL_QUERY_API_CALL_STATS, "Current demo user does not exist.", "USER_NOT_FOUND",
                        "user not found: " + context.getUserId(), context, spanId, started);
            }
            String apiCode = normalizeApiCode(request == null ? null : request.getApiCode());
            if (!StringUtils.hasText(apiCode)) {
                return failure(TOOL_QUERY_API_CALL_STATS, "apiCode is required.", "INVALID_ARGUMENT",
                        "apiCode is required", context, spanId, started);
            }
            Map<String, Object> api = findApi(apiCode);
            if (api == null) {
                return failure(TOOL_QUERY_API_CALL_STATS, "API was not found.", "API_NOT_FOUND",
                        "api not found: " + apiCode, context, spanId, started);
            }
            if (!canAccessApi(context, ((Number) api.get("id")).longValue())) {
                return failure(TOOL_QUERY_API_CALL_STATS, "Current user has no permission to query this API.",
                        "PERMISSION_DENIED", "permission denied for api: " + apiCode, context, spanId, started);
            }

            LocalDateTime startTime = parseTimeOrDefault(request == null ? null : request.getStartTime(), "2026-06-19 00:00:00");
            LocalDateTime endTime = parseTimeOrDefault(request == null ? null : request.getEndTime(), "2026-06-19 23:59:59");
            if (startTime == null || endTime == null || startTime.isAfter(endTime)) {
                return failure(TOOL_QUERY_API_CALL_STATS, "Invalid time range.", "INVALID_ARGUMENT",
                        "startTime must be before or equal to endTime", context, spanId, started);
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT id, stat_time, total_count, success_count, fail_count, error_4xx_count,
                           error_5xx_count, rate_limit_count, avg_latency_ms, p95_latency_ms,
                           p99_latency_ms, max_latency_ms
                    FROM api_call_stat_hourly
                    WHERE api_id = ? AND stat_time >= ? AND stat_time <= ? AND status = 'ACTIVE'
                    ORDER BY stat_time ASC
                    """,
                    api.get("id"),
                    Timestamp.valueOf(startTime),
                    Timestamp.valueOf(endTime)
            );

            Map<String, Object> data = buildStatsData(api, startTime, endTime, rows);
            String summary = rows.isEmpty()
                    ? "No hourly stats were found for " + apiCode + " in the selected time range."
                    : "API call stats query completed for " + apiCode + ".";
            return ToolResult.success(TOOL_QUERY_API_CALL_STATS, summary, data, context, spanId, elapsedMs(started));
        } catch (DateTimeParseException e) {
            return failure(TOOL_QUERY_API_CALL_STATS, "Invalid time format.", "INVALID_ARGUMENT",
                    "time format must be yyyy-MM-dd HH:mm:ss", context, spanId, started);
        } catch (Exception e) {
            return failure(TOOL_QUERY_API_CALL_STATS, "API call stats query failed.", "DATA_SOURCE_ERROR",
                    "data source error", context, spanId, started);
        }
    }

    private ToolResult queryGatewayLogs(QueryGatewayLogsRequest request, ToolContext context) {
        long started = System.nanoTime();
        String spanId = newSpanId(TOOL_QUERY_GATEWAY_LOGS);
        try {
            ApiCheck check = checkApiAccess(
                    TOOL_QUERY_GATEWAY_LOGS,
                    request == null ? null : request.getApiCode(),
                    context,
                    spanId,
                    started
            );
            if (check.result() != null) {
                return check.result();
            }

            LocalDateTime startTime = parseTimeOrDefault(request == null ? null : request.getStartTime(), "2026-06-19 00:00:00");
            LocalDateTime endTime = parseTimeOrDefault(request == null ? null : request.getEndTime(), "2026-06-19 23:59:59");
            if (startTime == null || endTime == null || startTime.isAfter(endTime)) {
                return failure(TOOL_QUERY_GATEWAY_LOGS, "Invalid time range.", "INVALID_ARGUMENT",
                        "startTime must be before or equal to endTime", context, spanId, started);
            }

            int limit = normalizeLimit(request == null ? null : request.getLimit(), 20, 100);
            GatewayLogWhere where = buildGatewayLogWhere(check.api(), request, startTime, endTime);
            Long totalMatched = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM gateway_log l " + where.whereClause(),
                    Long.class,
                    where.params().toArray()
            );
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT l.id, l.trace_id, l.request_path, l.request_method, l.http_status,
                           l.error_code, l.error_message, l.latency_ms, l.client_ip,
                           l.request_time, l.extra_info, app.app_code
                    FROM gateway_log l
                    LEFT JOIN api_consumer_app app ON app.id = l.app_id
                    """ + where.whereClause() + " ORDER BY l.request_time DESC, l.id DESC LIMIT ?",
                    appendParam(where.params(), limit).toArray()
            );

            Map<String, Object> data = buildGatewayLogData(check.api(), request, startTime, endTime,
                    totalMatched == null ? 0 : totalMatched, rows);
            ToolResult result = ToolResult.success(
                    TOOL_QUERY_GATEWAY_LOGS,
                    rows.isEmpty() ? "No gateway logs were found in the selected range." : "Gateway log query completed for " + check.apiCode() + ".",
                    data,
                    context,
                    spanId,
                    elapsedMs(started)
            );
            result.setEvidenceItems(buildGatewayLogEvidence(check.apiCode(), rows));
            return result;
        } catch (DateTimeParseException e) {
            return failure(TOOL_QUERY_GATEWAY_LOGS, "Invalid time format.", "INVALID_ARGUMENT",
                    "time format must be yyyy-MM-dd HH:mm:ss", context, spanId, started);
        } catch (Exception e) {
            return failure(TOOL_QUERY_GATEWAY_LOGS, "Gateway log query failed.", "DATA_SOURCE_ERROR",
                    "data source error", context, spanId, started);
        }
    }

    private ToolResult queryRateLimitRule(QueryRateLimitRuleRequest request, ToolContext context) {
        long started = System.nanoTime();
        String spanId = newSpanId(TOOL_QUERY_RATE_LIMIT_RULE);
        try {
            ApiCheck check = checkApiAccess(
                    TOOL_QUERY_RATE_LIMIT_RULE,
                    request == null ? null : request.getApiCode(),
                    context,
                    spanId,
                    started
            );
            if (check.result() != null) {
                return check.result();
            }

            boolean includeInactive = request != null && Boolean.TRUE.equals(request.getIncludeInactive());
            List<Map<String, Object>> rows = queryRateLimitRuleRows(((Number) check.api().get("id")).longValue(), includeInactive);
            Map<String, Object> data = buildRateLimitRuleData(check.api(), includeInactive, rows);
            ToolResult result = ToolResult.success(
                    TOOL_QUERY_RATE_LIMIT_RULE,
                    rows.isEmpty()
                            ? "No rate limit rule is configured for " + check.apiCode() + "."
                            : "Rate limit rule query completed for " + check.apiCode() + ".",
                    data,
                    context,
                    spanId,
                    elapsedMs(started)
            );
            result.setEvidenceItems(buildRateLimitRuleEvidence(check.apiCode(), rows));
            return result;
        } catch (Exception e) {
            return failure(TOOL_QUERY_RATE_LIMIT_RULE, "Rate limit rule query failed.", "DATA_SOURCE_ERROR",
                    "data source error", context, spanId, started);
        }
    }

    private Map<String, Object> buildApiInfoData(Map<String, Object> api) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiCode", api.get("api_code"));
        data.put("apiName", api.get("api_name"));
        data.put("apiType", api.get("api_type"));
        data.put("description", api.get("description"));
        data.put("providerUserId", api.get("provider_user_id"));
        data.put("providerName", api.get("provider_name"));
        data.put("providerUserType", api.get("provider_user_type"));
        data.put("ownerTeam", api.get("owner_team"));
        data.put("riskLevel", api.get("risk_level"));
        data.put("onlineStatus", api.get("online_status"));
        data.put("status", api.get("status"));
        data.put("path", api.get("path"));
        data.put("method", api.get("method"));
        data.put("createdAt", formatTime(api.get("created_at")));
        data.put("updatedAt", formatTime(api.get("updated_at")));
        data.put("extraInfo", parseJsonObject(api.get("extra_info")));
        return data;
    }

    private Map<String, Object> buildStatsData(Map<String, Object> api, LocalDateTime startTime, LocalDateTime endTime, List<Map<String, Object>> rows) {
        long total = 0;
        long success = 0;
        long fail = 0;
        long rateLimited = 0;
        long weightedLatency = 0;
        int maxP95 = 0;
        int maxP99 = 0;
        int maxLatency = 0;
        List<Map<String, Object>> hourlyStats = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            long rowTotal = longValue(row.get("total_count"));
            long rowSuccess = longValue(row.get("success_count"));
            long rowFail = longValue(row.get("fail_count"));
            int avgLatency = intValue(row.get("avg_latency_ms"));
            int p95 = intValue(row.get("p95_latency_ms"));
            int p99 = intValue(row.get("p99_latency_ms"));
            int rowMaxLatency = intValue(row.get("max_latency_ms"));

            total += rowTotal;
            success += rowSuccess;
            fail += rowFail;
            rateLimited += longValue(row.get("rate_limit_count"));
            weightedLatency += rowTotal * avgLatency;
            maxP95 = Math.max(maxP95, p95);
            maxP99 = Math.max(maxP99, p99);
            maxLatency = Math.max(maxLatency, rowMaxLatency);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("statTime", formatTime(row.get("stat_time")));
            item.put("totalCount", rowTotal);
            item.put("successCount", rowSuccess);
            item.put("failCount", rowFail);
            item.put("error4xxCount", longValue(row.get("error_4xx_count")));
            item.put("error5xxCount", longValue(row.get("error_5xx_count")));
            item.put("rateLimitedCount", longValue(row.get("rate_limit_count")));
            item.put("avgLatencyMs", avgLatency);
            item.put("p95LatencyMs", p95);
            item.put("p99LatencyMs", p99);
            item.put("maxLatencyMs", rowMaxLatency);
            item.put("failRate", rowTotal == 0 ? 0 : round4((double) rowFail / rowTotal));
            hourlyStats.add(item);
        }

        double failRate = total == 0 ? 0 : round4((double) fail / total);
        int avgLatency = total == 0 ? 0 : (int) Math.round((double) weightedLatency / total);
        List<String> riskReasons = buildRiskReasons(failRate, maxP95, rateLimited);
        String riskLevel = determineRiskLevel(failRate, maxP95, rateLimited, total);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiCode", api.get("api_code"));
        data.put("apiName", api.get("api_name"));
        data.put("startTime", FORMATTER.format(startTime));
        data.put("endTime", FORMATTER.format(endTime));
        data.put("totalCallCount", total);
        data.put("totalSuccessCount", success);
        data.put("totalFailCount", fail);
        data.put("failRate", failRate);
        data.put("avgLatencyMs", avgLatency);
        data.put("maxP95LatencyMs", maxP95);
        data.put("maxP99LatencyMs", maxP99);
        data.put("maxLatencyMs", maxLatency);
        data.put("totalRateLimitedCount", rateLimited);
        data.put("riskLevel", riskLevel);
        data.put("riskReasons", riskReasons);
        data.put("hourlyStats", hourlyStats);
        return data;
    }

    private List<String> buildRiskReasons(double failRate, int maxP95, long rateLimited) {
        List<String> reasons = new ArrayList<>();
        if (failRate >= 0.05) {
            reasons.add("fail rate is elevated");
        }
        if (maxP95 >= 700) {
            reasons.add("P95 latency is high");
        } else if (maxP95 >= 350) {
            reasons.add("P95 latency increased");
        }
        if (rateLimited > 100) {
            reasons.add("rate limited calls increased");
        }
        if (reasons.isEmpty()) {
            reasons.add("no obvious risk in selected range");
        }
        return reasons;
    }

    private String determineRiskLevel(double failRate, int maxP95, long rateLimited, long total) {
        if (failRate >= 0.05 || maxP95 >= 700 || rateLimited > 100) {
            return "HIGH";
        }
        if (total >= 10000 || maxP95 >= 350 || rateLimited > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<Map<String, Object>> queryRateLimitRules(Long apiId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, rule_name, qps_limit, burst_limit, degrade_enabled, fallback_message,
                       effective_start, effective_end, status
                FROM rate_limit_rule
                WHERE api_id = ? AND status <> 'DELETED'
                ORDER BY id ASC
                """,
                apiId
        ).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("ruleName", row.get("rule_name"));
            item.put("qpsLimit", row.get("qps_limit"));
            item.put("burstLimit", row.get("burst_limit"));
            item.put("degradeEnabled", intValue(row.get("degrade_enabled")) == 1);
            item.put("fallbackMessage", row.get("fallback_message"));
            item.put("effectiveStart", formatTime(row.get("effective_start")));
            item.put("effectiveEnd", formatTime(row.get("effective_end")));
            item.put("status", row.get("status"));
            return item;
        }).toList();
    }

    private List<Map<String, Object>> queryRateLimitRuleRows(Long apiId, boolean includeInactive) {
        String statusClause = includeInactive ? "status <> 'DELETED'" : "status = 'ACTIVE'";
        return jdbcTemplate.queryForList(
                """
                SELECT id, rule_name, qps_limit, burst_limit, degrade_enabled, fallback_message,
                       effective_start, effective_end, status, created_at, updated_at
                FROM rate_limit_rule
                WHERE api_id = ? AND
                """ + " " + statusClause + " ORDER BY id ASC",
                apiId
        );
    }

    private List<Map<String, Object>> queryConsumerApps(Long apiId) {
        return jdbcTemplate.queryForList(
                """
                SELECT app.id, app.app_code, app.app_name, app.owner_team, app.app_type,
                       app.status, auth.auth_status
                FROM api_authorization auth
                JOIN api_consumer_app app ON app.id = auth.app_id
                WHERE auth.api_id = ? AND auth.status = 'ACTIVE' AND app.status = 'ACTIVE'
                ORDER BY app.id ASC
                """,
                apiId
        ).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("appCode", row.get("app_code"));
            item.put("appName", row.get("app_name"));
            item.put("ownerTeam", row.get("owner_team"));
            item.put("appType", row.get("app_type"));
            item.put("status", row.get("status"));
            item.put("authStatus", row.get("auth_status"));
            return item;
        }).toList();
    }

    private Map<String, Object> findApi(String apiCode) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT api.id, api.api_code, api.api_name, api.api_type, api.path, api.method,
                           api.description, api.provider_user_id, api.owner_team, api.risk_level,
                           api.online_status, api.status, api.extra_info, api.created_at, api.updated_at,
                           u.display_name AS provider_name, u.user_type AS provider_user_type
                    FROM api_endpoint api
                    JOIN sys_user u ON u.id = api.provider_user_id
                    WHERE api.api_code = ? AND api.status <> 'DELETED'
                    """,
                    apiCode
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Map<String, Object> findActiveUser(Long userId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, user_type FROM sys_user WHERE id = ? AND status = 'ACTIVE'",
                    userId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private boolean canAccessApi(ToolContext context, Long apiId) {
        if ("MANAGER".equals(context.getUserType())) {
            return true;
        }
        if (!"USER".equals(context.getUserType())) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM api_endpoint api
                WHERE api.id = ?
                  AND (
                    api.provider_user_id = ?
                    OR EXISTS (
                      SELECT 1
                      FROM api_authorization auth
                      JOIN api_consumer_app app ON app.id = auth.app_id
                      WHERE auth.api_id = api.id
                        AND auth.status = 'ACTIVE'
                        AND auth.auth_status = 'APPROVED'
                        AND app.status = 'ACTIVE'
                        AND app.owner_user_id = ?
                    )
                  )
                """,
                Integer.class,
                apiId,
                context.getUserId(),
                context.getUserId()
        );
        return count != null && count > 0;
    }

    private boolean isKnownUser(ToolContext context) {
        return "MANAGER".equals(context.getUserType()) || "USER".equals(context.getUserType());
    }

    private ApiCheck checkApiAccess(String toolName, String rawApiCode, ToolContext context, String spanId, long started) {
        if (!isKnownUser(context)) {
            return new ApiCheck(null, null, failure(toolName, "Current demo user does not exist.", "USER_NOT_FOUND",
                    "user not found: " + context.getUserId(), context, spanId, started));
        }
        String apiCode = normalizeApiCode(rawApiCode);
        if (!StringUtils.hasText(apiCode)) {
            return new ApiCheck(null, null, failure(toolName, "apiCode is required.", "INVALID_ARGUMENT",
                    "apiCode is required", context, spanId, started));
        }
        Map<String, Object> api = findApi(apiCode);
        if (api == null) {
            return new ApiCheck(apiCode, null, failure(toolName, "API was not found.", "API_NOT_FOUND",
                    "api not found: " + apiCode, context, spanId, started));
        }
        if (!canAccessApi(context, ((Number) api.get("id")).longValue())) {
            return new ApiCheck(apiCode, api, failure(toolName, "Current user has no permission to query this API.",
                    "PERMISSION_DENIED", "permission denied for api: " + apiCode, context, spanId, started));
        }
        return new ApiCheck(apiCode, api, null);
    }

    private GatewayLogWhere buildGatewayLogWhere(Map<String, Object> api, QueryGatewayLogsRequest request,
                                                 LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder where = new StringBuilder(" WHERE l.api_id = ? AND l.request_time >= ? AND l.request_time <= ? AND l.status = 'ACTIVE'");
        List<Object> params = new ArrayList<>();
        params.add(api.get("id"));
        params.add(Timestamp.valueOf(startTime));
        params.add(Timestamp.valueOf(endTime));
        if (request != null && request.getHttpStatus() != null) {
            where.append(" AND l.http_status = ?");
            params.add(request.getHttpStatus());
        }
        if (request != null && StringUtils.hasText(request.getKeyword())) {
            where.append(" AND (LOWER(COALESCE(l.error_code, '')) LIKE ?")
                    .append(" OR LOWER(COALESCE(l.error_message, '')) LIKE ?")
                    .append(" OR LOWER(COALESCE(CAST(l.extra_info AS CHAR), '')) LIKE ?)");
            String keyword = "%" + request.getKeyword().trim().toLowerCase() + "%";
            params.add(keyword);
            params.add(keyword);
            params.add(keyword);
        }
        return new GatewayLogWhere(where.toString(), params);
    }

    private Map<String, Object> buildGatewayLogData(Map<String, Object> api, QueryGatewayLogsRequest request,
                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                    long totalMatched, List<Map<String, Object>> rows) {
        Map<Integer, Integer> statusDistribution = new LinkedHashMap<>();
        Map<String, Integer> appDistribution = new LinkedHashMap<>();
        Map<String, Integer> errorMessages = new LinkedHashMap<>();
        List<Map<String, Object>> logs = new ArrayList<>();
        List<String> riskHints = new ArrayList<>();
        int highLatencyCount = 0;

        for (Map<String, Object> row : rows) {
            int status = intValue(row.get("http_status"));
            statusDistribution.put(status, statusDistribution.getOrDefault(status, 0) + 1);
            String appCode = row.get("app_code") == null ? "UNKNOWN_APP" : String.valueOf(row.get("app_code"));
            appDistribution.put(appCode, appDistribution.getOrDefault(appCode, 0) + 1);
            if (row.get("error_message") != null) {
                String message = String.valueOf(row.get("error_message"));
                errorMessages.put(message, errorMessages.getOrDefault(message, 0) + 1);
            }
            if (intValue(row.get("latency_ms")) >= 800) {
                highLatencyCount++;
            }
            logs.add(buildGatewayLogItem(api, row));
        }

        addGatewayRiskHints(riskHints, statusDistribution, errorMessages, highLatencyCount, request == null ? null : request.getKeyword());

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("httpStatus", request == null ? null : request.getHttpStatus());
        filters.put("keyword", request == null ? null : request.getKeyword());
        filters.put("limit", request == null ? 20 : normalizeLimit(request.getLimit(), 20, 100));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiCode", api.get("api_code"));
        data.put("apiName", api.get("api_name"));
        data.put("startTime", FORMATTER.format(startTime));
        data.put("endTime", FORMATTER.format(endTime));
        data.put("filters", filters);
        data.put("totalMatched", totalMatched);
        data.put("returnedCount", logs.size());
        data.put("statusDistribution", statusDistribution);
        data.put("appDistribution", appDistribution);
        data.put("topErrorMessages", errorMessages);
        data.put("logs", logs);
        data.put("riskHints", riskHints);
        return data;
    }

    private Map<String, Object> buildGatewayLogItem(Map<String, Object> api, Map<String, Object> row) {
        Map<String, Object> extraInfo = parseJsonMap(row.get("extra_info"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("logId", row.get("id"));
        item.put("apiCode", api.get("api_code"));
        item.put("appCode", row.get("app_code"));
        item.put("consumerAppCode", row.get("app_code"));
        item.put("httpStatus", row.get("http_status"));
        item.put("requestPath", row.get("request_path"));
        item.put("method", row.get("request_method"));
        item.put("latencyMs", row.get("latency_ms"));
        item.put("errorCode", row.get("error_code"));
        item.put("errorMessage", row.get("error_message"));
        item.put("message", row.get("error_message"));
        item.put("traceId", row.get("trace_id"));
        item.put("requestTime", formatTime(row.get("request_time")));
        item.put("userAgent", extraInfo.get("userAgent"));
        item.put("clientIp", maskValue(row.get("client_ip")));
        item.put("diagnosisHint", extraInfo.get("diagnosisHint"));
        return item;
    }

    private void addGatewayRiskHints(List<String> riskHints, Map<Integer, Integer> statusDistribution,
                                     Map<String, Integer> errorMessages, int highLatencyCount, String keyword) {
        if (statusDistribution.getOrDefault(401, 0) + statusDistribution.getOrDefault(403, 0) > 0) {
            riskHints.add("authentication or signature failures are present");
        }
        if (statusDistribution.getOrDefault(429, 0) > 0) {
            riskHints.add("rate limiting was triggered");
        }
        int serverErrors = statusDistribution.entrySet().stream()
                .filter(entry -> entry.getKey() >= 500)
                .mapToInt(Map.Entry::getValue)
                .sum();
        if (serverErrors > 0) {
            riskHints.add("server-side or downstream dependency errors are present");
        }
        if (highLatencyCount > 0) {
            riskHints.add("slow responses are present");
        }
        String joinedErrors = String.join(" ", errorMessages.keySet()).toLowerCase();
        String actualKeyword = keyword == null ? "" : keyword.toLowerCase();
        if (joinedErrors.contains("duplicate") || joinedErrors.contains("idempotency") || joinedErrors.contains("conflict")
                || actualKeyword.contains("duplicate") || actualKeyword.contains("idempotency") || actualKeyword.contains("conflict")) {
            riskHints.add("duplicate request or idempotency risk is present");
        }
        if (joinedErrors.contains("unknown app") || actualKeyword.contains("unknown app") || actualKeyword.contains("suspicious")) {
            riskHints.add("suspicious caller pattern may need attention");
        }
        if (riskHints.isEmpty()) {
            riskHints.add("no obvious risk hint from returned logs");
        }
    }

    private Map<String, Object> buildRateLimitRuleData(Map<String, Object> api, boolean includeInactive, List<Map<String, Object>> rows) {
        List<Map<String, Object>> rules = new ArrayList<>();
        int activeCount = 0;
        for (Map<String, Object> row : rows) {
            if ("ACTIVE".equals(row.get("status"))) {
                activeCount++;
            }
            rules.add(buildRateLimitRuleItem(row));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiCode", api.get("api_code"));
        data.put("apiName", api.get("api_name"));
        data.put("includeInactive", includeInactive);
        data.put("ruleCount", rules.size());
        data.put("activeRuleCount", activeCount);
        data.put("rules", rules);
        data.put("recommendedCheckPoints", buildRateLimitCheckPoints(String.valueOf(api.get("api_code"))));
        data.put("riskHints", buildRateLimitRiskHints(String.valueOf(api.get("api_code")), rows, activeCount));
        return data;
    }

    private Map<String, Object> buildRateLimitRuleItem(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ruleId", row.get("id"));
        item.put("ruleName", row.get("rule_name"));
        item.put("limitType", "QPS");
        item.put("windowSeconds", 1);
        item.put("windowSize", 1);
        item.put("maxRequests", row.get("qps_limit"));
        item.put("threshold", row.get("burst_limit"));
        item.put("qpsLimit", row.get("qps_limit"));
        item.put("burstLimit", row.get("burst_limit"));
        item.put("degradeEnabled", intValue(row.get("degrade_enabled")) == 1);
        item.put("fallbackMessage", row.get("fallback_message"));
        item.put("appCode", null);
        item.put("status", row.get("status"));
        item.put("description", row.get("rule_name"));
        item.put("effectiveStart", formatTime(row.get("effective_start")));
        item.put("effectiveEnd", formatTime(row.get("effective_end")));
        item.put("createdAt", formatTime(row.get("created_at")));
        item.put("updatedAt", formatTime(row.get("updated_at")));
        return item;
    }

    private List<String> buildRateLimitCheckPoints(String apiCode) {
        List<String> points = new ArrayList<>();
        points.add("compare rate_limit_count with 429 gateway logs");
        points.add("check whether QPS and burst thresholds match the event window");
        if ("LECTURE_REGISTER".equals(apiCode) || "VENUE_RESERVE".equals(apiCode)) {
            points.add("check high-concurrency write protection and idempotency behavior");
        }
        return points;
    }

    private List<String> buildRateLimitRiskHints(String apiCode, List<Map<String, Object>> rows, int activeCount) {
        List<String> hints = new ArrayList<>();
        if (rows.isEmpty()) {
            hints.add("no rate limit rule is configured; high-traffic APIs may need protection");
        }
        if (activeCount > 0 && ("LECTURE_REGISTER".equals(apiCode) || "VENUE_RESERVE".equals(apiCode))) {
            hints.add("rate limit rule exists; compare it with 429 logs and call stats during peak windows");
        }
        if (activeCount < rows.size()) {
            hints.add("inactive rate limit rules exist; check whether they should be enabled for peak windows");
        }
        if (hints.isEmpty()) {
            hints.add("rate limit configuration is available for review");
        }
        return hints;
    }

    private List<Object> buildGatewayLogEvidence(String apiCode, List<Map<String, Object>> rows) {
        List<Object> evidence = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("evidenceType", "GATEWAY_LOG");
            item.put("title", "Gateway log sample for " + apiCode);
            item.put("summary", "HTTP " + row.get("http_status") + " at " + formatTime(row.get("request_time")));
            item.put("sourceType", "gateway_log");
            item.put("sourceId", String.valueOf(row.get("id")));
            item.put("quote", buildLogQuote(row));
            item.put("score", null);
            item.put("metadata", Map.of(
                    "apiCode", apiCode,
                    "httpStatus", row.get("http_status"),
                    "traceId", row.get("trace_id"),
                    "requestTime", formatTime(row.get("request_time"))
            ));
            evidence.add(item);
        }
        return evidence;
    }

    private List<Object> buildRateLimitRuleEvidence(String apiCode, List<Map<String, Object>> rows) {
        List<Object> evidence = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("evidenceType", "RATE_LIMIT_RULE");
            item.put("title", "Rate limit rule for " + apiCode);
            item.put("summary", "QPS " + row.get("qps_limit") + ", burst " + row.get("burst_limit") + ", status " + row.get("status"));
            item.put("sourceType", "rate_limit_rule");
            item.put("sourceId", String.valueOf(row.get("id")));
            item.put("quote", row.get("rule_name") + " qps=" + row.get("qps_limit") + " burst=" + row.get("burst_limit"));
            item.put("metadata", Map.of(
                    "apiCode", apiCode,
                    "limitType", "QPS",
                    "threshold", row.get("burst_limit"),
                    "windowSeconds", 1,
                    "status", row.get("status")
            ));
            evidence.add(item);
        }
        return evidence;
    }

    private String buildLogQuote(Map<String, Object> row) {
        String errorCode = row.get("error_code") == null ? "" : String.valueOf(row.get("error_code"));
        String errorMessage = row.get("error_message") == null ? "" : String.valueOf(row.get("error_message"));
        return ("status=" + row.get("http_status") + " " + errorCode + " " + errorMessage).trim();
    }

    private void writeTrace(ToolContext context, ToolResult result, Object input) {
        try {
            Long sessionId = ensureSession(context);
            jdbcTemplate.update(
                    """
                    INSERT INTO tool_call_trace (
                      session_id, trace_id, span_id, parent_span_id, tool_name, tool_type,
                      input_json, output_json, latency_ms, success, error_code, error_message,
                      status, extra_info, created_at
                    ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    sessionId,
                    result.getTraceId(),
                    result.getSpanId(),
                    result.getToolName(),
                    TOOL_TYPE_LOCAL,
                    toJson(trimInput(input)),
                    toJson(trimOutput(result)),
                    result.getLatencyMs(),
                    result.isSuccess() ? 1 : 0,
                    result.getErrorCode(),
                    result.getErrorMessage(),
                    result.isSuccess() ? "SUCCESS" : "FAILED",
                    toJson(Map.of("source", context.getSource(), "userId", context.getUserId()))
            );
        } catch (Exception ignored) {
            // Dev-only tool calls should not fail the HTTP response just because trace persistence failed.
        }
    }

    private Long ensureSession(ToolContext context) {
        if (context.getSessionId() != null) {
            return context.getSessionId();
        }
        List<Long> existing = jdbcTemplate.query(
                "SELECT id FROM agent_session WHERE trace_id = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                context.getTraceId()
        );
        if (!existing.isEmpty()) {
            context.setSessionId(existing.get(0));
            return existing.get(0);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sessionCode = "sess_dev_tool_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO agent_session (
                      session_code, trace_id, user_id, user_type, session_type, title, workflow_name,
                      status, duration_ms, retry_count, last_event_seq, started_at, finished_at,
                      extra_info, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'CHAT', 'Dev Tool Debug Session', 'dev_tool_debug',
                      'SUCCESS', 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                      ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, sessionCode);
            ps.setString(2, context.getTraceId());
            ps.setLong(3, context.getUserId());
            ps.setString(4, context.getUserType());
            Map<String, Object> extraInfo = new LinkedHashMap<>();
            extraInfo.put("source", context.getSource());
            extraInfo.put("requestId", context.getRequestId());
            ps.setString(5, toJson(extraInfo));
            return ps;
        }, keyHolder);
        Long sessionId = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        context.setSessionId(sessionId);
        return sessionId;
    }

    private Map<String, Object> trimInput(Object input) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("input", input);
        return data;
    }

    private Map<String, Object> trimOutput(ToolResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", result.isSuccess());
        data.put("toolName", result.getToolName());
        data.put("summary", result.getSummary());
        data.put("errorCode", result.getErrorCode());
        data.put("errorMessage", result.getErrorMessage());
        if (result.getData() instanceof Map<?, ?> resultData) {
            data.put("apiCode", resultData.get("apiCode"));
            data.put("riskLevel", resultData.get("riskLevel"));
            data.put("totalCallCount", resultData.get("totalCallCount"));
            data.put("totalMatched", resultData.get("totalMatched"));
            data.put("returnedCount", resultData.get("returnedCount"));
            data.put("ruleCount", resultData.get("ruleCount"));
            data.put("activeRuleCount", resultData.get("activeRuleCount"));
        }
        return data;
    }

    private ToolResult failure(String toolName, String summary, String errorCode, String errorMessage,
                               ToolContext context, String spanId, long started) {
        return ToolResult.failure(toolName, summary, errorCode, errorMessage, context, spanId, elapsedMs(started));
    }

    private String normalizeApiCode(String apiCode) {
        return StringUtils.hasText(apiCode) ? apiCode.trim().toUpperCase() : apiCode;
    }

    private String newSpanId(String toolName) {
        return "span_" + toolName + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private LocalDateTime parseTimeOrDefault(String value, String defaultValue) {
        String actual = StringUtils.hasText(value) ? value.trim() : defaultValue;
        return LocalDateTime.parse(actual, FORMATTER);
    }

    private String formatTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return FORMATTER.format(timestamp.toLocalDateTime());
        }
        if (value instanceof LocalDateTime dateTime) {
            return FORMATTER.format(dateTime);
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(Object value) {
        Object parsed = parseJsonObject(value);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return Map.of();
    }

    private Object parseJsonObject(Object value) {
        if (value == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), Object.class);
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

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private int normalizeLimit(Integer limit, int defaultValue, int maxValue) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, maxValue);
    }

    private List<Object> appendParam(List<Object> params, Object value) {
        List<Object> next = new ArrayList<>(params);
        next.add(value);
        return next;
    }

    private String maskValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() <= 8) {
            return text;
        }
        return text.substring(0, 4) + "***" + text.substring(text.length() - 4);
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record ApiCheck(String apiCode, Map<String, Object> api, ToolResult result) {
    }

    private record GatewayLogWhere(String whereClause, List<Object> params) {
    }
}
