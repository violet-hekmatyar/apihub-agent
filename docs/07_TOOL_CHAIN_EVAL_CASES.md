# Tool Chain Eval Cases

## Purpose

This document defines the deterministic Tool Chain Eval baseline used by the current Agent Run skeleton.

It is not formal Agent output. It does not call an LLM, does not stream SSE, does not write `agent_report`, and does not write `evidence_item`. It verifies that the current read-only Tools can produce a stable evidence chain. The first Agent Run skeleton now reuses this chain and performs Agent-side session, message, report, and evidence persistence.

## Implemented P0 Tools

- `queryApiInfo`
- `queryApiCallStats`
- `queryGatewayLogs`
- `queryRateLimitRule`
- `queryAlertEvents`
- `queryCampusEvents`
- `queryApiDocs`

## Boundary

Tool Chain Eval is deterministic backend orchestration:

- It calls fixed Tools in a fixed order.
- It returns structured steps, merged evidence, risk reasons, and a template conclusion.
- It does not generate a free-form natural-language report.
- It does not call DashScope, Milvus, Embedding, SSE, or Agent runtime.
- The current Agent Run skeleton reuses these chains as an evidence baseline.

## Scenarios

### AUTH_LOGIN_403_DIAG

- Default API: `AUTH_LOGIN`
- Background: unified login 403, signature failure, token expiry, or caller configuration mismatch.
- Tool order: `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents`, `queryApiDocs`.
- Expected evidence: API metadata, hourly failure stats, 403 gateway logs, alert events, signature/error-code document chunks.
- Expected conclusion direction: check signature algorithm, timestamp, nonce, token expiry, and caller configuration first. This is diagnostic guidance, not automatic repair.

### LECTURE_REGISTER_PEAK

- Default API: `LECTURE_REGISTER`
- Background: lecture signup open window may create write concurrency, latency, and 429 risk.
- Tool order: `queryApiInfo`, `queryCampusEvents`, `queryApiCallStats`, `queryGatewayLogs`, `queryRateLimitRule`, `queryAlertEvents`, `queryApiDocs`.
- Expected evidence: campus event context, related `AUTH_LOGIN` and `LECTURE_LIST` APIs, call stats, 429 logs, active rate-limit rule, alert event, peak/rate-limit documentation.
- Expected conclusion direction: likely business peak risk, requiring joint observation of auth, list query, and submit APIs.

### VENUE_RESERVE_IDEMPOTENCY

- Default API: `VENUE_RESERVE`
- Background: venue reservation open window can trigger duplicate submit, reservation conflict, and idempotency risks.
- Tool order: `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryCampusEvents`, `queryAlertEvents`, `queryApiDocs`.
- Expected evidence: stats, duplicate/idempotency gateway logs, campus event context, alert event, idempotency documentation.
- Expected conclusion direction: check frontend retry behavior, idempotency key, business lock, and conflict handling.

### LIBRARY_BORROW_DEPENDENCY

- Default API: `LIBRARY_BORROW`
- Background: downstream library service timeout or unavailable state can cause 5xx risk.
- Tool order: `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents`, `queryApiDocs`.
- Expected evidence: stats, dependency timeout gateway logs, alert event, dependency troubleshooting documentation.
- Expected conclusion direction: inspect downstream health, timeout settings, and degrade behavior.

## Current Limits And Next Steps

- Tool Chain Eval itself still does not connect Agent, SSE, LLM, Milvus, or Embedding.
- Tool Chain Eval itself still does not write `evidence_item` or `agent_report`.
- The Tool Chain Eval result uses `mergedEvidenceItems` and `templateConclusion` only.
- Agent Run/SSE skeletons consume these deterministic evidence chains and add minimal Agent persistence.
