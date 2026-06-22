package com.apihub.agent.model.vo;

import java.util.LinkedHashMap;
import java.util.Map;

public class AlertEvaluateItemVO {

    private String eventCode;
    private String apiCode;
    private String apiName;
    private String alertType;
    private String severity;
    private String title;
    private String description;
    private String windowStart;
    private String windowEnd;
    private long totalCount;
    private long failCount;
    private long rateLimitCount;
    private long error4xxCount;
    private long error5xxCount;
    private int p95LatencyMs;
    private int p99LatencyMs;
    private Map<String, Object> extraInfo = new LinkedHashMap<>();

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(String windowStart) {
        this.windowStart = windowStart;
    }

    public String getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(String windowEnd) {
        this.windowEnd = windowEnd;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getFailCount() {
        return failCount;
    }

    public void setFailCount(long failCount) {
        this.failCount = failCount;
    }

    public long getRateLimitCount() {
        return rateLimitCount;
    }

    public void setRateLimitCount(long rateLimitCount) {
        this.rateLimitCount = rateLimitCount;
    }

    public long getError4xxCount() {
        return error4xxCount;
    }

    public void setError4xxCount(long error4xxCount) {
        this.error4xxCount = error4xxCount;
    }

    public long getError5xxCount() {
        return error5xxCount;
    }

    public void setError5xxCount(long error5xxCount) {
        this.error5xxCount = error5xxCount;
    }

    public int getP95LatencyMs() {
        return p95LatencyMs;
    }

    public void setP95LatencyMs(int p95LatencyMs) {
        this.p95LatencyMs = p95LatencyMs;
    }

    public int getP99LatencyMs() {
        return p99LatencyMs;
    }

    public void setP99LatencyMs(int p99LatencyMs) {
        this.p99LatencyMs = p99LatencyMs;
    }

    public Map<String, Object> getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(Map<String, Object> extraInfo) {
        this.extraInfo = extraInfo;
    }
}
