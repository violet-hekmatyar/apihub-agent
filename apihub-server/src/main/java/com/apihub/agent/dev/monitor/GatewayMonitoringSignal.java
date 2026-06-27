package com.apihub.agent.dev.monitor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class GatewayMonitoringSignal {
    private Long gatewayLogId;
    private String traceId;
    private String requestId;
    private String scenarioRunId;
    private String phaseCode;
    private Long apiId;
    private String apiCode;
    private Long appId;
    private String callerAppCode;
    private String mockScenario;
    private int httpStatus;
    private Integer businessCode;
    private String errorCode;
    private long latencyMs;
    private String failureSource;
    private LocalDateTime requestTime;
    private Map<String, Object> extraInfo = new LinkedHashMap<>();

    public Long getGatewayLogId() { return gatewayLogId; }
    public void setGatewayLogId(Long gatewayLogId) { this.gatewayLogId = gatewayLogId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getScenarioRunId() { return scenarioRunId; }
    public void setScenarioRunId(String scenarioRunId) { this.scenarioRunId = scenarioRunId; }
    public String getPhaseCode() { return phaseCode; }
    public void setPhaseCode(String phaseCode) { this.phaseCode = phaseCode; }
    public Long getApiId() { return apiId; }
    public void setApiId(Long apiId) { this.apiId = apiId; }
    public String getApiCode() { return apiCode; }
    public void setApiCode(String apiCode) { this.apiCode = apiCode; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public String getCallerAppCode() { return callerAppCode; }
    public void setCallerAppCode(String callerAppCode) { this.callerAppCode = callerAppCode; }
    public String getMockScenario() { return mockScenario; }
    public void setMockScenario(String mockScenario) { this.mockScenario = mockScenario; }
    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }
    public Integer getBusinessCode() { return businessCode; }
    public void setBusinessCode(Integer businessCode) { this.businessCode = businessCode; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getFailureSource() { return failureSource; }
    public void setFailureSource(String failureSource) { this.failureSource = failureSource; }
    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }
    public Map<String, Object> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, Object> extraInfo) { this.extraInfo = extraInfo; }
}
