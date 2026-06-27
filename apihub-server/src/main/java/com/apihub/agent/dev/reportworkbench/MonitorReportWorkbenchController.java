package com.apihub.agent.dev.reportworkbench;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.dev.reportworkbench.MonitorReportWorkbenchDtos.LatestAnomalyRequest;
import com.apihub.agent.dev.reportworkbench.MonitorReportWorkbenchDtos.MonitorEventReportRequest;
import com.apihub.agent.dev.reportworkbench.MonitorReportWorkbenchDtos.RangeReportRequest;
import com.apihub.agent.dev.reportworkbench.MonitorReportWorkbenchDtos.WorkbenchReportResponse;
import com.apihub.agent.model.vo.AgentReportDetailVO;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/dev/report-workbench")
public class MonitorReportWorkbenchController {

    private final MonitorReportWorkbenchService service;

    public MonitorReportWorkbenchController(MonitorReportWorkbenchService service) {
        this.service = service;
    }

    @PostMapping("/from-monitor-event")
    public BaseResponse<WorkbenchReportResponse> fromMonitorEvent(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody MonitorEventReportRequest request
    ) {
        return ResultUtils.success(service.fromMonitorEvent(request, userId, requestId));
    }

    @PostMapping("/analyze-latest-anomaly")
    public BaseResponse<WorkbenchReportResponse> analyzeLatestAnomaly(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody(required = false) LatestAnomalyRequest request
    ) {
        return ResultUtils.success(service.analyzeLatestAnomaly(request, userId, requestId));
    }

    @PostMapping("/analyze-range")
    public BaseResponse<WorkbenchReportResponse> analyzeRange(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody RangeReportRequest request
    ) {
        return ResultUtils.success(service.analyzeRange(request, userId, requestId));
    }

    @GetMapping("/reports/recent")
    public BaseResponse<List<Map<String, Object>>> recentReports(
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ResultUtils.success(service.recentReports(limit));
    }

    @GetMapping("/reports/{reportId}")
    public BaseResponse<AgentReportDetailVO> reportDetail(@PathVariable Long reportId) {
        return ResultUtils.success(service.reportDetail(reportId));
    }

    @GetMapping(value = "/reports/{reportId}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> reportHtml(
            @PathVariable Long reportId,
            @RequestParam(value = "download", required = false) Boolean download
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));
        if (Boolean.TRUE.equals(download)) {
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("apihub-monitor-report-" + reportId + ".html", StandardCharsets.UTF_8)
                    .build());
        }
        return ResponseEntity.ok().headers(headers).body(service.renderHtml(reportId));
    }
}
