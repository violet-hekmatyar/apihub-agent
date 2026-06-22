package com.apihub.agent.model.vo;

public class StatsAggregateItemVO {

    private String apiCode;
    private String apiName;
    private String statTime;
    private long totalCount;
    private long successCount;
    private long failCount;
    private long error4xxCount;
    private long error5xxCount;
    private long rateLimitCount;
    private long rateLimitedCount;
    private int avgLatencyMs;
    private int p95LatencyMs;
    private int p99LatencyMs;
    private int maxLatencyMs;

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

    public String getStatTime() {
        return statTime;
    }

    public void setStatTime(String statTime) {
        this.statTime = statTime;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
    }

    public long getFailCount() {
        return failCount;
    }

    public void setFailCount(long failCount) {
        this.failCount = failCount;
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

    public long getRateLimitCount() {
        return rateLimitCount;
    }

    public void setRateLimitCount(long rateLimitCount) {
        this.rateLimitCount = rateLimitCount;
        this.rateLimitedCount = rateLimitCount;
    }

    public long getRateLimitedCount() {
        return rateLimitedCount;
    }

    public void setRateLimitedCount(long rateLimitedCount) {
        this.rateLimitedCount = rateLimitedCount;
        this.rateLimitCount = rateLimitedCount;
    }

    public int getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(int avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
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

    public int getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(int maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }
}
