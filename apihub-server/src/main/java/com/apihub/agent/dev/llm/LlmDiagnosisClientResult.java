package com.apihub.agent.dev.llm;

import java.util.ArrayList;
import java.util.List;

public class LlmDiagnosisClientResult {

    private boolean success;
    private String rawContent;
    private String provider;
    private String model;
    private String requestId;
    private Long latencyMs;
    private String errorCode;
    private String errorMessage;
    private List<LlmDiagnosisAttempt> attempts = new ArrayList<>();

    public static LlmDiagnosisClientResult success(String provider, String model, String rawContent, String requestId, long latencyMs) {
        LlmDiagnosisClientResult result = new LlmDiagnosisClientResult();
        result.setSuccess(true);
        result.setProvider(provider);
        result.setModel(model);
        result.setRawContent(rawContent);
        result.setRequestId(requestId);
        result.setLatencyMs(latencyMs);
        return result;
    }

    public static LlmDiagnosisClientResult failure(String provider, String model, String errorCode, String errorMessage, long latencyMs) {
        LlmDiagnosisClientResult result = new LlmDiagnosisClientResult();
        result.setSuccess(false);
        result.setProvider(provider);
        result.setModel(model);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        result.setLatencyMs(latencyMs);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
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

    public List<LlmDiagnosisAttempt> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<LlmDiagnosisAttempt> attempts) {
        this.attempts = attempts == null ? new ArrayList<>() : attempts;
    }
}
