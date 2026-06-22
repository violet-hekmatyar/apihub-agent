package com.apihub.agent.model.dto;

public class StatsAggregateRequest {

    private String startTime;
    private String endTime;
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
