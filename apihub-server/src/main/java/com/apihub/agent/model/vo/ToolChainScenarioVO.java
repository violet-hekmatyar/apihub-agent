package com.apihub.agent.model.vo;

import java.util.ArrayList;
import java.util.List;

public class ToolChainScenarioVO {

    private String scenarioCode;
    private String scenarioName;
    private String defaultApiCode;
    private String purpose;
    private List<String> toolSequence = new ArrayList<>();

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

    public String getDefaultApiCode() {
        return defaultApiCode;
    }

    public void setDefaultApiCode(String defaultApiCode) {
        this.defaultApiCode = defaultApiCode;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public List<String> getToolSequence() {
        return toolSequence;
    }

    public void setToolSequence(List<String> toolSequence) {
        this.toolSequence = toolSequence;
    }
}
