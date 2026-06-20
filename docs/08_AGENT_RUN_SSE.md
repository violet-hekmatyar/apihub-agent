# Agent Run And SSE Skeleton

## Purpose

This document describes the first Agent Run skeleton. It provides a deterministic backend execution path and SSE event stream on top of the existing Tool Chain Eval scenarios.

This version does not call LLM, DashScope, Milvus, Embedding, frontend APIs, real external business APIs, auto-fix workflows, or multi-agent orchestration.

## Endpoints

### `POST /api/agent/run`

Runs one deterministic Agent scenario and returns the final result as JSON.

Request example:

```json
{
  "scenarioCode": "LECTURE_REGISTER_PEAK",
  "question": "Analyze lecture registration peak risk",
  "startTime": "2026-06-19 00:00:00",
  "endTime": "2026-06-19 23:59:59"
}
```

Response `data` includes:

- `success`
- `runId`
- `sessionId`
- `reportId`
- `scenarioCode`
- `apiCode`
- `steps`
- `evidenceItems`
- `riskLevel`
- `riskReasons`
- `finalAnswer`
- `errorCode`
- `errorMessage`
- `traceId`
- `latencyMs`

### `POST /api/agent/run/stream`

Runs the same deterministic Agent scenario and returns `text/event-stream`.

Current event types:

- `agent_start`
- `stage`
- `tool_step`
- `evidence`
- `risk`
- `answer`
- `error`
- `done`

The stream is intended for frontend integration validation only. It is not a token-by-token LLM stream.

## Scenario Selection

`scenarioCode` has priority when provided.

If `scenarioCode` is omitted, the skeleton applies simple deterministic matching against `apiCode` and `question`:

- `AUTH_LOGIN_403_DIAG`: `AUTH_LOGIN`, `403`, signature, token, auth, login keywords.
- `LECTURE_REGISTER_PEAK`: `LECTURE_REGISTER`, lecture, signup/register, peak, rate-limit, `429` keywords.
- `VENUE_RESERVE_IDEMPOTENCY`: `VENUE_RESERVE`, venue, reserve, duplicate, idempotency, conflict keywords.
- `LIBRARY_BORROW_DEPENDENCY`: `LIBRARY_BORROW`, library, borrow, timeout, dependency keywords.

No match returns `success=false` with `errorCode=SCENARIO_NOT_MATCHED`.

## Persistence

Successful or matched failed runs use the current schema without migration:

- `agent_session`: one run session, with `session_type=DIAGNOSIS` and workflow `agent_run_skeleton`.
- `agent_message`: user question and assistant answer using `message_role=USER/ASSISTANT`.
- `agent_report`: deterministic markdown report content and result summary.
- `evidence_item`: merged Tool evidence persisted with normalized `source_type` and original evidence JSON in `extra_info`.

`tool_call_trace` remains written by the underlying Tools. Agent Run does not write Tool traces directly.

## Current Boundary

The final answer is template-based and deterministic. It uses Tool Chain Eval `templateConclusion`, risk level, risk reasons, and evidence count.

This skeleton is suitable for smoke testing Agent session/report/evidence persistence and SSE event shape. It is not a production diagnostic agent and does not perform automatic remediation.
