package com.apihub.agent.dev.gateway;

import com.apihub.agent.common.ErrorCode;
import com.apihub.agent.common.TraceContext;
import com.apihub.agent.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GatewayInvokeService {

    private static final int DEFAULT_TIMEOUT_MS = 3000;
    private static final int MAX_TIMEOUT_MS = 10000;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final GatewayInvokeRouteRegistry routeRegistry;
    private final GatewayInvokeProperties properties;
    private final HttpClient httpClient;

    public GatewayInvokeService(JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper,
                                GatewayInvokeRouteRegistry routeRegistry,
                                GatewayInvokeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.routeRegistry = routeRegistry;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public GatewayInvokeResultVO invoke(GatewayInvokeRequest request, String requestId) {
        long started = System.nanoTime();
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "request body is required");
        }
        String apiCode = normalize(request.getApiCode());
        if (!StringUtils.hasText(apiCode)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "apiCode is required");
        }
        GatewayInvokeRoute route = routeRegistry.get(apiCode);
        if (route == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "gateway route not found for apiCode: " + apiCode);
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "mock-provider baseUrl is not configured");
        }

        Map<String, Object> api = findApi(apiCode);
        if (api == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "api not found: " + apiCode);
        }

        String appCode = normalize(StringUtils.hasText(request.getAppCode()) ? request.getAppCode() : route.defaultAppCode());
        Map<String, Object> app = findApp(appCode);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "app not found: " + appCode);
        }
        if (!hasActiveAuthorization(app, api)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "appCode is not authorized to call apiCode: " + appCode + " -> " + apiCode);
        }

        String mockScenario = StringUtils.hasText(request.getMockScenario())
                ? request.getMockScenario().trim().toUpperCase(Locale.ROOT)
                : "NORMAL";
        int timeoutMs = normalizeTimeout(request.getTimeoutMs());
        String actualRequestId = StringUtils.hasText(requestId)
                ? requestId.trim()
                : "req_" + UUID.randomUUID().toString().replace("-", "");
        String traceId = TraceContext.getTraceId();

        UpstreamResponse upstream = callMockProvider(route, request, mockScenario, traceId, actualRequestId, timeoutMs);
        long latencyMs = elapsedMs(started);

        Long gatewayLogId = insertGatewayLog(api, app, route, request, upstream, mockScenario, traceId, actualRequestId, latencyMs);

        GatewayInvokeResultVO result = new GatewayInvokeResultVO();
        result.setApiCode(apiCode);
        result.setAppCode(appCode);
        result.setMockScenario(mockScenario);
        result.setSuccess(upstream.statusCode() >= 200 && upstream.statusCode() < 300);
        result.setUpstreamStatus(upstream.statusCode());
        result.setUpstreamCode(upstream.bodyCode());
        result.setUpstreamMessage(upstream.bodyMessage());
        result.setErrorCode(result.isSuccess() ? null : resolveErrorCode(mockScenario, upstream));
        result.setLatencyMs(latencyMs);
        result.setGatewayLogId(gatewayLogId);
        result.setUpstreamData(upstream.bodyData());
        result.setTraceId(traceId);
        result.setRequestId(actualRequestId);
        return result;
    }

    private UpstreamResponse callMockProvider(GatewayInvokeRoute route, GatewayInvokeRequest request,
                                              String mockScenario, String traceId, String requestId, int timeoutMs) {
        try {
            String url = buildUrl(route, request, mockScenario);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .header("X-Trace-Id", traceId)
                    .header("X-Request-Id", requestId)
                    .header("X-Mock-Scenario", mockScenario);

            if ("POST".equals(route.method())) {
                Map<String, Object> body = new LinkedHashMap<>(safeMap(request.getBody()));
                body.putIfAbsent("mockScenario", mockScenario);
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(body)));
            } else {
                builder.GET();
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> responseBody = parseJsonMap(response.body());
            Integer bodyCode = integerValue(responseBody.get("code"));
            String bodyMessage = stringValue(responseBody.get("message"));
            Object data = responseBody.get("data");
            return new UpstreamResponse(response.statusCode(), bodyCode, bodyMessage, data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "mock-provider call interrupted");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "mock-provider call failed: " + e.getMessage());
        }
    }

    private String buildUrl(GatewayInvokeRoute route, GatewayInvokeRequest request, String mockScenario) {
        StringBuilder url = new StringBuilder(properties.getBaseUrl().replaceAll("/+$", "")).append(route.path());
        Map<String, Object> query = new LinkedHashMap<>(safeMap(request.getQueryParams()));
        if ("GET".equals(route.method())) {
            query.putIfAbsent("mockScenario", mockScenario);
        }
        if (!query.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                url.append(first ? '?' : '&');
                first = false;
                url.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(entry.getValue())));
            }
        }
        return url.toString();
    }

    private Long insertGatewayLog(Map<String, Object> api, Map<String, Object> app, GatewayInvokeRoute route,
                                  GatewayInvokeRequest request, UpstreamResponse upstream, String mockScenario,
                                  String traceId, String requestId, long latencyMs) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String errorCode = upstream.statusCode() >= 200 && upstream.statusCode() < 300 ? null : resolveErrorCode(mockScenario, upstream);
        String errorMessage = upstream.statusCode() >= 200 && upstream.statusCode() < 300 ? null : truncate(upstream.bodyMessage(), 512);
        Map<String, Object> extraInfo = buildExtraInfo(request, upstream, mockScenario, requestId);
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO gateway_log (
                          trace_id, api_id, app_id, access_key, request_path, request_method,
                          http_status, error_code, error_message, latency_ms, client_ip,
                          request_time, status, extra_info
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, traceId);
                ps.setLong(2, numberValue(api.get("id")));
                ps.setLong(3, numberValue(app.get("id")));
                ps.setString(4, stringValue(app.get("access_key")));
                ps.setString(5, route.path());
                ps.setString(6, route.method());
                ps.setInt(7, upstream.statusCode());
                ps.setString(8, errorCode);
                ps.setString(9, errorMessage);
                ps.setInt(10, (int) Math.min(latencyMs, Integer.MAX_VALUE));
                ps.setString(11, clientIp(request));
                ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(13, toJson(extraInfo));
                return ps;
            }, keyHolder);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "gateway_log write failed");
        }
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private Map<String, Object> buildExtraInfo(GatewayInvokeRequest request, UpstreamResponse upstream,
                                               String mockScenario, String requestId) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("requestId", requestId);
        GatewayInvokeRequest.ScenarioContext context = request.getScenarioContext();
        if (context != null) {
            extra.put("scenarioRunId", context.getScenarioRunId());
            extra.put("scenarioId", context.getScenarioId());
            extra.put("scenarioKey", context.getScenarioKey());
            extra.put("phase", context.getPhase());
            extra.put("sequenceNo", context.getSequenceNo());
        }
        extra.put("mockScenario", mockScenario);
        extra.put("upstreamCode", upstream.bodyCode());
        extra.put("upstreamMessage", upstream.bodyMessage());
        extra.put("upstreamDataSummary", summarize(upstream.bodyData()));
        extra.put("targetProvider", "apihub-mock-provider");
        extra.put("clientUserAgent", request.getClientInfo() == null ? null : request.getClientInfo().getUserAgent());
        extra.put("queryParams", maskMap(request.getQueryParams()));
        extra.put("requestBodySummary", summarize(maskMap(request.getBody())));
        return extra;
    }

    private Map<String, Object> findApi(String apiCode) {
        return jdbcTemplate.queryForList(
                "SELECT id, api_code, api_name, path, method, status FROM api_endpoint WHERE api_code = ? AND status = 'ACTIVE' LIMIT 1",
                apiCode
        ).stream().findFirst().orElse(null);
    }

    private Map<String, Object> findApp(String appCode) {
        return jdbcTemplate.queryForList(
                "SELECT id, app_code, app_name, access_key, status FROM api_consumer_app WHERE app_code = ? AND status = 'ACTIVE' LIMIT 1",
                appCode
        ).stream().findFirst().orElse(null);
    }

    private boolean hasActiveAuthorization(Map<String, Object> app, Map<String, Object> api) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM api_authorization
                WHERE app_id = ? AND api_id = ? AND auth_status = 'APPROVED' AND status = 'ACTIVE'
                """, Integer.class, app.get("id"), api.get("id"));
        return count != null && count > 0;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> safeMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : source;
    }

    private Map<String, Object> maskMap(Map<String, Object> source) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : safeMap(source).entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("token") || lower.contains("secret") || lower.contains("password")) {
                masked.put(key, "***");
            } else if (lower.contains("signature")) {
                masked.put(key, entry.getValue() == null ? null : "***present***");
            } else {
                masked.put(key, entry.getValue());
            }
        }
        return masked;
    }

    private String summarize(Object value) {
        if (value == null) {
            return null;
        }
        Object masked = value instanceof Map<?, ?> map ? maskGenericMap(map) : value;
        return truncate(toJson(masked), 512);
    }

    private Map<String, Object> maskGenericMap(Map<?, ?> source) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("token") || lower.contains("secret") || lower.contains("password")) {
                masked.put(key, "***");
            } else if (value instanceof Map<?, ?> child) {
                masked.put(key, maskGenericMap(child));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    private String resolveErrorCode(String mockScenario, UpstreamResponse upstream) {
        if (StringUtils.hasText(mockScenario) && !"NORMAL".equals(mockScenario)) {
            return mockScenario;
        }
        return "HTTP_" + upstream.statusCode();
    }

    private int normalizeTimeout(Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(timeoutMs, MAX_TIMEOUT_MS);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String clientIp(GatewayInvokeRequest request) {
        return request.getClientInfo() == null ? null : truncate(request.getClientInfo().getClientIp(), 64);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private record UpstreamResponse(int statusCode, Integer bodyCode, String bodyMessage, Object bodyData) {
    }
}
