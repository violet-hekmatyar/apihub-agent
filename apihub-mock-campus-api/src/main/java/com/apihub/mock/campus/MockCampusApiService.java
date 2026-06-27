package com.apihub.mock.campus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
class MockCampusApiService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    MockCampusApiService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    ResponseEntity<MockCampusResponse> invoke(MockCampusInvokeRequest request) {
        long started = System.nanoTime();
        String scenario = normalize(firstNonBlank(request.mockScenario, "NORMAL"));
        String apiCode = normalize(firstNonBlank(request.apiCode, "UNKNOWN"));
        CampusOutcome outcome = outcome(apiCode, scenario);
        MockCampusResponse body = MockCampusResponse.of(outcome.success(), apiCode, outcome.businessCode(), outcome.message());
        body.data.put("scenarioRunId", request.scenarioRunId);
        body.data.put("phaseCode", request.phaseCode);
        body.data.put("failureSource", outcome.failureSource());
        int latencyMs = (int) Math.min(Integer.MAX_VALUE, Math.max(outcome.syntheticLatencyMs(), elapsedMs(started)));
        insertLog(request, outcome, latencyMs);
        return ResponseEntity.status(outcome.status()).body(body);
    }

    Map<String, Object> upstreamSummary(String scenarioRunId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scenarioRunId", scenarioRunId);
        summary.put("upstreamReceivedCount", count("SELECT COUNT(*) FROM mock_campus_api_request_log WHERE scenario_run_id = ?", scenarioRunId));
        summary.put("successCount", count("SELECT COUNT(*) FROM mock_campus_api_request_log WHERE scenario_run_id = ? AND response_status BETWEEN 200 AND 299", scenarioRunId));
        summary.put("errorCount", count("SELECT COUNT(*) FROM mock_campus_api_request_log WHERE scenario_run_id = ? AND response_status >= 400", scenarioRunId));
        summary.put("apiDistribution", distribution("api_code", scenarioRunId));
        summary.put("phaseDistribution", distribution("phase_code", scenarioRunId));
        summary.put("responseStatusDistribution", distribution("response_status", scenarioRunId));
        summary.put("mockScenarioDistribution", distribution("mock_scenario", scenarioRunId));
        summary.put("failureSourceDistribution", distribution("failure_source", scenarioRunId));
        return summary;
    }

    private void insertLog(MockCampusInvokeRequest request, CampusOutcome outcome, int latencyMs) {
        jdbcTemplate.update("""
                INSERT INTO mock_campus_api_request_log (
                  scenario_run_id, request_id, trace_id, phase_code, api_code, mock_scenario,
                  receive_time, response_status, business_code, latency_ms, response_type, failure_source, extra_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                firstNonBlank(request.scenarioRunId, "UNKNOWN"),
                firstNonBlank(request.requestId, "UNKNOWN"),
                request.traceId,
                firstNonBlank(request.phaseCode, "UNKNOWN"),
                normalize(firstNonBlank(request.apiCode, "UNKNOWN")),
                normalize(firstNonBlank(request.mockScenario, "NORMAL")),
                Timestamp.valueOf(LocalDateTime.now()),
                outcome.status(),
                outcome.businessCode(),
                latencyMs,
                outcome.responseType(),
                outcome.failureSource(),
                toJson(Map.of("profileCode", firstNonBlank(request.profileCode, ""), "mode", firstNonBlank(request.mode, "")))
        );
    }

    private CampusOutcome outcome(String apiCode, String mockScenario) {
        return switch (mockScenario) {
            case "TOKEN_EXPIRED" -> new CampusOutcome(false, 401, "TOKEN_EXPIRED", "caller token is expired in mock scenario", "AUTH", "UPSTREAM", 30);
            case "SIGNATURE_MISMATCH" -> new CampusOutcome(false, 403, "SIGNATURE_MISMATCH", "signature mismatch in mock scenario", "AUTH", "UPSTREAM", 35);
            case "RATE_LIMITED" -> new CampusOutcome(false, 429, "RATE_LIMITED", "lecture registration is rate limited in mock scenario", "RATE_LIMIT", "UPSTREAM", 45);
            case "DUPLICATE_REQUEST" -> new CampusOutcome(false, 409, "DUPLICATE_REQUEST", "duplicate request in mock scenario", "BUSINESS_CONFLICT", "UPSTREAM", 40);
            case "SOLD_OUT" -> new CampusOutcome(false, 409, "SOLD_OUT", "lecture quota is sold out in mock scenario", "BUSINESS_CONFLICT", "UPSTREAM", 45);
            case "DOWNSTREAM_TIMEOUT", "COURSE_SYSTEM_TIMEOUT" -> new CampusOutcome(false, 504, mockScenario, "mock downstream timeout", "TIMEOUT", "UPSTREAM", 150);
            case "UPSTREAM_INTERNAL_ERROR" -> new CampusOutcome(false, 500, "UPSTREAM_INTERNAL_ERROR", "mock upstream internal error", "SERVER_ERROR", "UPSTREAM", 80);
            default -> new CampusOutcome(true, 200, "OK", "mock campus api success", "NORMAL", "NONE", 20);
        };
    }

    private Map<String, Integer> distribution(String column, String scenarioRunId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + column + " AS k, COUNT(*) AS c FROM mock_campus_api_request_log WHERE scenario_run_id = ? GROUP BY " + column,
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private record CampusOutcome(boolean success, int status, String businessCode, String message,
                                 String responseType, String failureSource, int syntheticLatencyMs) {
    }
}
