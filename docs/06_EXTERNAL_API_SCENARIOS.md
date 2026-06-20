# External API Scenario Baseline

## Purpose

This document defines the first baseline of external business APIs managed by API-HUB Agent.
It is the shared scenario source for later Tool, Evidence, Agent, and frontend Dashboard work.

The current round uses mock MySQL seed facts and dev-only read-only Tool execution for backend validation. It does not implement real business APIs, Agent execution, SSE, or frontend pages.

Current implemented Tool support is limited to:

- `queryApiInfo`
- `queryApiCallStats`
- `queryGatewayLogs`
- `queryRateLimitRule`
- `queryAlertEvents`
- `queryCampusEvents`
- `queryApiDocs`

The current `queryApiDocs` implementation is MySQL keyword retrieval over `rag_document` and `rag_chunk_meta`; it is not Milvus vector retrieval.

## Boundary Between External APIs And Internal Capabilities

`api_endpoint` stores only business APIs exposed by API-HUB to caller applications.

Internal platform capabilities are not external APIs and must not be stored in `api_endpoint`:

- RAG search
- Agent execution
- Trace query
- Evidence query
- Dashboard query
- Report query
- API documentation retrieval itself

`queryApiDocs` retrieves documents for external APIs. The document retrieval capability is internal; the retrieved documents describe external APIs such as `AUTH_LOGIN` and `LECTURE_REGISTER`.

## External API List

