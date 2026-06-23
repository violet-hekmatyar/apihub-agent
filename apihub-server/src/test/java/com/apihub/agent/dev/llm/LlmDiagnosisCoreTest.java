package com.apihub.agent.dev.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        String raw = mockClient.diagnose(promptBuilder.build(input), input).getRawContent();

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
        LlmDiagnosisParseResult parsed = parser.parse(mockClient.diagnose(promptBuilder.build(input), input).getRawContent());

        LlmDiagnosisValidationResult validation = validator.validate(input, parsed.getOutput());

        assertTrue(parsed.isSuccess());
        assertTrue(validation.isSuccess());
        assertTrue(Boolean.FALSE.equals(parsed.getOutput().getRiskLevelChanged()));
    }

    @Test
    void mockWarningPreservesRiskAndSimulationBoundary() {
        LlmDiagnosisInput input = sampleInput("WARNING", "ABNORMAL_PEAK");
        LlmDiagnosisParseResult parsed = parser.parse(mockClient.diagnose(promptBuilder.build(input), input).getRawContent());

        LlmDiagnosisValidationResult validation = validator.validate(input, parsed.getOutput());

        assertTrue(parsed.isSuccess());
        assertTrue(validation.isSuccess());
        assertTrue(parsed.getOutput().getSimulationBoundaryStatement().contains("development simulation"));
    }

    @Test
    void dashScopeRequestBodyContainsJsonFormatAndNoApiKey() throws Exception {
        DashScopeLlmDiagnosisClient client = new DashScopeLlmDiagnosisClient(objectMapper, resolverWith(new DashScopeLlmProperties()));
        DashScopeLlmProperties properties = new DashScopeLlmProperties();
        properties.setApiKey("secret-key-for-test");
        properties.setModel("qwen-plus");

        String body = client.buildRequestJson(promptBuilder.build(sampleInput("WARNING", "ABNORMAL_PEAK")), properties, true);

        assertTrue(body.contains("\"model\":\"qwen-plus\""));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"response_format\""));
        assertTrue(body.contains("\"json_object\""));
        assertFalse(body.contains("secret-key-for-test"));
    }

    @Test
    void orchestratorMockPathStillWorks() {
        LlmDiagnosisOrchestrator orchestrator = orchestrator();
        LlmDiagnosisResult result = orchestrator.runWithClient(1L, sampleInput("NORMAL", "NORMAL_BASELINE"), false, mockClient, "MOCK");

        assertTrue(result.isSuccess());
        assertEquals("MOCK", result.getProvider());
        assertFalse(result.isFallbackUsed());
        assertNull(result.getPrompt());
    }

    @Test
    void orchestratorDashScopeSuccessWithFakeClientPassesValidation() {
        LlmDiagnosisInput input = sampleInput("WARNING", "ABNORMAL_PEAK");
        LlmDiagnosisOutput output = validOutput("WARNING");
        LlmDiagnosisOrchestrator orchestrator = orchestrator();

        LlmDiagnosisResult result = orchestrator.runWithClient(1L, input, true,
                fakeClient("DASHSCOPE", "qwen-plus", toJson(output), true), "DASHSCOPE");

        assertTrue(result.isSuccess());
        assertEquals("DASHSCOPE", result.getProvider());
        assertEquals("qwen-plus", result.getModel());
        assertFalse(result.isFallbackUsed());
        assertNotNull(result.getPrompt());
    }

    @Test
    void orchestratorDashScopeInvalidJsonUsesFallback() {
        LlmDiagnosisOrchestrator orchestrator = orchestrator();

        LlmDiagnosisResult result = orchestrator.runWithClient(1L, sampleInput("WARNING", "ABNORMAL_PEAK"), false,
                fakeClient("DASHSCOPE", "qwen-plus", "{bad-json", true), "DASHSCOPE");

        assertFalse(result.isSuccess());
        assertTrue(result.isFallbackUsed());
        assertEquals("LLM_PARSE_FAILED", result.getFallbackReason());
        assertNotNull(result.getFallbackSummary());
    }

    @Test
    void orchestratorDashScopeValidationFailureUsesFallback() {
        LlmDiagnosisOutput output = validOutput("NORMAL");
        LlmDiagnosisOrchestrator orchestrator = orchestrator();

        LlmDiagnosisResult result = orchestrator.runWithClient(1L, sampleInput("WARNING", "ABNORMAL_PEAK"), false,
                fakeClient("DASHSCOPE", "qwen-plus", toJson(output), true), "DASHSCOPE");

        assertFalse(result.isSuccess());
        assertTrue(result.isFallbackUsed());
        assertEquals("LLM_VALIDATION_FAILED", result.getFallbackReason());
    }

    @Test
    void dashScopeMissingApiKeyReturnsReadableFailure() {
        DashScopeLlmProperties properties = new DashScopeLlmProperties();
        properties.setApiKey("");
        DashScopeLlmDiagnosisClient client = new DashScopeLlmDiagnosisClient(objectMapper, resolverWith(properties));

        LlmDiagnosisClientResult result = client.diagnose(promptBuilder.build(sampleInput("WARNING", "ABNORMAL_PEAK")), sampleInput("WARNING", "ABNORMAL_PEAK"));

        assertFalse(result.isSuccess());
        assertEquals("DASHSCOPE_API_KEY_MISSING", result.getErrorCode());
        assertFalse(result.getErrorMessage().contains("secret"));
    }

    @Test
    void validatorAllowsSafeNegatedNormalBoundaryAndRecommendations() {
        LlmDiagnosisInput input = sampleInput("NORMAL", "NORMAL_BASELINE");
        LlmDiagnosisOutput output = validOutput("NORMAL");
        output.setSimulationBoundaryStatement("This diagnosis is based on development simulation only; no live-user impact was observed. no production incident, no outage.");
        output.getRecommendations().get(0).setReason("Routine observation only; no outage was observed.");

        LlmDiagnosisValidationResult result = validator.validate(input, output);

        assertTrue(result.isSuccess(), () -> String.join("; ", result.getErrors()));
    }

    @Test
    void validatorRejectsAffirmativeNormalIncidentWording() {
        LlmDiagnosisInput input = sampleInput("NORMAL", "NORMAL_BASELINE");
        LlmDiagnosisOutput output = validOutput("NORMAL");
        output.setExecutiveSummary("A production incident occurred during the baseline.");

        LlmDiagnosisValidationResult result = validator.validate(input, output);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("abnormal wording")));
    }

    @Test
    void dashScopeRetriesDirectThenUsesProxyFallback() {
        DashScopeLlmProperties properties = new DashScopeLlmProperties();
        properties.setApiKey("secret-key-for-test");
        properties.setModel("qwen-plus");
        properties.setDirectRetryCount(2);
        properties.setProxyEnabled(true);
        properties.setProxyHost("127.0.0.1");
        properties.setProxyPort(10808);
        properties.setProxyFallbackEnabled(true);
        FakeHttpClient direct = new FakeHttpClient(List.<Object>of(
                new IOException("Connection reset"),
                new IOException("Connection reset")
        ));
        FakeHttpClient proxy = new FakeHttpClient(List.<Object>of(
                new FakeResponseSpec(200, dashScopeSuccessJson("{\"riskLevel\":\"NORMAL\"}"))
        ));
        DashScopeLlmDiagnosisClient client = new DashScopeLlmDiagnosisClient(objectMapper, resolverWith(properties), direct, proxy);

        LlmDiagnosisClientResult result = client.diagnose(promptBuilder.build(sampleInput("NORMAL", "NORMAL_BASELINE")), sampleInput("NORMAL", "NORMAL_BASELINE"));

        assertTrue(result.isSuccess());
        assertEquals(2, direct.requestBodies.size());
        assertEquals(1, proxy.requestBodies.size());
        assertEquals(3, result.getAttempts().size());
        assertFalse(result.getAttempts().get(0).isProxy());
        assertFalse(result.getAttempts().get(1).isProxy());
        assertTrue(result.getAttempts().get(2).isProxy());
        assertFalse(result.getAttempts().toString().contains("secret-key-for-test"));
    }

    @Test
    void dashScopeHttp400RetriesWithoutResponseFormatInSameChannel() {
        DashScopeLlmProperties properties = new DashScopeLlmProperties();
        properties.setApiKey("secret-key-for-test");
        properties.setModel("qwen-plus");
        FakeHttpClient direct = new FakeHttpClient(List.<Object>of(
                new FakeResponseSpec(400, "{\"error\":\"response_format unsupported\"}"),
                new FakeResponseSpec(200, dashScopeSuccessJson("{\"riskLevel\":\"NORMAL\"}"))
        ));
        DashScopeLlmDiagnosisClient client = new DashScopeLlmDiagnosisClient(objectMapper, resolverWith(properties), direct);

        LlmDiagnosisClientResult result = client.diagnose(promptBuilder.build(sampleInput("NORMAL", "NORMAL_BASELINE")), sampleInput("NORMAL", "NORMAL_BASELINE"));

        assertTrue(result.isSuccess());
        assertEquals(2, direct.requestBodies.size());
        assertTrue(direct.requestBodies.get(0).contains("response_format"));
        assertFalse(direct.requestBodies.get(1).contains("response_format"));
        assertTrue(result.getAttempts().get(0).isResponseFormat());
        assertFalse(result.getAttempts().get(1).isResponseFormat());
    }

    @Test
    void resolverReadsProxyPropertiesWithoutDockerEnvMutation() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("AI_LLM_PROXY_ENABLED", "true")
                .withProperty("AI_LLM_PROXY_HOST", "127.0.0.1")
                .withProperty("AI_LLM_PROXY_PORT", "10808")
                .withProperty("AI_LLM_PROXY_SCHEME", "http")
                .withProperty("AI_LLM_PROXY_FALLBACK_ENABLED", "true")
                .withProperty("AI_LLM_DIRECT_RETRY_COUNT", "2");

        DashScopeLlmProperties properties = new DashScopeLlmPropertiesResolver(environment).resolve();

        assertTrue(properties.isProxyEnabled());
        assertEquals("127.0.0.1", properties.getProxyHost());
        assertEquals(10808, properties.getProxyPort());
        assertEquals("http", properties.getProxyScheme());
        assertTrue(properties.isProxyFallbackEnabled());
        assertEquals(2, properties.getDirectRetryCount());
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private LlmDiagnosisClient fakeClient(String provider, String model, String rawContent, boolean success) {
        return (prompt, input) -> success
                ? LlmDiagnosisClientResult.success(provider, model, rawContent, "req-test", 12L)
                : LlmDiagnosisClientResult.failure(provider, model, "TEST_ERROR", "test error", 12L);
    }

    private LlmDiagnosisOrchestrator orchestrator() {
        return new LlmDiagnosisOrchestrator(null, promptBuilder, mockClient, null, parser, validator);
    }

    private DashScopeLlmPropertiesResolver resolverWith(DashScopeLlmProperties properties) {
        return new DashScopeLlmPropertiesResolver(null) {
            @Override
            public DashScopeLlmProperties resolve() {
                return properties;
            }
        };
    }

    private String dashScopeSuccessJson(String content) {
        return """
                {
                  "id": "req-test",
                  "choices": [
                    {
                      "message": {
                        "content": "%s"
                      }
                    }
                  ]
                }
                """.formatted(content.replace("\"", "\\\""));
    }

    private static final class FakeResponseSpec {
        private final int statusCode;
        private final String body;

        private FakeResponseSpec(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static final class FakeHttpClient extends HttpClient {
        private final Queue<Object> outcomes;
        private final List<String> requestBodies = new ArrayList<>();

        private FakeHttpClient(List<Object> outcomes) {
            this.outcomes = new ArrayDeque<>(outcomes);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            requestBodies.add(readBody(request));
            Object outcome = outcomes.isEmpty() ? new IOException("No fake response configured") : outcomes.remove();
            if (outcome instanceof IOException ioException) {
                throw ioException;
            }
            if (outcome instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            FakeResponseSpec spec = (FakeResponseSpec) outcome;
            @SuppressWarnings("unchecked")
            T body = (T) spec.body;
            return new FakeHttpResponse<>(spec.statusCode, body, request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        private String readBody(HttpRequest request) {
            Optional<HttpRequest.BodyPublisher> publisher = request.bodyPublisher();
            if (publisher.isEmpty()) {
                return "";
            }
            BodySubscriber subscriber = new BodySubscriber();
            publisher.get().subscribe(subscriber);
            return subscriber.body();
        }
    }

    private static final class BodySubscriber implements Flow.Subscriber<ByteBuffer> {
        private final StringBuilder builder = new StringBuilder();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            builder.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }

        private String body() {
            return builder.toString();
        }
    }

    private static final class FakeHttpResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final T body;
        private final HttpRequest request;

        private FakeHttpResponse(int statusCode, T body, HttpRequest request) {
            this.statusCode = statusCode;
            this.body = body;
            this.request = request;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Collections.emptyMap(), (name, value) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
