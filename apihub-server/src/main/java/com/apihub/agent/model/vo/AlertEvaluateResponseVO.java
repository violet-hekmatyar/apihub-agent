package com.apihub.agent.model.vo;

import java.util.ArrayList;
import java.util.List;

public class AlertEvaluateResponseVO {

    private String startTime;
    private String endTime;
    private String mode;
    private int windowSeconds;
    private String scenarioRunId;
    private String apiCode;
    private int evaluatedApiCount;
    private int evaluatedWindowCount;
    private int createdAlertCount;
    private int updatedAlertCount;
    private int deletedOldAlertCount;
    private long sourceRowCount;
    private long latencyMs;
    private List<AlertEvaluateItemVO> items = new ArrayList<>();

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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
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

    public int getEvaluatedApiCount() {
        return evaluatedApiCount;
    }

    public void setEvaluatedApiCount(int evaluatedApiCount) {
        this.evaluatedApiCount = evaluatedApiCount;
    }

    public int getEvaluatedWindowCount() {
        return evaluatedWindowCount;
    }

    public void setEvaluatedWindowCount(int evaluatedWindowCount) {
        this.evaluatedWindowCount = evaluatedWindowCount;
    }

    public int getCreatedAlertCount() {
        return createdAlertCount;
    }

    public void setCreatedAlertCount(int createdAlertCount) {
        this.createdAlertCount = createdAlertCount;
    }

    public int getUpdatedAlertCount() {
        return updatedAlertCount;
    }

    public void setUpdatedAlertCount(int updatedAlertCount) {
        this.updatedAlertCount = updatedAlertCount;
    }

    public int getDeletedOldAlertCount() {
        return deletedOldAlertCount;
    }

    public void setDeletedOldAlertCount(int deletedOldAlertCount) {
        this.deletedOldAlertCount = deletedOldAlertCount;
    }

    public long getSourceRowCount() {
        return sourceRowCount;
    }

    public void setSourceRowCount(long sourceRowCount) {
        this.sourceRowCount = sourceRowCount;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public List<AlertEvaluateItemVO> getItems() {
        return items;
    }

    public void setItems(List<AlertEvaluateItemVO> items) {
        this.items = items;
    }
}
