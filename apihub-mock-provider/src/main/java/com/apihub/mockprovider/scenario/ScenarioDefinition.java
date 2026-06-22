package com.apihub.mockprovider.scenario;

import java.util.List;

record ScenarioDefinition(String scenarioId, List<ApiWeight> apiWeights) {

    record ApiWeight(String apiCode, int weight, List<MockScenarioWeight> mockScenarios) {
    }

    record MockScenarioWeight(String mockScenario, int weight) {
    }
}
