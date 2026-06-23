package com.apihub.agent.dev.llm;

import java.util.ArrayList;
import java.util.List;

public class LlmDiagnosisResult {

    private boolean success;
    private String provider;
    private boolean fallbackUsed;
    private String fallbackReason;
    private Long reportId;
    private LlmDiagnosisPrompt prompt;
    private String rawResponse;
    private LlmDiagnosisOutput output;
    private LlmDiagnosisValidationResult validation;
    private List<String> parseErrors = new ArrayList<>();
    private List<String> parseWarnings = new ArrayList<>();
    private String fallbackSummary;
    private String model;
    private String requestId;
    private Long latencyMs;
    private String clientErrorCode;
    private String clientErrorMessage;
    private List<LlmDiagnosisAttempt> attempts = new ArrayList<>();

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public LlmDiagnosisPrompt getPrompt() {
        return prompt;
    }

    public void setPrompt(LlmDiagnosisPrompt prompt) {
        this.prompt = prompt;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public LlmDiagnosisOutput getOutput() {
        return output;
    }

    public void setOutput(LlmDiagnosisOutput output) {
        this.output = output;
    }

    public LlmDiagnosisValidationResult getValidation() {
        return validation;
    }

    public void setValidation(LlmDiagnosisValidationResult validation) {
        this.validation = validation;
    }

    public List<String> getParseErrors() {
        return parseErrors;
    }

    public void setParseErrors(List<String> parseErrors) {
        this.parseErrors = parseErrors;
    }

    public List<String> getParseWarnings() {
        return parseWarnings;
    }

    public void setParseWarnings(List<String> parseWarnings) {
        this.parseWarnings = parseWarnings;
    }

    public String getFallbackSummary() {
        return fallbackSummary;
    }

    public void setFallbackSummary(String fallbackSummary) {
        this.fallbackSummary = fallbackSummary;
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

    public String getClientErrorCode() {
        return clientErrorCode;
    }

    public void setClientErrorCode(String clientErrorCode) {
        this.clientErrorCode = clientErrorCode;
    }

    public String getClientErrorMessage() {
        return clientErrorMessage;
    }

    public void setClientErrorMessage(String clientErrorMessage) {
        this.clientErrorMessage = clientErrorMessage;
    }

    public List<LlmDiagnosisAttempt> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<LlmDiagnosisAttempt> attempts) {
        this.attempts = attempts == null ? new ArrayList<>() : attempts;
    }
}
