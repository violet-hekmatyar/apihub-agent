USE apihub_agent;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM evidence_item;
DELETE FROM agent_report;
DELETE FROM tool_call_trace;
DELETE FROM agent_message;
DELETE FROM agent_session;
DELETE FROM rag_chunk_meta;
DELETE FROM rag_document;
DELETE FROM event_api_relation;
DELETE FROM campus_event;
DELETE FROM alert_event;
DELETE FROM gateway_log;
DELETE FROM api_call_stat_hourly;
DELETE FROM rate_limit_rule;
DELETE FROM api_authorization;
DELETE FROM api_consumer_app;
DELETE FROM api_endpoint;
DELETE FROM sys_user;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO sys_user (
  id, username, display_name, email, phone, user_type, org_name, status, extra_info, remark, created_at, updated_at
) VALUES
(1, 'admin_apihub', 'API Hub Admin', 'admin-demo@school.example', NULL, 'MANAGER', 'Information Center', 'ACTIVE',
 JSON_OBJECT('avatar', '/avatar/admin.png', 'mock', true), 'Mock platform manager account', '2026-06-01 09:00:00', '2026-06-01 09:00:00'),
(2, 'provider_auth', 'Auth API Provider', 'auth-demo@school.example', NULL, 'USER', 'Identity Center', 'ACTIVE',
 JSON_OBJECT('teamRole', 'api_provider', 'mock', true), 'Mock auth API provider', '2026-06-01 09:05:00', '2026-06-01 09:05:00'),
(3, 'provider_lecture', 'Lecture API Provider', 'lecture-demo@school.example', NULL, 'USER', 'Lecture System Team', 'ACTIVE',
 JSON_OBJECT('teamRole', 'api_provider', 'mock', true), 'Mock lecture API provider', '2026-06-01 09:10:00', '2026-06-01 09:10:00'),
(4, 'consumer_course_helper', 'Course Helper Owner', 'course-helper-demo@school.example', NULL, 'USER', 'Course Helper Team', 'ACTIVE',
 JSON_OBJECT('teamRole', 'api_consumer', 'mock', true), 'Mock course helper app owner', '2026-06-01 09:15:00', '2026-06-01 09:15:00'),
(5, 'consumer_club', 'Club Activity Owner', 'club-demo@school.example', NULL, 'USER', 'Club Activity Team', 'ACTIVE',
 JSON_OBJECT('teamRole', 'api_consumer', 'mock', true), 'Mock club app owner for permission boundary', '2026-06-01 09:20:00', '2026-06-01 09:20:00');

INSERT INTO api_endpoint (
  id, api_code, api_name, api_type, path, method, description, provider_user_id, owner_team,
  risk_level, online_status, status, extra_info, remark, created_at, updated_at
) VALUES
(1001, 'AUTH_LOGIN', 'School Account Login', 'AUTH', '/api/auth/login', 'POST',
 'Unified login API for campus apps. Requests must pass signature verification.', 2, 'Identity Center',
 'HIGH', 'ONLINE', 'ACTIVE',
 JSON_OBJECT('tags', JSON_ARRAY('core', 'high-frequency', '403-diagnosis'), 'docUrl', '/docs/auth-login'),
 'Core unified login API', '2026-06-01 10:00:00', '2026-06-19 09:00:00'),
(1002, 'COURSE_TODAY', 'Today Course Schedule', 'COURSE', '/api/course/today', 'GET',
 'Returns today course schedule. Traffic is high during morning and semester-start windows.', 2, 'Academic Affairs Data Team',
 'MEDIUM', 'ONLINE', 'ACTIVE',
 JSON_OBJECT('tags', JSON_ARRAY('course', 'schedule'), 'cacheRecommended', true),
 'Daily inspection core API', '2026-06-01 10:05:00', '2026-06-19 09:00:00'),
(1003, 'LECTURE_LIST', 'Lecture List', 'ACTIVITY', '/api/lecture/list', 'GET',
 'Returns recent lecture list, lecture details, and signup status.', 3, 'Lecture System Team',
 'MEDIUM', 'ONLINE', 'ACTIVE',
 JSON_OBJECT('tags', JSON_ARRAY('lecture', 'list', 'activity'), 'publicDoc', true),
 'Lecture list query API', '2026-06-01 10:10:00', '2026-06-19 09:00:00'),
(1004, 'LECTURE_REGISTER', 'Lecture Register', 'ACTIVITY', '/api/lecture/register', 'POST',
 'Write API used during lecture signup windows. Watch concurrency, rate limits, and latency.', 3, 'Lecture System Team',
 'HIGH', 'ONLINE', 'ACTIVE',
 JSON_OBJECT('tags', JSON_ARRAY('lecture', 'register', 'warning'), 'writeApi', true),
 'Lecture register peak warning API', '2026-06-01 10:15:00', '2026-06-19 09:00:00'),
(1005, 'CAMPUS_NOTICE', 'Campus Notice', 'NOTICE', '/api/campus/notices', 'GET',
 'Returns campus notices. Traffic rises shortly after notice publication.', 1, 'Information Center',
 'LOW', 'ONLINE', 'ACTIVE',
 JSON_OBJECT('tags', JSON_ARRAY('notice', 'announcement'), 'publicDoc', true),
 'Normal daily inspection API', '2026-06-01 10:20:00', '2026-06-19 09:00:00'),
(1006, 'VENUE_RESERVE', 'Venue Reserve', 'RESOURCE', '/api/venue/reserve', 'POST',
 'Reserves classrooms, halls, labs, and activity venues. Watch duplicate submits and reservation conflicts.', 1, 'Campus Resource Team',
 'HIGH', 'ONLINE', 'ACTIVE',
 JSON_OBJECT('tags', JSON_ARRAY('venue', 'reservation', 'idempotency'), 'writeApi', true),
 'Venue reservation conflict and idempotency risk API', '2026-06-01 10:25:00', '2026-06-19 09:00:00'),
(1007, 'LIBRARY_BORROW', 'Library Borrow', 'RESOURCE', '/api/library/borrow', 'GET',
 'Returns borrow records, due reminders, and renewal status from the library system.', 1, 'Library Integration Team',
 'MEDIUM', 'ONLINE', 'ACTIVE',
 JSON_OBJECT('tags', JSON_ARRAY('library', 'borrow', 'dependency'), 'downstream', 'mock-library-service'),
 'Library dependency timeout diagnosis API', '2026-06-01 10:30:00', '2026-06-19 09:00:00');

INSERT INTO api_consumer_app (
  id, app_code, app_name, owner_user_id, owner_team, app_type, access_key,
  contact_email, status, extra_info, remark, created_at, updated_at
) VALUES
(2001, 'COURSE_HELPER', 'Course Helper Mini App', 4, 'Course Helper Team', 'MINI_APP',
 'mock_access_key_course_helper', 'course-helper-demo@school.example', 'ACTIVE',
 JSON_OBJECT('scene', 'course schedule, reminders, unified login', 'mockSecretStoredElsewhere', true),
 'Main abnormal caller for login 403 diagnosis', '2026-06-01 11:00:00', '2026-06-19 09:00:00'),
(2002, 'CLUB_ACTIVITY', 'Club Activity System', 5, 'Club Activity Team', 'WEB',
 'mock_access_key_club_activity', 'club-demo@school.example', 'ACTIVE',
 JSON_OBJECT('scene', 'club signup and check-in', 'mockSecretStoredElsewhere', true),
 'Other team app used for permission boundary', '2026-06-01 11:05:00', '2026-06-19 09:00:00'),
