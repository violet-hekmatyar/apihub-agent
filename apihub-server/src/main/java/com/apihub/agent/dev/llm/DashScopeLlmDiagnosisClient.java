package com.apihub.agent.dev.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DashScopeLlmDiagnosisClient implements LlmDiagnosisClient {

    private static final String PROVIDER = "DASHSCOPE";
    private final ObjectMapper objectMapper;
    private final DashScopeLlmPropertiesResolver propertiesResolver;
    private final HttpClient httpClient;
    private final HttpClient proxyHttpClientForTest;

    @Autowired
    public DashScopeLlmDiagnosisClient(ObjectMapper objectMapper, DashScopeLlmPropertiesResolver propertiesResolver) {
        this(objectMapper, propertiesResolver, HttpClient.newHttpClient());
    }

    DashScopeLlmDiagnosisClient(ObjectMapper objectMapper,
                                DashScopeLlmPropertiesResolver propertiesResolver,
                                HttpClient httpClient) {
        this(objectMapper, propertiesResolver, httpClient, null);
    }

    DashScopeLlmDiagnosisClient(ObjectMapper objectMapper,
                                DashScopeLlmPropertiesResolver propertiesResolver,
                                HttpClient httpClient,
                                HttpClient proxyHttpClientForTest) {
        this.objectMapper = objectMapper;
        this.propertiesResolver = propertiesResolver;
        this.httpClient = httpClient;
        this.proxyHttpClientForTest = proxyHttpClientForTest;
    }

    @Override
    public LlmDiagnosisClientResult diagnose(LlmDiagnosisPrompt prompt, LlmDiagnosisInput input) {
        long started = System.nanoTime();
        DashScopeLlmProperties properties = propertiesResolver.resolve();
        if (!properties.hasApiKey()) {
            return LlmDiagnosisClientResult.failure(PROVIDER, properties.getModel(), "DASHSCOPE_API_KEY_MISSING",
                    "DASHSCOPE_API_KEY is not configured; deterministic fallback is required.", elapsedMs(started));
        }
        List<LlmDiagnosisAttempt> attempts = new ArrayList<>();
        LlmDiagnosisClientResult last = null;
        int directAttempts = Math.max(1, properties.getDirectRetryCount());
        for (int i = 1; i <= directAttempts; i++) {
            last = attempt("DIRECT attempt " + i, directClient(), false, prompt, properties, true, started, attempts);
            if (last.isSuccess()) {
                return withAttempts(last, attempts);
            }
            if ("DASHSCOPE_HTTP_400".equals(last.getErrorCode())) {
                LlmDiagnosisClientResult retryWithoutFormat = attempt("DIRECT attempt " + i + " response_format fallback",
                        directClient(), false, prompt, properties, false, started, attempts);
                last = retryWithoutFormat;
                if (retryWithoutFormat.isSuccess()) {
                    return withAttempts(retryWithoutFormat, attempts);
                }
            }
            if (!isRetryable(last)) {
                break;
            }
        }
        if (properties.isProxyFallbackEnabled() && properties.isProxyEnabled() && hasProxyConfig(properties)) {
            LlmDiagnosisClientResult proxyResult = attempt("PROXY fallback", proxyClient(properties), true,
                    prompt, properties, true, started, attempts);
            last = proxyResult;
            if (proxyResult.isSuccess()) {
                return withAttempts(proxyResult, attempts);
            }
            if ("DASHSCOPE_HTTP_400".equals(proxyResult.getErrorCode())) {
                LlmDiagnosisClientResult proxyWithoutFormat = attempt("PROXY fallback response_format fallback",
                        proxyClient(properties), true, prompt, properties, false, started, attempts);
                last = proxyWithoutFormat;
                if (proxyWithoutFormat.isSuccess()) {
                    return withAttempts(proxyWithoutFormat, attempts);
                }
            }
        } else if (properties.isProxyFallbackEnabled()) {
            attempts.add(new LlmDiagnosisAttempt("PROXY fallback skipped", true, true, null,
                    "PROXY_NOT_CONFIGURED", "Proxy fallback skipped because proxy is disabled or host/port is missing.", 0));
        }
        return withAttempts(last == null
                ? LlmDiagnosisClientResult.failure(PROVIDER, properties.getModel(), "DASHSCOPE_CLIENT_ERROR",
                "DashScope call failed before any attempt was completed.", elapsedMs(started))
                : last, attempts);
    }

    private LlmDiagnosisClientResult attempt(String attemptName,
                                             HttpClient client,
                                             boolean useProxy,
                                             LlmDiagnosisPrompt prompt,
                                             DashScopeLlmProperties properties,
                                             boolean includeResponseFormat,
                                             long started,
                                             List<LlmDiagnosisAttempt> attempts) {
        long attemptStarted = System.nanoTime();
        try {
            LlmDiagnosisClientResult result = callDashScope(client, prompt, properties, includeResponseFormat, started);
            attempts.add(new LlmDiagnosisAttempt(attemptName, useProxy, includeResponseFormat,
                    statusCode(result.getErrorCode()), result.getErrorCode(), sanitize(result.getErrorMessage()),
                    elapsedMs(attemptStarted)));
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LlmDiagnosisClientResult result = LlmDiagnosisClientResult.failure(PROVIDER, properties.getModel(),
                    "DASHSCOPE_CLIENT_ERROR", sanitize(e.getMessage()), elapsedMs(started));
            attempts.add(new LlmDiagnosisAttempt(attemptName, useProxy, includeResponseFormat, null,
                    result.getErrorCode(), result.getErrorMessage(), elapsedMs(attemptStarted)));
            return result;
        } catch (IOException e) {
            LlmDiagnosisClientResult result = LlmDiagnosisClientResult.failure(PROVIDER, properties.getModel(),
                    "DASHSCOPE_CLIENT_ERROR", sanitize(e.getMessage()), elapsedMs(started));
            attempts.add(new LlmDiagnosisAttempt(attemptName, useProxy, includeResponseFormat, null,
                    result.getErrorCode(), result.getErrorMessage(), elapsedMs(attemptStarted)));
            return result;
        } catch (Exception e) {
            LlmDiagnosisClientResult result = LlmDiagnosisClientResult.failure(PROVIDER, properties.getModel(),
                    "DASHSCOPE_CLIENT_ERROR", sanitize(e.getMessage()), elapsedMs(started));
            attempts.add(new LlmDiagnosisAttempt(attemptName, useProxy, includeResponseFormat, null,
                    result.getErrorCode(), result.getErrorMessage(), elapsedMs(attemptStarted)));
            return result;
        }
    }

    LlmDiagnosisClientResult callDashScope(HttpClient client,
                                           LlmDiagnosisPrompt prompt,
                                           DashScopeLlmProperties properties,
                                           boolean includeResponseFormat,
                                           long started) throws Exception {
        String body = buildRequestJson(prompt, properties, includeResponseFormat);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint(properties.getBaseUrl())))
                .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return LlmDiagnosisClientResult.failure(PROVIDER, properties.getModel(),
                    "DASHSCOPE_HTTP_" + response.statusCode(),
                    "DashScope returned HTTP " + response.statusCode() + ": " + truncate(sanitize(responseBody), 500),
                    elapsedMs(started));
        }
        return parseSuccessResponse(responseBody, properties, started);
    }

    private HttpClient directClient() {
        return httpClient;
    }

    private HttpClient proxyClient(DashScopeLlmProperties properties) {
        if (proxyHttpClientForTest != null) {
            return proxyHttpClientForTest;
        }
        return HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress(properties.getProxyHost(), properties.getProxyPort())))
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build();
    }

    private boolean hasProxyConfig(DashScopeLlmProperties properties) {
        return properties != null
                && StringUtils.hasText(properties.getProxyHost())
                && properties.getProxyPort() > 0;
    }

    String buildRequestJson(LlmDiagnosisPrompt prompt, DashScopeLlmProperties properties, boolean includeResponseFormat) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getModel());
        request.put("messages", List.of(
                Map.of("role", "system", "content", safeText(prompt == null ? null : prompt.getSystemPrompt())),
                Map.of("role", "user", "content", safeText(prompt == null ? null : prompt.getDiagnosisPrompt()))
        ));
        request.put("temperature", properties.getTemperature());
        if (includeResponseFormat) {
            request.put("response_format", Map.of("type", "json_object"));
        }
        return objectMapper.writeValueAsString(request);
    }

    private LlmDiagnosisClientResult parseSuccessResponse(String responseBody,
                                                          DashScopeLlmProperties properties,
                                                          long started) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String requestId = firstText(root, "request_id", "requestId", "id");
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return LlmDiagnosisClientResult.failure(PROVIDER, properties.getModel(), "DASHSCOPE_EMPTY_CHOICES",
                    "DashScope response did not contain choices.", elapsedMs(started));
        }
        String content = choices.get(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            return LlmDiagnosisClientResult.failure(PROVIDER, properties.getModel(), "DASHSCOPE_EMPTY_CONTENT",
                    "DashScope response content was empty.", elapsedMs(started));
        }
        return LlmDiagnosisClientResult.success(PROVIDER, properties.getModel(), content, requestId, elapsedMs(started));
    }

    private String endpoint(String baseUrl) {
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://dashscope.aliyuncs.com/compatible-mode/v1";
        return value.endsWith("/") ? value + "chat/completions" : value + "/chat/completions";
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(?i)(api[_-]?key|authorization|bearer)\\s*[:=]\\s*[^\\s,}]+", "$1=***");
    }

    private boolean isRetryable(LlmDiagnosisClientResult result) {
        if (result == null) {
            return false;
        }
        String code = result.getErrorCode();
        if (!StringUtils.hasText(code)) {
            return false;
        }
        if ("DASHSCOPE_CLIENT_ERROR".equals(code)) {
            String message = result.getErrorMessage() == null ? "" : result.getErrorMessage().toLowerCase();
            return message.contains("connection reset") || message.contains("timeout") || message.contains("timed out")
                    || message.contains("connection") || message.contains("reset");
        }
        Integer status = statusCode(code);
        return status != null && (status == 429 || status == 500 || status == 502 || status == 503 || status == 504);
    }

    private Integer statusCode(String errorCode) {
        if (!StringUtils.hasText(errorCode) || !errorCode.startsWith("DASHSCOPE_HTTP_")) {
            return null;
        }
        try {
            return Integer.parseInt(errorCode.substring("DASHSCOPE_HTTP_".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LlmDiagnosisClientResult withAttempts(LlmDiagnosisClientResult result, List<LlmDiagnosisAttempt> attempts) {
        result.setAttempts(attempts);
        return result;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
