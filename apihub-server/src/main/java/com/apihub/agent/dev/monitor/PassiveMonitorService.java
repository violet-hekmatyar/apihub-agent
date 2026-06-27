package com.apihub.agent.dev.monitor;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PassiveMonitorService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PassiveMonitorConfig config;
    private final PassiveAlertRuleEvaluator ruleEvaluator;
    private final PassiveMonitorEventRepository eventRepository;
    private final PassiveAlertSnapshotRepository snapshotRepository;
    private final Map<String, NavigableMap<Long, SlidingMetricBucket>> buckets = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastSignalTime;
    private volatile LocalDateTime lastEventTime;

    PassiveMonitorService(PassiveMonitorConfig config,
                          PassiveAlertRuleEvaluator ruleEvaluator,
                          PassiveMonitorEventRepository eventRepository,
                          PassiveAlertSnapshotRepository snapshotRepository) {
        this.config = config;
        this.ruleEvaluator = ruleEvaluator;
        this.eventRepository = eventRepository;
        this.snapshotRepository = snapshotRepository;
    }

    public PassiveMonitorStatusVO start() {
        config.setEnabled(true);
        return status();
    }

    public PassiveMonitorStatusVO stop() {
        config.setEnabled(false);
        return status();
    }

    public PassiveMonitorStatusVO applyConfig(PassiveMonitorConfigRequest request) {
        config.apply(request);
        return status();
    }

    public PassiveMonitorStatusVO status() {
        PassiveMonitorStatusVO status = new PassiveMonitorStatusVO();
        status.setEnabled(config.isEnabled());
        status.setStatus(config.isEnabled() ? "RUNNING" : "STOPPED");
        status.setConfig(config.view());
        status.setActiveEventCount(eventRepository.countStatus("FIRING"));
        status.setCooldownEventCount(eventRepository.countStatus("COOLDOWN"));
        status.setBucketKeyCount(buckets.size());
        status.setLastSignalTime(format(lastSignalTime));
        status.setLastEventTime(format(lastEventTime));
        return status;
    }

    public void onGatewaySignal(GatewayMonitoringSignal signal) {
        if (!config.isEnabled() || signal == null || !StringUtils.hasText(signal.getApiCode())) {
            return;
        }
        LocalDateTime signalTime = signal.getRequestTime() == null ? LocalDateTime.now() : signal.getRequestTime();
        lastSignalTime = signalTime;
        String key = metricKey(signal.getApiCode(), signal.getCallerAppCode());
        long bucketStart = bucketStartEpoch(signalTime);
        NavigableMap<Long, SlidingMetricBucket> series = buckets.computeIfAbsent(key, ignored -> new java.util.TreeMap<>());
        synchronized (series) {
            series.computeIfAbsent(bucketStart, start -> new SlidingMetricBucket(start, config.getBucketSeconds())).add(signal);
            prune(series, signalTime);
            MetricWindowView current = window(series, signalTime.minusSeconds(config.getShortWindowSeconds()), signalTime.plusSeconds(1));
            MetricWindowView baseline = window(series,
                    signalTime.minusSeconds(config.getBaselineWindowSeconds() + config.getShortWindowSeconds()),
                    signalTime.minusSeconds(config.getShortWindowSeconds()));
            List<PassiveAlertCandidate> candidates = ruleEvaluator.evaluate(signal.getApiCode(), signal.getCallerAppCode(), current, baseline, config);
            for (PassiveAlertCandidate candidate : candidates) {
                upsertEvent(signal, candidate, signalTime);
            }
        }
        eventRepository.markCooldown(LocalDateTime.now());
    }

    public List<Map<String, Object>> recent(int limit) {
        return eventRepository.recent(limit);
    }

    public List<Map<String, Object>> query(PassiveMonitorEventQuery query) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;
        LocalDateTime end;
        if (StringUtils.hasText(query.getStartTime()) && StringUtils.hasText(query.getEndTime())) {
            start = LocalDateTime.parse(query.getStartTime(), FORMATTER);
            end = LocalDateTime.parse(query.getEndTime(), FORMATTER);
        } else if ("7d".equalsIgnoreCase(query.getRange())) {
            start = now.minusDays(7);
            end = now.plusSeconds(1);
        } else {
            start = now.minusHours(24);
            end = now.plusSeconds(1);
        }
        return eventRepository.query(query, start, end);
    }

    public PassiveMonitorEventVO detail(String monitorEventId) {
        Map<String, Object> event = eventRepository.findByMonitorEventId(monitorEventId);
        PassiveMonitorEventVO detail = new PassiveMonitorEventVO();
        detail.setEvent(event == null ? Map.of() : event);
        detail.setSnapshots(snapshotRepository.findByMonitorEventId(monitorEventId));
        if (event != null && event.get("alert_event_id") instanceof Number number) {
            detail.setAlertEvent(eventRepository.findAlertEvent(number.longValue()));
        }
        return detail;
    }

    public Map<String, Object> closeCheck() {
        LocalDateTime now = LocalDateTime.now();
        eventRepository.markCooldown(now);
        int resolved = 0;
        List<String> resolvedIds = new ArrayList<>();
        for (Map<String, Object> event : eventRepository.openEvents()) {
            LocalDateTime first = toTime(event.get("first_trigger_time"));
            LocalDateTime last = toTime(event.get("last_trigger_time"));
            if (first == null || last == null) {
                continue;
            }
            long activeSeconds = Math.max(0, java.time.Duration.between(first, last).getSeconds());
            long quietSeconds = Math.max(180, activeSeconds / 2);
            if (now.isBefore(last.plusSeconds(quietSeconds))) {
                continue;
            }
            String monitorEventId = String.valueOf(event.get("monitor_event_id"));
            int duration = (int) Math.max(0, java.time.Duration.between(first, now).getSeconds());
            if (eventRepository.resolveEvent(monitorEventId, now, duration) > 0) {
                resolved++;
                resolvedIds.add(monitorEventId);
                Long alertEventId = event.get("alert_event_id") instanceof Number number ? number.longValue() : null;
                eventRepository.resolveAlertEvent(alertEventId, now);
                snapshotRepository.insertSnapshot(monitorEventId, "CLOSE_SUMMARY",
                        String.valueOf(event.get("api_code")),
                        event.get("caller_app_code") == null ? null : String.valueOf(event.get("caller_app_code")),
                        eventWindow(event),
                        Map.of("quietPeriodSeconds", quietSeconds, "durationSeconds", duration),
                        Map.of("closedBy", "dev-close-check"));
            }
        }
        return Map.of("checkedAt", format(now), "resolvedCount", resolved, "resolvedMonitorEventIds", resolvedIds);
    }

    private void upsertEvent(GatewayMonitoringSignal signal, PassiveAlertCandidate candidate, LocalDateTime now) {
        Map<String, Object> existing = eventRepository.findOpenByDedupKey(candidate.dedupKey());
        LocalDateTime cooldownUntil = now.plusSeconds(config.getCooldownSeconds());
        Map<String, Object> extra = eventExtra(signal, candidate);
        if (existing == null) {
            String eventCode = "PM_" + hash8(candidate.dedupKey()) + "_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now);
            Long alertEventId = eventRepository.upsertAlertEvent(signal.getApiId(), eventCode, candidate, now, extra);
            String monitorEventId = "pm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
            eventRepository.insertEvent(monitorEventId, alertEventId, candidate, now, cooldownUntil, extra);
            snapshotRepository.insertSnapshot(monitorEventId, "TRIGGER_WINDOW", candidate.apiCode(), candidate.callerAppCode(),
                    candidate.currentWindow(), thresholds(candidate), extra);
            snapshotRepository.insertSnapshot(monitorEventId, "CONTEXT_BEFORE", candidate.apiCode(), candidate.callerAppCode(),
                    candidate.baselineWindow(), thresholds(candidate), Map.of("source", "baselineWindow"));
            lastEventTime = now;
        } else {
            String monitorEventId = String.valueOf(existing.get("monitor_event_id"));
            Long alertEventId = existing.get("alert_event_id") instanceof Number number
                    ? number.longValue()
                    : eventRepository.upsertAlertEvent(signal.getApiId(),
                    "PM_" + hash8(candidate.dedupKey()) + "_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now),
                    candidate, now, extra);
            eventRepository.updateTriggered(monitorEventId, alertEventId, candidate, now, cooldownUntil, extra);
            lastEventTime = now;
        }
    }

    private MetricWindowView window(NavigableMap<Long, SlidingMetricBucket> series, LocalDateTime start, LocalDateTime end) {
        long startEpoch = start.atZone(ZONE).toEpochSecond();
        long endEpoch = end.atZone(ZONE).toEpochSecond();
        MetricWindowView view = new MetricWindowView();
        for (SlidingMetricBucket bucket : series.subMap(startEpoch, true, endEpoch, true).values()) {
            view.add(bucket);
        }
        return view;
    }

    private void prune(NavigableMap<Long, SlidingMetricBucket> series, LocalDateTime now) {
        long keepAfter = now.minusSeconds(config.getBaselineWindowSeconds()
                + config.getContextBeforeSeconds()
                + config.getShortWindowSeconds()
                + 60L).atZone(ZONE).toEpochSecond();
        series.headMap(keepAfter, false).clear();
    }

    private MetricWindowView eventWindow(Map<String, Object> event) {
        MetricWindowView view = new MetricWindowView();
        SlidingMetricBucket bucket = new SlidingMetricBucket(toTime(event.get("window_start_time")).atZone(ZONE).toEpochSecond(), config.getBucketSeconds());
        GatewayMonitoringSignal synthetic = new GatewayMonitoringSignal();
        synthetic.setHttpStatus(intValue(event.get("error_count")) > 0 ? 500 : 200);
        synthetic.setLatencyMs(intValue(event.get("p95_latency_ms")));
        synthetic.setRequestId("close-summary");
        synthetic.setFailureSource("PASSIVE_MONITOR");
        int requests = Math.max(1, intValue(event.get("request_count")));
        for (int i = 0; i < requests; i++) {
            bucket.add(synthetic);
        }
        view.add(bucket);
        return view;
    }

    private Map<String, Object> eventExtra(GatewayMonitoringSignal signal, PassiveAlertCandidate candidate) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("source", "AdaptivePassiveAlertMonitorV1");
        extra.put("threshold", candidate.threshold());
        extra.put("gatewayLogId", signal.getGatewayLogId());
        extra.put("traceId", signal.getTraceId());
        extra.put("requestId", signal.getRequestId());
        extra.put("scenarioRunId", signal.getScenarioRunId());
        extra.put("phaseCode", signal.getPhaseCode());
        extra.put("mockScenario", signal.getMockScenario());
        extra.put("failureSource", signal.getFailureSource());
        extra.put("currentWindow", candidate.currentWindow().metricsMap());
        extra.put("baselineWindow", candidate.baselineWindow() == null ? Map.of() : candidate.baselineWindow().metricsMap());
        extra.put("mqReserved", "GatewayMonitoringSignalPublisher can later forward this fact to MQ or async batch writer.");
        return extra;
    }

    private Map<String, Object> thresholds(PassiveAlertCandidate candidate) {
        return Map.of(
                "alertType", candidate.alertType(),
                "threshold", candidate.threshold(),
                "config", config.view()
        );
    }

    private String metricKey(String apiCode, String callerAppCode) {
        return apiCode + "|" + (callerAppCode == null ? "UNKNOWN" : callerAppCode);
    }

    private long bucketStartEpoch(LocalDateTime time) {
        long epoch = time.atZone(ZONE).toEpochSecond();
        return (epoch / config.getBucketSeconds()) * config.getBucketSeconds();
    }

    private String hash8(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 8);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String format(LocalDateTime time) {
        return time == null ? null : FORMATTER.format(time);
    }

    private LocalDateTime toTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZONE);
        }
        return null;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
