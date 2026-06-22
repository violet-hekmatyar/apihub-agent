package com.apihub.agent.dev.alert;

import com.apihub.agent.common.ErrorCode;
import com.apihub.agent.exception.BusinessException;
import com.apihub.agent.model.dto.AlertEvaluateRequest;
import com.apihub.agent.model.vo.AlertEvaluateItemVO;
import com.apihub.agent.model.vo.AlertEvaluateResponseVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AlertEvaluateService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MIN_TOTAL_COUNT = 20;
    private static final int DEFAULT_HOURLY_WINDOW_SECONDS = 3600;
    private static final int DEV_SHORT_WINDOW_SECONDS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AlertEvaluateService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AlertEvaluateResponseVO evaluate(AlertEvaluateRequest request) {
        long started = System.nanoTime();
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "request body is required");
        }

        LocalDateTime startTime = parseRequiredTime(request.getStartTime(), "startTime");
        LocalDateTime endTime = parseRequiredTime(request.getEndTime(), "endTime");
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "startTime must be before endTime");
        }

        String mode = normalizeMode(request.getMode());
        int windowSeconds = normalizeWindowSeconds(mode, request.getWindowSeconds());
        String apiCode = normalizeApiCode(request.getApiCode());
        Long apiId = null;
        if (StringUtils.hasText(apiCode)) {
            apiId = findActiveApiId(apiCode);
            if (apiId == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "api not found: " + apiCode);
            }
        }

        boolean forceRebuild = request.getForceRebuild() == null || Boolean.TRUE.equals(request.getForceRebuild());
        List<MetricWindow> windows = "DEV_SHORT_WINDOW".equals(mode)
                ? buildShortWindows(startTime, endTime, apiId, request.getScenarioRunId())
                : buildHourlyWindows(startTime, endTime, apiId);
        windows.sort(Comparator.comparing(MetricWindow::windowStart).thenComparing(MetricWindow::apiCode));

        List<AlertCandidate> candidates = new ArrayList<>();
        for (MetricWindow window : windows) {
            candidates.addAll(evaluateWindow(window, mode, windowSeconds, request.getScenarioRunId()));
        }

        int deletedOldAlertCount = 0;
        int createdAlertCount = 0;
        int updatedAlertCount = 0;
        List<AlertEvaluateItemVO> items = new ArrayList<>();
        for (AlertCandidate candidate : candidates) {
            if (forceRebuild) {
                deletedOldAlertCount += deleteExistingAlert(candidate, mode, windowSeconds);
            }
            int affectedRows = upsertAlert(candidate);
            if (affectedRows > 1) {
                updatedAlertCount++;
            } else {
                createdAlertCount++;
            }
            items.add(candidate.item());
        }

        Set<Long> apiIds = new LinkedHashSet<>();
        for (MetricWindow window : windows) {
            apiIds.add(window.apiId());
        }

        AlertEvaluateResponseVO response = new AlertEvaluateResponseVO();
        response.setStartTime(FORMATTER.format(startTime));
        response.setEndTime(FORMATTER.format(endTime));
        response.setMode(mode);
        response.setWindowSeconds(windowSeconds);
        response.setScenarioRunId(request.getScenarioRunId());
        response.setApiCode(apiCode);
        response.setEvaluatedApiCount(apiIds.size());
        response.setEvaluatedWindowCount(windows.size());
        response.setCreatedAlertCount(createdAlertCount);
        response.setUpdatedAlertCount(updatedAlertCount);
        response.setDeletedOldAlertCount(deletedOldAlertCount);
        response.setSourceRowCount(windows.stream().mapToLong(MetricWindow::sourceRowCount).sum());
        response.setLatencyMs(Math.max(0, (System.nanoTime() - started) / 1_000_000L));
        response.setItems(items);
        return response;
    }

    private List<MetricWindow> buildHourlyWindows(LocalDateTime startTime, LocalDateTime endTime, Long apiId) {
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(startTime.truncatedTo(ChronoUnit.HOURS)));
        params.add(Timestamp.valueOf(endTime));

        String apiClause = "";
        if (apiId != null) {
            apiClause = " AND s.api_id = ?";
            params.add(apiId);
        }

        return jdbcTemplate.query(
                """
                SELECT s.id, s.api_id, api.api_code, api.api_name, s.stat_time,
                       s.total_count, s.success_count, s.fail_count, s.error_4xx_count,
                       s.error_5xx_count, s.rate_limit_count, s.avg_latency_ms,
                       s.p95_latency_ms, s.p99_latency_ms, s.max_latency_ms
                FROM api_call_stat_hourly s
                JOIN api_endpoint api ON api.id = s.api_id
                WHERE s.stat_time >= ? AND s.stat_time < ? AND s.status = 'ACTIVE'
                """ + apiClause + " ORDER BY s.stat_time ASC, api.api_code ASC",
                (rs, rowNum) -> new MetricWindow(
                        rs.getLong("api_id"),
                        rs.getString("api_code"),
                        rs.getString("api_name"),
                        rs.getTimestamp("stat_time").toLocalDateTime(),
                        rs.getTimestamp("stat_time").toLocalDateTime().plusHours(1),
                        rs.getLong("total_count"),
                        rs.getLong("success_count"),
                        rs.getLong("fail_count"),
                        rs.getLong("error_4xx_count"),
                        rs.getLong("error_5xx_count"),
                        rs.getLong("rate_limit_count"),
                        rs.getInt("avg_latency_ms"),
                        rs.getInt("p95_latency_ms"),
                        rs.getInt("p99_latency_ms"),
                        rs.getInt("max_latency_ms"),
                        1,
                        Map.of("statId", rs.getLong("id"))
                ),
                params.toArray()
        );
    }

    private List<MetricWindow> buildShortWindows(LocalDateTime startTime, LocalDateTime endTime, Long apiId,
                                                 String scenarioRunId) {
        List<GatewayLogFact> facts = queryGatewayLogs(startTime, endTime, apiId);
        Map<WindowKey, ShortWindowBucket> buckets = new LinkedHashMap<>();
        for (GatewayLogFact fact : facts) {
            if (StringUtils.hasText(scenarioRunId) && !scenarioRunId.equals(fact.extraInfo().get("scenarioRunId"))) {
                continue;
            }
            long offsetSeconds = Math.max(0, Duration.between(startTime, fact.requestTime()).getSeconds());
            long bucketOffset = (offsetSeconds / DEV_SHORT_WINDOW_SECONDS) * DEV_SHORT_WINDOW_SECONDS;
            LocalDateTime bucketStart = startTime.plusSeconds(bucketOffset);
            LocalDateTime bucketEnd = bucketStart.plusSeconds(DEV_SHORT_WINDOW_SECONDS);
            if (bucketEnd.isAfter(endTime)) {
                bucketEnd = endTime;
            }
            WindowKey key = new WindowKey(fact.apiId(), fact.apiCode(), fact.apiName(), bucketStart, bucketEnd);
            buckets.computeIfAbsent(key, ShortWindowBucket::new).add(fact);
        }

        List<MetricWindow> windows = new ArrayList<>();
        for (ShortWindowBucket bucket : buckets.values()) {
            windows.add(bucket.toMetricWindow());
        }
        return windows;
    }

    private List<GatewayLogFact> queryGatewayLogs(LocalDateTime startTime, LocalDateTime endTime, Long apiId) {
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(startTime));
        params.add(Timestamp.valueOf(endTime));

        String apiClause = "";
        if (apiId != null) {
            apiClause = " AND l.api_id = ?";
            params.add(apiId);
        }

        return jdbcTemplate.query(
                """
                SELECT l.id, l.api_id, api.api_code, api.api_name, l.http_status,
                       l.error_code, l.latency_ms, l.request_time, l.extra_info
                FROM gateway_log l
                JOIN api_endpoint api ON api.id = l.api_id
                WHERE l.request_time >= ? AND l.request_time < ? AND l.status = 'ACTIVE'
                """ + apiClause + " ORDER BY l.request_time ASC, l.id ASC",
                (rs, rowNum) -> new GatewayLogFact(
                        rs.getLong("id"),
                        rs.getLong("api_id"),
                        rs.getString("api_code"),
                        rs.getString("api_name"),
                        rs.getInt("http_status"),
                        rs.getString("error_code"),
                        rs.getInt("latency_ms"),
                        rs.getTimestamp("request_time").toLocalDateTime(),
                        parseJsonMap(rs.getString("extra_info"))
                ),
                params.toArray()
        );
    }

    private List<AlertCandidate> evaluateWindow(MetricWindow window, String mode, int windowSeconds,
                                                String scenarioRunId) {
        List<AlertCandidate> candidates = new ArrayList<>();
        if (window.totalCount() < MIN_TOTAL_COUNT) {
            return candidates;
        }

        double failRate = rate(window.failCount(), window.totalCount());
        double rateLimitRate = rate(window.rateLimitCount(), window.totalCount());
        double error4xxRate = rate(window.error4xxCount(), window.totalCount());
        double error5xxRate = rate(window.error5xxCount(), window.totalCount());
        String severity = severity(failRate, error5xxRate, window.p95LatencyMs());

        if (failRate >= 0.10d) {
            candidates.add(buildCandidate(window, mode, windowSeconds, scenarioRunId, "HIGH_FAILURE_RATE",
                    severity, failRate, "failRate >= 0.10"));
        }
        if (window.rateLimitCount() >= 5 || rateLimitRate >= 0.05d) {
            candidates.add(buildCandidate(window, mode, windowSeconds, scenarioRunId, "HIGH_RATE_LIMIT",
                    severity, rateLimitRate, "rateLimitCount >= 5 OR rateLimitRate >= 0.05"));
        }
        if (window.p95LatencyMs() >= 1000) {
            candidates.add(buildCandidate(window, mode, windowSeconds, scenarioRunId, "HIGH_LATENCY",
                    severity, window.p95LatencyMs(), "p95LatencyMs >= 1000"));
        }
        if (window.error5xxCount() >= 3 || error5xxRate >= 0.05d) {
            candidates.add(buildCandidate(window, mode, windowSeconds, scenarioRunId, "HIGH_5XX",
                    severity, error5xxRate, "error5xxCount >= 3 OR error5xxRate >= 0.05"));
        }
        if ("AUTH_LOGIN".equals(window.apiCode()) && (window.error4xxCount() >= 5 || error4xxRate >= 0.10d)) {
            candidates.add(buildCandidate(window, mode, windowSeconds, scenarioRunId, "AUTH_FAILURE_SPIKE",
                    severity, error4xxRate, "AUTH_LOGIN and error4xxCount >= 5 OR error4xxRate >= 0.10"));
        }
        return candidates;
    }

    private AlertCandidate buildCandidate(MetricWindow window, String mode, int windowSeconds, String scenarioRunId,
                                          String alertType, String severity, Object actualValue, String threshold) {
        String eventCode = eventCode(alertType, window.apiCode(), mode, window.windowStart(), window.windowEnd(), windowSeconds);
        Map<String, Object> extraInfo = new LinkedHashMap<>();
        extraInfo.put("source", "AlertEvaluatorV1");
        extraInfo.put("mode", mode);
        extraInfo.put("windowSeconds", windowSeconds);
        extraInfo.put("statSource", "DEV_SHORT_WINDOW".equals(mode) ? "GATEWAY_LOG_SHORT_WINDOW" : "API_CALL_STAT_HOURLY");
        extraInfo.put("scenarioRunId", scenarioRunId);
        extraInfo.put("threshold", threshold);
        extraInfo.put("actualValue", actualValue);
        extraInfo.put("totalCount", window.totalCount());
        extraInfo.put("successCount", window.successCount());
        extraInfo.put("failCount", window.failCount());
        extraInfo.put("failRate", rate(window.failCount(), window.totalCount()));
        extraInfo.put("rateLimitCount", window.rateLimitCount());
        extraInfo.put("rateLimitRate", rate(window.rateLimitCount(), window.totalCount()));
        extraInfo.put("error4xxCount", window.error4xxCount());
        extraInfo.put("error4xxRate", rate(window.error4xxCount(), window.totalCount()));
        extraInfo.put("error5xxCount", window.error5xxCount());
        extraInfo.put("error5xxRate", rate(window.error5xxCount(), window.totalCount()));
        extraInfo.put("avgLatencyMs", window.avgLatencyMs());
        extraInfo.put("p95LatencyMs", window.p95LatencyMs());
        extraInfo.put("p99LatencyMs", window.p99LatencyMs());
        extraInfo.put("maxLatencyMs", window.maxLatencyMs());
        extraInfo.put("windowStart", FORMATTER.format(window.windowStart()));
        extraInfo.put("windowEnd", FORMATTER.format(window.windowEnd()));
        extraInfo.put("evidenceSummary", evidenceSummary(window, alertType, threshold, actualValue));
        extraInfo.putAll(window.evidence());

        String title = alertType + " on " + window.apiCode();
        String description = window.apiCode() + " triggered " + alertType + " from "
                + FORMATTER.format(window.windowStart()) + " to " + FORMATTER.format(window.windowEnd())
                + "; " + threshold + "; actual=" + actualValue + "; totalCount=" + window.totalCount();

        AlertEvaluateItemVO item = new AlertEvaluateItemVO();
        item.setEventCode(eventCode);
        item.setApiCode(window.apiCode());
        item.setApiName(window.apiName());
        item.setAlertType(alertType);
        item.setSeverity(severity);
        item.setTitle(title);
        item.setDescription(description);
        item.setWindowStart(FORMATTER.format(window.windowStart()));
        item.setWindowEnd(FORMATTER.format(window.windowEnd()));
        item.setTotalCount(window.totalCount());
        item.setFailCount(window.failCount());
        item.setRateLimitCount(window.rateLimitCount());
        item.setError4xxCount(window.error4xxCount());
        item.setError5xxCount(window.error5xxCount());
        item.setP95LatencyMs(window.p95LatencyMs());
        item.setP99LatencyMs(window.p99LatencyMs());
        item.setExtraInfo(extraInfo);

        return new AlertCandidate(eventCode, window.apiId(), alertType, severity, title, description,
                window.windowStart(), window.windowEnd(), extraInfo, item);
    }

    private int deleteExistingAlert(AlertCandidate candidate, String mode, int windowSeconds) {
        return jdbcTemplate.update(
                """
                DELETE FROM alert_event
                WHERE api_id = ? AND event_type = ? AND start_time = ? AND end_time = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.source')) = 'AlertEvaluatorV1'
                  AND JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.mode')) = ?
                  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(extra_info, '$.windowSeconds')) AS UNSIGNED) = ?
                """,
                candidate.apiId(),
                candidate.alertType(),
                Timestamp.valueOf(candidate.startTime()),
                Timestamp.valueOf(candidate.endTime()),
                mode,
                windowSeconds
        );
    }

    private int upsertAlert(AlertCandidate candidate) {
        return jdbcTemplate.update(
                """
                INSERT INTO alert_event (
                  event_code, api_id, event_type, severity, title, description,
                  start_time, end_time, resolved, status, extra_info, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  severity = VALUES(severity),
                  title = VALUES(title),
                  description = VALUES(description),
                  end_time = VALUES(end_time),
                  resolved = 0,
                  status = 'ACTIVE',
                  extra_info = VALUES(extra_info),
                  updated_at = CURRENT_TIMESTAMP
                """,
                candidate.eventCode(),
                candidate.apiId(),
                candidate.alertType(),
                candidate.severity(),
                candidate.title(),
                candidate.description(),
                Timestamp.valueOf(candidate.startTime()),
                Timestamp.valueOf(candidate.endTime()),
                toJson(candidate.extraInfo())
        );
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
            return "HOURLY";
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (!"HOURLY".equals(normalized) && !"DEV_SHORT_WINDOW".equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "mode must be HOURLY or DEV_SHORT_WINDOW");
        }
        return normalized;
    }

    private int normalizeWindowSeconds(String mode, Integer windowSeconds) {
        if ("HOURLY".equals(mode)) {
            return DEFAULT_HOURLY_WINDOW_SECONDS;
        }
        int value = windowSeconds == null ? DEV_SHORT_WINDOW_SECONDS : windowSeconds;
        if (value != DEV_SHORT_WINDOW_SECONDS) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "DEV_SHORT_WINDOW supports windowSeconds=30");
        }
        return value;
    }

    private Long findActiveApiId(String apiCode) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM api_endpoint WHERE api_code = ? AND status = 'ACTIVE' LIMIT 1",
                    Long.class,
                    apiCode
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
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

    private String normalizeApiCode(String apiCode) {
        return StringUtils.hasText(apiCode) ? apiCode.trim().toUpperCase(Locale.ROOT) : null;
    }

    private double rate(long value, long total) {
        return total <= 0 ? 0d : (double) value / total;
    }

    private String severity(double failRate, double error5xxRate, int p95LatencyMs) {
        return failRate >= 0.30d || error5xxRate >= 0.20d || p95LatencyMs >= 3000 ? "CRITICAL" : "WARNING";
    }

    private String evidenceSummary(MetricWindow window, String alertType, String threshold, Object actualValue) {
        return alertType + " threshold matched: " + threshold + ", actual=" + actualValue
                + ", total=" + window.totalCount() + ", fail=" + window.failCount()
                + ", 4xx=" + window.error4xxCount() + ", 5xx=" + window.error5xxCount()
                + ", 429=" + window.rateLimitCount() + ", p95=" + window.p95LatencyMs();
    }

    private String eventCode(String alertType, String apiCode, String mode, LocalDateTime startTime,
                             LocalDateTime endTime, int windowSeconds) {
        String safeApiCode = apiCode.replaceAll("[^A-Z0-9_]", "_");
        if (safeApiCode.length() > 24) {
            safeApiCode = safeApiCode.substring(0, 24);
        }
        String seed = alertType + "|" + apiCode + "|" + mode + "|" + FORMATTER.format(startTime)
                + "|" + FORMATTER.format(endTime) + "|" + windowSeconds;
        return "AE_" + alertAbbrev(alertType) + "_" + safeApiCode + "_"
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(startTime) + "_" + hash8(seed);
    }

    private String alertAbbrev(String alertType) {
        return switch (alertType) {
            case "HIGH_FAILURE_RATE" -> "HFR";
            case "HIGH_RATE_LIMIT" -> "HRL";
            case "HIGH_LATENCY" -> "HLAT";
            case "HIGH_5XX" -> "H5XX";
            case "AUTH_FAILURE_SPIKE" -> "AFS";
            default -> "GEN";
        };
    }

    private String hash8(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private record GatewayLogFact(
            long id,
            long apiId,
            String apiCode,
            String apiName,
            int httpStatus,
            String errorCode,
            int latencyMs,
            LocalDateTime requestTime,
            Map<String, Object> extraInfo
    ) {
    }

    private record WindowKey(
            long apiId,
            String apiCode,
            String apiName,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {
    }

    private record MetricWindow(
            long apiId,
            String apiCode,
            String apiName,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            long totalCount,
            long successCount,
            long failCount,
            long error4xxCount,
            long error5xxCount,
            long rateLimitCount,
            int avgLatencyMs,
            int p95LatencyMs,
            int p99LatencyMs,
            int maxLatencyMs,
            long sourceRowCount,
            Map<String, Object> evidence
    ) {
    }

    private record AlertCandidate(
            String eventCode,
            long apiId,
            String alertType,
            String severity,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Map<String, Object> extraInfo,
            AlertEvaluateItemVO item
    ) {
    }

    private static final class ShortWindowBucket {

        private final WindowKey key;
        private final List<Integer> latencies = new ArrayList<>();
        private long totalCount;
        private long successCount;
        private long failCount;
        private long error4xxCount;
        private long error5xxCount;
        private long rateLimitCount;
        private long latencySum;
        private long minLogId = Long.MAX_VALUE;
        private long maxLogId = Long.MIN_VALUE;
        private final Map<String, Long> statusDistribution = new LinkedHashMap<>();
        private final Map<String, Long> errorCodeDistribution = new LinkedHashMap<>();

        private ShortWindowBucket(WindowKey key) {
            this.key = key;
        }

        private void add(GatewayLogFact fact) {
            totalCount++;
            if (fact.httpStatus() >= 200 && fact.httpStatus() <= 399) {
                successCount++;
            }
            if (fact.httpStatus() >= 400) {
                failCount++;
            }
            if (fact.httpStatus() >= 400 && fact.httpStatus() <= 499) {
                error4xxCount++;
            }
            if (fact.httpStatus() >= 500) {
                error5xxCount++;
            }
            if (fact.httpStatus() == 429) {
                rateLimitCount++;
            }
            int latency = Math.max(0, fact.latencyMs());
            latencies.add(latency);
            latencySum += latency;
            minLogId = Math.min(minLogId, fact.id());
            maxLogId = Math.max(maxLogId, fact.id());
            statusDistribution.merge(String.valueOf(fact.httpStatus()), 1L, Long::sum);
            if (StringUtils.hasText(fact.errorCode())) {
                errorCodeDistribution.merge(fact.errorCode(), 1L, Long::sum);
            }
        }

        private MetricWindow toMetricWindow() {
            latencies.sort(Integer::compareTo);
            int avgLatency = totalCount == 0 ? 0 : (int) Math.round((double) latencySum / totalCount);
            int maxLatency = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("logIdRange", List.of(
                    minLogId == Long.MAX_VALUE ? 0 : minLogId,
                    maxLogId == Long.MIN_VALUE ? 0 : maxLogId
            ));
            evidence.put("statusDistribution", statusDistribution);
            evidence.put("errorCodeDistribution", errorCodeDistribution);
            return new MetricWindow(
                    key.apiId(),
                    key.apiCode(),
                    key.apiName(),
                    key.windowStart(),
                    key.windowEnd(),
                    totalCount,
                    successCount,
                    failCount,
                    error4xxCount,
                    error5xxCount,
                    rateLimitCount,
                    avgLatency,
                    percentile(latencies, 0.95),
                    percentile(latencies, 0.99),
                    maxLatency,
                    totalCount,
                    evidence
            );
        }

        private static int percentile(List<Integer> sortedValues, double quantile) {
            if (sortedValues.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil(quantile * sortedValues.size()) - 1;
            index = Math.max(0, Math.min(index, sortedValues.size() - 1));
            return sortedValues.get(index);
        }
    }
}
