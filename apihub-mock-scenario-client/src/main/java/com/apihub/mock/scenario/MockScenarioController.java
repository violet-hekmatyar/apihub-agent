package com.apihub.mock.scenario;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mock")
class MockScenarioController {

    private final MockScenarioRunnerService service;

    MockScenarioController(MockScenarioRunnerService service) {
        this.service = service;
    }

    @GetMapping("/health")
    Map<String, Object> health() {
        return ok(Map.of("status", "UP", "app", "apihub-mock-scenario-client"));
    }

    @GetMapping("/scenario-profiles")
    Map<String, Object> profiles() {
        List<ScenarioProfileView> profiles = service.profiles();
        return ok(profiles);
    }

    @PostMapping("/scenario-runs")
    Map<String, Object> start(@RequestBody ScenarioStartRequest request) {
        return ok(service.start(request));
    }

    @GetMapping("/scenario-runs/{scenarioRunId}")
    Map<String, Object> status(@PathVariable String scenarioRunId) {
        return ok(service.status(scenarioRunId));
    }

    @PostMapping("/scenario-runs/{scenarioRunId}/stop")
    Map<String, Object> stop(@PathVariable String scenarioRunId) {
        return ok(service.stop(scenarioRunId));
    }

    @GetMapping("/scenario-runs/{scenarioRunId}/sender-summary")
    Map<String, Object> senderSummary(@PathVariable String scenarioRunId) {
        return ok(service.senderSummary(scenarioRunId));
    }

    @GetMapping("/scenario-runs/{scenarioRunId}/reconciliation-summary")
    Map<String, Object> reconciliationSummary(@PathVariable String scenarioRunId) {
        return ok(service.reconciliationSummary(scenarioRunId));
    }

    private Map<String, Object> ok(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }
}