(2003, 'LECTURE_PORTAL', 'Lecture Portal Service', 3, 'Lecture System Team', 'WEB',
 'mock_access_key_lecture_portal', 'lecture-demo@school.example', 'ACTIVE',
 JSON_OBJECT('scene', 'lecture list and signup portal', 'mockSecretStoredElsewhere', true),
 'Lecture team owned app', '2026-06-01 11:10:00', '2026-06-19 09:00:00'),
(2004, 'STUDENT_SERVICE', 'Student Service Portal', 4, 'Student Service Team', 'WEB',
 'mock_access_key_student_service', 'student-service-demo@school.example', 'ACTIVE',
 JSON_OBJECT('scene', 'campus notices and venue reservation', 'mockSecretStoredElsewhere', true),
 'Mock caller for campus notices and venue reservation', '2026-06-01 11:15:00', '2026-06-19 09:00:00'),
(2005, 'LIBRARY_MINI', 'Library Mini App', 5, 'Library Mini App Team', 'MINI_APP',
 'mock_access_key_library_mini', 'library-demo@school.example', 'ACTIVE',
 JSON_OBJECT('scene', 'borrow records, due reminders, renewal status', 'mockSecretStoredElsewhere', true),
 'Mock caller for library borrow dependency diagnosis', '2026-06-01 11:20:00', '2026-06-19 09:00:00');

INSERT INTO api_authorization (
  id, app_id, api_id, auth_status, approved_by, approved_at, expire_at, status, extra_info, remark, created_at, updated_at
) VALUES
(3001, 2001, 1001, 'APPROVED', 1, '2026-06-01 12:00:00', NULL, 'ACTIVE',
 JSON_OBJECT('quotaPerDay', 200000), 'COURSE_HELPER can call AUTH_LOGIN', '2026-06-01 12:00:00', '2026-06-01 12:00:00'),
(3002, 2001, 1002, 'APPROVED', 1, '2026-06-01 12:05:00', NULL, 'ACTIVE',
 JSON_OBJECT('quotaPerDay', 300000), 'COURSE_HELPER can call COURSE_TODAY', '2026-06-01 12:05:00', '2026-06-01 12:05:00'),
(3003, 2002, 1001, 'APPROVED', 1, '2026-06-01 12:10:00', NULL, 'ACTIVE',
 JSON_OBJECT('quotaPerDay', 60000), 'CLUB_ACTIVITY can call AUTH_LOGIN', '2026-06-01 12:10:00', '2026-06-01 12:10:00'),
(3004, 2002, 1003, 'APPROVED', 1, '2026-06-01 12:15:00', NULL, 'ACTIVE',
 JSON_OBJECT('quotaPerDay', 80000), 'CLUB_ACTIVITY can call LECTURE_LIST', '2026-06-01 12:15:00', '2026-06-01 12:15:00'),
(3005, 2003, 1004, 'APPROVED', 1, '2026-06-01 12:20:00', NULL, 'ACTIVE',
 JSON_OBJECT('quotaPerDay', 120000), 'LECTURE_PORTAL can call LECTURE_REGISTER', '2026-06-01 12:20:00', '2026-06-01 12:20:00'),
(3006, 2003, 1001, 'APPROVED', 1, '2026-06-01 12:25:00', NULL, 'ACTIVE',
 JSON_OBJECT('quotaPerDay', 120000), 'LECTURE_PORTAL can call AUTH_LOGIN', '2026-06-01 12:25:00', '2026-06-01 12:25:00'),
(3007, 2004, 1005, 'APPROVED', 1, '2026-06-01 12:30:00', NULL, 'ACTIVE',
 JSON_OBJECT('quotaPerDay', 100000), 'STUDENT_SERVICE can call CAMPUS_NOTICE', '2026-06-01 12:30:00', '2026-06-01 12:30:00'),
(3008, 2004, 1006, 'APPROVED', 1, '2026-06-01 12:35:00', NULL, 'ACTIVE',
 JSON_OBJECT('quotaPerDay', 50000), 'STUDENT_SERVICE can call VENUE_RESERVE', '2026-06-01 12:35:00', '2026-06-01 12:35:00'),
(3009, 2005, 1007, 'APPROVED', 1, '2026-06-01 12:40:00', NULL, 'ACTIVE',
 JSON_OBJECT('quotaPerDay', 80000), 'LIBRARY_MINI can call LIBRARY_BORROW', '2026-06-01 12:40:00', '2026-06-01 12:40:00');

INSERT INTO rate_limit_rule (
  id, api_id, rule_name, qps_limit, burst_limit, degrade_enabled, fallback_message,
  effective_start, effective_end, status, extra_info, remark, created_at, updated_at
) VALUES
(4001, 1001, 'Default login rate limit', 300, 600, 1,
 'Login service is busy. Please retry later.', NULL, NULL, 'ACTIVE',
 JSON_OBJECT('strategy', 'gateway-token-bucket', 'observeMetric', '403_count'),
 'Core login API protection', '2026-06-01 13:00:00', '2026-06-19 09:00:00'),
(4002, 1004, 'Lecture register open-window rate limit', 180, 360, 1,
 'Signup traffic is high. Please retry later.', '2026-06-19 11:45:00', '2026-06-19 12:45:00', 'ACTIVE',
 JSON_OBJECT('strategy', 'queue-and-token-bucket', 'cacheSuggestion', 'prewarm lecture detail and quota cache'),
 'Lecture signup peak protection', '2026-06-01 13:05:00', '2026-06-19 09:00:00'),
(4003, 1002, 'Course schedule cache protection', 500, 900, 0,
 NULL, NULL, NULL, 'ACTIVE',
 JSON_OBJECT('strategy', 'cache-first', 'cacheTtlSeconds', 60),
 'High-frequency course query protection', '2026-06-01 13:10:00', '2026-06-19 09:00:00'),
(4004, 1006, 'Venue reservation idempotency protection', 80, 120, 1,
 'Reservation requests are busy. Please do not submit repeatedly.', NULL, NULL, 'ACTIVE',
 JSON_OBJECT('strategy', 'idempotency-key-and-token-bucket', 'requireIdempotencyKey', true),
 'Venue reservation duplicate submit protection', '2026-06-01 13:15:00', '2026-06-19 09:00:00'),
(4005, 1007, 'Library borrow dependency degrade rule', 120, 240, 1,
 'Library service is temporarily slow. Please retry later.', NULL, NULL, 'ACTIVE',
 JSON_OBJECT('strategy', 'dependency-timeout-degrade', 'downstream', 'mock-library-service'),
 'Library downstream timeout protection', '2026-06-01 13:20:00', '2026-06-19 09:00:00');

INSERT INTO api_call_stat_hourly (
  id, api_id, stat_time, total_count, success_count, fail_count, error_4xx_count, error_5xx_count,
  rate_limit_count, avg_latency_ms, p95_latency_ms, p99_latency_ms, max_latency_ms, status, extra_info,
  created_at, updated_at
) VALUES
(5001, 1001, '2026-06-19 09:00:00', 14220, 13980, 240, 220, 20, 0, 82, 210, 360, 820, 'ACTIVE',
 JSON_OBJECT('topErrorCode', 'TOKEN_EXPIRED', 'topAppCode', 'COURSE_HELPER'), '2026-06-19 09:05:00', '2026-06-19 09:05:00'),
