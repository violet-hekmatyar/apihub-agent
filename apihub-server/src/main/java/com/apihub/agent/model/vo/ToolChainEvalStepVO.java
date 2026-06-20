package com.apihub.agent.model.vo;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolChainEvalStepVO {

    private int stepNo;
    private String toolName;
    private String purpose;
    private boolean success;
    private String summary;
    private String errorCode;
    private long latencyMs;
    private int evidenceCount;
    private Map<String, Object> keyData = new LinkedHashMap<>();

    public int getStepNo() {
        return stepNo;
    }

    public void setStepNo(int stepNo) {
        this.stepNo = stepNo;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(int evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public Map<String, Object> getKeyData() {
        return keyData;
    }

    public void setKeyData(Map<String, Object> keyData) {
        this.keyData = keyData;
    }
}
