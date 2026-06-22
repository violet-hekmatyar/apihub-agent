package com.apihub.agent.dev.llm;

import java.util.ArrayList;
import java.util.List;

public class LlmDiagnosisValidationResult {

    private boolean success;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public static LlmDiagnosisValidationResult ok() {
        LlmDiagnosisValidationResult result = new LlmDiagnosisValidationResult();
        result.setSuccess(true);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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

    public void error(String message) {
        errors.add(message);
        success = false;
    }

    public void warn(String message) {
        warnings.add(message);
    }
}
