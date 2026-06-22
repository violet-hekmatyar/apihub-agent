package com.apihub.agent.dev.diagnosis;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.model.dto.AgentDiagnoseRequest;
import com.apihub.agent.model.vo.AgentDiagnoseResponseVO;
import com.apihub.agent.model.vo.AgentReportDetailVO;
import com.apihub.agent.model.vo.AgentReportListVO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/dev/agent")
public class DevAgentDiagnosisController {

    private final AgentDiagnosisEvidenceService diagnosisEvidenceService;

    public DevAgentDiagnosisController(AgentDiagnosisEvidenceService diagnosisEvidenceService) {
        this.diagnosisEvidenceService = diagnosisEvidenceService;
    }

    @PostMapping("/diagnose")
    public BaseResponse<AgentDiagnoseResponseVO> diagnose(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody AgentDiagnoseRequest request
    ) {
        return ResultUtils.success(diagnosisEvidenceService.diagnose(request, userId, requestId));
    }

    @GetMapping("/reports")
    public BaseResponse<AgentReportListVO> listReports(
            @RequestParam(value = "apiCode", required = false) String apiCode,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        return ResultUtils.success(diagnosisEvidenceService.listReports(
                apiCode, riskLevel, status, startTime, endTime, keyword, pageNo, pageSize
        ));
    }

    @GetMapping("/reports/{reportId}")
    public BaseResponse<AgentReportDetailVO> getReport(@PathVariable Long reportId) {
        return ResultUtils.success(diagnosisEvidenceService.getReport(reportId));
    }

    @GetMapping(value = "/reports/{reportId}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getReportHtml(
            @PathVariable Long reportId,
            @RequestParam(value = "download", required = false) Boolean download
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));
        if (Boolean.TRUE.equals(download)) {
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("apihub-agent-report-" + reportId + ".html", StandardCharsets.UTF_8)
                    .build());
        }
        return ResponseEntity.ok().headers(headers).body(diagnosisEvidenceService.renderReportHtml(reportId));
    }
}
