package com.apihub.agent.dev.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LlmDiagnosisOutputParser {

    private final ObjectMapper objectMapper;

    public LlmDiagnosisOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LlmDiagnosisParseResult parse(String rawResponse) {
        LlmDiagnosisParseResult result = new LlmDiagnosisParseResult();
        result.setRawJson(rawResponse);
        if (!StringUtils.hasText(rawResponse)) {
            result.getErrors().add("LLM response is empty");
            return result;
        }
        String json = rawResponse.trim();
        if (json.startsWith("```")) {
            json = stripFence(json);
            result.getWarnings().add("Markdown fence was stripped from LLM response");
        }
        try {
            result.setOutput(objectMapper.readValue(json, LlmDiagnosisOutput.class));
            result.setRawJson(json);
            result.setSuccess(true);
        } catch (Exception e) {
            result.getErrors().add("Invalid LLM JSON: " + e.getMessage());
        }
        return result;
    }

    private String stripFence(String value) {
        String text = value.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
}
