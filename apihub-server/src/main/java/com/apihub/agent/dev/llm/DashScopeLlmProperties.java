package com.apihub.agent.dev.llm;

public class DashScopeLlmProperties {

    private String apiKey;
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String model = "qwen-plus";
    private int timeoutSeconds = 60;
    private double temperature = 0.1d;
    private boolean proxyEnabled = false;
    private String proxyHost;
    private int proxyPort = 0;
    private String proxyScheme = "http";
    private boolean proxyFallbackEnabled = true;
    private int directRetryCount = 2;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyScheme() {
        return proxyScheme;
    }

    public void setProxyScheme(String proxyScheme) {
        this.proxyScheme = proxyScheme;
    }

    public boolean isProxyFallbackEnabled() {
        return proxyFallbackEnabled;
    }

    public void setProxyFallbackEnabled(boolean proxyFallbackEnabled) {
        this.proxyFallbackEnabled = proxyFallbackEnabled;
    }

    public int getDirectRetryCount() {
        return directRetryCount;
    }

    public void setDirectRetryCount(int directRetryCount) {
        this.directRetryCount = directRetryCount;
    }
}
