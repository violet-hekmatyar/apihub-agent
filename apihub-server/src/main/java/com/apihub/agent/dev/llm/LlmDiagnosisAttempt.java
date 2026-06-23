package com.apihub.agent.dev.llm;

public class LlmDiagnosisAttempt {

    private String name;
    private boolean proxy;
    private boolean responseFormat;
    private Integer statusCode;
    private String errorCode;
    private String errorMessage;
    private long latencyMs;

    public LlmDiagnosisAttempt() {
    }

    public LlmDiagnosisAttempt(String name,
                               boolean proxy,
                               boolean responseFormat,
                               Integer statusCode,
                               String errorCode,
                               String errorMessage,
                               long latencyMs) {
        this.name = name;
        this.proxy = proxy;
        this.responseFormat = responseFormat;
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.latencyMs = latencyMs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isProxy() {
        return proxy;
    }

    public void setProxy(boolean proxy) {
        this.proxy = proxy;
    }

    public boolean isResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(boolean responseFormat) {
        this.responseFormat = responseFormat;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
