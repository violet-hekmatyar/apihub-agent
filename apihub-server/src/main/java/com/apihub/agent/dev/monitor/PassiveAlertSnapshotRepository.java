package com.apihub.agent.dev.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PassiveAlertSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    PassiveAlertSnapshotRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    void insertSnapshot(String monitorEventId, String snapshotType, String apiCode, String callerAppCode,
                        MetricWindowView window, Map<String, Object> thresholds, Map<String, Object> extra) {
        if (window == null || window.getWindowStartTime() == null || window.getWindowEndTime() == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO passive_alert_snapshot (
                  snapshot_id, monitor_event_id, snapshot_time, snapshot_type, api_code, caller_app_code,
                  window_start_time, window_end_time, request_count, success_count, error_count, error_rate,
                  status_code_distribution_json, business_code_distribution_json, caller_app_distribution_json,
                  sample_request_ids_json, threshold_snapshot_json, extra_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "snap_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18),
                monitorEventId,
                Timestamp.valueOf(LocalDateTime.now()),
                snapshotType,
                apiCode,
                callerAppCode,
                Timestamp.valueOf(window.getWindowStartTime()),
                Timestamp.valueOf(window.getWindowEndTime()),
                window.getRequestCount(),
                window.getSuccessCount(),
                window.getErrorCount(),
                window.errorRate(),
                toJson(window.getStatusCodeDistribution()),
                toJson(window.getBusinessCodeDistribution()),
                toJson(Map.of(callerAppCode == null ? "UNKNOWN" : callerAppCode, window.getRequestCount())),
                toJson(window.getSampleRequestIds()),
                toJson(thresholds),
                toJson(extra));
    }

    List<Map<String, Object>> findByMonitorEventId(String monitorEventId) {
        return jdbcTemplate.queryForList("""
                SELECT * FROM passive_alert_snapshot
                WHERE monitor_event_id = ?
                ORDER BY snapshot_time ASC, id ASC
                """, monitorEventId).stream().map(this::parseRow).toList();
    }

    private Map<String, Object> parseRow(Map<String, Object> row) {
        Map<String, Object> parsed = new LinkedHashMap<>(row);
        for (String key : List.of("status_code_distribution_json", "business_code_distribution_json",
                "caller_app_distribution_json", "sample_request_ids_json", "threshold_snapshot_json", "extra_json")) {
            Object value = parsed.get(key);
            if (value != null) {
                parsed.put(key.replace("_json", ""), parseJson(String.valueOf(value)));
            }
        }
        return parsed;
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
