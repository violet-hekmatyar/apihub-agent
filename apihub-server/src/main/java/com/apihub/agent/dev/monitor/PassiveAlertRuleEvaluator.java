package com.apihub.agent.dev.monitor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
class PassiveAlertRuleEvaluator {

    List<PassiveAlertCandidate> evaluate(String apiCode, String callerAppCode, MetricWindowView current,
                                         MetricWindowView baseline, PassiveMonitorConfig config) {
        List<PassiveAlertCandidate> result = new ArrayList<>();
        if (current.getRequestCount() >= config.getMinRequestCount()
                && current.getErrorCount() >= config.getMinErrorCount()
                && current.errorRate() >= config.getHighErrorRateThreshold()) {
            result.add(candidate("HIGH_ERROR_RATE", apiCode, callerAppCode, current, baseline,
                    "requestCount >= minRequestCount AND errorCount >= minErrorCount AND errorRate >= threshold"));
        }
        if (current.getRequestCount() >= config.getMinRequestCount()
                && current.getRateLimitCount() >= 3
                && current.rateLimitRate() >= config.getHighRateLimitThreshold()) {
            result.add(candidate("HIGH_RATE_LIMIT", apiCode, callerAppCode, current, baseline,
                    "requestCount >= minRequestCount AND rateLimitCount >= 3 AND rateLimitRate >= threshold"));
        }
        if ("AUTH_LOGIN".equals(apiCode)
                && current.getRequestCount() >= 15
                && current.getAuthFailureCount() >= 3
                && current.authFailureRate() >= config.getAuthFailureThreshold()) {
            result.add(candidate("AUTH_FAILURE_SPIKE", apiCode, callerAppCode, current, baseline,
                    "apiCode = AUTH_LOGIN AND requestCount >= 15 AND authFailureCount >= 3 AND authFailureRate >= threshold"));
        }
        if (current.getRequestCount() >= config.getMinRequestCount()
                && current.getError5xxCount() >= 3
                && current.error5xxRate() >= config.getHigh5xxRateThreshold()) {
            result.add(candidate("HIGH_5XX_RATE", apiCode, callerAppCode, current, baseline,
                    "requestCount >= minRequestCount AND 5xxCount >= 3 AND 5xxRate >= threshold"));
        }
        int baselineCount = baseline == null ? 0 : baseline.getRequestCount();
        if (current.getRequestCount() >= 30
                && baselineCount > 0
                && (current.getRequestCount() >= baselineCount * 2 || current.getRequestCount() >= 50)) {
            result.add(candidate("TRAFFIC_SPIKE", apiCode, callerAppCode, current, baseline,
                    "currentWindowRequestCount >= 30 AND current >= baseline * 2"));
        }
        if (current.getRequestCount() >= config.getMinRequestCount()
                && current.p95LatencyMs() >= config.getLatencyThresholdMs()) {
            result.add(candidate("HIGH_LATENCY", apiCode, callerAppCode, current, baseline,
                    "requestCount >= minRequestCount AND p95LatencyMs >= threshold"));
        }
        return result;
    }

    private PassiveAlertCandidate candidate(String alertType, String apiCode, String callerAppCode,
                                            MetricWindowView current, MetricWindowView baseline, String threshold) {
        String riskLevel = "CRITICAL";
        if (current.errorRate() < 0.30d && current.error5xxRate() < 0.20d && current.p95LatencyMs() < 3000) {
            riskLevel = "WARNING";
        }
        return new PassiveAlertCandidate(alertType, riskLevel, apiCode, callerAppCode, threshold, current, baseline);
    }
}

record PassiveAlertCandidate(String alertType, String riskLevel, String apiCode, String callerAppCode,
                             String threshold, MetricWindowView currentWindow, MetricWindowView baselineWindow) {
    String dedupKey() {
        return apiCode + "|" + alertType + "|" + (callerAppCode == null ? "ALL" : callerAppCode);
    }
}