(5002, 1001, '2026-06-19 10:00:00', 18520, 17100, 1420, 1360, 60, 0, 96, 280, 520, 1100, 'ACTIVE',
 JSON_OBJECT('topErrorCode', 'SIGNATURE_INVALID', 'topAppCode', 'COURSE_HELPER', 'failRate', 0.0767), '2026-06-19 10:05:00', '2026-06-19 10:05:00'),
(5003, 1001, '2026-06-19 11:00:00', 16800, 16220, 580, 540, 40, 0, 90, 240, 420, 900, 'ACTIVE',
 JSON_OBJECT('topErrorCode', 'TOKEN_EXPIRED', 'topAppCode', 'COURSE_HELPER'), '2026-06-19 11:05:00', '2026-06-19 11:05:00'),
(5004, 1002, '2026-06-19 08:00:00', 22800, 22560, 240, 220, 20, 0, 68, 160, 260, 620, 'ACTIVE',
 JSON_OBJECT('inspectionLabel', 'high_traffic_stable'), '2026-06-19 08:05:00', '2026-06-19 08:05:00'),
(5005, 1002, '2026-06-19 09:00:00', 24600, 24310, 290, 260, 30, 0, 72, 170, 280, 680, 'ACTIVE',
 JSON_OBJECT('inspectionLabel', 'high_traffic_stable'), '2026-06-19 09:05:00', '2026-06-19 09:05:00'),
(5006, 1003, '2026-06-19 11:00:00', 4200, 4140, 60, 52, 8, 0, 75, 190, 320, 720, 'ACTIVE',
 JSON_OBJECT('eventCode', 'LECTURE_SIGNUP_20260619'), '2026-06-19 11:05:00', '2026-06-19 11:05:00'),
(5007, 1004, '2026-06-19 11:00:00', 3600, 3520, 80, 66, 14, 4, 105, 360, 620, 1400, 'ACTIVE',
 JSON_OBJECT('eventCode', 'LECTURE_SIGNUP_20260619', 'phase', 'before_open'), '2026-06-19 11:05:00', '2026-06-19 11:05:00'),
(5008, 1004, '2026-06-19 12:00:00', 16800, 15120, 1680, 860, 120, 700, 188, 720, 1180, 2600, 'ACTIVE',
 JSON_OBJECT('eventCode', 'LECTURE_SIGNUP_20260619', 'phase', 'open_window', 'risk', 'HIGH'), '2026-06-19 12:05:00', '2026-06-19 12:05:00'),
(5009, 1005, '2026-06-19 10:00:00', 6100, 6040, 60, 52, 8, 0, 58, 130, 210, 480, 'ACTIVE',
 JSON_OBJECT('inspectionLabel', 'normal'), '2026-06-19 10:05:00', '2026-06-19 10:05:00'),
(5010, 1004, '2026-06-16 12:00:00', 9800, 9210, 590, 360, 80, 150, 160, 560, 920, 2100, 'ACTIVE',
 JSON_OBJECT('weeklyReview', true, 'eventCode', 'LECTURE_SIGNUP_REHEARSAL_20260616'), '2026-06-16 12:05:00', '2026-06-16 12:05:00'),
(5011, 1001, '2026-06-17 08:00:00', 20500, 20100, 400, 370, 30, 0, 84, 230, 410, 980, 'ACTIVE',
 JSON_OBJECT('weeklyReview', true, 'eventCode', 'SEMESTER_START_20260617'), '2026-06-17 08:05:00', '2026-06-17 08:05:00'),
(5012, 1005, '2026-06-19 14:00:00', 9800, 9705, 95, 80, 15, 0, 132, 420, 760, 1600, 'ACTIVE',
 JSON_OBJECT('inspectionLabel', 'notice_publish_cache_pressure', 'hint', 'cache miss and downstream slow'), '2026-06-19 14:05:00', '2026-06-19 14:05:00'),
(5013, 1006, '2026-06-19 15:00:00', 5200, 4720, 480, 430, 50, 180, 210, 680, 980, 1900, 'ACTIVE',
 JSON_OBJECT('risk', 'duplicate_submit', 'topErrorCode', 'RESERVATION_CONFLICT'), '2026-06-19 15:05:00', '2026-06-19 15:05:00'),
(5014, 1007, '2026-06-19 16:00:00', 6800, 6120, 680, 120, 560, 0, 340, 1200, 2200, 4200, 'ACTIVE',
 JSON_OBJECT('risk', 'dependency_timeout', 'downstream', 'mock-library-service'), '2026-06-19 16:05:00', '2026-06-19 16:05:00'),
(5015, 1002, '2026-06-20 08:00:00', 25800, 25490, 310, 270, 40, 0, 118, 390, 620, 1280, 'ACTIVE',
 JSON_OBJECT('trend', 'semester_start_cache_pressure', 'hint', 'course query cache miss'), '2026-06-20 08:05:00', '2026-06-20 08:05:00');

INSERT INTO gateway_log (
  id, trace_id, api_id, app_id, access_key, request_path, request_method, http_status,
  error_code, error_message, latency_ms, client_ip, request_time, status, extra_info, created_at
) VALUES
(6001, 'tr_20260619_auth_001', 1001, 2001, 'mock_access_key_course_helper', '/api/auth/login', 'POST', 403,
 'SIGNATURE_INVALID', 'signature verification failed', 88, 'mock-client-course-helper-a', '2026-06-19 10:12:30', 'ACTIVE',
 JSON_OBJECT('timestampDiffSeconds', 420, 'nonce', 'mock_nonce_001', 'diagnosisHint', 'timestamp too old'), '2026-06-19 10:12:31'),
(6002, 'tr_20260619_auth_002', 1001, 2001, 'mock_access_key_course_helper', '/api/auth/login', 'POST', 403,
 'SIGNATURE_INVALID', 'signature verification failed', 92, 'mock-client-course-helper-b', '2026-06-19 10:18:12', 'ACTIVE',
 JSON_OBJECT('timestampDiffSeconds', 390, 'nonce', 'mock_nonce_002', 'diagnosisHint', 'secret mismatch or clock skew'), '2026-06-19 10:18:13'),
(6003, 'tr_20260619_auth_003', 1001, 2001, 'mock_access_key_course_helper', '/api/auth/login', 'POST', 403,
 'TOKEN_EXPIRED', 'token expired', 76, 'mock-client-course-helper-c', '2026-06-19 10:25:45', 'ACTIVE',
 JSON_OBJECT('tokenPreview', 'mock_token_expired_***', 'diagnosisHint', 'refresh token before retry'), '2026-06-19 10:25:46'),
(6004, 'tr_20260619_auth_004', 1001, 2001, 'mock_access_key_course_helper', '/api/auth/login', 'POST', 403,
 'SECRET_MISMATCH', 'caller secret config mismatch', 101, 'mock-client-course-helper-d', '2026-06-19 10:42:03', 'ACTIVE',
 JSON_OBJECT('configVersion', 'mock_config_v2', 'diagnosisHint', 'check mock secret config'), '2026-06-19 10:42:04'),
(6005, 'tr_20260619_auth_005', 1001, 2002, 'mock_access_key_club_activity', '/api/auth/login', 'POST', 200,
 NULL, NULL, 70, 'mock-client-club-a', '2026-06-19 10:43:20', 'ACTIVE',
 JSON_OBJECT('diagnosisHint', 'other team normal login sample'), '2026-06-19 10:43:21'),
