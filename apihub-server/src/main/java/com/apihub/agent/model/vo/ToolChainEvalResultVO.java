package com.apihub.agent.model.vo;

import java.util.ArrayList;
import java.util.List;

public class ToolChainEvalResultVO {

    private boolean success;
    private String scenarioCode;
    private String scenarioName;
    private String apiCode;
    private String apiName;
    private String startTime;
    private String endTime;
    private int stepCount;
    private int passedStepCount;
    private int failedStepCount;
    private List<ToolChainEvalStepVO> steps = new ArrayList<>();
    private List<Object> mergedEvidenceItems = new ArrayList<>();
    private String riskLevel;
    private List<String> riskReasons = new ArrayList<>();
    private String templateConclusion;
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

    public int getStepCount() {
        return stepCount;
    }

    public void setStepCount(int stepCount) {
        this.stepCount = stepCount;
    }

    public int getPassedStepCount() {
        return passedStepCount;
    }

    public void setPassedStepCount(int passedStepCount) {
        this.passedStepCount = passedStepCount;
    }

    public int getFailedStepCount() {
        return failedStepCount;
    }

    public void setFailedStepCount(int failedStepCount) {
        this.failedStepCount = failedStepCount;
    }

    public List<ToolChainEvalStepVO> getSteps() {
        return steps;
    }

    public void setSteps(List<ToolChainEvalStepVO> steps) {
        this.steps = steps;
    }

    public List<Object> getMergedEvidenceItems() {
        return mergedEvidenceItems;
    }

    public void setMergedEvidenceItems(List<Object> mergedEvidenceItems) {
        this.mergedEvidenceItems = mergedEvidenceItems;
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

    public String getTemplateConclusion() {
        return templateConclusion;
    }

    public void setTemplateConclusion(String templateConclusion) {
        this.templateConclusion = templateConclusion;
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
