package com.apihub.mockprovider.common;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

public final class ResponseSupport {

    public static final String NORMAL = "NORMAL";
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String SCENARIO_HEADER = "X-Mock-Scenario";

    private ResponseSupport() {
    }

    public static String traceId(String headerTraceId) {
        if (StringUtils.hasText(headerTraceId)) {
            String traceId = headerTraceId.trim();
            if (!traceId.matches("^[a-f0-9]{32}$")) {
                throw new IllegalArgumentException("invalid X-Trace-Id");
            }
            return traceId;
        }
        return newTraceId();
    }

    public static String safeTraceId(String headerTraceId) {
        if (StringUtils.hasText(headerTraceId) && headerTraceId.trim().matches("^[a-f0-9]{32}$")) {
            return headerTraceId.trim();
        }
        return newTraceId();
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String scenario(String headerScenario, String requestScenario) {
        String raw = StringUtils.hasText(headerScenario) ? headerScenario : requestScenario;
        if (!StringUtils.hasText(raw)) {
            return NORMAL;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public static String scenarioHeaderName() {
        return SCENARIO_HEADER;
    }

    public static <T> ResponseEntity<MockResponse<T>> ok(T data, String traceId) {
        return response(HttpStatus.OK, "success", data, traceId);
    }

    public static ResponseEntity<MockResponse<Map<String, Object>>> error(HttpStatus status, String message, String traceId) {
        return response(status, message, null, traceId);
    }

    public static <T> ResponseEntity<MockResponse<T>> response(HttpStatus status, String message, T data, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TRACE_HEADER, traceId);
        return ResponseEntity.status(status)
                .headers(headers)
                .body(new MockResponse<>(status.value(), message, data, traceId));
    }
}
