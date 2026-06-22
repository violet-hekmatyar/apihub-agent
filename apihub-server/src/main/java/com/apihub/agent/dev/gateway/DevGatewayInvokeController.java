package com.apihub.agent.dev.gateway;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/gateway")
public class DevGatewayInvokeController {

    private final GatewayInvokeService gatewayInvokeService;

    public DevGatewayInvokeController(GatewayInvokeService gatewayInvokeService) {
        this.gatewayInvokeService = gatewayInvokeService;
    }

    @PostMapping("/invoke")
    public BaseResponse<GatewayInvokeResultVO> invoke(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody GatewayInvokeRequest request
    ) {
        return ResultUtils.success(gatewayInvokeService.invoke(request, requestId));
    }
}
