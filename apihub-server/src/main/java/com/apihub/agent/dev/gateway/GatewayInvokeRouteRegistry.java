package com.apihub.agent.dev.gateway;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GatewayInvokeRouteRegistry {

    private final Map<String, GatewayInvokeRoute> routes = new LinkedHashMap<>();

    public GatewayInvokeRouteRegistry() {
        register("AUTH_LOGIN", "POST", "/mock-provider/auth/login", "COURSE_HELPER");
        register("COURSE_TODAY", "GET", "/mock-provider/course/today", "COURSE_HELPER");
        register("LECTURE_LIST", "GET", "/mock-provider/lecture/list", "LECTURE_PORTAL");
        register("LECTURE_REGISTER", "POST", "/mock-provider/lecture/register", "LECTURE_PORTAL");
        register("CAMPUS_NOTICE", "GET", "/mock-provider/notice/list", "STUDENT_SERVICE");
        register("VENUE_RESERVE", "POST", "/mock-provider/venue/reserve", "CLUB_ACTIVITY");
        register("LIBRARY_BORROW", "GET", "/mock-provider/library/borrow", "LIBRARY_MINI");
    }

    public GatewayInvokeRoute get(String apiCode) {
        if (apiCode == null) {
            return null;
        }
        return routes.get(apiCode.trim().toUpperCase(Locale.ROOT));
    }

    private void register(String apiCode, String method, String path, String defaultAppCode) {
        routes.put(apiCode, new GatewayInvokeRoute(apiCode, method, path, defaultAppCode));
    }
}
