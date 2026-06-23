package com.apihub.agent.dev.llm;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LlmDiagnosisValidator {

    private static final Set<String> VALID_RISK_LEVELS = Set.of("NORMAL", "WARNING", "CRITICAL", "UNKNOWN");
    private static final List<String> NORMAL_FORBIDDEN_WORDS = List.of(
            "\u6545\u969c", "\u4e8b\u6545", "\u5f02\u5e38", "\u5931\u8d25\u7387\u5347\u9ad8", "\u9650\u6d41\u5f02\u5e38",
            "outage", "incident", "failure spike", "rate-limit anomaly",
            "production incident occurred", "outage occurred", "failure spike detected",
            "rate-limit anomaly detected", "serious outage", "active incident"
    );
    private static final List<String> NORMAL_SAFE_NEGATED_PHRASES = List.of(
            "no production incident",
            "no live-user impact",
            "not a production incident",
            "no outage",
            "no service degradation",
            "no user-facing impact",
            "without production impact",
            "development simulation only"
    );
    private static final List<String> ALERT_TYPES = List.of(
            "HIGH_5XX", "AUTH_FAILURE_SPIKE", "HIGH_RATE_LIMIT", "HIGH_FAILURE_RATE"
    );

    public LlmDiagnosisValidationResult validate(LlmDiagnosisInput input, LlmDiagnosisOutput output) {
        LlmDiagnosisValidationResult result = LlmDiagnosisValidationResult.ok();
        if (input == null) {
            result.error("input is required");
            return result;
        }
        if (output == null) {
            result.error("output is required");
            return result;
        }

        String deterministicRisk = normalize(input.getDeterministicDiagnosis() == null ? null : input.getDeterministicDiagnosis().getRiskLevel());
        String outputRisk = normalize(output.getRiskLevel());
        if (!VALID_RISK_LEVELS.contains(outputRisk)) {
            result.error("riskLevel is invalid: " + output.getRiskLevel());
        }
        if (StringUtils.hasText(deterministicRisk) && !deterministicRisk.equals(outputRisk)) {
            result.error("riskLevel must equal deterministic riskLevel: " + deterministicRisk);
        }
        if (!Boolean.FALSE.equals(output.getRiskLevelChanged())) {
            result.error("riskLevelChanged must be false in local LLM readiness mode");
        }
        requireText(result, "executiveSummary", output.getExecutiveSummary());
        requireText(result, "technicalSummary", output.getTechnicalSummary());
        requireText(result, "rootCause", output.getRootCause());
        requireText(result, "impactScope", output.getImpactScope());
        requireText(result, "simulationBoundaryStatement", output.getSimulationBoundaryStatement());

        Set<String> refs = evidenceRefs(input);
        validateEvidenceRefs(result, refs, output);
        validateNormalWording(result, input, output);
        validateUnsupportedAlerts(result, input, output);
        return result;
    }

    private void validateEvidenceRefs(LlmDiagnosisValidationResult result, Set<String> refs, LlmDiagnosisOutput output) {
        if (output.getRecommendations() != null) {
            for (LlmDiagnosisOutput.Recommendation recommendation : output.getRecommendations()) {
                if (recommendation.getEvidenceRefs() == null) {
                    continue;
                }
                for (String ref : recommendation.getEvidenceRefs()) {
                    if (!refs.contains(ref)) {
                        result.error("recommendation references unknown evidenceRef: " + ref);
                    }
                }
            }
        }
        if (output.getEvidenceUsage() != null) {
            for (LlmDiagnosisOutput.EvidenceUsage usage : output.getEvidenceUsage()) {
                if (!refs.contains(usage.getEvidenceRef())) {
                    result.error("evidenceUsage references unknown evidenceRef: " + usage.getEvidenceRef());
                }
            }
        }
    }

    private void validateNormalWording(LlmDiagnosisValidationResult result, LlmDiagnosisInput input, LlmDiagnosisOutput output) {
        String scenarioType = input.getTask() == null ? "" : normalize(input.getTask().getScenarioType());
        if (!"NORMAL_BASELINE".equals(scenarioType) || !"NORMAL".equals(normalize(output.getRiskLevel()))) {
            return;
        }
        String text = removeSafeNegatedPhrases(joinedNormalRiskFields(output)).toLowerCase(Locale.ROOT);
        for (String word : NORMAL_FORBIDDEN_WORDS) {
            if (text.contains(word.toLowerCase(Locale.ROOT))) {
                result.error("NORMAL baseline output contains abnormal wording: " + word);
            }
        }
    }

    private void validateUnsupportedAlerts(LlmDiagnosisValidationResult result, LlmDiagnosisInput input, LlmDiagnosisOutput output) {
        String outputText = joinedOutput(output);
        String evidenceText = joinedEvidence(input);
        for (String alertType : ALERT_TYPES) {
            if (outputText.contains(alertType) && !evidenceText.contains(alertType)) {
                result.error("output mentions unsupported alert type: " + alertType);
            }
        }
    }

    private Set<String> evidenceRefs(LlmDiagnosisInput input) {
        Set<String> refs = new HashSet<>();
        if (input.getEvidenceGroups() == null) {
            return refs;
        }
        input.getEvidenceGroups().values().forEach(items -> {
            if (items == null) {
                return;
            }
            for (LlmEvidenceItem item : items) {
                if (StringUtils.hasText(item.getEvidenceRef())) {
                    refs.add(item.getEvidenceRef());
                }
            }
        });
        return refs;
    }

    private String joinedEvidence(LlmDiagnosisInput input) {
        StringBuilder builder = new StringBuilder();
        if (input.getEvidenceGroups() == null) {
            return "";
        }
        input.getEvidenceGroups().values().forEach(items -> {
            if (items == null) {
                return;
            }
            for (LlmEvidenceItem item : items) {
                builder.append(' ').append(item.getTitle()).append(' ').append(item.getContent()).append(' ').append(item.getMetadata());
            }
        });
        return builder.toString();
    }

    private String joinedOutput(LlmDiagnosisOutput output) {
        StringBuilder builder = new StringBuilder();
        builder.append(output.getExecutiveSummary()).append(' ')
                .append(output.getTechnicalSummary()).append(' ')
                .append(output.getRootCause()).append(' ')
                .append(output.getImpactScope()).append(' ')
                .append(output.getRiskLevelChangeReason()).append(' ')
                .append(output.getSimulationBoundaryStatement()).append(' ')
                .append(output.getUncertainties()).append(' ')
                .append(output.getFollowUpChecks());
        if (output.getRecommendations() != null) {
            for (LlmDiagnosisOutput.Recommendation recommendation : output.getRecommendations()) {
                builder.append(' ').append(recommendation.getAction()).append(' ').append(recommendation.getReason());
            }
        }
        if (output.getEvidenceUsage() != null) {
            for (LlmDiagnosisOutput.EvidenceUsage usage : output.getEvidenceUsage()) {
                builder.append(' ').append(usage.getUsedFor());
            }
        }
        return builder.toString();
    }

    private String joinedNormalRiskFields(LlmDiagnosisOutput output) {
        StringBuilder builder = new StringBuilder();
        builder.append(output.getExecutiveSummary()).append(' ')
                .append(output.getTechnicalSummary()).append(' ')
                .append(output.getRootCause()).append(' ')
                .append(output.getImpactScope()).append(' ');
        if (output.getRecommendations() != null) {
            for (LlmDiagnosisOutput.Recommendation recommendation : output.getRecommendations()) {
                builder.append(' ').append(recommendation.getAction()).append(' ').append(recommendation.getReason());
            }
        }
        return builder.toString();
    }

    private String removeSafeNegatedPhrases(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = text;
        for (String phrase : NORMAL_SAFE_NEGATED_PHRASES) {
            sanitized = sanitized.replaceAll("(?i)" + Pattern.quote(phrase), " ");
        }
        return sanitized;
    }

    private void requireText(LlmDiagnosisValidationResult result, String field, String value) {
        if (!StringUtils.hasText(value)) {
            result.error(field + " is required");
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
