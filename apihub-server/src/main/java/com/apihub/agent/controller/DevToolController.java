package com.apihub.agent.controller;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.model.dto.QueryApiCallStatsRequest;
import com.apihub.agent.model.dto.QueryApiInfoRequest;
import com.apihub.agent.model.tool.ToolContext;
import com.apihub.agent.model.tool.ToolResult;
import com.apihub.agent.service.ToolService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/tools")
public class DevToolController {

    private final ToolService toolService;

    public DevToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @PostMapping("/queryApiInfo")
    public BaseResponse<ToolResult> queryApiInfo(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody QueryApiInfoRequest request
    ) {
        ToolContext context = toolService.buildContext(userId, requestId);
        return ResultUtils.success(toolService.queryApiInfoWithTrace(request, context));
    }

    @PostMapping("/queryApiCallStats")
    public BaseResponse<ToolResult> queryApiCallStats(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody QueryApiCallStatsRequest request
    ) {
        ToolContext context = toolService.buildContext(userId, requestId);
        return ResultUtils.success(toolService.queryApiCallStatsWithTrace(request, context));
    }
}
