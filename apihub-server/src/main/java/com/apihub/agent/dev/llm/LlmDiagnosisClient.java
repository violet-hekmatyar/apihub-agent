package com.apihub.agent.dev.llm;

public interface LlmDiagnosisClient {

    LlmDiagnosisClientResult diagnose(LlmDiagnosisPrompt prompt, LlmDiagnosisInput input);
}
