package com.apihub.agent.dev.gateway;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GatewayScenarioSummaryService {

    private final JdbcTemplate jdbcTemplate;

    public GatewayScenarioSummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> summary(String scenarioRunId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scenarioRunId", scenarioRunId);
        summary.put("gatewaySummaryAvailable", true);
        summary.put("gatewayReceivedCount", count("SELECT COUNT(*) FROM gateway_log WHERE JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.scenarioRunId')) = ?", scenarioRunId));
        summary.put("gatewayForwardedCount", count("SELECT COUNT(*) FROM gateway_log WHERE JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.scenarioRunId')) = ? AND http_status IS NOT NULL", scenarioRunId));
        summary.put("gatewayBlockedCount", count("SELECT COUNT(*) FROM gateway_log WHERE JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.scenarioRunId')) = ? AND JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.failureSource')) = 'GATEWAY'", scenarioRunId));
        summary.put("successCount", count("SELECT COUNT(*) FROM gateway_log WHERE JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.scenarioRunId')) = ? AND http_status BETWEEN 200 AND 299", scenarioRunId));
        summary.put("failCount", count("SELECT COUNT(*) FROM gateway_log WHERE JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.scenarioRunId')) = ? AND http_status >= 400", scenarioRunId));
        summary.put("statusCodeDistribution", distribution("http_status", scenarioRunId));
        summary.put("mockScenarioDistribution", distribution("JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.mockScenario'))", scenarioRunId));
        return summary;
    }

    private int count(String sql, String scenarioRunId) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, scenarioRunId);
        return value == null ? 0 : value;
    }

    private Map<String, Integer> distribution(String expression, String scenarioRunId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + expression + " AS k, COUNT(*) AS c FROM gateway_log WHERE JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.scenarioRunId')) = ? GROUP BY " + expression,
                scenarioRunId);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get("k")), ((Number) row.get("c")).intValue());
        }
        return result;
    }
}
