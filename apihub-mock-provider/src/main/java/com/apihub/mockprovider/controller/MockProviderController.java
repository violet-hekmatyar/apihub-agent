package com.apihub.mockprovider.controller;

import com.apihub.mockprovider.common.MockResponse;
import com.apihub.mockprovider.common.ResponseSupport;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-provider")
public class MockProviderController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/health")
    public ResponseEntity<MockResponse<Map<String, Object>>> health(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader) {
        String traceId = ResponseSupport.traceId(traceHeader);
        return ResponseSupport.ok(Map.of(
                "app", "apihub-mock-provider",
                "status", "UP",
                "time", TIME_FORMATTER.format(LocalDateTime.now()),
                "javaVersion", System.getProperty("java.version")
        ), traceId);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<MockResponse<Map<String, Object>>> authLogin(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String scenarioHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        String traceId = ResponseSupport.traceId(traceHeader);
        String scenario = scenario(scenarioHeader, body);

        return switch (scenario) {
            case "SIGNATURE_MISMATCH" -> ResponseSupport.error(HttpStatus.FORBIDDEN, "signature mismatch", traceId);
            case "TOKEN_EXPIRED" -> ResponseSupport.error(HttpStatus.UNAUTHORIZED, "token expired", traceId);
            case "TIMESTAMP_EXPIRED" -> ResponseSupport.error(HttpStatus.FORBIDDEN, "timestamp expired", traceId);
            case "NONCE_REPLAY" -> ResponseSupport.error(HttpStatus.FORBIDDEN, "nonce replay", traceId);
            case "UNKNOWN_APP" -> ResponseSupport.error(HttpStatus.FORBIDDEN, "unknown appCode", traceId);
            default -> ResponseSupport.ok(Map.of(
                    "studentNo", stringValue(body, "studentNo", "2023001001"),
                    "displayName", "Mock Student",
                    "accessToken", "mock_token_AUTH_LOGIN_001",
                    "expiresIn", 7200,
                    "tokenType", "Bearer"
            ), traceId);
        };
    }

    @GetMapping("/course/today")
    public ResponseEntity<MockResponse<Map<String, Object>>> courseToday(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String scenarioHeader,
            @RequestParam(value = "studentNo", required = false, defaultValue = "2023001001") String studentNo,
            @RequestParam(value = "date", required = false, defaultValue = "2026-06-19") String date,
            @RequestParam(value = "mockScenario", required = false) String scenarioParam) {
        String traceId = ResponseSupport.traceId(traceHeader);
        String scenario = ResponseSupport.scenario(scenarioHeader, scenarioParam);

        return switch (scenario) {
            case "COURSE_SYSTEM_TIMEOUT" -> ResponseSupport.error(HttpStatus.GATEWAY_TIMEOUT, "course system timeout", traceId);
            case "SLOW_RESPONSE" -> {
                sleep(900);
                yield ResponseSupport.ok(courseData(studentNo, date, true, false, "normal"), traceId);
            }
            case "DOWNSTREAM_SLOW" -> ResponseSupport.ok(courseData(studentNo, date, true, true, "downstream slow"), traceId);
            case "CACHE_MISS" -> {
                sleep(250);
                yield ResponseSupport.ok(courseData(studentNo, date, false, false, "cache miss"), traceId);
            }
            default -> ResponseSupport.ok(courseData(studentNo, date, true, false, "normal"), traceId);
        };
    }

    @GetMapping("/lecture/list")
    public ResponseEntity<MockResponse<Map<String, Object>>> lectureList(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String scenarioHeader,
            @RequestParam(value = "date", required = false, defaultValue = "2026-06-19") String date,
            @RequestParam(value = "mockScenario", required = false) String scenarioParam) {
        String traceId = ResponseSupport.traceId(traceHeader);
        String scenario = ResponseSupport.scenario(scenarioHeader, scenarioParam);

        return switch (scenario) {
            case "SERVICE_BUSY" -> ResponseSupport.error(HttpStatus.SERVICE_UNAVAILABLE, "lecture service busy", traceId);
            case "SLOW_RESPONSE" -> {
                sleep(450);
                yield ResponseSupport.ok(lectureData(date, false, false), traceId);
            }
            case "HOT_READ" -> ResponseSupport.ok(lectureData(date, false, true), traceId);
            default -> ResponseSupport.ok(lectureData(date, true, false), traceId);
        };
    }

    @PostMapping("/lecture/register")
    public ResponseEntity<MockResponse<Map<String, Object>>> lectureRegister(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String scenarioHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        String traceId = ResponseSupport.traceId(traceHeader);
        String scenario = scenario(scenarioHeader, body);

        return switch (scenario) {
            case "RATE_LIMITED" -> ResponseSupport.error(HttpStatus.TOO_MANY_REQUESTS, "too many requests", traceId);
            case "DUPLICATE_REQUEST" -> ResponseSupport.error(HttpStatus.CONFLICT, "duplicate request", traceId);
            case "SOLD_OUT" -> ResponseSupport.error(HttpStatus.CONFLICT, "quota full, lecture sold out", traceId);
            case "IDEMPOTENCY_KEY_MISSING" -> ResponseSupport.error(HttpStatus.BAD_REQUEST, "idempotencyKey is required", traceId);
            case "SERVICE_BUSY" -> ResponseSupport.error(HttpStatus.SERVICE_UNAVAILABLE, "lecture register service busy", traceId);
            case "SLOW_RESPONSE" -> {
                sleep(900);
                yield ResponseSupport.ok(lectureRegisterData(body), traceId);
            }
            default -> ResponseSupport.ok(lectureRegisterData(body), traceId);
        };
    }

    @GetMapping("/notice/list")
    public ResponseEntity<MockResponse<Map<String, Object>>> noticeList(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String scenarioHeader,
            @RequestParam(value = "category", required = false, defaultValue = "exam") String category,
            @RequestParam(value = "mockScenario", required = false) String scenarioParam) {
        String traceId = ResponseSupport.traceId(traceHeader);
        String scenario = ResponseSupport.scenario(scenarioHeader, scenarioParam);

        return switch (scenario) {
            case "SERVICE_BUSY" -> ResponseSupport.error(HttpStatus.SERVICE_UNAVAILABLE, "notice service busy", traceId);
            case "SLOW_RESPONSE" -> {
                sleep(500);
                yield ResponseSupport.ok(noticeData(category, true, false), traceId);
            }
            case "CACHE_MISS" -> {
                sleep(250);
                yield ResponseSupport.ok(noticeData(category, false, false), traceId);
            }
            case "HOT_NOTICE" -> ResponseSupport.ok(noticeData(category, true, true), traceId);
            default -> ResponseSupport.ok(noticeData(category, true, false), traceId);
        };
    }

    @PostMapping("/venue/reserve")
    public ResponseEntity<MockResponse<Map<String, Object>>> venueReserve(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String scenarioHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        String traceId = ResponseSupport.traceId(traceHeader);
        String scenario = scenario(scenarioHeader, body);

        return switch (scenario) {
            case "RESERVATION_CONFLICT" -> ResponseSupport.error(HttpStatus.CONFLICT, "reservation conflict", traceId);
            case "DUPLICATE_REQUEST" -> ResponseSupport.error(HttpStatus.CONFLICT, "duplicate request", traceId);
            case "IDEMPOTENCY_KEY_MISSING" -> ResponseSupport.error(HttpStatus.BAD_REQUEST, "idempotencyKey is required", traceId);
            case "RATE_LIMITED" -> ResponseSupport.error(HttpStatus.TOO_MANY_REQUESTS, "too many requests", traceId);
            case "SLOW_RESPONSE" -> {
                sleep(800);
                yield ResponseSupport.ok(venueReserveData(body), traceId);
            }
            default -> ResponseSupport.ok(venueReserveData(body), traceId);
        };
    }

    @GetMapping("/library/borrow")
    public ResponseEntity<MockResponse<Map<String, Object>>> libraryBorrow(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String scenarioHeader,
            @RequestParam(value = "studentNo", required = false, defaultValue = "2023001001") String studentNo,
            @RequestParam(value = "mockScenario", required = false) String scenarioParam) {
        String traceId = ResponseSupport.traceId(traceHeader);
        String scenario = ResponseSupport.scenario(scenarioHeader, scenarioParam);

        return switch (scenario) {
            case "DOWNSTREAM_TIMEOUT" -> ResponseSupport.error(HttpStatus.GATEWAY_TIMEOUT, "library downstream timeout", traceId);
            case "DEPENDENCY_UNAVAILABLE" -> ResponseSupport.error(HttpStatus.SERVICE_UNAVAILABLE, "library service unavailable", traceId);
            case "SERVICE_ERROR" -> ResponseSupport.error(HttpStatus.INTERNAL_SERVER_ERROR, "library service error", traceId);
            case "SLOW_RESPONSE" -> {
                sleep(850);
                yield ResponseSupport.ok(libraryBorrowData(studentNo), traceId);
            }
            default -> ResponseSupport.ok(libraryBorrowData(studentNo), traceId);
        };
    }

    private static Map<String, Object> courseData(String studentNo, String date, boolean cacheHit,
            boolean downstreamSlow, String scenarioHint) {
        return Map.of(
                "studentNo", studentNo,
                "date", date,
                "cacheHit", cacheHit,
                "downstreamSlow", downstreamSlow,
                "scenarioHint", scenarioHint,
                "courses", List.of(
                        Map.of(
                                "courseName", "Software Engineering",
                                "teacher", "Teacher Wang",
                                "timeRange", "08:30-10:10",
                                "room", "Teaching Building A301",
                                "weekNo", 16
                        ),
                        Map.of(
                                "courseName", "Introduction to Artificial Intelligence",
                                "teacher", "Teacher Li",
                                "timeRange", "14:30-16:10",
                                "room", "Lab Building B204",
                                "weekNo", 16
                        )
                )
        );
    }

    private static Map<String, Object> lectureData(String date, boolean cacheHit, boolean hotRead) {
        return Map.of(
                "date", date,
                "cacheHit", cacheHit,
                "hotRead", hotRead,
                "lectures", List.of(Map.of(
                        "lectureId", "lec_20260619_ai_001",
                        "title", "Large Model Application Development Practice",
                        "location", "Academic Hall 201",
                        "startTime", "2026-06-19 19:00:00",
                        "quota", 120,
                        "registeredCount", 86,
                        "registerStatus", "OPEN"
                ))
        );
    }

    private static Map<String, Object> lectureRegisterData(Map<String, Object> body) {
        return Map.of(
                "lectureId", stringValue(body, "lectureId", "lec_20260619_ai_001"),
                "studentNo", stringValue(body, "studentNo", "2023001001"),
                "registerStatus", "SUCCESS",
                "ticketNo", "ticket_mock_001"
        );
    }

    private static Map<String, Object> noticeData(String category, boolean cacheHit, boolean hotNotice) {
        return Map.of(
                "category", category,
                "cacheHit", cacheHit,
                "hotNotice", hotNotice,
                "notices", List.of(Map.of(
                        "noticeId", "notice_20260619_exam_001",
                        "title", "Final Exam Schedule Notice",
                        "publishTime", "2026-06-19 09:00:00",
                        "publisher", "Academic Affairs Office"
                ))
        );
    }

    private static Map<String, Object> venueReserveData(Map<String, Object> body) {
        return Map.of(
                "venueId", stringValue(body, "venueId", "venue_report_hall_201"),
                "studentNo", stringValue(body, "studentNo", "2023001001"),
                "reserveDate", stringValue(body, "reserveDate", "2026-06-20"),
                "timeRange", stringValue(body, "timeRange", "19:00-21:00"),
                "reserveStatus", "SUCCESS",
                "reserveNo", "reserve_mock_001"
        );
    }

    private static Map<String, Object> libraryBorrowData(String studentNo) {
        return Map.of(
                "studentNo", studentNo,
                "borrowRecords", List.of(Map.of(
                        "bookName", "Understanding the Java Virtual Machine",
                        "borrowDate", "2026-06-01",
                        "dueDate", "2026-07-01",
                        "status", "BORROWED"
                ))
        );
    }

    private static String scenario(String scenarioHeader, Map<String, Object> body) {
        return ResponseSupport.scenario(scenarioHeader, stringValue(body, "mockScenario", null));
    }

    private static String stringValue(Map<String, Object> body, String key, String defaultValue) {
        if (body == null || body.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(body.get(key));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
