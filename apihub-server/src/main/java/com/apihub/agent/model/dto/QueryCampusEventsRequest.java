package com.apihub.agent.model.dto;

public class QueryCampusEventsRequest {

    private String apiCode;
    private String startTime;
    private String endTime;
    private String eventType;
    private Boolean includeRelatedApis;
    private Integer limit;

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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Boolean getIncludeRelatedApis() {
        return includeRelatedApis;
    }

    public void setIncludeRelatedApis(Boolean includeRelatedApis) {
        this.includeRelatedApis = includeRelatedApis;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
