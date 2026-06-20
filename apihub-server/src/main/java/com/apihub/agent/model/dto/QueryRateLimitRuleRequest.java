package com.apihub.agent.model.dto;

public class QueryRateLimitRuleRequest {

    private String apiCode;
    private Boolean includeInactive;

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public Boolean getIncludeInactive() {
        return includeInactive;
    }

    public void setIncludeInactive(Boolean includeInactive) {
        this.includeInactive = includeInactive;
    }
}
