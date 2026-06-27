package com.apihub.agent.dev.monitor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PassiveMonitorDtos {
    private PassiveMonitorDtos() {
    }
}

class PassiveMonitorConfigRequest {
    private Boolean enabled;
    private Integer bucketSeconds;
    private Integer shortWindowSeconds;
    private Integer baselineWindowSeconds;
    private Integer contextBeforeSeconds;
    private Integer cooldownSeconds;
    private Integer minRequestCount;
    private Integer minErrorCount;
    private Double highErrorRateThreshold;
    private Double highRateLimitThreshold;
    private Double high5xxRateThreshold;
    private Double authFailureThreshold;
    private Integer latencyThresholdMs;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getBucketSeconds() { return bucketSeconds; }
    public void setBucketSeconds(Integer bucketSeconds) { this.bucketSeconds = bucketSeconds; }
    public Integer getShortWindowSeconds() { return shortWindowSeconds; }
    public void setShortWindowSeconds(Integer shortWindowSeconds) { this.shortWindowSeconds = shortWindowSeconds; }
    public Integer getBaselineWindowSeconds() { return baselineWindowSeconds; }
    public void setBaselineWindowSeconds(Integer baselineWindowSeconds) { this.baselineWindowSeconds = baselineWindowSeconds; }
    public Integer getContextBeforeSeconds() { return contextBeforeSeconds; }
    public void setContextBeforeSeconds(Integer contextBeforeSeconds) { this.contextBeforeSeconds = contextBeforeSeconds; }
    public Integer getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(Integer cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public Integer getMinRequestCount() { return minRequestCount; }
    public void setMinRequestCount(Integer minRequestCount) { this.minRequestCount = minRequestCount; }
    public Integer getMinErrorCount() { return minErrorCount; }
    public void setMinErrorCount(Integer minErrorCount) { this.minErrorCount = minErrorCount; }
    public Double getHighErrorRateThreshold() { return highErrorRateThreshold; }
    public void setHighErrorRateThreshold(Double highErrorRateThreshold) { this.highErrorRateThreshold = highErrorRateThreshold; }
    public Double getHighRateLimitThreshold() { return highRateLimitThreshold; }
    public void setHighRateLimitThreshold(Double highRateLimitThreshold) { this.highRateLimitThreshold = highRateLimitThreshold; }
    public Double getHigh5xxRateThreshold() { return high5xxRateThreshold; }
    public void setHigh5xxRateThreshold(Double high5xxRateThreshold) { this.high5xxRateThreshold = high5xxRateThreshold; }
    public Double getAuthFailureThreshold() { return authFailureThreshold; }
    public void setAuthFailureThreshold(Double authFailureThreshold) { this.authFailureThreshold = authFailureThreshold; }
    public Integer getLatencyThresholdMs() { return latencyThresholdMs; }
    public void setLatencyThresholdMs(Integer latencyThresholdMs) { this.latencyThresholdMs = latencyThresholdMs; }
}

class PassiveMonitorConfigView extends PassiveMonitorConfigRequest {
}

class PassiveMonitorStatusVO {
    private boolean enabled;
    private String status;
    private PassiveMonitorConfigView config;
    private int activeEventCount;
    private int cooldownEventCount;
    private int bucketKeyCount;
    private String lastSignalTime;
    private String lastEventTime;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public PassiveMonitorConfigView getConfig() { return config; }
    public void setConfig(PassiveMonitorConfigView config) { this.config = config; }
    public int getActiveEventCount() { return activeEventCount; }
    public void setActiveEventCount(int activeEventCount) { this.activeEventCount = activeEventCount; }
    public int getCooldownEventCount() { return cooldownEventCount; }
    public void setCooldownEventCount(int cooldownEventCount) { this.cooldownEventCount = cooldownEventCount; }
    public int getBucketKeyCount() { return bucketKeyCount; }
    public void setBucketKeyCount(int bucketKeyCount) { this.bucketKeyCount = bucketKeyCount; }
    public String getLastSignalTime() { return lastSignalTime; }
    public void setLastSignalTime(String lastSignalTime) { this.lastSignalTime = lastSignalTime; }
    public String getLastEventTime() { return lastEventTime; }
    public void setLastEventTime(String lastEventTime) { this.lastEventTime = lastEventTime; }
}

class PassiveMonitorEventQuery {
    private String range;
    private String startTime;
    private String endTime;
    private String apiCode;
    private String alertType;
    private String riskLevel;
    private String eventStatus;
    private String callerAppCode;
    private Integer limit;

    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getApiCode() { return apiCode; }
    public void setApiCode(String apiCode) { this.apiCode = apiCode; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getEventStatus() { return eventStatus; }
    public void setEventStatus(String eventStatus) { this.eventStatus = eventStatus; }
    public String getCallerAppCode() { return callerAppCode; }
    public void setCallerAppCode(String callerAppCode) { this.callerAppCode = callerAppCode; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}

class PassiveMonitorEventVO {
    private Map<String, Object> event = new LinkedHashMap<>();
    private List<Map<String, Object>> snapshots = List.of();
    private Map<String, Object> alertEvent;

    public Map<String, Object> getEvent() { return event; }
    public void setEvent(Map<String, Object> event) { this.event = event; }
    public List<Map<String, Object>> getSnapshots() { return snapshots; }
    public void setSnapshots(List<Map<String, Object>> snapshots) { this.snapshots = snapshots; }
    public Map<String, Object> getAlertEvent() { return alertEvent; }
    public void setAlertEvent(Map<String, Object> alertEvent) { this.alertEvent = alertEvent; }
}
