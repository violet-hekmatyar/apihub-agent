package com.apihub.agent.dev.stats;

import com.apihub.agent.common.ErrorCode;
import com.apihub.agent.exception.BusinessException;
import com.apihub.agent.model.dto.StatsAggregateRequest;
import com.apihub.agent.model.vo.StatsAggregateItemVO;
import com.apihub.agent.model.vo.StatsAggregateResponseVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StatsAggregateService {

    private static final Logger log = LoggerFactory.getLogger(StatsAggregateService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StatsAggregateService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StatsAggregateResponseVO aggregate(StatsAggregateRequest request) {
        long started = System.nanoTime();
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "request body is required");
        }

        LocalDateTime startTime = parseRequiredTime(request.getStartTime(), "startTime");
        LocalDateTime endTime = parseRequiredTime(request.getEndTime(), "endTime");
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "startTime must be before endTime");
        }

        String apiCode = normalizeApiCode(request.getApiCode());
        Long apiId = null;
        if (StringUtils.hasText(apiCode)) {
            apiId = findActiveApiId(apiCode);
            if (apiId == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "api not found: " + apiCode);
            }
        }

        boolean forceRebuild = request.getForceRebuild() == null || Boolean.TRUE.equals(request.getForceRebuild());
        List<GatewayLogFact> facts = queryGatewayLogs(startTime, endTime, apiId);
        Map<StatKey, StatBucket> buckets = new LinkedHashMap<>();
        long scenarioMatchedLogs = 0;
        for (GatewayLogFact fact : facts) {
            if (StringUtils.hasText(request.getScenarioRunId())
                    && request.getScenarioRunId().equals(fact.extraInfo().get("scenarioRunId"))) {
                scenarioMatchedLogs++;
            }
            StatKey key = new StatKey(fact.apiId(), fact.apiCode(), fact.apiName(), fact.requestTime().truncatedTo(ChronoUnit.HOURS));
            buckets.computeIfAbsent(key, StatBucket::new).add(fact);
        }

        List<StatBucket> orderedBuckets = new ArrayList<>(buckets.values());
        orderedBuckets.sort(Comparator
                .comparing((StatBucket bucket) -> bucket.key().statTime())
                .thenComparing(bucket -> bucket.key().apiCode()));

        int deletedRows = 0;
        if (forceRebuild) {
            for (StatBucket bucket : orderedBuckets) {
                deletedRows += jdbcTemplate.update(
                        "DELETE FROM api_call_stat_hourly WHERE api_id = ? AND stat_time = ?",
                        bucket.key().apiId(),
                        Timestamp.valueOf(bucket.key().statTime())
                );
            }
        }

        List<StatsAggregateItemVO> items = new ArrayList<>();
        Set<String> statTimeRange = new LinkedHashSet<>();
        Set<Long> affectedApis = new LinkedHashSet<>();
        for (StatBucket bucket : orderedBuckets) {
            BucketStat stat = bucket.toStat();
            insertStat(bucket, stat, request, startTime, endTime);
            items.add(toItem(bucket, stat));
            statTimeRange.add(FORMATTER.format(bucket.key().statTime()));
            affectedApis.add(bucket.key().apiId());
        }

        StatsAggregateResponseVO response = new StatsAggregateResponseVO();
        response.setStartTime(FORMATTER.format(startTime));
        response.setEndTime(FORMATTER.format(endTime));
        response.setScenarioRunId(request.getScenarioRunId());
        response.setApiCode(apiCode);
        response.setForceRebuild(forceRebuild);
        response.setAffectedApiCount(affectedApis.size());
        response.setAggregatedRows(items.size());
        response.setDeletedRows(deletedRows);
        response.setTotalLogs(facts.size());
        response.setScenarioMatchedLogs(scenarioMatchedLogs);
        response.setStatTimeRange(new ArrayList<>(statTimeRange));
        response.setItems(items);
        response.setLatencyMs(elapsedMs(started));

        log.info("stats aggregate completed startTime={} endTime={} scenarioRunId={} apiCode={} totalLogs={} affectedApiCount={} aggregatedRows={} latencyMs={}",
                response.getStartTime(), response.getEndTime(), response.getScenarioRunId(), response.getApiCode(),
                response.getTotalLogs(), response.getAffectedApiCount(), response.getAggregatedRows(), response.getLatencyMs());
        return response;
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
                SELECT l.id, l.api_id, api.api_code, api.api_name, app.app_code,
                       l.http_status, l.error_code, l.latency_ms, l.request_time, l.extra_info
                FROM gateway_log l
                JOIN api_endpoint api ON api.id = l.api_id
                LEFT JOIN api_consumer_app app ON app.id = l.app_id
                WHERE l.request_time >= ? AND l.request_time < ? AND l.status = 'ACTIVE'
                """ + apiClause + " ORDER BY l.request_time ASC, l.id ASC",
                (rs, rowNum) -> new GatewayLogFact(
                        rs.getLong("id"),
                        rs.getLong("api_id"),
                        rs.getString("api_code"),
                        rs.getString("api_name"),
                        rs.getString("app_code"),
                        rs.getInt("http_status"),
                        rs.getString("error_code"),
                        rs.getInt("latency_ms"),
                        rs.getTimestamp("request_time").toLocalDateTime(),
                        parseJsonMap(rs.getString("extra_info"))
                ),
                params.toArray()
        );
    }

    private void insertStat(StatBucket bucket, BucketStat stat, StatsAggregateRequest request,
                            LocalDateTime startTime, LocalDateTime endTime) {
        jdbcTemplate.update(
                """
                INSERT INTO api_call_stat_hourly (
                  api_id, stat_time, total_count, success_count, fail_count,
                  error_4xx_count, error_5xx_count, rate_limit_count,
                  avg_latency_ms, p95_latency_ms, p99_latency_ms, max_latency_ms,
                  status, extra_info, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  total_count = VALUES(total_count),
                  success_count = VALUES(success_count),
                  fail_count = VALUES(fail_count),
                  error_4xx_count = VALUES(error_4xx_count),
                  error_5xx_count = VALUES(error_5xx_count),
                  rate_limit_count = VALUES(rate_limit_count),
                  avg_latency_ms = VALUES(avg_latency_ms),
                  p95_latency_ms = VALUES(p95_latency_ms),
                  p99_latency_ms = VALUES(p99_latency_ms),
                  max_latency_ms = VALUES(max_latency_ms),
                  status = 'ACTIVE',
                  extra_info = VALUES(extra_info),
                  updated_at = CURRENT_TIMESTAMP
                """,
                bucket.key().apiId(),
                Timestamp.valueOf(bucket.key().statTime()),
                stat.totalCount(),
                stat.successCount(),
                stat.failCount(),
                stat.error4xxCount(),
                stat.error5xxCount(),
                stat.rateLimitCount(),
                stat.avgLatencyMs(),
                stat.p95LatencyMs(),
                stat.p99LatencyMs(),
                stat.maxLatencyMs(),
                toJson(buildExtraInfo(bucket, stat, request, startTime, endTime))
        );
    }

    private Map<String, Object> buildExtraInfo(StatBucket bucket, BucketStat stat, StatsAggregateRequest request,
                                               LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("source", "gateway_log");
        extra.put("aggregatorVersion", "v1");
        extra.put("startTime", FORMATTER.format(startTime));
        extra.put("endTime", FORMATTER.format(endTime));
        extra.put("scenarioRunId", request.getScenarioRunId());
        extra.put("scenarioRunIdUsedForAggregation", false);
        extra.put("apiCode", bucket.key().apiCode());
        extra.put("logIdRange", List.of(bucket.minLogId(), bucket.maxLogId()));
        extra.put("appBreakdown", bucket.appBreakdown());
        extra.put("statusDistribution", bucket.statusDistribution());
        extra.put("errorCodeDistribution", bucket.errorCodeDistribution());
        extra.put("latencyCalculation", Map.of(
                "avg", "rounded arithmetic mean of gateway_log.latency_ms",
                "p95", "ceil(0.95 * n) - 1 after sorting latency_ms ascending",
                "p99", "ceil(0.99 * n) - 1 after sorting latency_ms ascending",
                "sampleSize", stat.totalCount()
        ));
        return extra;
    }

    private StatsAggregateItemVO toItem(StatBucket bucket, BucketStat stat) {
        StatsAggregateItemVO item = new StatsAggregateItemVO();
        item.setApiCode(bucket.key().apiCode());
        item.setApiName(bucket.key().apiName());
        item.setStatTime(FORMATTER.format(bucket.key().statTime()));
        item.setTotalCount(stat.totalCount());
        item.setSuccessCount(stat.successCount());
        item.setFailCount(stat.failCount());
        item.setError4xxCount(stat.error4xxCount());
        item.setError5xxCount(stat.error5xxCount());
        item.setRateLimitCount(stat.rateLimitCount());
        item.setAvgLatencyMs(stat.avgLatencyMs());
        item.setP95LatencyMs(stat.p95LatencyMs());
        item.setP99LatencyMs(stat.p99LatencyMs());
        item.setMaxLatencyMs(stat.maxLatencyMs());
        return item;
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

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private record GatewayLogFact(
            long id,
            long apiId,
            String apiCode,
            String apiName,
            String appCode,
            int httpStatus,
            String errorCode,
            int latencyMs,
            LocalDateTime requestTime,
            Map<String, Object> extraInfo
    ) {
    }

    private record StatKey(long apiId, String apiCode, String apiName, LocalDateTime statTime) {
    }

    private record BucketStat(
            long totalCount,
            long successCount,
            long failCount,
            long error4xxCount,
            long error5xxCount,
            long rateLimitCount,
            int avgLatencyMs,
            int p95LatencyMs,
            int p99LatencyMs,
            int maxLatencyMs
    ) {
    }

    private static final class StatBucket {

        private final StatKey key;
        private final List<Integer> latencies = new ArrayList<>();
        private final Map<String, Long> appBreakdown = new LinkedHashMap<>();
        private final Map<String, Long> statusDistribution = new LinkedHashMap<>();
        private final Map<String, Long> errorCodeDistribution = new LinkedHashMap<>();
        private long totalCount;
        private long successCount;
        private long failCount;
        private long error4xxCount;
        private long error5xxCount;
        private long rateLimitCount;
        private long latencySum;
        private long minLogId = Long.MAX_VALUE;
        private long maxLogId = Long.MIN_VALUE;

        private StatBucket(StatKey key) {
            this.key = key;
        }

        private StatKey key() {
            return key;
        }

        private long minLogId() {
            return minLogId == Long.MAX_VALUE ? 0 : minLogId;
        }

        private long maxLogId() {
            return maxLogId == Long.MIN_VALUE ? 0 : maxLogId;
        }

        private Map<String, Long> appBreakdown() {
            return appBreakdown;
        }

        private Map<String, Long> statusDistribution() {
            return statusDistribution;
        }

        private Map<String, Long> errorCodeDistribution() {
            return errorCodeDistribution;
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

            String appCode = StringUtils.hasText(fact.appCode()) ? fact.appCode() : "UNKNOWN";
            appBreakdown.merge(appCode, 1L, Long::sum);
            statusDistribution.merge(String.valueOf(fact.httpStatus()), 1L, Long::sum);
            if (StringUtils.hasText(fact.errorCode())) {
                errorCodeDistribution.merge(fact.errorCode(), 1L, Long::sum);
            }
        }

        private BucketStat toStat() {
            latencies.sort(Integer::compareTo);
            int avgLatency = totalCount == 0 ? 0 : (int) Math.round((double) latencySum / totalCount);
            int maxLatency = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
            return new BucketStat(
                    totalCount,
                    successCount,
                    failCount,
                    error4xxCount,
                    error5xxCount,
                    rateLimitCount,
                    avgLatency,
                    percentile(latencies, 0.95),
                    percentile(latencies, 0.99),
                    maxLatency
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