| API Code | API Name | Main Callers | Business Purpose | Typical Incident Scenarios | Relevant Tools |
| --- | --- | --- | --- | --- | --- |
| `AUTH_LOGIN` | Unified Login API | Course Helper, Club Activity System, Lecture Portal | Campus account login, token refresh, unified identity authentication | 401/403 increase, signature failure, token expiry, timestamp/nonce failure, suspicious high-frequency failures | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryApiDocs`, `queryAlertEvents` |
| `COURSE_TODAY` | Today Course Schedule API | Course Helper Mini App | Student daily course schedule, classroom, teaching-week info | Semester-start peak, cache pressure, P95/P99 increase, downstream academic system slow | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents` |
| `LECTURE_LIST` | Lecture List API | College public account, Club Activity System, Lecture Portal | Recent lectures, lecture detail, signup status | Notice publish traffic increase, hot activity query increase, cache hit-rate drop | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryCampusEvents` |
| `LECTURE_REGISTER` | Lecture Register API | Lecture Portal, activity signup systems | Student lecture registration and quota competition | Open-window concurrency, rate limit, repeated request, quota race, P95 increase | `queryApiInfo`, `queryApiCallStats`, `queryRateLimitRule`, `queryGatewayLogs`, `queryCampusEvents`, `queryApiDocs` |
| `CAMPUS_NOTICE` | Campus Notice API | Course Helper, Student Service Portal, school sites | College notices, campus announcements, exam notices | Post-publish traffic spike, read-heavy access, stale cache, slow hot notice response | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents` |
| `VENUE_RESERVE` | Venue Reserve API | Student Service Portal, club activity systems | Classroom, hall, lab, and activity venue reservation | Reservation open concurrency, duplicate submit, idempotency risk, 409/429 increase | `queryApiInfo`, `queryApiCallStats`, `queryRateLimitRule`, `queryGatewayLogs`, `queryApiDocs`, `queryAlertEvents` |
| `LIBRARY_BORROW` | Library Borrow API | Library Mini App, study assistants | Borrow records, due reminders, renewal status | Downstream library service slow, dependency timeout, 5xx increase, external dependency unavailable | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryApiDocs`, `queryAlertEvents` |

Implemented now for all 7 APIs:

- `queryApiInfo`: returns basic API metadata with optional rate-limit and authorized caller summaries.
- `queryApiCallStats`: returns hourly stat totals, latency indicators, rate-limit counts, and a simple risk level.
- `queryGatewayLogs`: returns gateway log samples, status/app/error distributions, risk hints, and in-response `GATEWAY_LOG` evidence items.
- `queryRateLimitRule`: returns active or optionally inactive rate-limit rules, check points, risk hints, and in-response `RATE_LIMIT_RULE` evidence items.
- `queryAlertEvents`: returns alert event summaries, severity/status distributions, open alert counts, risk hints, and in-response `ALERT_EVENT` evidence items.
- `queryCampusEvents`: returns campus business event context, related API codes, risk hints, and in-response `CAMPUS_EVENT` evidence items.
- `queryApiDocs`: returns MySQL document chunks for external API documentation, signing rules, error codes, rate-limit notes, and troubleshooting manuals with in-response `DOC_CHUNK` evidence items.

`queryRateLimitRule` returns data only for APIs that have seeded rate-limit rules, such as `AUTH_LOGIN`, `LECTURE_REGISTER`, and `VENUE_RESERVE`. `queryCampusEvents` returns business context for seeded events such as lecture signup, semester start, notice publish, and venue reservation open windows. `queryApiDocs` is an internal Tool for reading documentation about external APIs; it must not be modeled as an external API.

Tool Chain Eval scenarios are deterministic backend evaluation chains that combine these Tools before Agent integration. They are not external business APIs and are not formal Agent runs.

## Scenario Matrix

| Scenario Type | APIs | Seed Evidence | Tools To Call | Expected Evidence Types | Agent Analysis Direction |
| --- | --- | --- | --- | --- | --- |
| Authentication failure / signature error | `AUTH_LOGIN` | `api_call_stat_hourly` 403 increase, `gateway_log` with `SIGNATURE_INVALID`, `TOKEN_EXPIRED`, `SECRET_MISMATCH`, suspicious unknown app sample, RAG chunks for signature rules | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryApiDocs`, `queryAlertEvents` | `API`, `STAT`, `LOG`, `DOC`, `ALERT` | Identify caller concentration, compare error codes, explain signature/timestamp/nonce/token causes, recommend checking mock secret config and client clock. |
| Business peak / high concurrency | `LECTURE_REGISTER`, `AUTH_LOGIN`, `LECTURE_LIST` | `campus_event` `LECTURE_SIGNUP_20260619`, `event_api_relation`, high `total_count`, P95/P99 increase | `queryCampusEvents`, `queryApiCallStats`, `queryRateLimitRule` | `EVENT`, `STAT`, `RULE`, `API` | Predict impacted APIs, define observation window, recommend cache prewarm and rate-limit monitoring. |
| Cache pressure / slow response | `COURSE_TODAY`, `CAMPUS_NOTICE` | High latency stats, `gateway_log` hints `cache miss`, `downstream slow`, `query timeout` | `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents` | `STAT`, `LOG`, `ALERT` | Distinguish traffic increase from failure-rate increase, focus on cache hit rate and downstream response time. |
| Rate limit triggered | `LECTURE_REGISTER`, `VENUE_RESERVE` | `rate_limit_rule`, `api_call_stat_hourly.rate_limit_count`, `gateway_log` 429 samples | `queryRateLimitRule`, `queryApiCallStats`, `queryGatewayLogs` | `RULE`, `STAT`, `LOG` | Check whether limits match open-window traffic, explain rate-limited callers, recommend temporary queue or prewarm. |
| Downstream dependency exception | `LIBRARY_BORROW` | 5xx stat increase, `gateway_log` `DEPENDENCY_TIMEOUT`, alert for mock library service | `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents`, `queryApiDocs` | `STAT`, `LOG`, `ALERT`, `DOC` | Attribute failure to downstream dependency, propose timeout/degrade and user-facing retry guidance. |
| Duplicate request / idempotency risk | `VENUE_RESERVE` | `gateway_log` 409/429, missing idempotency key, alert for duplicate submit | `queryApiCallStats`, `queryGatewayLogs`, `queryRateLimitRule`, `queryApiDocs` | `STAT`, `LOG`, `RULE`, `DOC`, `ALERT` | Explain duplicate-submit risk, check idempotency key policy, recommend caller retry discipline. |
| Permission boundary rejection | `LECTURE_LIST` logs owned by `CLUB_ACTIVITY` | `tool_call_trace` failed with `PERMISSION_DENIED`, no evidence generated | `queryGatewayLogs` | none | Refuse cross-team log disclosure for normal users and explain authorization boundary. |

