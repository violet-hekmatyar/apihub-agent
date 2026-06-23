package com.apihub.agent.dev.llm;

import java.util.ArrayList;
import java.util.List;

public class LlmDiagnosisResult {

    private boolean success;
    private boolean fallbackUsed;
    private Long reportId;
    private LlmDiagnosisPrompt prompt;
    private String rawResponse;
    private LlmDiagnosisOutput output;
    private LlmDiagnosisValidationResult validation;
    private List<String> parseErrors = new ArrayList<>();
    private List<String> parseWarnings = new ArrayList<>();
    private String fallbackSummary;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
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
}
