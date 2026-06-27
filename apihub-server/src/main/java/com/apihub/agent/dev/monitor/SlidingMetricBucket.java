package com.apihub.agent.dev.monitor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SlidingMetricBucket {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final long bucketStartEpochSecond;
    private final long bucketEndEpochSecond;
    private int requestCount;
    private int successCount;
    private int errorCount;
    private int error4xxCount;
    private int error5xxCount;
    private int rateLimitCount;
    private int authFailureCount;
    private int timeoutCount;
    private long latencySum;
    private int maxLatencyMs;
    private final List<Integer> latencySamples = new ArrayList<>();
    private final Map<String, Integer> statusCodeDistribution = new LinkedHashMap<>();
    private final Map<String, Integer> businessCodeDistribution = new LinkedHashMap<>();
    private final Map<String, Integer> failureSourceDistribution = new LinkedHashMap<>();
    private final List<String> sampleRequestIds = new ArrayList<>();

    SlidingMetricBucket(long bucketStartEpochSecond, int bucketSeconds) {
        this.bucketStartEpochSecond = bucketStartEpochSecond;
        this.bucketEndEpochSecond = bucketStartEpochSecond + bucketSeconds;
    }

    void add(GatewayMonitoringSignal signal) {
        requestCount++;
        if (signal.getHttpStatus() >= 200 && signal.getHttpStatus() <= 399) {
            successCount++;
        }
        if (signal.getHttpStatus() >= 400) {
            errorCount++;
        }
        if (signal.getHttpStatus() >= 400 && signal.getHttpStatus() <= 499) {
            error4xxCount++;
        }
        if (signal.getHttpStatus() >= 500) {
            error5xxCount++;
        }
        if (signal.getHttpStatus() == 429 || "RATE_LIMITED".equalsIgnoreCase(signal.getErrorCode())) {
            rateLimitCount++;
        }
        if ("AUTH_LOGIN".equalsIgnoreCase(signal.getApiCode()) && signal.getHttpStatus() >= 400 && signal.getHttpStatus() <= 499) {
            authFailureCount++;
        }
        if (signal.getHttpStatus() == 504 || contains(signal.getErrorCode(), "TIMEOUT")) {
            timeoutCount++;
        }
        int latency = (int) Math.max(0, Math.min(Integer.MAX_VALUE, signal.getLatencyMs()));
        latencySum += latency;
        maxLatencyMs = Math.max(maxLatencyMs, latency);
        if (latencySamples.size() < 200) {
            latencySamples.add(latency);
        }
        merge(statusCodeDistribution, String.valueOf(signal.getHttpStatus()));
        if (signal.getBusinessCode() != null) {
            merge(businessCodeDistribution, String.valueOf(signal.getBusinessCode()));
        }
        merge(failureSourceDistribution, signal.getFailureSource() == null ? "UNKNOWN" : signal.getFailureSource());
        if (signal.getRequestId() != null && sampleRequestIds.size() < 20) {
            sampleRequestIds.add(signal.getRequestId());
        }
    }

    MetricWindowView toWindow() {
        MetricWindowView view = new MetricWindowView();
        view.add(this);
        return view;
    }

    long bucketStartEpochSecond() {
        return bucketStartEpochSecond;
    }

    LocalDateTime startTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(bucketStartEpochSecond), ZONE);
    }

    LocalDateTime endTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(bucketEndEpochSecond), ZONE);
    }

    int requestCount() { return requestCount; }
    int successCount() { return successCount; }
    int errorCount() { return errorCount; }
    int error4xxCount() { return error4xxCount; }
    int error5xxCount() { return error5xxCount; }
    int rateLimitCount() { return rateLimitCount; }
    int authFailureCount() { return authFailureCount; }
    int timeoutCount() { return timeoutCount; }
    long latencySum() { return latencySum; }
    int maxLatencyMs() { return maxLatencyMs; }
    List<Integer> latencySamples() { return latencySamples; }
    Map<String, Integer> statusCodeDistribution() { return statusCodeDistribution; }
    Map<String, Integer> businessCodeDistribution() { return businessCodeDistribution; }
    Map<String, Integer> failureSourceDistribution() { return failureSourceDistribution; }
    List<String> sampleRequestIds() { return sampleRequestIds; }

    private boolean contains(String value, String token) {
        return value != null && value.toUpperCase().contains(token);
    }

    private void merge(Map<String, Integer> target, String key) {
        target.merge(key, 1, Integer::sum);
    }
}

