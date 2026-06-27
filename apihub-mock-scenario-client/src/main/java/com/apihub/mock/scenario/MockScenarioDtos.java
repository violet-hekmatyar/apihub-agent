package com.apihub.mock.scenario;

import java.util.List;
import java.util.Map;

class ScenarioStartRequest {
    public String profileCode;
    public String mode;
    public String targetGatewayBaseUrl;
    public Long randomSeed;
    public Double rpsScale;
    public Boolean includeTrafficSamples;
}

class ScenarioRunResponse {
    public String scenarioRunId;
    public String profileCode;
    public String mode;
    public String status;
    public String targetGatewayBaseUrl;
    public int durationSeconds;
    public int elapsedSeconds;
    public String currentPhaseCode;
    public int totalRequestCount;
    public int successCount;
    public int failCount;
    public Map<String, Integer> statusCodeDistribution;
    public Map<String, Integer> apiDistribution;
}

record ScenarioProfileView(String code, String name, String description, List<String> supportedModes,
                           int fastDurationSeconds, int normalDurationSeconds) {
}
