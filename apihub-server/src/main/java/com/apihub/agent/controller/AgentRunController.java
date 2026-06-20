package com.apihub.agent.controller;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.common.TraceContext;
import com.apihub.agent.model.dto.AgentRunRequest;
import com.apihub.agent.model.vo.AgentRunResultVO;
import com.apihub.agent.service.AgentRunService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
public class AgentRunController {

    private final AgentRunService agentRunService;

    public AgentRunController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @PostMapping("/run")
    public BaseResponse<AgentRunResultVO> run(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody AgentRunRequest request
    ) {
        return ResultUtils.success(agentRunService.run(request, userId, requestId));
    }

    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody AgentRunRequest request
    ) {
        return agentRunService.stream(request, userId, requestId, TraceContext.getTraceId());
    }
}
