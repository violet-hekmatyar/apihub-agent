package com.apihub.agent.dev.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LlmDiagnosisInput {

    private Task task = new Task();
    private DeterministicDiagnosis deterministicDiagnosis = new DeterministicDiagnosis();
    private List<LlmToolSummary> toolSummaries = new ArrayList<>();
    private Map<String, List<LlmEvidenceItem>> evidenceGroups = new LinkedHashMap<>();
    private List<String> constraints = new ArrayList<>();

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public DeterministicDiagnosis getDeterministicDiagnosis() {
        return deterministicDiagnosis;
    }

    public void setDeterministicDiagnosis(DeterministicDiagnosis deterministicDiagnosis) {
        this.deterministicDiagnosis = deterministicDiagnosis;
    }

    public List<LlmToolSummary> getToolSummaries() {
        return toolSummaries;
    }

    public void setToolSummaries(List<LlmToolSummary> toolSummaries) {
        this.toolSummaries = toolSummaries;
    }

    public Map<String, List<LlmEvidenceItem>> getEvidenceGroups() {
        return evidenceGroups;
    }

    public void setEvidenceGroups(Map<String, List<LlmEvidenceItem>> evidenceGroups) {
        this.evidenceGroups = evidenceGroups;
    }

    public List<String> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<String> constraints) {
        this.constraints = constraints;
    }

    public static class Task {
        private String question;
        private String apiCode;
        private String apiName;
        private String startTime;
        private String endTime;
        private String scenarioRunId;
        private String scenarioType;
        private String environment;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
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

        public String getScenarioRunId() {
            return scenarioRunId;
        }

        public void setScenarioRunId(String scenarioRunId) {
            this.scenarioRunId = scenarioRunId;
        }

        public String getScenarioType() {
            return scenarioType;
        }

        public void setScenarioType(String scenarioType) {
            this.scenarioType = scenarioType;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }
    }

    public static class DeterministicDiagnosis {
        private Long reportId;
        private String riskLevel;
        private String summary;
        private String rootCause;
        private String recommendation;

        public Long getReportId() {
            return reportId;
        }

        public void setReportId(Long reportId) {
            this.reportId = reportId;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getRootCause() {
            return rootCause;
        }

        public void setRootCause(String rootCause) {
            this.rootCause = rootCause;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }
    }
}
