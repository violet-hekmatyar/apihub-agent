package com.apihub.agent.model.vo;

import java.util.ArrayList;
import java.util.List;

public class StatsAggregateResponseVO {

    private String startTime;
    private String endTime;
    private String scenarioRunId;
    private String apiCode;
    private boolean forceRebuild;
    private int affectedApiCount;
    private int aggregatedRows;
    private int deletedRows;
    private long totalLogs;
    private long scenarioMatchedLogs;
    private long latencyMs;
    private List<String> statTimeRange = new ArrayList<>();
    private List<StatsAggregateItemVO> items = new ArrayList<>();

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getScenarioRunId() {
        return scenarioRunId;
    }

    public void setScenarioRunId(String scenarioRunId) {
        this.scenarioRunId = scenarioRunId;
    }

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public boolean isForceRebuild() {
        return forceRebuild;
    }

    public void setForceRebuild(boolean forceRebuild) {
        this.forceRebuild = forceRebuild;
    }

    public int getAffectedApiCount() {
        return affectedApiCount;
    }

    public void setAffectedApiCount(int affectedApiCount) {
        this.affectedApiCount = affectedApiCount;
    }

    public int getAggregatedRows() {
        return aggregatedRows;
    }

    public void setAggregatedRows(int aggregatedRows) {
        this.aggregatedRows = aggregatedRows;
    }

    public int getDeletedRows() {
        return deletedRows;
    }

    public void setDeletedRows(int deletedRows) {
        this.deletedRows = deletedRows;
    }

    public long getTotalLogs() {
        return totalLogs;
    }

    public void setTotalLogs(long totalLogs) {
        this.totalLogs = totalLogs;
    }

    public long getScenarioMatchedLogs() {
        return scenarioMatchedLogs;
    }

    public void setScenarioMatchedLogs(long scenarioMatchedLogs) {
        this.scenarioMatchedLogs = scenarioMatchedLogs;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public List<String> getStatTimeRange() {
        return statTimeRange;
    }

    public void setStatTimeRange(List<String> statTimeRange) {
        this.statTimeRange = statTimeRange;
    }

    public List<StatsAggregateItemVO> getItems() {
        return items;
    }

    public void setItems(List<StatsAggregateItemVO> items) {
        this.items = items;
    }
}