(6006, 'tr_20260619_course_001', 1002, 2001, 'mock_access_key_course_helper', '/api/course/today', 'GET', 200,
 NULL, NULL, 63, 'mock-client-course-helper-a', '2026-06-19 09:20:11', 'ACTIVE',
 JSON_OBJECT('inspectionLabel', 'normal'), '2026-06-19 09:20:12'),
(6007, 'tr_20260619_lecture_001', 1004, 2003, 'mock_access_key_lecture_portal', '/api/lecture/register', 'POST', 429,
 'RATE_LIMITED', 'lecture signup peak limited', 142, 'mock-client-lecture-a', '2026-06-19 12:08:08', 'ACTIVE',
 JSON_OBJECT('eventCode', 'LECTURE_SIGNUP_20260619', 'queueEnabled', true), '2026-06-19 12:08:09'),
(6008, 'tr_20260619_club_001', 1003, 2002, 'mock_access_key_club_activity', '/api/lecture/list', 'GET', 200,
 NULL, NULL, 78, 'mock-client-club-a', '2026-06-19 12:10:22', 'ACTIVE',
 JSON_OBJECT('permissionBoundary', 'course_helper_user_should_not_read_this_app_log'), '2026-06-19 12:10:23'),
(6009, 'tr_20260619_auth_scan_001', 1001, NULL, NULL, '/api/auth/login', 'POST', 403,
 'UNKNOWN_APP', 'unknown appCode with abnormal user-agent', 65, 'mock-client-unknown-a', '2026-06-19 10:50:10', 'ACTIVE',
 JSON_OBJECT('userAgent', 'mock-scanner-agent', 'diagnosisHint', 'suspicious high-frequency failed auth request'), '2026-06-19 10:50:11'),
(6010, 'tr_20260619_notice_001', 1005, 2004, 'mock_access_key_student_service', '/api/campus/notices', 'GET', 200,
 NULL, NULL, 880, 'mock-client-student-service-a', '2026-06-19 14:12:10', 'ACTIVE',
 JSON_OBJECT('diagnosisHint', 'cache miss after notice publish; downstream slow'), '2026-06-19 14:12:11'),
(6011, 'tr_20260619_course_slow_001', 1002, 2001, 'mock_access_key_course_helper', '/api/course/today', 'GET', 200,
 NULL, NULL, 1180, 'mock-client-course-helper-b', '2026-06-20 08:10:10', 'ACTIVE',
 JSON_OBJECT('diagnosisHint', 'query timeout risk; cache miss during morning peak'), '2026-06-20 08:10:11'),
(6012, 'tr_20260619_venue_001', 1006, 2004, 'mock_access_key_student_service', '/api/venue/reserve', 'POST', 409,
 'RESERVATION_CONFLICT', 'duplicate request caused reservation conflict', 260, 'mock-client-student-service-b', '2026-06-19 15:03:20', 'ACTIVE',
 JSON_OBJECT('idempotencyKey', 'missing', 'diagnosisHint', 'duplicate request and idempotency key missing'), '2026-06-19 15:03:21'),
(6013, 'tr_20260619_venue_002', 1006, 2004, 'mock_access_key_student_service', '/api/venue/reserve', 'POST', 429,
 'TOO_MANY_REQUESTS', 'too many requests for venue reservation', 210, 'mock-client-student-service-c', '2026-06-19 15:05:20', 'ACTIVE',
 JSON_OBJECT('diagnosisHint', 'rate limited after repeated submits'), '2026-06-19 15:05:21'),
(6014, 'tr_20260619_library_001', 1007, 2005, 'mock_access_key_library_mini', '/api/library/borrow', 'GET', 503,
 'DEPENDENCY_TIMEOUT', 'downstream library service unavailable', 3000, 'mock-client-library-a', '2026-06-19 16:08:30', 'ACTIVE',
 JSON_OBJECT('downstream', 'mock-library-service', 'diagnosisHint', 'dependency timeout'), '2026-06-19 16:08:31');

INSERT INTO alert_event (
  id, event_code, api_id, event_type, severity, title, description, start_time, end_time,
  resolved, status, extra_info, created_at, updated_at
) VALUES
(7001, 'ALERT_AUTH_403_20260619', 1001, 'HIGH_ERROR_RATE', 'HIGH',
 'AUTH_LOGIN 403 errors increased',
 '403 errors increased during 10:00-11:00 and were mainly from COURSE_HELPER.',
 '2026-06-19 10:00:00', '2026-06-19 11:00:00', 0, 'ACTIVE',
 JSON_OBJECT('mainErrorCode', 'SIGNATURE_INVALID', 'affectedAppId', 2001, 'statId', 5002), '2026-06-19 10:05:00', '2026-06-19 10:05:00'),
(7002, 'ALERT_LECTURE_REGISTER_P95_20260619', 1004, 'HIGH_LATENCY', 'HIGH',
 'LECTURE_REGISTER P95 increased',
 'P95 increased and rate limits appeared during lecture signup open window.',
 '2026-06-19 12:00:00', '2026-06-19 12:30:00', 0, 'ACTIVE',
 JSON_OBJECT('eventCode', 'LECTURE_SIGNUP_20260619', 'statId', 5008), '2026-06-19 12:05:00', '2026-06-19 12:05:00'),
(7003, 'ALERT_COURSE_TODAY_STABLE_20260619', 1002, 'TRAFFIC_SPIKE', 'LOW',
 'COURSE_TODAY traffic high but stable',
 'Course schedule traffic was high in morning peak but fail rate and P95 were stable.',
 '2026-06-19 08:00:00', '2026-06-19 10:00:00', 1, 'ACTIVE',
 JSON_OBJECT('statIds', JSON_ARRAY(5004, 5005), 'inspectionLabel', 'stable'), '2026-06-19 10:05:00', '2026-06-19 10:05:00'),
(7004, 'ALERT_NOTICE_CACHE_PRESSURE_20260619', 1005, 'HIGH_LATENCY', 'MEDIUM',
 'CAMPUS_NOTICE cache pressure after publish',
 'Notice traffic increased after publish. Gateway logs show cache miss and downstream slow hints.',
 '2026-06-19 14:00:00', '2026-06-19 14:30:00', 0, 'ACTIVE',
 JSON_OBJECT('statId', 5012, 'logId', 6010), '2026-06-19 14:05:00', '2026-06-19 14:05:00'),
(7005, 'ALERT_LIBRARY_DEP_TIMEOUT_20260619', 1007, 'HIGH_ERROR_RATE', 'HIGH',
 'LIBRARY_BORROW downstream dependency timeout',
 '5xx increased because the mock library downstream service timed out or was unavailable.',
 '2026-06-19 16:00:00', '2026-06-19 16:30:00', 0, 'ACTIVE',
 JSON_OBJECT('statId', 5014, 'downstream', 'mock-library-service'), '2026-06-19 16:05:00', '2026-06-19 16:05:00'),
(7006, 'ALERT_VENUE_DUPLICATE_20260619', 1006, 'RATE_LIMIT_SPIKE', 'MEDIUM',
 'VENUE_RESERVE duplicate submit and idempotency risk',
 '409 and 429 increased with duplicate request and missing idempotency key hints.',
 '2026-06-19 15:00:00', '2026-06-19 15:30:00', 0, 'ACTIVE',
 JSON_OBJECT('statId', 5013, 'logIds', JSON_ARRAY(6012, 6013)), '2026-06-19 15:05:00', '2026-06-19 15:05:00');

