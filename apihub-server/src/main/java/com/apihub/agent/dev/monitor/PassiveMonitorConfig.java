package com.apihub.agent.dev.monitor;

import org.springframework.stereotype.Component;

@Component
public class PassiveMonitorConfig {

    private volatile boolean enabled = false;
    private volatile int bucketSeconds = 5;
    private volatile int shortWindowSeconds = 30;
    private volatile int baselineWindowSeconds = 300;
    private volatile int contextBeforeSeconds = 60;
    private volatile int cooldownSeconds = 120;
    private volatile int minRequestCount = 20;
    private volatile int minErrorCount = 3;
    private volatile double highErrorRateThreshold = 0.10d;
    private volatile double highRateLimitThreshold = 0.05d;
    private volatile double high5xxRateThreshold = 0.05d;
    private volatile double authFailureThreshold = 0.10d;
    private volatile int latencyThresholdMs = 1000;

    public void apply(PassiveMonitorConfigRequest request) {
        if (request == null) {
            return;
        }
        if (request.getEnabled() != null) {
            enabled = request.getEnabled();
        }
        bucketSeconds = positive(request.getBucketSeconds(), bucketSeconds);
        shortWindowSeconds = positive(request.getShortWindowSeconds(), shortWindowSeconds);
        baselineWindowSeconds = positive(request.getBaselineWindowSeconds(), baselineWindowSeconds);
        contextBeforeSeconds = positive(request.getContextBeforeSeconds(), contextBeforeSeconds);
        cooldownSeconds = positive(request.getCooldownSeconds(), cooldownSeconds);
        minRequestCount = positive(request.getMinRequestCount(), minRequestCount);
        minErrorCount = positive(request.getMinErrorCount(), minErrorCount);
        highErrorRateThreshold = positiveDouble(request.getHighErrorRateThreshold(), highErrorRateThreshold);
        highRateLimitThreshold = positiveDouble(request.getHighRateLimitThreshold(), highRateLimitThreshold);
        high5xxRateThreshold = positiveDouble(request.getHigh5xxRateThreshold(), high5xxRateThreshold);
        authFailureThreshold = positiveDouble(request.getAuthFailureThreshold(), authFailureThreshold);
        latencyThresholdMs = positive(request.getLatencyThresholdMs(), latencyThresholdMs);
    }

    public PassiveMonitorConfigView view() {
        PassiveMonitorConfigView view = new PassiveMonitorConfigView();
        view.setEnabled(enabled);
        view.setBucketSeconds(bucketSeconds);
        view.setShortWindowSeconds(shortWindowSeconds);
        view.setBaselineWindowSeconds(baselineWindowSeconds);
        view.setContextBeforeSeconds(contextBeforeSeconds);
        view.setCooldownSeconds(cooldownSeconds);
        view.setMinRequestCount(minRequestCount);
        view.setMinErrorCount(minErrorCount);
        view.setHighErrorRateThreshold(highErrorRateThreshold);
        view.setHighRateLimitThreshold(highRateLimitThreshold);
        view.setHigh5xxRateThreshold(high5xxRateThreshold);
        view.setAuthFailureThreshold(authFailureThreshold);
        view.setLatencyThresholdMs(latencyThresholdMs);
        return view;
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private double positiveDouble(Double value, double fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBucketSeconds() {
        return bucketSeconds;
    }

    public int getShortWindowSeconds() {
        return shortWindowSeconds;
    }

    public int getBaselineWindowSeconds() {
        return baselineWindowSeconds;
    }

    public int getContextBeforeSeconds() {
        return contextBeforeSeconds;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getMinRequestCount() {
        return minRequestCount;
    }

    public int getMinErrorCount() {
        return minErrorCount;
    }

    public double getHighErrorRateThreshold() {
        return highErrorRateThreshold;
    }

    public double getHighRateLimitThreshold() {
        return highRateLimitThreshold;
    }

    public double getHigh5xxRateThreshold() {
        return high5xxRateThreshold;
    }

    public double getAuthFailureThreshold() {
        return authFailureThreshold;
    }

    public int getLatencyThresholdMs() {
        return latencyThresholdMs;
    }
}
