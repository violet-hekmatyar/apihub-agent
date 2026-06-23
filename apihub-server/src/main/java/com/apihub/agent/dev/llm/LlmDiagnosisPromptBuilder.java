package com.apihub.agent.dev.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmDiagnosisPromptBuilder {

    private static final int MAX_ITEMS_PER_GROUP = 5;
    private static final int MAX_CONTENT_LENGTH = 700;
    private final ObjectMapper objectMapper;

    public LlmDiagnosisPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LlmDiagnosisPrompt build(LlmDiagnosisInput input) {
        LlmDiagnosisInput safeInput = compact(input);
        LlmDiagnosisPrompt prompt = new LlmDiagnosisPrompt();
        prompt.setSystemPrompt(systemPrompt());
        prompt.setInputJson(toJson(safeInput));
        prompt.setOutputSchemaJson(outputSchemaJson());
        prompt.setDiagnosisPrompt("""
                Use the following deterministic diagnosis, tool summaries, and evidence groups to produce one JSON object.
                The deterministic risk level is authoritative. Do not call external systems and do not infer facts beyond evidence.

                Input JSON:
                %s

                Output schema:
                %s
                """.formatted(prompt.getInputJson(), prompt.getOutputSchemaJson()));
        return prompt;
    }

    private String systemPrompt() {
        return """
                You are API-HUB Agent's local LLM diagnosis writer.
                Return JSON only; no markdown fences, no prose outside JSON.
                You must not invent metrics, logs, alerts, documents, owners, incidents, business events, or production facts.
                riskLevelChanged must be false and riskLevel must equal deterministicDiagnosis.riskLevel.
                Reference evidence only by existing evidenceRef values.
                If evidence is insufficient, state the insufficiency instead of guessing.
                This is a development simulation. Do not describe it as a real production incident.
                Use this exact simulationBoundaryStatement:
                "This diagnosis is based on development simulation only; no live-user impact was observed."
                For NORMAL scenarios:
                - Do not use outage, incident, failure spike, or rate-limit anomaly in executiveSummary, technicalSummary, rootCause, impactScope, recommendations, or evidenceUsage.
                - Keep NORMAL summaries positive and quiet, such as stable baseline behavior, observed values within expected range, and no action beyond routine observation.
                """;
    }

    private String outputSchemaJson() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("riskLevel", "NORMAL|WARNING|CRITICAL|UNKNOWN");
        schema.put("riskLevelChanged", false);
        schema.put("riskLevelChangeReason", "string");
        schema.put("executiveSummary", "string");
        schema.put("technicalSummary", "string");
        schema.put("rootCause", "string");
        schema.put("impactScope", "string");
        schema.put("recommendations", List.of(Map.of(
                "priority", "P0|P1|P2|P3",
                "action", "string",
                "reason", "string",
                "evidenceRefs", List.of("API_CALL_STAT#1")
        )));
        schema.put("evidenceUsage", List.of(Map.of(
                "evidenceRef", "API_CALL_STAT#1",
                "usedFor", "string"
        )));
        schema.put("uncertainties", List.of("string"));
        schema.put("simulationBoundaryStatement", "string");
        schema.put("followUpChecks", List.of("string"));
        return toJson(schema);
    }

    private LlmDiagnosisInput compact(LlmDiagnosisInput input) {
        if (input == null) {
            return new LlmDiagnosisInput();
        }
        LlmDiagnosisInput copy = new LlmDiagnosisInput();
        copy.setTask(input.getTask());
        copy.setDeterministicDiagnosis(input.getDeterministicDiagnosis());
        copy.setToolSummaries(input.getToolSummaries() == null ? List.of() : input.getToolSummaries());
        copy.setConstraints(input.getConstraints() == null ? List.of() : input.getConstraints());
        Map<String, List<LlmEvidenceItem>> groups = new LinkedHashMap<>();
        if (input.getEvidenceGroups() != null) {
            input.getEvidenceGroups().forEach((type, items) -> groups.put(type, compactItems(items)));
        }
        copy.setEvidenceGroups(groups);
        return copy;
    }

    private List<LlmEvidenceItem> compactItems(List<LlmEvidenceItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<LlmEvidenceItem> result = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_ITEMS_PER_GROUP, items.size()); i++) {
            LlmEvidenceItem source = items.get(i);
            LlmEvidenceItem item = new LlmEvidenceItem();
            item.setEvidenceRef(source.getEvidenceRef());
            item.setEvidenceType(source.getEvidenceType());
            item.setSourceTool(source.getSourceTool());
            item.setSourceRef(source.getSourceRef());
            item.setTitle(source.getTitle());
            item.setScore(source.getScore());
            item.setMetadata(source.getMetadata() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getMetadata()));
            String content = source.getContent();
            if (StringUtils.hasText(content) && content.length() > MAX_CONTENT_LENGTH) {
                item.setContent(content.substring(0, MAX_CONTENT_LENGTH));
                item.getMetadata().put("truncated", true);
            } else {
                item.setContent(content);
            }
            result.add(item);
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to build LLM prompt JSON", e);
        }
    }
}