INSERT INTO campus_event (
  id, event_code, event_name, event_type, description, start_time, end_time, location,
  expected_traffic_level, source, status, extra_info, remark, created_at, updated_at
) VALUES
(8001, 'LECTURE_SIGNUP_20260619', 'AI Lecture Signup Open', 'LECTURE_SIGNUP',
 'Lecture signup opens at 12:00. AUTH_LOGIN, LECTURE_LIST, and LECTURE_REGISTER traffic are expected to rise.',
 '2026-06-19 12:00:00', '2026-06-19 12:30:00', 'Campus Hall',
 'HIGH', 'Campus Activity System', 'ACTIVE',
 JSON_OBJECT('expectedParticipants', 800, 'observeWindowMinutes', 60),
 'Lecture signup peak warning mock event', '2026-06-18 18:00:00', '2026-06-19 09:00:00'),
(8002, 'SEMESTER_START_20260617', 'Summer Term Start Reminder', 'SEMESTER_START',
 'After course start notice, AUTH_LOGIN and COURSE_TODAY traffic increased in morning peak.',
 '2026-06-17 08:00:00', '2026-06-17 09:00:00', 'Online',
 'MEDIUM', 'Academic Affairs System', 'ACTIVE',
 JSON_OBJECT('expectedParticipants', 3000, 'weeklyReview', true),
 'Weekly review mock event', '2026-06-16 18:00:00', '2026-06-17 09:00:00'),
(8003, 'NOTICE_PUBLISH_20260619', 'Campus Notice Publish', 'EXAM_NOTICE',
 'A campus notice was published at 14:00. CAMPUS_NOTICE traffic and cache pressure are expected to rise.',
 '2026-06-19 14:00:00', '2026-06-19 14:30:00', 'Online',
 'MEDIUM', 'Information Center', 'ACTIVE',
 JSON_OBJECT('expectedReaders', 5000, 'cachePressure', true),
 'Campus notice cache pressure mock event', '2026-06-19 13:50:00', '2026-06-19 14:00:00'),
(8004, 'VENUE_OPEN_20260619', 'Venue Reservation Open', 'ACTIVITY_CHECKIN',
 'Venue reservation opens at 15:00. Duplicate submit and idempotency risks need attention.',
 '2026-06-19 15:00:00', '2026-06-19 15:30:00', 'Online',
 'HIGH', 'Student Service Portal', 'ACTIVE',
 JSON_OBJECT('expectedRequests', 1200, 'idempotencyRisk', true),
 'Venue reservation conflict mock event', '2026-06-19 14:50:00', '2026-06-19 15:00:00');

INSERT INTO event_api_relation (
  id, event_id, api_id, impact_type, impact_level, reason, status, extra_info, created_at
) VALUES
(9001, 8001, 1001, 'TRAFFIC_INCREASE', 'HIGH',
 'Users need unified login before lecture signup.', 'ACTIVE',
 JSON_OBJECT('observeWindowMinutes', 60, 'suggestion', 'watch 403 and login success rate'), '2026-06-18 18:05:00'),
(9002, 8001, 1003, 'TRAFFIC_INCREASE', 'MEDIUM',
 'Users refresh lecture activity list before signup.', 'ACTIVE',
 JSON_OBJECT('observeWindowMinutes', 60, 'suggestion', 'prewarm activity list cache'), '2026-06-18 18:05:00'),
(9003, 8001, 1004, 'TRAFFIC_INCREASE', 'HIGH',
 'Concurrent writes increase in signup open window.', 'ACTIVE',
 JSON_OBJECT('observeWindowMinutes', 60, 'suggestion', 'enable queue and rate limit protection'), '2026-06-18 18:05:00'),
(9004, 8002, 1001, 'TRAFFIC_INCREASE', 'MEDIUM',
 'Term-start reminder triggers more login requests.', 'ACTIVE',
 JSON_OBJECT('weeklyReview', true), '2026-06-16 18:05:00'),
(9005, 8002, 1002, 'TRAFFIC_INCREASE', 'HIGH',
 'Students query today course schedule intensively.', 'ACTIVE',
 JSON_OBJECT('weeklyReview', true, 'suggestion', 'increase course cache hit rate'), '2026-06-16 18:05:00'),
(9006, 8003, 1005, 'TRAFFIC_INCREASE', 'MEDIUM',
 'Students and school sites refresh campus notices after publish.', 'ACTIVE',
 JSON_OBJECT('observeWindowMinutes', 30, 'suggestion', 'watch cache miss and downstream slow logs'), '2026-06-19 13:55:00'),
(9007, 8004, 1006, 'TRAFFIC_INCREASE', 'HIGH',
 'Venue reservation open window may trigger duplicate submit and conflicts.', 'ACTIVE',
 JSON_OBJECT('observeWindowMinutes', 30, 'suggestion', 'check idempotency key and rate limit rule'), '2026-06-19 14:55:00');

INSERT INTO rag_document (
  id, doc_code, title, doc_type, source_type, source_path, file_name, file_ext, file_size,
  content_hash, version_no, uploaded_by, chunk_count, embedding_model, embedding_dim, milvus_collection,
  index_status, last_indexed_at, error_message, status, extra_info, remark, created_at, updated_at
) VALUES
(10001, 'DOC_AUTH_SIGN_RULE', 'AUTH_LOGIN Signature Rule', 'SIGN_RULE', 'LOCAL_FILE',
 './uploads/mock/auth-sign-rule.md', 'auth-sign-rule.md', 'md', 8420,
 'sha256:mock_auth_sign_rule', 1, 1, 3, 'text-embedding-v4', 1024, 'biz',
 'INDEXED', '2026-06-19 09:30:10', NULL, 'ACTIVE',
 JSON_OBJECT('relatedApiCode', 'AUTH_LOGIN', 'mock', true), 'Mock doc for 403 signature failure diagnosis', '2026-06-19 09:30:00', '2026-06-19 09:30:10'),
(10002, 'DOC_AUTH_ERROR_CODE', 'AUTH_LOGIN Error Codes', 'ERROR_CODE', 'LOCAL_FILE',
 './uploads/mock/auth-error-code.md', 'auth-error-code.md', 'md', 4200,
 'sha256:mock_auth_error_code', 1, 1, 2, 'text-embedding-v4', 1024, 'biz',
 'INDEXED', '2026-06-19 09:31:10', NULL, 'ACTIVE',
 JSON_OBJECT('relatedApiCode', 'AUTH_LOGIN', 'mock', true), 'Mock doc for 403 and token expiry', '2026-06-19 09:31:00', '2026-06-19 09:31:10'),
(10003, 'DOC_LECTURE_REGISTER_API', 'LECTURE_REGISTER API Guide', 'API_DOC', 'LOCAL_FILE',
 './uploads/mock/lecture-signup-api.md', 'lecture-signup-api.md', 'md', 5300,
 'sha256:mock_lecture_signup_api', 1, 1, 2, 'text-embedding-v4', 1024, 'biz',
 'INDEXED', '2026-06-19 09:32:10', NULL, 'ACTIVE',
 JSON_OBJECT('relatedApiCode', 'LECTURE_REGISTER', 'mock', true), 'Mock doc for lecture register peak warning', '2026-06-19 09:32:00', '2026-06-19 09:32:10'),
