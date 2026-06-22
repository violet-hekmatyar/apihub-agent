package com.apihub.agent.model.vo;

import java.util.ArrayList;
import java.util.List;

public class AgentDiagnoseResponseVO {

    private Long reportId;
    private Long sessionId;
    private String traceId;
    private String apiCode;
    private String apiName;
    private String startTime;
    private String endTime;
    private String scenarioRunId;
    private Long alertId;
    private String diagnosisMode;
    private String status;
    private String riskLevel;
    private String summary;
    private String rootCause;
    private String recommendation;
    private int evidenceCount;
    private int toolCallCount;
    private long latencyMs;
    private List<AgentEvidenceItemVO> items = new ArrayList<>();

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(int evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(int toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public List<AgentEvidenceItemVO> getItems() {
        return items;
    }

    public void setItems(List<AgentEvidenceItemVO> items) {
        this.items = items;
    }
}
