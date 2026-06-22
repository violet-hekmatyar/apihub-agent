package com.apihub.agent.dev.llm;

import com.apihub.agent.dev.diagnosis.AgentDiagnosisEvidenceService;
import com.apihub.agent.model.vo.AgentEvidenceItemVO;
import com.apihub.agent.model.vo.AgentReportDetailVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class LlmDiagnosisOrchestrator {

    private final AgentDiagnosisEvidenceService diagnosisEvidenceService;
    private final LlmDiagnosisPromptBuilder promptBuilder;
    private final MockLlmDiagnosisClient mockClient;
    private final LlmDiagnosisOutputParser parser;
    private final LlmDiagnosisValidator validator;

    public LlmDiagnosisOrchestrator(AgentDiagnosisEvidenceService diagnosisEvidenceService,
                                    LlmDiagnosisPromptBuilder promptBuilder,
                                    MockLlmDiagnosisClient mockClient,
                                    LlmDiagnosisOutputParser parser,
                                    LlmDiagnosisValidator validator) {
        this.diagnosisEvidenceService = diagnosisEvidenceService;
        this.promptBuilder = promptBuilder;
        this.mockClient = mockClient;
        this.parser = parser;
        this.validator = validator;
    }

    public LlmDiagnosisResult runMock(Long reportId, boolean includePrompt) {
        AgentReportDetailVO detail = diagnosisEvidenceService.getReport(reportId);
        LlmDiagnosisInput input = buildInput(detail);
        LlmDiagnosisPrompt prompt = promptBuilder.build(input);
        String rawResponse = mockClient.diagnose(prompt, input);
        LlmDiagnosisParseResult parseResult = parser.parse(rawResponse);

        LlmDiagnosisResult result = new LlmDiagnosisResult();
        result.setReportId(reportId);
        result.setRawResponse(rawResponse);
        result.setParseErrors(parseResult.getErrors());
        result.setParseWarnings(parseResult.getWarnings());
        result.setPrompt(includePrompt ? prompt : null);

        if (!parseResult.isSuccess()) {
            result.setSuccess(false);
            result.setFallbackUsed(true);
            result.setFallbackSummary(fallbackSummary(input));
            return result;
        }
        LlmDiagnosisValidationResult validation = validator.validate(input, parseResult.getOutput());
        result.setOutput(parseResult.getOutput());
        result.setValidation(validation);
        result.setSuccess(validation.isSuccess());
        result.setFallbackUsed(!validation.isSuccess());
        if (!validation.isSuccess()) {
            result.setFallbackSummary(fallbackSummary(input));
        }
        return result;
    }

    LlmDiagnosisInput buildInput(AgentReportDetailVO detail) {
        Map<String, Object> report = detail.getReport() == null ? Map.of() : detail.getReport();
        LlmDiagnosisInput input = new LlmDiagnosisInput();

        LlmDiagnosisInput.Task task = new LlmDiagnosisInput.Task();
        task.setApiCode(text(report.get("apiCode")));
        task.setApiName(text(report.get("apiName")));
        task.setStartTime(text(report.get("startTime")));
        task.setEndTime(text(report.get("endTime")));
        task.setScenarioRunId(text(report.get("scenarioRunId")));
        task.setScenarioType(inferScenarioType(task.getApiCode(), text(report.get("riskLevel")), detail.getEvidenceItems()));
        task.setEnvironment("development_simulation");
        task.setQuestion("Generate evidence-grounded local LLM diagnosis for " + task.getApiCode());
        input.setTask(task);

        LlmDiagnosisInput.DeterministicDiagnosis deterministic = new LlmDiagnosisInput.DeterministicDiagnosis();
        deterministic.setReportId(asLong(report.get("reportId")));
        deterministic.setRiskLevel(text(report.get("riskLevel")));
        deterministic.setSummary(text(report.get("summary")));
        deterministic.setRootCause(text(report.get("rootCause")));
        deterministic.setRecommendation(text(report.get("recommendation")));
        input.setDeterministicDiagnosis(deterministic);

        input.setEvidenceGroups(buildEvidenceGroups(detail.getEvidenceItems()));
        input.setToolSummaries(buildToolSummaries(detail.getToolCallTraces(), detail.getEvidenceItems()));
        input.setConstraints(List.of(
                "No real LLM or external API call is allowed in this mode.",
                "riskLevel must remain equal to deterministicDiagnosis.riskLevel.",
                "Use only supplied evidenceRef values.",
                "Development simulation must not be described as production incident."
        ));
        return input;
    }

    private Map<String, List<LlmEvidenceItem>> buildEvidenceGroups(List<AgentEvidenceItemVO> evidenceItems) {
        Map<String, AtomicInteger> indexes = new LinkedHashMap<>();
        Map<String, List<LlmEvidenceItem>> groups = new LinkedHashMap<>();
        if (evidenceItems == null) {
            return groups;
        }
        for (AgentEvidenceItemVO source : evidenceItems) {
            String type = normalize(firstNonBlank(source.getEvidenceType(), "UNKNOWN"));
            int index = indexes.computeIfAbsent(type, ignored -> new AtomicInteger()).incrementAndGet();
            LlmEvidenceItem item = new LlmEvidenceItem();
            item.setEvidenceRef(type + "#" + index);
            item.setEvidenceType(type);
            item.setSourceTool(source.getSourceTool());
            item.setSourceRef(source.getSourceRef());
            item.setTitle(source.getTitle());
            item.setContent(source.getContent());
            item.setScore(source.getScore());
            item.setMetadata(source.getExtraInfo() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getExtraInfo()));
            groups.computeIfAbsent(type, ignored -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    private List<LlmToolSummary> buildToolSummaries(List<Map<String, Object>> traces, List<AgentEvidenceItemVO> evidenceItems) {
        Map<String, Long> evidenceCounts = evidenceItems == null ? Map.of() : evidenceItems.stream()
                .collect(Collectors.groupingBy(AgentEvidenceItemVO::getSourceTool, Collectors.counting()));
        List<LlmToolSummary> summaries = new ArrayList<>();
        if (traces == null) {
            return summaries;
        }
        for (Map<String, Object> trace : traces) {
            LlmToolSummary summary = new LlmToolSummary();
            String toolName = text(trace.get("toolName"));
            summary.setToolName(toolName);
            summary.setSuccess(asBoolean(trace.get("success")));
            summary.setLatencyMs(asLong(trace.get("latencyMs")));
            summary.setStatus(text(trace.get("status")));
            summary.setErrorCode(text(trace.get("errorCode")));
            summary.setResponseSummary(text(trace.get("responseSummary")));
            summary.setEvidenceCount(evidenceCounts.getOrDefault(toolName, 0L).intValue());
            summaries.add(summary);
        }
        return summaries;
    }

    private String inferScenarioType(String apiCode, String riskLevel, List<AgentEvidenceItemVO> evidenceItems) {
        String evidenceText = evidenceItems == null ? "" : evidenceItems.stream()
                .map(item -> item.getTitle() + " " + item.getContent() + " " + item.getExtraInfo())
                .collect(Collectors.joining(" "))
                .toUpperCase(Locale.ROOT);
        if ("NORMAL".equals(normalize(riskLevel)) && !evidenceText.contains("HIGH_") && !evidenceText.contains("AUTH_FAILURE")) {
            return "NORMAL_BASELINE";
        }
        if ("LECTURE_REGISTER".equals(normalize(apiCode)) && (evidenceText.contains("HIGH_RATE_LIMIT") || evidenceText.contains("HIGH_FAILURE_RATE"))) {
            return "ABNORMAL_PEAK";
        }
        if ("AUTH_LOGIN".equals(normalize(apiCode)) || evidenceText.contains("AUTH_FAILURE")) {
            return "AUTH_FAILURE";
        }
        if (evidenceText.contains("HIGH_5XX") || evidenceText.contains("TIMEOUT") || evidenceText.contains("DEPENDENCY")) {
            return "DEPENDENCY_FAILURE";
        }
        return "UNKNOWN";
    }

    private String fallbackSummary(LlmDiagnosisInput input) {
        LlmDiagnosisInput.DeterministicDiagnosis diagnosis = input.getDeterministicDiagnosis();
        return "[" + firstNonBlank(diagnosis.getRiskLevel(), "UNKNOWN") + "] "
                + firstNonBlank(diagnosis.getSummary(), "Fallback to deterministic diagnosis.");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (!StringUtils.hasText(text(value))) {
            return null;
        }
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return StringUtils.hasText(text(value)) ? Boolean.parseBoolean(text(value)) : null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
