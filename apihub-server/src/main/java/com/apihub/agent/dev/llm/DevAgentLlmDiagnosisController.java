package com.apihub.agent.dev.llm;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/agent/diagnose/llm")
public class DevAgentLlmDiagnosisController {

    private final LlmDiagnosisOrchestrator orchestrator;

    public DevAgentLlmDiagnosisController(LlmDiagnosisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/mock")
    public BaseResponse<LlmDiagnosisResult> runMock(@RequestBody LlmDiagnosisMockRequest request) {
        Long reportId = request == null ? null : request.getReportId();
        boolean includePrompt = request != null && Boolean.TRUE.equals(request.getIncludePrompt());
        return ResultUtils.success(orchestrator.runMock(reportId, includePrompt));
    }

    public static class LlmDiagnosisMockRequest {
        private Long reportId;
        private Boolean includePrompt;

        public Long getReportId() {
            return reportId;
        }

        public void setReportId(Long reportId) {
            this.reportId = reportId;
        }

        public Boolean getIncludePrompt() {
            return includePrompt;
        }

        public void setIncludePrompt(Boolean includePrompt) {
            this.includePrompt = includePrompt;
        }
    }
}
