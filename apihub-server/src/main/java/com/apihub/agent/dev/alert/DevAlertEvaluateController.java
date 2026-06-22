package com.apihub.agent.dev.alert;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.model.dto.AlertEvaluateRequest;
import com.apihub.agent.model.vo.AlertEvaluateResponseVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/alerts")
public class DevAlertEvaluateController {

    private final AlertEvaluateService alertEvaluateService;

    public DevAlertEvaluateController(AlertEvaluateService alertEvaluateService) {
        this.alertEvaluateService = alertEvaluateService;
    }

    @PostMapping("/evaluate")
    public BaseResponse<AlertEvaluateResponseVO> evaluate(@RequestBody AlertEvaluateRequest request) {
        return ResultUtils.success(alertEvaluateService.evaluate(request));
    }
}
