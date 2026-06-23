package com.apihub.agent.dev.llm;

public class LlmDiagnosisPrompt {

    private String systemPrompt;
    private String diagnosisPrompt;
    private String inputJson;
    private String outputSchemaJson;

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getDiagnosisPrompt() {
        return diagnosisPrompt;
    }

    public void setDiagnosisPrompt(String diagnosisPrompt) {
        this.diagnosisPrompt = diagnosisPrompt;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getOutputSchemaJson() {
        return outputSchemaJson;
    }

    public void setOutputSchemaJson(String outputSchemaJson) {
        this.outputSchemaJson = outputSchemaJson;
    }
}