(10004, 'DOC_VENUE_RESERVE_IDEMPOTENCY', 'VENUE_RESERVE Idempotency Guide', 'API_DOC', 'LOCAL_FILE',
 './uploads/mock/venue-reserve-idempotency.md', 'venue-reserve-idempotency.md', 'md', 3600,
 'sha256:mock_venue_reserve_idempotency', 1, 1, 1, 'text-embedding-v4', 1024, 'biz',
 'INDEXED', '2026-06-19 09:33:10', NULL, 'ACTIVE',
 JSON_OBJECT('relatedApiCode', 'VENUE_RESERVE', 'mock', true), 'Mock doc for duplicate request and idempotency diagnosis', '2026-06-19 09:33:00', '2026-06-19 09:33:10'),
(10005, 'DOC_LIBRARY_BORROW_DEPENDENCY', 'LIBRARY_BORROW Dependency Guide', 'API_DOC', 'LOCAL_FILE',
 './uploads/mock/library-borrow-dependency.md', 'library-borrow-dependency.md', 'md', 3900,
 'sha256:mock_library_borrow_dependency', 1, 1, 1, 'text-embedding-v4', 1024, 'biz',
 'INDEXED', '2026-06-19 09:34:10', NULL, 'ACTIVE',
 JSON_OBJECT('relatedApiCode', 'LIBRARY_BORROW', 'mock', true), 'Mock doc for downstream timeout diagnosis', '2026-06-19 09:34:00', '2026-06-19 09:34:10');

INSERT INTO rag_chunk_meta (
  id, document_id, chunk_index, chunk_title, content_preview, start_offset, end_offset,
  content_hash, milvus_collection, milvus_chunk_id, embedding_model, embedding_dim, token_count,
  status, extra_info, created_at
) VALUES
(11001, 10001, 0, 'Signature verification flow',
 'For AUTH_LOGIN, caller builds signature from accessKey, timestamp, nonce, requestBody, and mock_secret. 403 SIGNATURE_INVALID can be caused by signature failure, timestamp skew, nonce replay validation, or wrong secret.',
 0, 820, 'sha256:mock_chunk_auth_sign_0', 'biz', 'DOC_AUTH_SIGN_RULE_0', 'text-embedding-v4', 1024, 320,
 'ACTIVE', JSON_OBJECT('keywords', JSON_ARRAY('signature failure', '403', 'timestamp', 'nonce', 'secret', 'replay validation')), '2026-06-19 09:30:05'),
(11002, 10001, 1, '403 troubleshooting steps',
 'When diagnosing 403, verify accessKey ownership, timestamp window, nonce uniqueness, mock_secret consistency, and replay validation result.',
 821, 1560, 'sha256:mock_chunk_auth_sign_1', 'biz', 'DOC_AUTH_SIGN_RULE_1', 'text-embedding-v4', 1024, 300,
 'ACTIVE', JSON_OBJECT('keywords', JSON_ARRAY('403', 'Token', 'timestamp', 'nonce', 'secret', 'config')), '2026-06-19 09:30:06'),
(11003, 10001, 2, 'Token and signature boundary',
 'TOKEN_EXPIRED means Token expired. SIGNATURE_INVALID means signature verification failed. Both may surface as 403, but Token refresh and secret/timestamp/nonce checks are different actions.',
 1561, 2300, 'sha256:mock_chunk_auth_sign_2', 'biz', 'DOC_AUTH_SIGN_RULE_2', 'text-embedding-v4', 1024, 310,
 'ACTIVE', JSON_OBJECT('keywords', JSON_ARRAY('Token', '403', 'SIGNATURE_INVALID', 'TOKEN_EXPIRED')), '2026-06-19 09:30:07'),
(11004, 10002, 0, 'Login error code map',
 'SIGNATURE_INVALID means signature verification failed. TOKEN_EXPIRED means Token expired. SECRET_MISMATCH means caller secret config mismatch. Demo data only uses mock accessKey and mock secret.',
 0, 760, 'sha256:mock_chunk_auth_error_0', 'biz', 'DOC_AUTH_ERROR_CODE_0', 'text-embedding-v4', 1024, 260,
 'ACTIVE', JSON_OBJECT('keywords', JSON_ARRAY('SIGNATURE_INVALID', 'TOKEN_EXPIRED', 'SECRET_MISMATCH')), '2026-06-19 09:31:05'),
(11005, 10003, 0, 'Lecture register open window',
 'Before lecture signup opens, prewarm activity detail cache, set API QPS and burst limits, and watch P95, P99, rate_limit_count, and fail rate.',
 0, 720, 'sha256:mock_chunk_lecture_register_0', 'biz', 'DOC_LECTURE_REGISTER_API_0', 'text-embedding-v4', 1024, 240,
 'ACTIVE', JSON_OBJECT('keywords', JSON_ARRAY('lecture register', 'rate limit', 'cache', 'P95')), '2026-06-19 09:32:05'),
(11006, 10004, 0, 'Venue reserve idempotency',
 'VENUE_RESERVE should require an idempotency key for write requests. 409 reservation conflict and 429 too many requests can indicate duplicate submit risk.',
 0, 680, 'sha256:mock_chunk_venue_reserve_0', 'biz', 'DOC_VENUE_RESERVE_IDEMPOTENCY_0', 'text-embedding-v4', 1024, 230,
 'ACTIVE', JSON_OBJECT('keywords', JSON_ARRAY('VENUE_RESERVE', 'duplicate request', 'idempotency key', '409', '429')), '2026-06-19 09:33:05'),
(11007, 10005, 0, 'Library borrow dependency timeout',
 'LIBRARY_BORROW depends on mock-library-service. 503 DEPENDENCY_TIMEOUT suggests downstream library service unavailable or timeout.',
 0, 640, 'sha256:mock_chunk_library_borrow_0', 'biz', 'DOC_LIBRARY_BORROW_DEPENDENCY_0', 'text-embedding-v4', 1024, 220,
 'ACTIVE', JSON_OBJECT('keywords', JSON_ARRAY('LIBRARY_BORROW', 'dependency timeout', '503', 'downstream unavailable')), '2026-06-19 09:34:05');

INSERT INTO agent_session (
  id, session_code, trace_id, user_id, user_type, session_type, title, workflow_name, status,
  error_code, error_message, duration_ms, retry_count, last_event_seq, cancelled_at, started_at,
  finished_at, extra_info, created_at, updated_at
) VALUES
(12001, 'sess_auth_403_20260619', 'trace_auth_403_20260619', 1, 'MANAGER', 'DIAGNOSIS',
 'AUTH_LOGIN 403 Diagnosis', 'login_403_diagnosis', 'SUCCESS',
 NULL, NULL, 18000, 0, 18, NULL, '2026-06-19 11:05:00', '2026-06-19 11:05:18',
 JSON_OBJECT('question', 'Why did AUTH_LOGIN 403 increase today?'), '2026-06-19 11:05:00', '2026-06-19 11:05:18'),
(12002, 'sess_daily_inspection_20260619', 'trace_daily_inspection_20260619', 1, 'MANAGER', 'INSPECTION',
 'Daily API Inspection', 'daily_api_inspection', 'SUCCESS',
 NULL, NULL, 16000, 0, 16, NULL, '2026-06-19 13:00:00', '2026-06-19 13:00:16',
 JSON_OBJECT('question', 'Which APIs need attention today?'), '2026-06-19 13:00:00', '2026-06-19 13:00:16'),
(12003, 'sess_lecture_warning_20260619', 'trace_lecture_warning_20260619', 1, 'MANAGER', 'WARNING',
 'Lecture Signup Peak Warning', 'lecture_signup_warning', 'SUCCESS',
 NULL, NULL, 15000, 0, 15, NULL, '2026-06-19 11:40:00', '2026-06-19 11:40:15',
 JSON_OBJECT('question', 'Which APIs will be affected by lecture signup this week?'), '2026-06-19 11:40:00', '2026-06-19 11:40:15'),
