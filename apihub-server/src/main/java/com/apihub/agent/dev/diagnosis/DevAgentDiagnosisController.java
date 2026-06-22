package com.apihub.agent.dev.diagnosis;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.model.dto.AgentDiagnoseRequest;
import com.apihub.agent.model.vo.AgentDiagnoseResponseVO;
import com.apihub.agent.model.vo.AgentReportDetailVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/reports/{reportId}")
    public BaseResponse<AgentReportDetailVO> getReport(@PathVariable Long reportId) {
        return ResultUtils.success(diagnosisEvidenceService.getReport(reportId));
    }
}
