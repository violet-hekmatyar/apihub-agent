package com.apihub.mockprovider.scenario;

import com.apihub.mockprovider.common.MockResponse;
import com.apihub.mockprovider.common.ResponseSupport;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-provider/scenario-runs")
public class ScenarioRunController {

    private final ScenarioRunService scenarioRunService;

    public ScenarioRunController(ScenarioRunService scenarioRunService) {
        this.scenarioRunService = scenarioRunService;
    }

    @PostMapping
    public ResponseEntity<MockResponse<Map<String, Object>>> createRun(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @RequestBody(required = false) ScenarioRunRequest request) {
        String traceId = ResponseSupport.traceId(traceHeader);
        Map<String, Object> data = scenarioRunService.createRun(request);
        return ResponseSupport.response(HttpStatus.ACCEPTED, "accepted", data, traceId);
    }

    @GetMapping("/{scenarioRunId}")
    public ResponseEntity<MockResponse<Map<String, Object>>> getRun(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @PathVariable String scenarioRunId) {
        String traceId = ResponseSupport.traceId(traceHeader);
        return ResponseSupport.ok(scenarioRunService.getRun(scenarioRunId), traceId);
    }

    @GetMapping("/{scenarioRunId}/result")
    public ResponseEntity<MockResponse<Map<String, Object>>> getResult(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @PathVariable String scenarioRunId) {
        String traceId = ResponseSupport.traceId(traceHeader);
        return ResponseSupport.ok(scenarioRunService.getResult(scenarioRunId), traceId);
    }

    @GetMapping("/{scenarioRunId}/sample-calls")
    public ResponseEntity<MockResponse<Map<String, Object>>> listSamples(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @PathVariable String scenarioRunId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        String traceId = ResponseSupport.traceId(traceHeader);
        List<Map<String, Object>> samples = scenarioRunService.listSamples(scenarioRunId, limit);
        return ResponseSupport.ok(Map.of(
                "scenarioRunId", scenarioRunId,
                "samples", samples,
                "count", samples.size()
        ), traceId);
    }

    @PostMapping("/{scenarioRunId}/cancel")
    public ResponseEntity<MockResponse<Map<String, Object>>> cancelRun(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @PathVariable String scenarioRunId) {
        String traceId = ResponseSupport.traceId(traceHeader);
        return ResponseSupport.ok(scenarioRunService.cancelRun(scenarioRunId), traceId);
    }
}