(12004, 'sess_permission_denied_20260619', 'trace_permission_denied_20260619', 4, 'USER', 'DIAGNOSIS',
 'Permission Boundary Check', 'permission_boundary_check', 'SUCCESS',
 NULL, NULL, 3000, 0, 5, NULL, '2026-06-19 14:00:00', '2026-06-19 14:00:03',
 JSON_OBJECT('question', 'USER tries to query other team API logs', 'expectedResult', 'PERMISSION_DENIED'), '2026-06-19 14:00:00', '2026-06-19 14:00:03');

INSERT INTO agent_message (
  id, session_id, message_role, content, message_order, status, extra_info, created_at
) VALUES
(13001, 12001, 'USER', 'Why did AUTH_LOGIN 403 increase today?', 1, 'SUCCESS',
 JSON_OBJECT('userType', 'MANAGER'), '2026-06-19 11:05:00'),
(13002, 12001, 'ASSISTANT', 'AUTH_LOGIN 403 increased during 10:00-11:00, mainly from COURSE_HELPER. Suspected causes are signature verification failure and some expired tokens.', 2, 'SUCCESS',
 JSON_OBJECT('reportCode', 'RPT_AUTH_403_20260619'), '2026-06-19 11:05:18'),
(13003, 12004, 'USER', 'Query CLUB_ACTIVITY lecture activity logs.', 1, 'SUCCESS',
 JSON_OBJECT('userType', 'USER', 'userId', 4), '2026-06-19 14:00:00'),
(13004, 12004, 'ASSISTANT', 'Current user has no permission to view other team gateway logs.', 2, 'SUCCESS',
 JSON_OBJECT('errorCode', 'PERMISSION_DENIED'), '2026-06-19 14:00:03');

INSERT INTO tool_call_trace (
  id, session_id, trace_id, span_id, parent_span_id, tool_name, tool_type, input_json, output_json,
  latency_ms, success, error_code, error_message, status, extra_info, created_at
) VALUES
(14001, 12001, 'trace_auth_403_20260619', 'span_query_api_info_001', NULL, 'queryApiInfo', 'LOCAL',
 JSON_OBJECT('apiCode', 'AUTH_LOGIN'),
 JSON_OBJECT('apiCode', 'AUTH_LOGIN', 'riskLevel', 'HIGH', 'ownerTeam', 'Identity Center'),
 80, 1, NULL, NULL, 'SUCCESS', JSON_OBJECT('evidenceGenerated', true), '2026-06-19 11:05:02'),
(14002, 12001, 'trace_auth_403_20260619', 'span_query_api_stats_001', NULL, 'queryApiCallStats', 'LOCAL',
 JSON_OBJECT('apiCode', 'AUTH_LOGIN', 'startTime', '2026-06-19 10:00:00', 'endTime', '2026-06-19 11:00:00'),
 JSON_OBJECT('matchedCount', 1, 'failRate', 0.0767, 'topErrorCode', 'SIGNATURE_INVALID'),
 128, 1, NULL, NULL, 'SUCCESS', JSON_OBJECT('evidenceGenerated', true), '2026-06-19 11:05:04'),
(14003, 12001, 'trace_auth_403_20260619', 'span_query_gateway_logs_001', NULL, 'queryGatewayLogs', 'LOCAL',
 JSON_OBJECT('apiCode', 'AUTH_LOGIN', 'appCode', 'COURSE_HELPER', 'httpStatus', 403, 'startTime', '2026-06-19 10:00:00', 'endTime', '2026-06-19 11:00:00'),
 JSON_OBJECT('matchedCount', 4, 'mainErrorCode', 'SIGNATURE_INVALID', 'topAppCode', 'COURSE_HELPER'),
 126, 1, NULL, NULL, 'SUCCESS', JSON_OBJECT('evidenceGenerated', true), '2026-06-19 11:05:06'),
(14004, 12001, 'trace_auth_403_20260619', 'span_query_api_docs_001', NULL, 'queryApiDocs', 'RAG',
 JSON_OBJECT('query', 'AUTH_LOGIN 403 signature verification failed troubleshooting', 'apiCode', 'AUTH_LOGIN', 'docType', 'SIGN_RULE', 'topK', 3),
 JSON_OBJECT('topK', 3, 'hitChunkIds', JSON_ARRAY('DOC_AUTH_SIGN_RULE_0', 'DOC_AUTH_SIGN_RULE_1', 'DOC_AUTH_SIGN_RULE_2')),
 420, 1, NULL, NULL, 'SUCCESS', JSON_OBJECT('evidenceGenerated', true), '2026-06-19 11:05:08'),
(14005, 12003, 'trace_lecture_warning_20260619', 'span_query_campus_events_001', NULL, 'queryCampusEvents', 'LOCAL',
 JSON_OBJECT('eventType', 'LECTURE_SIGNUP', 'startTime', '2026-06-19 00:00:00', 'endTime', '2026-06-19 23:59:59', 'includeImpactedApis', true),
 JSON_OBJECT('matchedCount', 1, 'eventCode', 'LECTURE_SIGNUP_20260619', 'impactedApiCount', 3),
 110, 1, NULL, NULL, 'SUCCESS', JSON_OBJECT('evidenceGenerated', true), '2026-06-19 11:40:03'),
(14006, 12003, 'trace_lecture_warning_20260619', 'span_query_rate_rule_001', NULL, 'queryRateLimitRule', 'LOCAL',
 JSON_OBJECT('apiCode', 'LECTURE_REGISTER'),
 JSON_OBJECT('rules', JSON_ARRAY(JSON_OBJECT('ruleName', 'Lecture register open-window rate limit', 'qpsLimit', 180, 'burstLimit', 360))),
 96, 1, NULL, NULL, 'SUCCESS', JSON_OBJECT('evidenceGenerated', true), '2026-06-19 11:40:06'),
(14007, 12004, 'trace_permission_denied_20260619', 'span_query_gateway_logs_denied_001', NULL, 'queryGatewayLogs', 'LOCAL',
 JSON_OBJECT('apiCode', 'LECTURE_LIST', 'appCode', 'CLUB_ACTIVITY', 'currentUserId', 4, 'currentUserType', 'USER'),
 JSON_OBJECT(),
 32, 0, 'PERMISSION_DENIED', 'Current user cannot view other team gateway logs.', 'FAILED',
 JSON_OBJECT('evidenceGenerated', false, 'boundary', 'app owner mismatch'), '2026-06-19 14:00:02');

INSERT INTO agent_report (
  id, report_code, session_id, trace_id, report_type, title, summary, risk_level, content_md,
  created_by, evidence_count, tool_call_count, duration_ms, status, error_code, error_message,
  generated_at, extra_info, remark, created_at, updated_at
) VALUES
(16001, 'RPT_AUTH_403_20260619', 12001, 'trace_auth_403_20260619', 'DIAGNOSIS',
 'AUTH_LOGIN 403 Diagnosis Report',
 'AUTH_LOGIN 403 errors mainly came from COURSE_HELPER. The suspected root cause is signature verification failure, with some expired tokens.',
 'HIGH',
 '## Conclusion\nAUTH_LOGIN 403 increased during 2026-06-19 10:00-11:00, mainly from COURSE_HELPER.\n\n## Evidence\n1. Hourly stat shows fail rate 7.67%.\n2. Gateway logs show SIGNATURE_INVALID, TOKEN_EXPIRED, and SECRET_MISMATCH.\n3. RAG docs say signature failure should check timestamp, nonce, secret, and replay validation.\n\n## Suggestion\nAsk Course Helper team to check mock secret config, client clock sync, and token refresh logic.',
 1, 4, 4, 18000, 'SUCCESS', NULL, NULL, '2026-06-19 11:05:18',
 JSON_OBJECT('source', 'agent_sse', 'mock', true), 'Mock diagnosis report', '2026-06-19 11:05:18', '2026-06-19 11:05:18'),
