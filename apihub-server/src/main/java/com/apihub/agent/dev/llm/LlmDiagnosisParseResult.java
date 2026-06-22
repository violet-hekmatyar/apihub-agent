package com.apihub.agent.dev.llm;

import java.util.ArrayList;
import java.util.List;

public class LlmDiagnosisParseResult {

    private boolean success;
    private LlmDiagnosisOutput output;
    private String rawJson;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public LlmDiagnosisOutput getOutput() {
        return output;
    }

    public void setOutput(LlmDiagnosisOutput output) {
        this.output = output;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