## Seed Data Notes

The seed data supports these first-round evaluation paths:

- Unified login 403 diagnosis: `AUTH_LOGIN` stats, gateway logs, alert, signature-rule RAG chunks, and diagnosis report.
- Today API inspection: `AUTH_LOGIN`, `LECTURE_REGISTER`, `COURSE_TODAY`, `CAMPUS_NOTICE`, `VENUE_RESERVE`, and `LIBRARY_BORROW` have stats, logs, and alerts for risk ranking.
- Lecture registration peak warning: `campus_event`, `event_api_relation`, stats, and rate-limit rules connect the signup open window to impacted APIs.
- RAG signature rule retrieval: RAG documents and chunks describe `AUTH_LOGIN` signing, token expiry, and troubleshooting.
- Normal user permission denial: a failed `queryGatewayLogs` trace records `PERMISSION_DENIED` for cross-team log access.
- Downstream dependency exception: `LIBRARY_BORROW` contains 5xx stats, timeout gateway logs, and alert evidence.
- Venue reservation duplicate request / idempotency risk: `VENUE_RESERVE` contains 409/429 logs, rate-limit rule, and idempotency documentation.
- In the current backend Tool layer, gateway log and rate-limit rule evidence is returned inside `ToolResult.evidenceItems`; it is not persisted into `evidence_item`.
- `AUTH_LOGIN` has alert context for 403 increase and campus event context through lecture signup and semester-start events.
- `LECTURE_REGISTER` has high-latency alert context and lecture signup campus event context with related `AUTH_LOGIN` and `LECTURE_LIST` APIs.
- `COURSE_TODAY` has a stable traffic alert and semester-start campus event context.
- `VENUE_RESERVE` has duplicate-submit alert context and venue reservation open-window campus event context.
- `LIBRARY_BORROW` has downstream dependency alert context; it currently has no seeded campus business event relation.
- In the current backend Tool layer, alert event and campus event evidence is also returned inside `ToolResult.evidenceItems`; it is not persisted into `evidence_item`.
- `queryApiDocs` can retrieve seeded MySQL document chunks for `AUTH_LOGIN` signature rules and error codes, `LECTURE_REGISTER` peak/rate-limit guidance, `VENUE_RESERVE` idempotency notes, and `LIBRARY_BORROW` dependency timeout troubleshooting.
- `queryApiDocs` does not perform vector search in the current round; Milvus, Embedding, and DashScope remain outside the implemented boundary.
- `AUTH_LOGIN_403_DIAG` combines API metadata, stats, 403 gateway logs, alerts, and signature docs for the unified-login 403 diagnosis path.
- `LECTURE_REGISTER_PEAK` combines campus events, stats, 429 logs, rate-limit rules, alerts, and docs for the lecture signup peak path.
- `VENUE_RESERVE_IDEMPOTENCY` combines stats, duplicate/idempotency logs, campus event context, alerts, and docs for duplicate-submit risk.
- `LIBRARY_BORROW_DEPENDENCY` combines stats, timeout logs, alerts, and docs for downstream dependency risk.

## Current Boundary

- Current data is mock demonstration data, not production data.
- No real fault API is implemented in this round.
- The current baseline uses MySQL seed rows to create queryable facts.
- Later rounds can connect additional Tool implementations, Trace playback, Evidence persistence, Agent SSE, and frontend Dashboard views to these facts.
