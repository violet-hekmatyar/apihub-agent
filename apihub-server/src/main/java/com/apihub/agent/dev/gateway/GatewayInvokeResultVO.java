package com.apihub.agent.dev.gateway;

public class GatewayInvokeResultVO {

    private String apiCode;
    private String appCode;
    private String mockScenario;
    private boolean success;
    private int upstreamStatus;
    private Integer upstreamCode;
    private String upstreamMessage;
    private String errorCode;
    private long latencyMs;
    private Long gatewayLogId;
    private Object upstreamData;
    private String traceId;
    private String requestId;

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getMockScenario() {
        return mockScenario;
    }

    public void setMockScenario(String mockScenario) {
        this.mockScenario = mockScenario;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    public void setUpstreamStatus(int upstreamStatus) {
        this.upstreamStatus = upstreamStatus;
    }

    public Integer getUpstreamCode() {
        return upstreamCode;
    }

    public void setUpstreamCode(Integer upstreamCode) {
        this.upstreamCode = upstreamCode;
    }

    public String getUpstreamMessage() {
        return upstreamMessage;
    }

    public void setUpstreamMessage(String upstreamMessage) {
        this.upstreamMessage = upstreamMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Long getGatewayLogId() {
        return gatewayLogId;
    }

    public void setGatewayLogId(Long gatewayLogId) {
        this.gatewayLogId = gatewayLogId;
    }

    public Object getUpstreamData() {
        return upstreamData;
    }

    public void setUpstreamData(Object upstreamData) {
        this.upstreamData = upstreamData;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
