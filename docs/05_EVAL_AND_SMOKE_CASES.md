# API-HUB Agent Evaluation And Smoke Cases

## Purpose

This document defines the current validation assets for the API-HUB Agent backend base interfaces and P0 read-only Tool debug APIs.

This is not a production-complete test plan. It is a concise smoke-test baseline for the current skeleton.

## Current Smoke Case Overview

Covered endpoints:

- `GET /api/health`
- `GET /api/health/db`
- `GET /api/users/current`
- `GET /api/users`
- `POST /api/users/switch`
- `POST /api/dev/tools/queryApiInfo`
- `POST /api/dev/tools/queryApiCallStats`
- `POST /api/dev/tools/queryGatewayLogs`
- `POST /api/dev/tools/queryRateLimitRule`
- `POST /api/dev/tools/queryAlertEvents`
- `POST /api/dev/tools/queryCampusEvents`
- `POST /api/dev/tools/queryApiDocs`
- `GET /api/dev/eval/tool-chain/scenarios`
- `POST /api/dev/eval/tool-chain/run`

Current validation assets:

- OpenAPI file: `docs/openapi/apihub-agent-api.yaml`
- PowerShell smoke script: `scripts/check-backend-smoke.ps1`
- External API scenario baseline: `docs/06_EXTERNAL_API_SCENARIOS.md`

The external API scenario baseline defines which mocked business APIs are managed by API-HUB Agent. It is the foundation for later Tool and Agent evaluation, including Tool selection, evidence generation, and diagnostic answer quality.

The current implemented P0 Tools are:

- `queryApiInfo`
- `queryApiCallStats`
- `queryGatewayLogs`
- `queryRateLimitRule`
- `queryAlertEvents`
- `queryCampusEvents`
- `queryApiDocs`

## Backend Base Interface Checklist

- All responses expose the unified structure `code/message/data/traceId`.
- `GET /api/health` returns `code=200` and `data.status=UP`.
- `GET /api/health/db` returns `code=200`, `data.databaseName=apihub_agent`, and `data.tableCount=17`.
- `GET /api/users/current` without `X-Demo-User-Id` returns the default demo user.
- `GET /api/users/current` with `X-Demo-User-Id=1` returns user `id=1`.
- `GET /api/users?pageNo=1&pageSize=20` returns `code=200`.
- `GET /api/users?pageNo=1&pageSize=200` returns `code=200` and caps `data.pageSize` to `100`.
- `POST /api/users/switch` with `{"userId":1}` returns user `id=1`.
- `GET /api/users/current` with `X-Demo-User-Id=999999` returns business `code=404`.
- `POST /api/users/switch` with `{}` returns business `code=400`.

## Endpoint Validation Goals

`GET /api/health`

Validate that the backend process is reachable and reports service status `UP`.

`GET /api/health/db`

Validate that the backend can connect to the local `apihub_agent` database and read the expected seed schema count.

`GET /api/users/current`

Validate the current demo user behavior, including the default user fallback and explicit `X-Demo-User-Id` header.

`GET /api/users`

Validate basic pagination and the current page-size cap behavior.

`POST /api/users/switch`

Validate demo user switch input handling for a valid user and a missing `userId`.

`POST /api/dev/tools/queryApiInfo`

Validate the dev-only Tool path for API metadata lookup, including success, API_NOT_FOUND, and permission-denied behavior.

`POST /api/dev/tools/queryApiCallStats`

Validate the dev-only Tool path for hourly API call statistics, including success and invalid time-range handling.

`POST /api/dev/tools/queryGatewayLogs`

Validate the dev-only Tool path for gateway log samples, including success, invalid time range, evidence generation in the response, and permission-denied behavior.

`POST /api/dev/tools/queryRateLimitRule`

Validate the dev-only Tool path for rate-limit rule lookup, including success, API_NOT_FOUND behavior, and evidence generation in the response.

`POST /api/dev/tools/queryAlertEvents`

Validate the dev-only Tool path for structured alert events, including success, invalid time range, permission-denied behavior, and response-only alert evidence.

`POST /api/dev/tools/queryCampusEvents`

Validate the dev-only Tool path for campus business event context, including success, API_NOT_FOUND behavior, related API context, and response-only campus event evidence.

`POST /api/dev/tools/queryApiDocs`

Validate the dev-only Tool path for API document chunk lookup, including MySQL keyword search mode, success, API_NOT_FOUND behavior, permission-denied behavior, and response-only document chunk evidence.

`GET /api/dev/eval/tool-chain/scenarios`

Validate the dev-only deterministic Tool Chain Eval scenario catalog before Agent integration.

`POST /api/dev/eval/tool-chain/run`

Validate deterministic Tool chains for fixed diagnostic scenarios. The eval endpoint calls existing Tools, merges `evidenceItems`, and returns a template conclusion. It does not call Agent, LLM, SSE, Milvus, Embedding, or DashScope.

## P0 Tool Smoke Cases

