package com.apihub.mock.campus;

import java.util.LinkedHashMap;
import java.util.Map;

class MockCampusInvokeRequest {
    public String scenarioRunId;
    public String requestId;
    public String traceId;
    public String profileCode;
    public String mode;
    public String phaseCode;
    public String apiCode;
    public String callerAppCode;
    public String mockScenario;
    public Map<String, Object> payload = new LinkedHashMap<>();
}

class MockCampusResponse {
    public boolean success;
    public String apiCode;
    public String businessCode;
    public String message;
    public Map<String, Object> data = new LinkedHashMap<>();

    static MockCampusResponse of(boolean success, String apiCode, String businessCode, String message) {
        MockCampusResponse response = new MockCampusResponse();
        response.success = success;
        response.apiCode = apiCode;
        response.businessCode = businessCode;
        response.message = message;
        response.data.put("mock", true);
        return response;
    }
}
