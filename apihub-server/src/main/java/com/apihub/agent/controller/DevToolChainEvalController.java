package com.apihub.agent.controller;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.model.dto.ToolChainEvalRunRequest;
import com.apihub.agent.model.vo.ToolChainEvalResultVO;
import com.apihub.agent.model.vo.ToolChainScenarioVO;
import com.apihub.agent.service.ToolChainEvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dev/eval/tool-chain")
public class DevToolChainEvalController {

    private final ToolChainEvalService toolChainEvalService;

    public DevToolChainEvalController(ToolChainEvalService toolChainEvalService) {
        this.toolChainEvalService = toolChainEvalService;
    }

    @GetMapping("/scenarios")
    public BaseResponse<List<ToolChainScenarioVO>> listScenarios() {
        return ResultUtils.success(toolChainEvalService.listScenarios());
    }

    @PostMapping("/run")
    public BaseResponse<ToolChainEvalResultVO> run(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody ToolChainEvalRunRequest request
    ) {
        return ResultUtils.success(toolChainEvalService.run(request, userId, requestId));
    }
}
