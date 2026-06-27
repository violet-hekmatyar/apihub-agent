package com.apihub.agent.dev.monitor;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/passive-monitor")
public class PassiveMonitorController {

    private final PassiveMonitorService service;

    public PassiveMonitorController(PassiveMonitorService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public BaseResponse<PassiveMonitorStatusVO> status() {
        return ResultUtils.success(service.status());
    }

    @PostMapping("/start")
    public BaseResponse<PassiveMonitorStatusVO> start() {
        return ResultUtils.success(service.start());
    }

    @PostMapping("/stop")
    public BaseResponse<PassiveMonitorStatusVO> stop() {
        return ResultUtils.success(service.stop());
    }

    @PostMapping("/config")
    public BaseResponse<PassiveMonitorStatusVO> config(@RequestBody PassiveMonitorConfigRequest request) {
        return ResultUtils.success(service.applyConfig(request));
    }

    @GetMapping("/events/recent")
    public BaseResponse<List<Map<String, Object>>> recent(@RequestParam(value = "limit", required = false) Integer limit) {
        return ResultUtils.success(service.recent(limit == null ? 20 : limit));
    }

    @GetMapping("/events")
    public BaseResponse<List<Map<String, Object>>> events(PassiveMonitorEventQuery query) {
        return ResultUtils.success(service.query(query));
    }

    @GetMapping("/events/{monitorEventId}")
    public BaseResponse<PassiveMonitorEventVO> detail(@PathVariable String monitorEventId) {
        return ResultUtils.success(service.detail(monitorEventId));
    }

    @PostMapping("/events/close-check")
    public BaseResponse<Map<String, Object>> closeCheck() {
        return ResultUtils.success(service.closeCheck());
    }
}
