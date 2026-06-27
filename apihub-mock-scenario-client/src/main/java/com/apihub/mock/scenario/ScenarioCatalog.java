package com.apihub.mock.scenario;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
class ScenarioCatalog {

    private final Map<String, ScenarioProfile> profiles = Map.of(
            "NORMAL_DAILY_INSPECTION", new ScenarioProfile("NORMAL_DAILY_INSPECTION", "Normal daily inspection", "Low-noise normal baseline", 60, 300),
            "LECTURE_REGISTRATION_PEAK", new ScenarioProfile("LECTURE_REGISTRATION_PEAK", "Lecture registration peak", "Lecture signup peak with auth increase and rate-limit signals", 300, 900),
            "AUTH_FAILURE_LOCALIZED", new ScenarioProfile("AUTH_FAILURE_LOCALIZED", "Localized auth failure", "Local token/signature failures around AUTH_LOGIN", 180, 480),
            "DOWNSTREAM_TIMEOUT_DEGRADATION", new ScenarioProfile("DOWNSTREAM_TIMEOUT_DEGRADATION", "Downstream timeout degradation", "LIBRARY_BORROW timeout-oriented dependency degradation", 180, 600)
    );

    List<ScenarioProfileView> views() {
        return profiles.values().stream()
                .map(p -> new ScenarioProfileView(p.code(), p.name(), p.description(), List.of("FAST_DEMO", "NORMAL_DEMO"), p.fastDurationSeconds(), p.normalDurationSeconds()))
                .toList();
    }

    ScenarioProfile profile(String code) {
        return profiles.get(normalize(code));
    }

    int durationSeconds(String profileCode, String mode) {
        ScenarioProfile profile = profile(profileCode);
        if (profile == null) {
            return 60;
        }
        return "NORMAL_DEMO".equals(normalize(mode)) ? profile.normalDurationSeconds() : profile.fastDurationSeconds();
    }

    PhaseSpec phase(String profileCode, String mode, int elapsedSecond) {
        String profile = normalize(profileCode);
        int duration = durationSeconds(profile, mode);
        if ("LECTURE_REGISTRATION_PEAK".equals(profile)) {
            double ratio = elapsedSecond / (double) Math.max(1, duration);
            if (ratio < 0.15) {
                return new PhaseSpec("PHASE_A_BASELINE", 1.2, List.of(w("AUTH_LOGIN", 15), w("COURSE_TODAY", 25), w("LECTURE_LIST", 20), w("CAMPUS_NOTICE", 20), w("LIBRARY_BORROW", 20)));
            }
            if (ratio < 0.35) {
                return new PhaseSpec("PHASE_B_WARMUP", 2.0, List.of(w("AUTH_LOGIN", 22), w("LECTURE_LIST", 42), w("LECTURE_REGISTER", 26), w("COURSE_TODAY", 10)));
            }
            if (ratio < 0.70) {
                return new PhaseSpec("PHASE_C_PEAK", 4.0, List.of(w("LECTURE_REGISTER", 55), w("AUTH_LOGIN", 18), w("LECTURE_LIST", 17), w("COURSE_TODAY", 5), w("LIBRARY_BORROW", 5)));
            }
            if (ratio < 0.90) {
                return new PhaseSpec("PHASE_D_RELIEF", 2.0, List.of(w("LECTURE_REGISTER", 42), w("AUTH_LOGIN", 18), w("LECTURE_LIST", 22), w("COURSE_TODAY", 10), w("LIBRARY_BORROW", 8)));
            }
            return new PhaseSpec("PHASE_E_RECOVERY", 1.2, List.of(w("AUTH_LOGIN", 15), w("COURSE_TODAY", 25), w("LECTURE_LIST", 20), w("CAMPUS_NOTICE", 20), w("LIBRARY_BORROW", 20)));
        }
        if ("AUTH_FAILURE_LOCALIZED".equals(profile)) {
            return new PhaseSpec(elapsedSecond < duration * 0.70 ? "PHASE_AUTH_LOCALIZED" : "PHASE_RECOVERY",
                    1.5, List.of(w("AUTH_LOGIN", 55), w("COURSE_TODAY", 15), w("LECTURE_LIST", 15), w("CAMPUS_NOTICE", 15)));
        }
        if ("DOWNSTREAM_TIMEOUT_DEGRADATION".equals(profile)) {
            return new PhaseSpec(elapsedSecond < duration * 0.70 ? "PHASE_TIMEOUT_DEGRADATION" : "PHASE_RECOVERY",
                    1.5, List.of(w("LIBRARY_BORROW", 55), w("COURSE_TODAY", 15), w("LECTURE_LIST", 15), w("AUTH_LOGIN", 15)));
        }
        return new PhaseSpec("PHASE_NORMAL_BASELINE", 1.2, List.of(w("AUTH_LOGIN", 15), w("COURSE_TODAY", 25), w("LECTURE_LIST", 20), w("CAMPUS_NOTICE", 20), w("LIBRARY_BORROW", 20)));
    }

    List<WeightedItem> scenarios(String profileCode, String phaseCode, String apiCode) {
        String profile = normalize(profileCode);
        String phase = normalize(phaseCode);
        String api = normalize(apiCode);
        if ("LECTURE_REGISTRATION_PEAK".equals(profile) && "PHASE_C_PEAK".equals(phase) && "LECTURE_REGISTER".equals(api)) {
            return List.of(w("NORMAL", 88), w("RATE_LIMITED", 7), w("DUPLICATE_REQUEST", 4), w("SOLD_OUT", 1));
        }
        if ("LECTURE_REGISTRATION_PEAK".equals(profile) && "PHASE_D_RELIEF".equals(phase) && "LECTURE_REGISTER".equals(api)) {
            return List.of(w("NORMAL", 94), w("RATE_LIMITED", 3), w("DUPLICATE_REQUEST", 2), w("SOLD_OUT", 1));
        }
        if ("AUTH_LOGIN".equals(api) && ("LECTURE_REGISTRATION_PEAK".equals(profile) || "AUTH_FAILURE_LOCALIZED".equals(profile))) {
            return "AUTH_FAILURE_LOCALIZED".equals(profile)
                    ? List.of(w("NORMAL", 92), w("TOKEN_EXPIRED", 5), w("SIGNATURE_MISMATCH", 3))
                    : List.of(w("NORMAL", 98), w("TOKEN_EXPIRED", 1), w("SIGNATURE_MISMATCH", 1));
        }
        if ("DOWNSTREAM_TIMEOUT_DEGRADATION".equals(profile) && "LIBRARY_BORROW".equals(api) && !"PHASE_RECOVERY".equals(phase)) {
            return List.of(w("NORMAL", 92), w("DOWNSTREAM_TIMEOUT", 8));
        }
        return List.of(w("NORMAL", 100));
    }

    String callerAppCode(String apiCode) {
        return switch (normalize(apiCode)) {
            case "LECTURE_LIST", "LECTURE_REGISTER" -> "LECTURE_PORTAL";
            case "CAMPUS_NOTICE" -> "STUDENT_SERVICE";
            case "LIBRARY_BORROW" -> "LIBRARY_MINI";
            default -> "COURSE_HELPER";
        };
    }

    private WeightedItem w(String code, int weight) {
        return new WeightedItem(code, weight);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

record ScenarioProfile(String code, String name, String description, int fastDurationSeconds, int normalDurationSeconds) {
}

record PhaseSpec(String phaseCode, double targetRps, List<WeightedItem> apiWeights) {
}

record WeightedItem(String code, int weight) {
}
