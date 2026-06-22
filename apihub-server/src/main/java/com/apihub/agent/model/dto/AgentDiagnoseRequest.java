package com.apihub.agent.model.dto;

public class AgentDiagnoseRequest {

    private String apiCode;
    private String startTime;
    private String endTime;
    private String scenarioRunId;
    private Long alertId;
    private String diagnosisMode;
    private Boolean forceRebuild;

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

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

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public String getDiagnosisMode() {
        return diagnosisMode;
    }

    public void setDiagnosisMode(String diagnosisMode) {
        this.diagnosisMode = diagnosisMode;
    }

    public Boolean getForceRebuild() {
        return forceRebuild;
    }

    public void setForceRebuild(Boolean forceRebuild) {
        this.forceRebuild = forceRebuild;
    }
}
