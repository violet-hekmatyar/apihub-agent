package com.apihub.agent.model.vo;

import java.util.ArrayList;
import java.util.List;

public class AgentRunResultVO {

    private boolean success;
    private String runId;
    private Long sessionId;
    private Long reportId;
    private String scenarioCode;
    private String scenarioName;
    private String apiCode;
    private String apiName;
    private String question;
    private String startTime;
    private String endTime;
    private String status;
    private List<ToolChainEvalStepVO> steps = new ArrayList<>();
    private List<Object> evidenceItems = new ArrayList<>();
    private String riskLevel;
    private List<String> riskReasons = new ArrayList<>();
    private String finalAnswer;
    private String errorCode;
    private String errorMessage;
    private String traceId;
    private long latencyMs;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public String getScenarioCode() {
        return scenarioCode;
    }

    public void setScenarioCode(String scenarioCode) {
        this.scenarioCode = scenarioCode;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ToolChainEvalStepVO> getSteps() {
        return steps;
    }

    public void setSteps(List<ToolChainEvalStepVO> steps) {
        this.steps = steps;
    }

    public List<Object> getEvidenceItems() {
        return evidenceItems;
    }

    public void setEvidenceItems(List<Object> evidenceItems) {
        this.evidenceItems = evidenceItems;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<String> getRiskReasons() {
        return riskReasons;
    }

    public void setRiskReasons(List<String> riskReasons) {
        this.riskReasons = riskReasons;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