(16002, 'RPT_DAILY_INSPECTION_20260619', 12002, 'trace_daily_inspection_20260619', 'DAILY_INSPECTION',
 'Daily API Inspection Report',
 'Focus on AUTH_LOGIN 403 increase and LECTURE_REGISTER P95 increase. COURSE_TODAY traffic is high but stable.',
 'HIGH',
 '## Daily Inspection\nHigh risk APIs: AUTH_LOGIN and LECTURE_REGISTER.\n\n## Metrics\nAUTH_LOGIN fail rate is 7.67% at 10:00. LECTURE_REGISTER P95 is 720ms at 12:00 with 700 rate-limited calls. COURSE_TODAY traffic is high but stable.\n\n## Suggestion\nWatch 403, P95, P99, and rate_limit_count.',
 1, 3, 3, 16000, 'SUCCESS', NULL, NULL, '2026-06-19 13:00:16',
 JSON_OBJECT('source', 'agent_sse', 'mock', true), 'Mock daily inspection report', '2026-06-19 13:00:16', '2026-06-19 13:00:16'),
(16003, 'RPT_LECTURE_WARNING_20260619', 12003, 'trace_lecture_warning_20260619', 'WARNING',
 'Lecture Signup Peak Warning Report',
 'Lecture signup open window affects AUTH_LOGIN, LECTURE_LIST, and LECTURE_REGISTER. LECTURE_REGISTER has the highest risk.',
 'HIGH',
 '## Warning\nLecture signup opens at 2026-06-19 12:00 and may cause a traffic peak.\n\n## Impacted APIs\nAUTH_LOGIN, LECTURE_LIST, LECTURE_REGISTER.\n\n## Suggestion\nPrewarm activity cache, keep lecture register rate limit, and watch P95, P99, rate_limit_count, and fail rate.',
 1, 3, 2, 15000, 'SUCCESS', NULL, NULL, '2026-06-19 11:40:15',
 JSON_OBJECT('source', 'agent_sse', 'mock', true), 'Mock warning report', '2026-06-19 11:40:15', '2026-06-19 11:40:15');

INSERT INTO evidence_item (
  id, session_id, trace_id, report_id, source_type, source_id, title, content, confidence,
  status, extra_info, created_at
) VALUES
(15001, 12001, 'trace_auth_403_20260619', 16001, 'API', '1001',
 'API info: School Account Login',
 'AUTH_LOGIN is a HIGH risk unified login API owned by Identity Center and currently ONLINE.',
 NULL, 'ACTIVE', JSON_OBJECT('apiCode', 'AUTH_LOGIN'), '2026-06-19 11:05:03'),
(15002, 12001, 'trace_auth_403_20260619', 16001, 'STAT', '5002',
 'AUTH_LOGIN 10:00-11:00 hourly stats',
 'AUTH_LOGIN total calls 18520, failed calls 1420, fail rate 7.67%, mainly 4xx errors.',
 NULL, 'ACTIVE', JSON_OBJECT('apiCode', 'AUTH_LOGIN', 'failRate', 0.0767), '2026-06-19 11:05:05'),
(15003, 12001, 'trace_auth_403_20260619', 16001, 'LOG', '6001',
 'AUTH_LOGIN 403 gateway log sample',
 'At 10:12:30 COURSE_HELPER called AUTH_LOGIN and got 403 SIGNATURE_INVALID. Log hint says timestamp skew and signature verification failure.',
 NULL, 'ACTIVE', JSON_OBJECT('traceId', 'tr_20260619_auth_001', 'appCode', 'COURSE_HELPER'), '2026-06-19 11:05:07'),
(15004, 12001, 'trace_auth_403_20260619', 16001, 'DOC', 'DOC_AUTH_SIGN_RULE_1',
 'Signature failure troubleshooting doc',
 'Doc suggests checking accessKey, timestamp, nonce, mock_secret, and replay validation. Token expiry and signature failure can both surface as 403.',
 0.8700, 'ACTIVE', JSON_OBJECT('documentId', 10001, 'docType', 'SIGN_RULE', 'apiCode', 'AUTH_LOGIN'), '2026-06-19 11:05:09'),
(15005, 12002, 'trace_daily_inspection_20260619', 16002, 'ALERT', '7001',
 'Daily high-risk alert: AUTH_LOGIN 403 increase',
 'AUTH_LOGIN has a HIGH severity 403 increase alert during 10:00-11:00 and is not resolved.',
 NULL, 'ACTIVE', JSON_OBJECT('eventCode', 'ALERT_AUTH_403_20260619'), '2026-06-19 13:00:04'),
(15006, 12002, 'trace_daily_inspection_20260619', 16002, 'STAT', '5008',
 'LECTURE_REGISTER 12:00 stats',
 'LECTURE_REGISTER total calls 16800, P95 720ms, and rate_limit_count 700 during open window.',
 NULL, 'ACTIVE', JSON_OBJECT('apiCode', 'LECTURE_REGISTER', 'risk', 'HIGH'), '2026-06-19 13:00:05'),
(15007, 12003, 'trace_lecture_warning_20260619', 16003, 'EVENT', '8001',
 'Campus event: AI Lecture Signup Open',
 'Lecture signup opens at 2026-06-19 12:00 and impacts AUTH_LOGIN, LECTURE_LIST, and LECTURE_REGISTER. Expected traffic level is HIGH.',
 NULL, 'ACTIVE', JSON_OBJECT('eventCode', 'LECTURE_SIGNUP_20260619'), '2026-06-19 11:40:04'),
(15008, 12003, 'trace_lecture_warning_20260619', 16003, 'RULE', '4002',
 'LECTURE_REGISTER rate limit rule',
 'LECTURE_REGISTER QPS limit is 180, burst limit is 360, and degrade message is enabled in the open window.',
 NULL, 'ACTIVE', JSON_OBJECT('apiCode', 'LECTURE_REGISTER', 'qpsLimit', 180), '2026-06-19 11:40:07'),
(15009, 12002, 'trace_daily_inspection_20260619', 16002, 'ALERT', '7005',
 'LIBRARY_BORROW dependency timeout alert',
 'LIBRARY_BORROW has 560 5xx errors at 16:00. Gateway log shows DEPENDENCY_TIMEOUT from mock-library-service.',
 NULL, 'ACTIVE', JSON_OBJECT('apiCode', 'LIBRARY_BORROW', 'downstream', 'mock-library-service'), '2026-06-19 13:00:06'),
(15010, 12002, 'trace_daily_inspection_20260619', 16002, 'ALERT', '7006',
 'VENUE_RESERVE duplicate submit risk',
 'VENUE_RESERVE has 409 reservation conflict and 429 rate limited logs with missing idempotency key hints.',
 NULL, 'ACTIVE', JSON_OBJECT('apiCode', 'VENUE_RESERVE', 'risk', 'idempotency'), '2026-06-19 13:00:07');
