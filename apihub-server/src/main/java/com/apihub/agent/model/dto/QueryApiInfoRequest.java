package com.apihub.agent.model.dto;

public class QueryApiInfoRequest {

    private String apiCode;
    private Boolean includeRateLimit;
    private Boolean includeConsumerApps;

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public Boolean getIncludeRateLimit() {
        return includeRateLimit;
    }

    public void setIncludeRateLimit(Boolean includeRateLimit) {
        this.includeRateLimit = includeRateLimit;
    }

    public Boolean getIncludeConsumerApps() {
        return includeConsumerApps;
    }

    public void setIncludeConsumerApps(Boolean includeConsumerApps) {
        this.includeConsumerApps = includeConsumerApps;
    }
}
