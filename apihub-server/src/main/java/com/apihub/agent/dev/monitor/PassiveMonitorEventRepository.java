package com.apihub.agent.dev.monitor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
class PassiveMonitorEventRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    PassiveMonitorEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    Map<String, Object> findOpenByDedupKey(String dedupKey) {
        return jdbcTemplate.queryForList("""
                SELECT * FROM passive_monitor_event
                WHERE dedup_key = ? AND event_status IN ('FIRING', 'COOLDOWN')
                ORDER BY first_trigger_time DESC LIMIT 1
                """, dedupKey).stream().findFirst().map(this::parseRow).orElse(null);
    }

    Map<String, Object> findByMonitorEventId(String monitorEventId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM passive_monitor_event WHERE monitor_event_id = ? LIMIT 1", monitorEventId)
                .stream().findFirst().map(this::parseRow).orElse(null);
    }

    void insertEvent(String monitorEventId, Long alertEventId, PassiveAlertCandidate candidate,
                     LocalDateTime now, LocalDateTime cooldownUntil, Map<String, Object> extra) {
        MetricWindowView current = candidate.currentWindow();
        MetricWindowView baseline = candidate.baselineWindow();
        jdbcTemplate.update("""
                INSERT INTO passive_monitor_event (
                  monitor_event_id, alert_event_id, alert_type, risk_level, event_status,
                  api_code, caller_app_code, dedup_key, first_trigger_time, last_trigger_time,
                  window_start_time, window_end_time, context_start_time, context_end_time,
                  request_count, error_count, error_rate, rate_limit_count, rate_limit_rate,
                  auth_failure_count, auth_failure_rate, timeout_count, timeout_rate, p95_latency_ms,
                  baseline_request_count, baseline_error_rate, cooldown_until, extra_json
                ) VALUES (?, ?, ?, ?, 'FIRING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                monitorEventId,
                alertEventId,
                candidate.alertType(),
                candidate.riskLevel(),
                candidate.apiCode(),
                candidate.callerAppCode(),
                candidate.dedupKey(),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                ts(current.getWindowStartTime()),
                ts(current.getWindowEndTime()),
                baseline == null ? null : ts(baseline.getWindowStartTime()),
                baseline == null ? null : ts(baseline.getWindowEndTime()),
                current.getRequestCount(),
                current.getErrorCount(),
                current.errorRate(),
                current.getRateLimitCount(),
                current.rateLimitRate(),
                current.getAuthFailureCount(),
                current.authFailureRate(),
                current.getTimeoutCount(),
                current.timeoutRate(),
                current.p95LatencyMs(),
                baseline == null ? 0 : baseline.getRequestCount(),
                baseline == null ? 0d : baseline.errorRate(),
                Timestamp.valueOf(cooldownUntil),
                toJson(extra));
    }

    void updateTriggered(String monitorEventId, Long alertEventId, PassiveAlertCandidate candidate,
                         LocalDateTime now, LocalDateTime cooldownUntil, Map<String, Object> extra) {
        MetricWindowView current = candidate.currentWindow();
        MetricWindowView baseline = candidate.baselineWindow();
        jdbcTemplate.update("""
                UPDATE passive_monitor_event
                SET alert_event_id = COALESCE(?, alert_event_id),
                    risk_level = ?,
                    event_status = 'FIRING',
                    last_trigger_time = ?,
                    window_start_time = ?,
                    window_end_time = ?,
                    context_start_time = ?,
                    context_end_time = ?,
                    request_count = ?,
                    error_count = ?,
                    error_rate = ?,
                    rate_limit_count = ?,
                    rate_limit_rate = ?,
                    auth_failure_count = ?,
                    auth_failure_rate = ?,
                    timeout_count = ?,
                    timeout_rate = ?,
                    p95_latency_ms = ?,
                    baseline_request_count = ?,
                    baseline_error_rate = ?,
                    cooldown_until = ?,
                    extra_json = ?
                WHERE monitor_event_id = ?
                """,
                alertEventId,
                candidate.riskLevel(),
                Timestamp.valueOf(now),
                ts(current.getWindowStartTime()),
                ts(current.getWindowEndTime()),
                baseline == null ? null : ts(baseline.getWindowStartTime()),
                baseline == null ? null : ts(baseline.getWindowEndTime()),
                current.getRequestCount(),
                current.getErrorCount(),
                current.errorRate(),
                current.getRateLimitCount(),
                current.rateLimitRate(),
                current.getAuthFailureCount(),
                current.authFailureRate(),
                current.getTimeoutCount(),
                current.timeoutRate(),
                current.p95LatencyMs(),
                baseline == null ? 0 : baseline.getRequestCount(),
                baseline == null ? 0d : baseline.errorRate(),
                Timestamp.valueOf(cooldownUntil),
                toJson(extra),
                monitorEventId);
    }

    int markCooldown(LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE passive_monitor_event
                SET event_status = 'COOLDOWN'
                WHERE event_status = 'FIRING' AND cooldown_until < ?
                """, Timestamp.valueOf(now));
    }

    int closeQuietEvents(LocalDateTime now) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT monitor_event_id, first_trigger_time, last_trigger_time
                FROM passive_monitor_event
                WHERE event_status IN ('FIRING', 'COOLDOWN')
                """);
        int closed = 0;
        for (Map<String, Object> row : rows) {
            LocalDateTime first = toTime(row.get("first_trigger_time"));
            LocalDateTime last = toTime(row.get("last_trigger_time"));
            if (first == null || last == null) {
                continue;
            }
            long durationSeconds = Math.max(0, java.time.Duration.between(first, last).getSeconds());
            long quietSeconds = Math.max(180, durationSeconds / 2);
            if (!now.isBefore(last.plusSeconds(quietSeconds))) {
                closed += jdbcTemplate.update("""
                        UPDATE passive_monitor_event
                        SET event_status = 'RESOLVED',
                            resolved_time = ?,
                            duration_seconds = ?
                        WHERE monitor_event_id = ? AND event_status IN ('FIRING', 'COOLDOWN')
                        """, Timestamp.valueOf(now), (int) Math.max(0, java.time.Duration.between(first, now).getSeconds()),
                        row.get("monitor_event_id"));
            }
        }
        return closed;
    }

    List<Map<String, Object>> openEvents() {
        return jdbcTemplate.queryForList("""
                SELECT * FROM passive_monitor_event
                WHERE event_status IN ('FIRING', 'COOLDOWN')
                ORDER BY first_trigger_time ASC
                """).stream().map(this::parseRow).toList();
    }

    int resolveEvent(String monitorEventId, LocalDateTime now, int durationSeconds) {
        return jdbcTemplate.update("""
                UPDATE passive_monitor_event
                SET event_status = 'RESOLVED',
                    resolved_time = ?,
                    duration_seconds = ?
                WHERE monitor_event_id = ? AND event_status IN ('FIRING', 'COOLDOWN')
                """, Timestamp.valueOf(now), durationSeconds, monitorEventId);
    }

    int countStatus(String status) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM passive_monitor_event WHERE event_status = ?", Integer.class, status);
        return value == null ? 0 : value;
    }

    List<Map<String, Object>> recent(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM passive_monitor_event ORDER BY first_trigger_time DESC LIMIT ?",
                Math.max(1, Math.min(limit, 200))).stream().map(this::parseRow).toList();
    }

    List<Map<String, Object>> query(PassiveMonitorEventQuery query, LocalDateTime start, LocalDateTime end) {
        StringBuilder sql = new StringBuilder("SELECT * FROM passive_monitor_event WHERE first_trigger_time >= ? AND first_trigger_time < ?");
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(start));
        params.add(Timestamp.valueOf(end));
        addFilter(sql, params, "api_code", query.getApiCode());
        addFilter(sql, params, "alert_type", query.getAlertType());
        addFilter(sql, params, "risk_level", query.getRiskLevel());
        addFilter(sql, params, "event_status", query.getEventStatus());
        addFilter(sql, params, "caller_app_code", query.getCallerAppCode());
        sql.append(" ORDER BY first_trigger_time DESC LIMIT ?");
        params.add(Math.max(1, Math.min(query.getLimit() == null ? 100 : query.getLimit(), 500)));
        return jdbcTemplate.queryForList(sql.toString(), params.toArray()).stream().map(this::parseRow).toList();
    }

    Long upsertAlertEvent(Long apiId, String eventCode, PassiveAlertCandidate candidate, LocalDateTime now, Map<String, Object> extra) {
        if (apiId == null) {
            return null;
        }
        jdbcTemplate.update("""
                INSERT INTO alert_event (
                  event_code, api_id, event_type, severity, title, description,
                  start_time, end_time, resolved, status, extra_info, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 0, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  severity = VALUES(severity),
                  title = VALUES(title),
                  description = VALUES(description),
                  resolved = 0,
                  status = 'ACTIVE',
                  extra_info = VALUES(extra_info),
                  updated_at = CURRENT_TIMESTAMP
                """,
                eventCode,
                apiId,
                candidate.alertType(),
                candidate.riskLevel(),
                candidate.alertType() + " on " + candidate.apiCode(),
                "Passive monitor detected " + candidate.alertType() + " for " + candidate.apiCode(),
                Timestamp.valueOf(now),
                toJson(extra));
        return jdbcTemplate.queryForObject("SELECT id FROM alert_event WHERE event_code = ?", Long.class, eventCode);
    }

    void resolveAlertEvent(Long alertEventId, LocalDateTime now) {
        if (alertEventId != null) {
            jdbcTemplate.update("UPDATE alert_event SET resolved = 1, end_time = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    Timestamp.valueOf(now), alertEventId);
        }
    }

    Map<String, Object> findAlertEvent(Long alertEventId) {
        if (alertEventId == null) {
            return null;
        }
        return jdbcTemplate.queryForList("SELECT * FROM alert_event WHERE id = ? LIMIT 1", alertEventId)
                .stream().findFirst().map(this::parseRow).orElse(null);
    }

    private void addFilter(StringBuilder sql, List<Object> params, String column, String value) {
        if (StringUtils.hasText(value)) {
            sql.append(" AND ").append(column).append(" = ?");
            params.add(value.trim().toUpperCase());
        }
    }

    private Map<String, Object> parseRow(Map<String, Object> row) {
        Map<String, Object> parsed = new LinkedHashMap<>(row);
        Object extra = parsed.get("extra_json");
        if (extra == null) {
            extra = parsed.get("extra_info");
        }
        if (extra != null) {
            parsed.put("extra", parseJson(String.valueOf(extra)));
        }
        return parsed;
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
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

    private Timestamp ts(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }

    private LocalDateTime toTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        return null;
    }
}
