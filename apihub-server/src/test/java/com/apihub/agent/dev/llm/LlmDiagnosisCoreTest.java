package com.apihub.agent.dev.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmDiagnosisCoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmDiagnosisPromptBuilder promptBuilder = new LlmDiagnosisPromptBuilder(objectMapper);
    private final MockLlmDiagnosisClient mockClient = new MockLlmDiagnosisClient(objectMapper);
    private final LlmDiagnosisOutputParser parser = new LlmDiagnosisOutputParser(objectMapper);
    private final LlmDiagnosisValidator validator = new LlmDiagnosisValidator();

    @Test
    void promptBuilderNormalCaseIncludesBoundariesAndEvidence() {
        LlmDiagnosisPrompt prompt = promptBuilder.build(sampleInput("NORMAL", "NORMAL_BASELINE"));

        assertTrue(prompt.getSystemPrompt().contains("must not invent"));
        assertTrue(prompt.getDiagnosisPrompt().contains("evidenceGroups"));
        assertTrue(prompt.getOutputSchemaJson().contains("riskLevelChanged"));
    }

    @Test
    void parserAcceptsValidJson() {
        LlmDiagnosisInput input = sampleInput("NORMAL", "NORMAL_BASELINE");
        String raw = mockClient.diagnose(promptBuilder.build(input), input);

        LlmDiagnosisParseResult result = parser.parse(raw);

        assertTrue(result.isSuccess());
        assertNotNull(result.getOutput());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void parserRejectsInvalidJsonWithoutThrowing() {
        LlmDiagnosisParseResult result = parser.parse("{not-json");

        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void validatorRejectsRiskMismatch() {
        LlmDiagnosisInput input = sampleInput("WARNING", "ABNORMAL_PEAK");
        LlmDiagnosisOutput output = validOutput("NORMAL");

        LlmDiagnosisValidationResult result = validator.validate(input, output);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("deterministic riskLevel")));
    }

    @Test
    void validatorRejectsInvalidEvidenceRef() {
        LlmDiagnosisInput input = sampleInput("WARNING", "ABNORMAL_PEAK");
        LlmDiagnosisOutput output = validOutput("WARNING");
        output.getEvidenceUsage().get(0).setEvidenceRef("ALERT_EVENT#404");

        LlmDiagnosisValidationResult result = validator.validate(input, output);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("unknown evidenceRef")));
    }

    @Test
    void validatorRejectsAbnormalWordingForNormalScenario() {
        LlmDiagnosisInput input = sampleInput("NORMAL", "NORMAL_BASELINE");
        LlmDiagnosisOutput output = validOutput("NORMAL");
        output.setExecutiveSummary("This normal baseline shows an outage.");

        LlmDiagnosisValidationResult result = validator.validate(input, output);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("abnormal wording")));
    }

    @Test
    void mockNormalPreservesRiskAndPassesValidation() {
        LlmDiagnosisInput input = sampleInput("NORMAL", "NORMAL_BASELINE");
        LlmDiagnosisParseResult parsed = parser.parse(mockClient.diagnose(promptBuilder.build(input), input));

        LlmDiagnosisValidationResult validation = validator.validate(input, parsed.getOutput());

        assertTrue(parsed.isSuccess());
        assertTrue(validation.isSuccess());
        assertTrue(Boolean.FALSE.equals(parsed.getOutput().getRiskLevelChanged()));
    }

    @Test
    void mockWarningPreservesRiskAndSimulationBoundary() {
        LlmDiagnosisInput input = sampleInput("WARNING", "ABNORMAL_PEAK");
        LlmDiagnosisParseResult parsed = parser.parse(mockClient.diagnose(promptBuilder.build(input), input));

        LlmDiagnosisValidationResult validation = validator.validate(input, parsed.getOutput());

        assertTrue(parsed.isSuccess());
        assertTrue(validation.isSuccess());
        assertTrue(parsed.getOutput().getSimulationBoundaryStatement().contains("development simulation"));
    }

    private LlmDiagnosisInput sampleInput(String riskLevel, String scenarioType) {
        LlmDiagnosisInput input = new LlmDiagnosisInput();
        LlmDiagnosisInput.Task task = new LlmDiagnosisInput.Task();
        task.setQuestion("Diagnose LECTURE_REGISTER");
        task.setApiCode("LECTURE_REGISTER");
        task.setApiName("Lecture registration");
        task.setStartTime("2026-06-22 19:00:00");
        task.setEndTime("2026-06-22 19:05:00");
        task.setScenarioRunId("test-run");
        task.setScenarioType(scenarioType);
        task.setEnvironment("development_simulation");
        input.setTask(task);

        LlmDiagnosisInput.DeterministicDiagnosis deterministic = new LlmDiagnosisInput.DeterministicDiagnosis();
        deterministic.setReportId(1L);
        deterministic.setRiskLevel(riskLevel);
        deterministic.setSummary("Deterministic summary");
        deterministic.setRootCause("Deterministic root cause");
        deterministic.setRecommendation("Deterministic recommendation");
        input.setDeterministicDiagnosis(deterministic);

        Map<String, List<LlmEvidenceItem>> groups = new LinkedHashMap<>();
        groups.put("API_CALL_STAT", List.of(evidence("API_CALL_STAT#1", "API_CALL_STAT", "Call stats", "totalCallCount=120")));
        groups.put("API_INFO", List.of(evidence("API_INFO#1", "API_INFO", "API info", "Lecture registration API")));
        groups.put("ALERT_EVENT", List.of(evidence("ALERT_EVENT#1", "ALERT_EVENT", "HIGH_RATE_LIMIT", "alertType=HIGH_RATE_LIMIT")));
        input.setEvidenceGroups(groups);
        input.setToolSummaries(List.of(tool("queryApiCallStats", 1), tool("queryAlertEvents", 1)));
        input.setConstraints(List.of("No real LLM", "Use evidence refs"));
        return input;
    }

    private LlmEvidenceItem evidence(String ref, String type, String title, String content) {
        LlmEvidenceItem item = new LlmEvidenceItem();
        item.setEvidenceRef(ref);
        item.setEvidenceType(type);
        item.setSourceTool("query" + type);
        item.setSourceRef(ref.toLowerCase());
        item.setTitle(title);
        item.setContent(content);
        item.setMetadata(Map.of("evidenceType", type));
        return item;
    }

    private LlmToolSummary tool(String toolName, int count) {
        LlmToolSummary summary = new LlmToolSummary();
        summary.setToolName(toolName);
        summary.setSuccess(true);
        summary.setLatencyMs(10L);
        summary.setStatus("SUCCESS");
        summary.setResponseSummary("ok");
        summary.setEvidenceCount(count);
        return summary;
    }

    private LlmDiagnosisOutput validOutput(String riskLevel) {
        LlmDiagnosisOutput output = new LlmDiagnosisOutput();
        output.setRiskLevel(riskLevel);
        output.setRiskLevelChanged(false);
        output.setRiskLevelChangeReason("unchanged");
        output.setExecutiveSummary("summary");
        output.setTechnicalSummary("technical summary");
        output.setRootCause("root cause");
        output.setImpactScope("impact scope");
        output.setSimulationBoundaryStatement("development simulation only");
        LlmDiagnosisOutput.EvidenceUsage usage = new LlmDiagnosisOutput.EvidenceUsage();
        usage.setEvidenceRef("API_CALL_STAT#1");
        usage.setUsedFor("stats");
        output.setEvidenceUsage(List.of(usage));
        LlmDiagnosisOutput.Recommendation recommendation = new LlmDiagnosisOutput.Recommendation();
        recommendation.setPriority("P2");
        recommendation.setAction("observe");
        recommendation.setReason("based on stats");
        recommendation.setEvidenceRefs(List.of("API_CALL_STAT#1"));
        output.setRecommendations(List.of(recommendation));
        output.setUncertainties(List.of("none"));
        output.setFollowUpChecks(List.of("check later"));
        return output;
    }
}
