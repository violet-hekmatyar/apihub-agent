package com.apihub.agent.controller;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.model.dto.QueryAlertEventsRequest;
import com.apihub.agent.model.dto.QueryApiCallStatsRequest;
import com.apihub.agent.model.dto.QueryApiDocsRequest;
import com.apihub.agent.model.dto.QueryApiInfoRequest;
import com.apihub.agent.model.dto.QueryCampusEventsRequest;
import com.apihub.agent.model.dto.QueryGatewayLogsRequest;
import com.apihub.agent.model.dto.QueryRateLimitRuleRequest;
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

    @PostMapping("/queryGatewayLogs")
    public BaseResponse<ToolResult> queryGatewayLogs(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody QueryGatewayLogsRequest request
    ) {
        ToolContext context = toolService.buildContext(userId, requestId);
        return ResultUtils.success(toolService.queryGatewayLogsWithTrace(request, context));
    }

    @PostMapping("/queryRateLimitRule")
    public BaseResponse<ToolResult> queryRateLimitRule(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody QueryRateLimitRuleRequest request
    ) {
        ToolContext context = toolService.buildContext(userId, requestId);
        return ResultUtils.success(toolService.queryRateLimitRuleWithTrace(request, context));
    }

    @PostMapping("/queryAlertEvents")
    public BaseResponse<ToolResult> queryAlertEvents(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody QueryAlertEventsRequest request
    ) {
        ToolContext context = toolService.buildContext(userId, requestId);
        return ResultUtils.success(toolService.queryAlertEventsWithTrace(request, context));
    }

    @PostMapping("/queryCampusEvents")
    public BaseResponse<ToolResult> queryCampusEvents(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody QueryCampusEventsRequest request
    ) {
        ToolContext context = toolService.buildContext(userId, requestId);
        return ResultUtils.success(toolService.queryCampusEventsWithTrace(request, context));
    }

    @PostMapping("/queryApiDocs")
    public BaseResponse<ToolResult> queryApiDocs(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody QueryApiDocsRequest request
    ) {
        ToolContext context = toolService.buildContext(userId, requestId);
        return ResultUtils.success(toolService.queryApiDocsWithTrace(request, context));
    }
}
