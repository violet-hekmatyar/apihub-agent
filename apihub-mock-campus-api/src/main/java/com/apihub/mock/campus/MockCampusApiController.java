package com.apihub.mock.campus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mock-campus")
class MockCampusApiController {

    private final MockCampusApiService service;

    MockCampusApiController(MockCampusApiService service) {
        this.service = service;
    }

    @GetMapping("/health")
    Map<String, Object> health() {
        return Map.of("code", 200, "message", "success", "data", Map.of("status", "UP", "app", "apihub-mock-campus-api"));
    }

    @PostMapping("/invoke")
    ResponseEntity<MockCampusResponse> invoke(@RequestBody MockCampusInvokeRequest request) {
        return service.invoke(request);
    }

    @GetMapping("/scenario-runs/{scenarioRunId}/upstream-summary")
    Map<String, Object> upstreamSummary(@PathVariable String scenarioRunId) {
        return Map.of("code", 200, "message", "success", "data", service.upstreamSummary(scenarioRunId));
    }
}
