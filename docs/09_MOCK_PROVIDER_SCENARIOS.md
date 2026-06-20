# Mock Provider Scenarios

## Document Purpose

This document defines the standalone external API mock provider for API-HUB.

`apihub-mock-provider` is independent from `apihub-server`. It exists so a later Scenario Runner can generate realistic external API calls before gateway log recording, stats aggregation, alert evaluation, Tool queries, and Agent diagnosis.

## Module Boundary

`apihub-server` owns Agent, Tool, Trace, Evidence, Report, and API-HUB backend behavior.

`apihub-mock-provider` owns only external business API simulation. It returns fixed or rule-based mock data for one request at a time.

A later Scenario Runner will own batch traffic generation. It can call this provider with different `mockScenario` values and then produce logs, stats, and alerts.

The mock provider does not connect to MySQL, Redis, Nacos, Milvus, DashScope, or any real business system.

## Scenario Triggering

All APIs default to `NORMAL`.

Exceptional scenarios are triggered explicitly:

- Header: `X-Mock-Scenario`
- Query parameter: `mockScenario`
- Request body field: `mockScenario`

Priority:

1. `X-Mock-Scenario`
2. `mockScenario`
3. `NORMAL`

Scenarios are deterministic. They are not randomly triggered by the provider.

## External API List

| API Code | Mock Path | Normal Purpose | Normal Response | Exceptional Scenarios |
| --- | --- | --- | --- | --- |
| `AUTH_LOGIN` | `POST /mock-provider/auth/login` | Mock school account login. | Mock bearer token, student number, display name, expiration. | `SIGNATURE_MISMATCH`, `TOKEN_EXPIRED`, `TIMESTAMP_EXPIRED`, `NONCE_REPLAY`, `UNKNOWN_APP` |
| `COURSE_TODAY` | `GET /mock-provider/course/today` | Mock today's student course schedule. | Fixed course list with room, teacher, time range, and week number. | `SLOW_RESPONSE`, `DOWNSTREAM_SLOW`, `COURSE_SYSTEM_TIMEOUT`, `CACHE_MISS` |
| `LECTURE_LIST` | `GET /mock-provider/lecture/list` | Mock lecture list lookup. | Fixed lecture list with quota and registration status. | `HOT_READ`, `SLOW_RESPONSE`, `SERVICE_BUSY` |
| `LECTURE_REGISTER` | `POST /mock-provider/lecture/register` | Mock lecture signup submit. | Success status and mock ticket number. | `RATE_LIMITED`, `DUPLICATE_REQUEST`, `SOLD_OUT`, `IDEMPOTENCY_KEY_MISSING`, `SLOW_RESPONSE`, `SERVICE_BUSY` |
| `CAMPUS_NOTICE` | `GET /mock-provider/notice/list` | Mock campus notice list lookup. | Fixed notice list with publisher and publish time. | `HOT_NOTICE`, `CACHE_MISS`, `SLOW_RESPONSE`, `SERVICE_BUSY` |
| `VENUE_RESERVE` | `POST /mock-provider/venue/reserve` | Mock venue reservation submit. | Success status and mock reservation number. | `RESERVATION_CONFLICT`, `DUPLICATE_REQUEST`, `IDEMPOTENCY_KEY_MISSING`, `RATE_LIMITED`, `SLOW_RESPONSE` |
| `LIBRARY_BORROW` | `GET /mock-provider/library/borrow` | Mock library borrow record lookup. | Fixed borrow records with due date and status. | `DOWNSTREAM_TIMEOUT`, `DEPENDENCY_UNAVAILABLE`, `SLOW_RESPONSE`, `SERVICE_ERROR` |

## Design Principles

Default traffic is normal and successful.

Exceptions are explicit and deterministic. The provider does not make every API fail by default and does not use random failure injection.

The provider simulates single API behavior only. Realistic traffic shape belongs to the future Scenario Runner. A realistic runner can send roughly 80%-95% normal traffic and 5%-20% mixed exceptional traffic, with different ratios per API and time window.

The current normal and exceptional modes exist so Scenario Runner can combine them into realistic business flows, such as lecture signup peaks, login signature failures, duplicate venue reservations, or library dependency timeouts.

## Future Plan

1. Scenario Runner calls this mock provider in batches.
2. Gateway Log Recorder stores simulated request results.
3. Stats Aggregator summarizes call counts, latency, failures, and rate limits.
4. Alert Evaluator generates alert events from aggregated signals.
5. Existing Tools query logs, stats, rules, alerts, events, and docs.
6. Agent Run diagnoses the newly generated data.
