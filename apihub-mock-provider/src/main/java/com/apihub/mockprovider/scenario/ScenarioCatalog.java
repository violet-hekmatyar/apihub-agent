package com.apihub.mockprovider.scenario;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ScenarioCatalog {

    static final Map<String, String> DEFAULT_APP_CODES = Map.of(
            "AUTH_LOGIN", "COURSE_HELPER",
            "COURSE_TODAY", "COURSE_HELPER",
            "LECTURE_LIST", "LECTURE_PORTAL",
            "LECTURE_REGISTER", "LECTURE_PORTAL",
            "CAMPUS_NOTICE", "STUDENT_SERVICE",
            "VENUE_RESERVE", "CLUB_ACTIVITY",
            "LIBRARY_BORROW", "LIBRARY_MINI"
    );

    private static final Map<String, ScenarioDefinition> DEFINITIONS = Map.of(
            "NORMAL_DAY", new ScenarioDefinition("NORMAL_DAY", List.of(
                    api("AUTH_LOGIN", 15, normal(96), ms("SIGNATURE_MISMATCH", 1), ms("TOKEN_EXPIRED", 1), ms("SLOW_RESPONSE", 2)),
                    api("COURSE_TODAY", 25, normal(96), ms("CACHE_MISS", 2), ms("SLOW_RESPONSE", 2)),
                    api("CAMPUS_NOTICE", 20, normal(97), ms("CACHE_MISS", 2), ms("HOT_NOTICE", 1)),
                    api("LECTURE_LIST", 15, normal(97), ms("HOT_READ", 2), ms("SLOW_RESPONSE", 1)),
                    api("LIBRARY_BORROW", 15, normal(96), ms("SLOW_RESPONSE", 2), ms("SERVICE_ERROR", 2)),
                    api("VENUE_RESERVE", 10, normal(97), ms("RATE_LIMITED", 1), ms("SLOW_RESPONSE", 2))
            )),
            "LECTURE_REGISTER_PEAK", new ScenarioDefinition("LECTURE_REGISTER_PEAK", List.of(
                    api("AUTH_LOGIN", 20, normal(92), ms("SIGNATURE_MISMATCH", 4), ms("TOKEN_EXPIRED", 4)),
                    api("LECTURE_LIST", 25, normal(95), ms("HOT_READ", 3), ms("SLOW_RESPONSE", 2)),
                    api("LECTURE_REGISTER", 55, normal(72), ms("RATE_LIMITED", 14), ms("DUPLICATE_REQUEST", 6), ms("SOLD_OUT", 5), ms("SLOW_RESPONSE", 3))
            )),
            "AUTH_FAILURE_SPIKE", new ScenarioDefinition("AUTH_FAILURE_SPIKE", List.of(
                    api("AUTH_LOGIN", 80, normal(75), ms("SIGNATURE_MISMATCH", 12), ms("TOKEN_EXPIRED", 6), ms("TIMESTAMP_EXPIRED", 4), ms("NONCE_REPLAY", 3)),
                    api("COURSE_TODAY", 10, normal(95), ms("CACHE_MISS", 3), ms("SLOW_RESPONSE", 2)),
                    api("LECTURE_LIST", 10, normal(95), ms("HOT_READ", 3), ms("SLOW_RESPONSE", 2))
            )),
            "VENUE_RESERVE_CONFLICT", new ScenarioDefinition("VENUE_RESERVE_CONFLICT", List.of(
                    api("AUTH_LOGIN", 15, normal(95), ms("TOKEN_EXPIRED", 3), ms("SIGNATURE_MISMATCH", 2)),
                    api("VENUE_RESERVE", 70, normal(70), ms("RESERVATION_CONFLICT", 15), ms("DUPLICATE_REQUEST", 8), ms("RATE_LIMITED", 5), ms("SLOW_RESPONSE", 2)),
                    api("CAMPUS_NOTICE", 15, normal(98), ms("HOT_NOTICE", 1), ms("CACHE_MISS", 1))
            )),
            "LIBRARY_DEPENDENCY_OUTAGE", new ScenarioDefinition("LIBRARY_DEPENDENCY_OUTAGE", List.of(
                    api("LIBRARY_BORROW", 75, normal(65), ms("DOWNSTREAM_TIMEOUT", 18), ms("DEPENDENCY_UNAVAILABLE", 12), ms("SERVICE_ERROR", 5)),
                    api("AUTH_LOGIN", 15, normal(95), ms("TOKEN_EXPIRED", 3), ms("SIGNATURE_MISMATCH", 2)),
                    api("CAMPUS_NOTICE", 10, normal(98), ms("HOT_NOTICE", 1), ms("CACHE_MISS", 1))
            ))
    );

    private ScenarioCatalog() {
    }

    static Optional<ScenarioDefinition> find(String scenarioId) {
        return Optional.ofNullable(DEFINITIONS.get(scenarioId));
    }

    private static ScenarioDefinition.ApiWeight api(String apiCode, int weight, ScenarioDefinition.MockScenarioWeight... mockScenarios) {
        return new ScenarioDefinition.ApiWeight(apiCode, weight, List.of(mockScenarios));
    }

    private static ScenarioDefinition.MockScenarioWeight normal(int weight) {
        return ms("NORMAL", weight);
    }

    private static ScenarioDefinition.MockScenarioWeight ms(String mockScenario, int weight) {
        return new ScenarioDefinition.MockScenarioWeight(mockScenario, weight);
    }
}
