package com.apihub.agent.dev.gateway;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dev/gateway")
public class DevGatewayInvokeController {

    private final GatewayInvokeService gatewayInvokeService;
    private final GatewayScenarioSummaryService gatewayScenarioSummaryService;

    public DevGatewayInvokeController(GatewayInvokeService gatewayInvokeService,
                                      GatewayScenarioSummaryService gatewayScenarioSummaryService) {
        this.gatewayInvokeService = gatewayInvokeService;
        this.gatewayScenarioSummaryService = gatewayScenarioSummaryService;
    }

    @PostMapping("/invoke")
    public BaseResponse<GatewayInvokeResultVO> invoke(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody GatewayInvokeRequest request
    ) {
        return ResultUtils.success(gatewayInvokeService.invoke(request, requestId));
    }

    @GetMapping("/scenario-runs/{scenarioRunId}/gateway-summary")
    public BaseResponse<Map<String, Object>> gatewaySummary(@PathVariable String scenarioRunId) {
        return ResultUtils.success(gatewayScenarioSummaryService.summary(scenarioRunId));
    }
}
