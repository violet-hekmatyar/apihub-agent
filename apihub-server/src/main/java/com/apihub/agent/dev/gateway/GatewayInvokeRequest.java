package com.apihub.agent.dev.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

public class GatewayInvokeRequest {

    private String apiCode;
    private String appCode;
    private String mockScenario;
    private Map<String, Object> queryParams = new LinkedHashMap<>();
    private Map<String, Object> body = new LinkedHashMap<>();
    private Integer timeoutMs;
    private ClientInfo clientInfo;
    private ScenarioContext scenarioContext;

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

    public Map<String, Object> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, Object> queryParams) {
        this.queryParams = queryParams;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setBody(Map<String, Object> body) {
        this.body = body;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(ClientInfo clientInfo) {
        this.clientInfo = clientInfo;
    }

    public ScenarioContext getScenarioContext() {
        return scenarioContext;
    }

    public void setScenarioContext(ScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
    }

    public static class ClientInfo {
        private String clientIp;
        private String userAgent;

        public String getClientIp() {
            return clientIp;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class ScenarioContext {
        private String scenarioRunId;
        private String scenarioId;
        private String scenarioKey;
        private String phase;
        private Integer sequenceNo;

        public String getScenarioRunId() {
            return scenarioRunId;
        }

        public void setScenarioRunId(String scenarioRunId) {
            this.scenarioRunId = scenarioRunId;
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public void setScenarioId(String scenarioId) {
            this.scenarioId = scenarioId;
        }

        public String getScenarioKey() {
            return scenarioKey;
        }

        public void setScenarioKey(String scenarioKey) {
            this.scenarioKey = scenarioKey;
        }

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public Integer getSequenceNo() {
            return sequenceNo;
        }

        public void setSequenceNo(Integer sequenceNo) {
            this.sequenceNo = sequenceNo;
        }
    }
}
