package com.apihub.agent.dev.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MockLlmDiagnosisClient implements LlmDiagnosisClient {

    private final ObjectMapper objectMapper;

    public MockLlmDiagnosisClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmDiagnosisClientResult diagnose(LlmDiagnosisPrompt prompt, LlmDiagnosisInput input) {
        long started = System.nanoTime();
        try {
            return LlmDiagnosisClientResult.success("MOCK", "mock-deterministic",
                    objectMapper.writeValueAsString(buildOutput(input)), "", elapsedMs(started));
        } catch (JsonProcessingException e) {
            return LlmDiagnosisClientResult.failure("MOCK", "mock-deterministic", "MOCK_JSON_ERROR",
                    "failed to write mock LLM response", elapsedMs(started));
        }
    }

    private LlmDiagnosisOutput buildOutput(LlmDiagnosisInput input) {
        String risk = risk(input);
        String scenarioType = input.getTask() == null ? "" : normalize(input.getTask().getScenarioType());
        String apiCode = input.getTask() == null ? "" : input.getTask().getApiCode();
        List<String> primaryRefs = primaryRefs(input, risk);

        LlmDiagnosisOutput output = new LlmDiagnosisOutput();
        output.setRiskLevel(risk);
        output.setRiskLevelChanged(false);
        output.setRiskLevelChangeReason("Local mock preserves deterministic risk level.");
        output.setEvidenceUsage(evidenceUsage(primaryRefs, risk));
        output.setRecommendations(recommendations(primaryRefs, risk));

        if ("NORMAL".equals(risk) && "NORMAL_BASELINE".equals(scenarioType)) {
            output.setSimulationBoundaryStatement("This diagnosis is generated from development simulation evidence only and is not a production status statement.");
            output.setExecutiveSummary(apiCode + " is in the expected daily baseline for the selected simulation window.");
            output.setTechnicalSummary("The available API metadata, call statistics, and tool traces do not provide evidence that the deterministic baseline should change.");
            output.setRootCause("No remediation root cause is identified from the supplied evidence.");
            output.setImpactScope("Observed development simulation traffic remains within the supplied baseline evidence.");
            output.setUncertainties(List.of("Only supplied deterministic evidence was evaluated."));
            output.setFollowUpChecks(List.of("Continue collecting baseline windows before comparing future peak traffic."));
            return output;
        }

        if ("WARNING".equals(risk) || "CRITICAL".equals(risk)) {
            output.setSimulationBoundaryStatement("This diagnosis is generated from development simulation evidence only and is not a production incident statement.");
            output.setExecutiveSummary(apiCode + " requires attention in this simulated window; deterministic evidence keeps risk at " + risk + ".");
            output.setTechnicalSummary("The conclusion is grounded in the provided alert, call-stat, and gateway-log evidence references where available.");
            output.setRootCause(firstNonBlank(input.getDeterministicDiagnosis().getRootCause(), "Evidence indicates a simulated pressure condition for the API window."));
            output.setImpactScope("Impact scope is limited to the selected development simulation window and the API evidence supplied in the report.");
            output.setUncertainties(primaryRefs.isEmpty() ? List.of("No concrete evidence references were supplied to the mock client.") : List.of("The mock client cannot inspect systems outside the provided report."));
            output.setFollowUpChecks(List.of("Compare hourly call volume, failure rate, and gateway status samples in adjacent windows."));
            return output;
        }

        output.setSimulationBoundaryStatement("This diagnosis is generated from development simulation evidence only and is not a production incident statement.");
        output.setExecutiveSummary(apiCode + " has insufficient evidence for a confident LLM-style narrative beyond deterministic output.");
        output.setTechnicalSummary("The mock client found limited evidence references and preserved the deterministic UNKNOWN boundary.");
        output.setRootCause("Evidence is insufficient to infer a root cause.");
        output.setImpactScope("Impact scope cannot be expanded beyond the selected development simulation report.");
        output.setUncertainties(List.of("Evidence is insufficient for root-cause expansion."));
        output.setFollowUpChecks(List.of("Collect API call stats, gateway logs, and alert events for the diagnosis window."));
        return output;
    }

    private List<LlmDiagnosisOutput.Recommendation> recommendations(List<String> refs, String risk) {
        LlmDiagnosisOutput.Recommendation recommendation = new LlmDiagnosisOutput.Recommendation();
        recommendation.setPriority("NORMAL".equals(risk) ? "P3" : "P1");
        recommendation.setAction("NORMAL".equals(risk)
                ? "Keep baseline observation and use this report as a comparison point."
                : "Review the referenced alert, call-stat, and gateway-log evidence before operational action.");
        recommendation.setReason("The recommendation is limited to deterministic report evidence and local mock validation.");
        recommendation.setEvidenceRefs(refs);
        return List.of(recommendation);
    }

    private List<LlmDiagnosisOutput.EvidenceUsage> evidenceUsage(List<String> refs, String risk) {
        List<LlmDiagnosisOutput.EvidenceUsage> result = new ArrayList<>();
        for (String ref : refs) {
            LlmDiagnosisOutput.EvidenceUsage usage = new LlmDiagnosisOutput.EvidenceUsage();
            usage.setEvidenceRef(ref);
            usage.setUsedFor("Support " + risk + " narrative without changing deterministic risk.");
            result.add(usage);
        }
        return result;
    }

    private List<String> primaryRefs(LlmDiagnosisInput input, String risk) {
        List<String> refs = new ArrayList<>();
        if (input.getEvidenceGroups() == null) {
            return refs;
        }
        List<String> preferred = "NORMAL".equals(risk)
                ? List.of("API_CALL_STAT", "API_INFO", "RATE_LIMIT_RULE", "API_DOC")
                : List.of("ALERT_EVENT", "API_CALL_STAT", "GATEWAY_LOG_SAMPLE", "RATE_LIMIT_RULE", "CAMPUS_EVENT", "API_DOC");
        for (String type : preferred) {
            List<LlmEvidenceItem> items = input.getEvidenceGroups().get(type);
            if (items == null || items.isEmpty()) {
                continue;
            }
            for (LlmEvidenceItem item : items) {
                if (StringUtils.hasText(item.getEvidenceRef())) {
                    refs.add(item.getEvidenceRef());
                }
                if (refs.size() >= 3) {
                    return refs;
                }
            }
        }
        return refs;
    }

    private String risk(LlmDiagnosisInput input) {
        if (input == null || input.getDeterministicDiagnosis() == null) {
            return "UNKNOWN";
        }
        String risk = normalize(input.getDeterministicDiagnosis().getRiskLevel());
        return StringUtils.hasText(risk) ? risk : "UNKNOWN";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
