package com.apihub.agent.dev.llm;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DashScopeLlmPropertiesResolver {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_MODEL = "qwen-plus";
    private final Environment environment;

    public DashScopeLlmPropertiesResolver(Environment environment) {
        this.environment = environment;
    }

    public DashScopeLlmProperties resolve() {
        Map<String, String> dotenv = readDotenv();
        DashScopeLlmProperties properties = new DashScopeLlmProperties();
        properties.setApiKey(firstValue(dotenv, "DASHSCOPE_API_KEY"));
        properties.setBaseUrl(firstValue(dotenv, "AI_BASE_URL", DEFAULT_BASE_URL));
        properties.setModel(firstValue(dotenv, "AI_CHAT_MODEL", DEFAULT_MODEL));
        properties.setTimeoutSeconds(parseInt(firstValue(dotenv, "AI_LLM_TIMEOUT_SECONDS", "60"), 60));
        properties.setTemperature(parseDouble(firstValue(dotenv, "AI_LLM_TEMPERATURE", "0.1"), 0.1d));
        properties.setProxyEnabled(parseBoolean(firstValue(dotenv, "AI_LLM_PROXY_ENABLED", "false"), false));
        properties.setProxyHost(firstValue(dotenv, "AI_LLM_PROXY_HOST"));
        properties.setProxyPort(parseInt(firstValue(dotenv, "AI_LLM_PROXY_PORT", "0"), 0));
        properties.setProxyScheme(firstValue(dotenv, "AI_LLM_PROXY_SCHEME", "http"));
        properties.setProxyFallbackEnabled(parseBoolean(firstValue(dotenv, "AI_LLM_PROXY_FALLBACK_ENABLED", "true"), true));
        properties.setDirectRetryCount(parseInt(firstValue(dotenv, "AI_LLM_DIRECT_RETRY_COUNT", "2"), 2));
        return properties;
    }

    private String firstValue(Map<String, String> dotenv, String key) {
        return firstValue(dotenv, key, null);
    }

    private String firstValue(Map<String, String> dotenv, String key, String fallback) {
        String value = environment.getProperty(key);
        if (!StringUtils.hasText(value)) {
            value = System.getProperty(key);
        }
        if (!StringUtils.hasText(value)) {
            value = System.getenv(key);
        }
        if (!StringUtils.hasText(value)) {
            value = dotenv.get(key);
        }
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Map<String, String> readDotenv() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Path path : candidateDotenvPaths()) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path)) {
                    parseDotenvLine(line, values);
                }
                return values;
            } catch (IOException ignored) {
                return values;
            }
        }
        return values;
    }

    private List<Path> candidateDotenvPaths() {
        Path userDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        return List.of(
                userDir.resolve("docker/.env"),
                userDir.resolve("../docker/.env").normalize(),
                Path.of("D:/apihub-agent-dev/docker/.env")
        );
    }

    private void parseDotenvLine(String line, Map<String, String> values) {
        if (!StringUtils.hasText(line)) {
            return;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("#") || !trimmed.contains("=")) {
            return;
        }
        int index = trimmed.indexOf('=');
        String key = trimmed.substring(0, index).trim();
        String value = trimmed.substring(index + 1).trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        if (StringUtils.hasText(key)) {
            values.put(key, value);
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return switch (value.trim().toLowerCase()) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> fallback;
        };
    }
}
