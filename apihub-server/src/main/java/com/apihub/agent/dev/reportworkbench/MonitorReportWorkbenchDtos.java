package com.apihub.agent.dev.reportworkbench;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MonitorReportWorkbenchDtos {

    private MonitorReportWorkbenchDtos() {
    }

    public static class MonitorEventReportRequest {
        private String monitorEventId;
        private Boolean includeLlm;
        private Boolean includePrompt;
        private Boolean forceRebuild;

        public String getMonitorEventId() {
            return monitorEventId;
        }

        public void setMonitorEventId(String monitorEventId) {
            this.monitorEventId = monitorEventId;
        }

        public Boolean getIncludeLlm() {
            return includeLlm;
        }

        public void setIncludeLlm(Boolean includeLlm) {
            this.includeLlm = includeLlm;
        }

        public Boolean getIncludePrompt() {
            return includePrompt;
        }

        public void setIncludePrompt(Boolean includePrompt) {
            this.includePrompt = includePrompt;
        }

        public Boolean getForceRebuild() {
            return forceRebuild;
        }

        public void setForceRebuild(Boolean forceRebuild) {
            this.forceRebuild = forceRebuild;
        }
    }

    public static class LatestAnomalyRequest {
        private Boolean includeLlm;
        private Boolean includePrompt;
        private Boolean forceRebuild;

        public Boolean getIncludeLlm() {
            return includeLlm;
        }

        public void setIncludeLlm(Boolean includeLlm) {
            this.includeLlm = includeLlm;
        }

        public Boolean getIncludePrompt() {
            return includePrompt;
        }

        public void setIncludePrompt(Boolean includePrompt) {
            this.includePrompt = includePrompt;
        }

        public Boolean getForceRebuild() {
            return forceRebuild;
        }

        public void setForceRebuild(Boolean forceRebuild) {
            this.forceRebuild = forceRebuild;
        }
    }

    public static class RangeReportRequest {
        private String range;
        private String startTime;
        private String endTime;
        private String apiCode;
        private Boolean includeLlm;
        private Boolean includePrompt;
        private Boolean includeNormalSummary;
        private Boolean forceRebuild;

        public String getRange() {
            return range;
        }

        public void setRange(String range) {
            this.range = range;
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

        public String getApiCode() {
            return apiCode;
        }

        public void setApiCode(String apiCode) {
            this.apiCode = apiCode;
        }

        public Boolean getIncludeLlm() {
            return includeLlm;
        }

        public void setIncludeLlm(Boolean includeLlm) {
            this.includeLlm = includeLlm;
        }

        public Boolean getIncludePrompt() {
            return includePrompt;
        }

        public void setIncludePrompt(Boolean includePrompt) {
            this.includePrompt = includePrompt;
        }

        public Boolean getIncludeNormalSummary() {
            return includeNormalSummary;
        }

        public void setIncludeNormalSummary(Boolean includeNormalSummary) {
            this.includeNormalSummary = includeNormalSummary;
        }

        public Boolean getForceRebuild() {
            return forceRebuild;
        }

        public void setForceRebuild(Boolean forceRebuild) {
            this.forceRebuild = forceRebuild;
        }
    }

    public static class WorkbenchReportResponse {
        private String status = "OK";
        private String message;
        private Long reportId;
        private String reportCode;
        private String reportType;
        private String htmlUrl;
        private String llmStatus;
        private String llmModel;
        private String displayStatus;
        private String statusLabel;
        private String colorLevel;
        private String summary;
        private String monitorEventId;
        private String apiCode;
        private String startTime;
        private String endTime;
        private Map<String, Object> analysisContextJson = new LinkedHashMap<>();
        private Map<String, Object> htmlRenderableJson = new LinkedHashMap<>();
        private Object llmResult;
        private List<String> validationWarnings;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Long getReportId() {
            return reportId;
        }

        public void setReportId(Long reportId) {
            this.reportId = reportId;
        }

        public String getReportCode() {
            return reportCode;
        }

        public void setReportCode(String reportCode) {
            this.reportCode = reportCode;
        }

        public String getReportType() {
            return reportType;
        }

        public void setReportType(String reportType) {
            this.reportType = reportType;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }

        public void setHtmlUrl(String htmlUrl) {
            this.htmlUrl = htmlUrl;
        }

        public String getLlmStatus() {
            return llmStatus;
        }

        public void setLlmStatus(String llmStatus) {
            this.llmStatus = llmStatus;
        }

        public String getLlmModel() {
            return llmModel;
        }

        public void setLlmModel(String llmModel) {
            this.llmModel = llmModel;
        }

        public String getDisplayStatus() {
            return displayStatus;
        }

        public void setDisplayStatus(String displayStatus) {
            this.displayStatus = displayStatus;
        }

        public String getStatusLabel() {
            return statusLabel;
        }

        public void setStatusLabel(String statusLabel) {
            this.statusLabel = statusLabel;
        }

        public String getColorLevel() {
            return colorLevel;
        }

        public void setColorLevel(String colorLevel) {
            this.colorLevel = colorLevel;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getMonitorEventId() {
            return monitorEventId;
        }

        public void setMonitorEventId(String monitorEventId) {
            this.monitorEventId = monitorEventId;
        }

        public String getApiCode() {
            return apiCode;
        }

        public void setApiCode(String apiCode) {
            this.apiCode = apiCode;
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

        public Map<String, Object> getAnalysisContextJson() {
            return analysisContextJson;
        }

        public void setAnalysisContextJson(Map<String, Object> analysisContextJson) {
            this.analysisContextJson = analysisContextJson;
        }

        public Map<String, Object> getHtmlRenderableJson() {
            return htmlRenderableJson;
        }

        public void setHtmlRenderableJson(Map<String, Object> htmlRenderableJson) {
            this.htmlRenderableJson = htmlRenderableJson;
        }

        public Object getLlmResult() {
            return llmResult;
        }

        public void setLlmResult(Object llmResult) {
            this.llmResult = llmResult;
        }

        public List<String> getValidationWarnings() {
            return validationWarnings;
        }

        public void setValidationWarnings(List<String> validationWarnings) {
            this.validationWarnings = validationWarnings;
        }
    }
}