- `queryApiInfo` with manager user `1` and `AUTH_LOGIN` must return `ToolResult.success=true`.
- `queryApiCallStats` with manager user `1` and `LECTURE_REGISTER` must return call totals and a non-empty risk level.
- `queryApiInfo` with `UNKNOWN_API` must return `ToolResult.success=false` and `errorCode=API_NOT_FOUND`.
- `queryApiCallStats` with `startTime > endTime` must return `ToolResult.success=false` and `errorCode=INVALID_ARGUMENT`.
- `queryApiInfo` with normal user `4` querying unrelated `LIBRARY_BORROW` must return `ToolResult.success=false` and `errorCode=PERMISSION_DENIED`.
- `queryGatewayLogs` with manager user `1`, `AUTH_LOGIN`, and HTTP status `403` must return matched gateway logs and non-empty response `evidenceItems`.
- `queryGatewayLogs` with `startTime > endTime` must return `ToolResult.success=false` and `errorCode=INVALID_ARGUMENT`.
- `queryRateLimitRule` with manager user `1` and `LECTURE_REGISTER` must return at least one active rule and non-empty response `evidenceItems`.
- `queryRateLimitRule` with `UNKNOWN_API` must return `ToolResult.success=false` and `errorCode=API_NOT_FOUND`.
- `queryGatewayLogs` with normal user `4` querying unrelated `LIBRARY_BORROW` must return `ToolResult.success=false` and `errorCode=PERMISSION_DENIED`.
- `queryAlertEvents` with manager user `1` and `LECTURE_REGISTER` must return at least one alert and non-empty response `evidenceItems`.
- `queryCampusEvents` with manager user `1` and `LECTURE_REGISTER` must return at least one campus event, related API context, and non-empty response `evidenceItems`.
- `queryAlertEvents` with `startTime > endTime` must return `ToolResult.success=false` and `errorCode=INVALID_ARGUMENT`.
- `queryCampusEvents` with `UNKNOWN_API` must return `ToolResult.success=false` and `errorCode=API_NOT_FOUND`.
- `queryAlertEvents` with normal user `4` querying unrelated `LIBRARY_BORROW` must return `ToolResult.success=false` and `errorCode=PERMISSION_DENIED`.
- `queryApiDocs` with manager user `1`, `AUTH_LOGIN`, and keyword `signature` must return `searchMode=MYSQL_KEYWORD`, at least one document chunk, and non-empty response `evidenceItems`.
- `queryApiDocs` with manager user `1`, `LECTURE_REGISTER`, and keyword `rate` must return `ToolResult.success=true` and `searchMode=MYSQL_KEYWORD`.
- `queryApiDocs` with `UNKNOWN_API` must return `ToolResult.success=false` and `errorCode=API_NOT_FOUND`.
- `queryApiDocs` with normal user `4` querying unrelated `LIBRARY_BORROW` must return `ToolResult.success=false` and `errorCode=PERMISSION_DENIED`.

## Tool Chain Eval Smoke Cases

- Scenario list must include `AUTH_LOGIN_403_DIAG`, `LECTURE_REGISTER_PEAK`, and `VENUE_RESERVE_IDEMPOTENCY`.
- Running `AUTH_LOGIN_403_DIAG` must return `apiCode=AUTH_LOGIN`, at least four steps, non-empty merged evidence, and a non-empty template conclusion.
- Running `LECTURE_REGISTER_PEAK` must return `apiCode=LECTURE_REGISTER`, at least five steps, non-empty merged evidence, and a non-empty template conclusion.
- Running `UNKNOWN_SCENARIO` must return `success=false` and `errorCode=SCENARIO_NOT_FOUND`.
- Running a scenario with `startTime > endTime` must return `success=false` and `errorCode=INVALID_ARGUMENT`.

## Apifox Import Instructions

1. Open Apifox and choose OpenAPI import.
2. Select `docs/openapi/apihub-agent-api.yaml`.
3. Configure an environment variable named `baseUrl` with value `http://localhost:8080`.
4. Execute the imported API collection, or debug each endpoint one by one.

No private Apifox configuration file is required or committed.

## PowerShell Smoke Script

Run from the repository root after `apihub-server` is started:

```powershell
cd D:\apihub-agent-dev
powershell -ExecutionPolicy Bypass -File .\scripts\check-backend-smoke.ps1
```

Use another backend address with `-BaseUrl`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-backend-smoke.ps1 -BaseUrl http://localhost:8080
```

The script prints `[PASS]` or `[FAIL]` for each case. Any failure exits with code `1`; all cases passing exits with code `0`.

## Boundary

This round validates only backend base APIs and dev-only P0 Tool query APIs. It still does not validate Agent execution, SSE streaming, LLM integration, RAG search, frontend behavior, gateway behavior, full authentication, or production monitoring.

Generated Tool `evidenceItems` are validated only as response payloads in this round. The smoke script does not expect rows to be written into `evidence_item`. The current smoke coverage is backend base APIs plus seven P0 Tools and deterministic Tool Chain Eval.

`queryApiDocs` currently validates MySQL keyword retrieval from `rag_document` and `rag_chunk_meta`. It is not Milvus vector retrieval and does not call LLM, Embedding, Agent, SSE, or DashScope.

Tool Chain Eval is also not Agent execution. It does not write `agent_report` and does not generate free-form natural-language reports.

## Future Expansion

Future smoke and evaluation cases can add:

- Tool Trace validation
- Evidence List validation
- Agent SSE streaming validation
- RAG search validation
- External API scenario evaluation based on `docs/06_EXTERNAL_API_SCENARIOS.md`