class MetricWindowView {
    private LocalDateTime windowStartTime;
    private LocalDateTime windowEndTime;
    private int requestCount;
    private int successCount;
    private int errorCount;
    private int error4xxCount;
    private int error5xxCount;
    private int rateLimitCount;
    private int authFailureCount;
    private int timeoutCount;
    private long latencySum;
    private int maxLatencyMs;
    private final List<Integer> latencySamples = new ArrayList<>();
    private final Map<String, Integer> statusCodeDistribution = new LinkedHashMap<>();
    private final Map<String, Integer> businessCodeDistribution = new LinkedHashMap<>();
    private final Map<String, Integer> failureSourceDistribution = new LinkedHashMap<>();
    private final List<String> sampleRequestIds = new ArrayList<>();

    void add(SlidingMetricBucket bucket) {
        if (windowStartTime == null || bucket.startTime().isBefore(windowStartTime)) {
            windowStartTime = bucket.startTime();
        }
        if (windowEndTime == null || bucket.endTime().isAfter(windowEndTime)) {
            windowEndTime = bucket.endTime();
        }
        requestCount += bucket.requestCount();
        successCount += bucket.successCount();
        errorCount += bucket.errorCount();
        error4xxCount += bucket.error4xxCount();
        error5xxCount += bucket.error5xxCount();
        rateLimitCount += bucket.rateLimitCount();
        authFailureCount += bucket.authFailureCount();
        timeoutCount += bucket.timeoutCount();
        latencySum += bucket.latencySum();
        maxLatencyMs = Math.max(maxLatencyMs, bucket.maxLatencyMs());
        for (Integer sample : bucket.latencySamples()) {
            if (latencySamples.size() < 500) {
                latencySamples.add(sample);
            }
        }
        mergeAll(statusCodeDistribution, bucket.statusCodeDistribution());
        mergeAll(businessCodeDistribution, bucket.businessCodeDistribution());
        mergeAll(failureSourceDistribution, bucket.failureSourceDistribution());
        for (String requestId : bucket.sampleRequestIds()) {
            if (sampleRequestIds.size() < 20) {
                sampleRequestIds.add(requestId);
            }
        }
    }

    double errorRate() { return rate(errorCount, requestCount); }
    double rateLimitRate() { return rate(rateLimitCount, requestCount); }
    double authFailureRate() { return rate(authFailureCount, requestCount); }
    double timeoutRate() { return rate(timeoutCount, requestCount); }
    double error5xxRate() { return rate(error5xxCount, requestCount); }
    int avgLatencyMs() { return requestCount == 0 ? 0 : (int) Math.round((double) latencySum / requestCount); }
    int p95LatencyMs() { return percentile(0.95d); }

    Map<String, Object> metricsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("windowStartTime", windowStartTime);
        map.put("windowEndTime", windowEndTime);
        map.put("requestCount", requestCount);
        map.put("successCount", successCount);
        map.put("errorCount", errorCount);
        map.put("errorRate", errorRate());
        map.put("rateLimitCount", rateLimitCount);
        map.put("rateLimitRate", rateLimitRate());
        map.put("authFailureCount", authFailureCount);
        map.put("authFailureRate", authFailureRate());
        map.put("timeoutCount", timeoutCount);
        map.put("timeoutRate", timeoutRate());
        map.put("error5xxCount", error5xxCount);
        map.put("error5xxRate", error5xxRate());
        map.put("avgLatencyMs", avgLatencyMs());
        map.put("p95LatencyMs", p95LatencyMs());
        map.put("maxLatencyMs", maxLatencyMs);
        return map;
    }

    private int percentile(double quantile) {
        if (latencySamples.isEmpty()) {
            return 0;
        }
        List<Integer> sorted = new ArrayList<>(latencySamples);
        sorted.sort(Integer::compareTo);
        int index = (int) Math.ceil(quantile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private double rate(int value, int total) {
        return total <= 0 ? 0d : (double) value / total;
    }

    private void mergeAll(Map<String, Integer> target, Map<String, Integer> source) {
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    public LocalDateTime getWindowStartTime() { return windowStartTime; }
    public LocalDateTime getWindowEndTime() { return windowEndTime; }
    public int getRequestCount() { return requestCount; }
    public int getSuccessCount() { return successCount; }
    public int getErrorCount() { return errorCount; }
    public int getError4xxCount() { return error4xxCount; }
    public int getError5xxCount() { return error5xxCount; }
    public int getRateLimitCount() { return rateLimitCount; }
    public int getAuthFailureCount() { return authFailureCount; }
    public int getTimeoutCount() { return timeoutCount; }
    public int getP95LatencyMs() { return p95LatencyMs(); }
    public Map<String, Integer> getStatusCodeDistribution() { return statusCodeDistribution; }
    public Map<String, Integer> getBusinessCodeDistribution() { return businessCodeDistribution; }
    public Map<String, Integer> getFailureSourceDistribution() { return failureSourceDistribution; }
    public List<String> getSampleRequestIds() { return sampleRequestIds; }
}
