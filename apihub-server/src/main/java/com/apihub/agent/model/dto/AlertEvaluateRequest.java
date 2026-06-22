package com.apihub.agent.model.dto;

public class AlertEvaluateRequest {

    private String startTime;
    private String endTime;
    private String mode;
    private Integer windowSeconds;
    private String scenarioRunId;
    private String apiCode;
    private Boolean forceRebuild;

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

    public Integer getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(Integer windowSeconds) {
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

    public Boolean getForceRebuild() {
        return forceRebuild;
    }

    public void setForceRebuild(Boolean forceRebuild) {
        this.forceRebuild = forceRebuild;
    }
}
