package com.apihub.agent.controller;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.model.vo.DbHealthVO;
import com.apihub.agent.model.vo.HealthVO;
import com.apihub.agent.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public BaseResponse<HealthVO> health() {
        return ResultUtils.success(healthService.getHealth());
    }

    @GetMapping("/db")
    public BaseResponse<DbHealthVO> dbHealth() {
        return ResultUtils.success(healthService.getDbHealth());
    }
}
