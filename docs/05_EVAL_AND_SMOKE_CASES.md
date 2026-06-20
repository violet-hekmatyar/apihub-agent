# API-HUB Agent Evaluation And Smoke Cases

## Purpose

This document defines the current validation assets for the API-HUB Agent backend base interfaces. It is intentionally limited to basic backend reachability, database health, and demo user APIs.

This is not a production-complete test plan. It is a concise smoke-test baseline for the current skeleton.

## Current Smoke Case Overview

Covered endpoints:

- `GET /api/health`
- `GET /api/health/db`
- `GET /api/users/current`
- `GET /api/users`
- `POST /api/users/switch`

Current validation assets:

- OpenAPI file: `docs/openapi/apihub-agent-api.yaml`
- PowerShell smoke script: `scripts/check-backend-smoke.ps1`
- External API scenario baseline: `docs/06_EXTERNAL_API_SCENARIOS.md`

The external API scenario baseline defines which mocked business APIs are managed by API-HUB Agent. It is the foundation for later Tool and Agent evaluation, including Tool selection, evidence generation, and diagnostic answer quality.

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

This round does not validate Agent execution, Tool Calling, SSE streaming, RAG search, frontend behavior, gateway behavior, authentication, authorization, or production monitoring.

## Future Expansion

Future smoke and evaluation cases can add:

- Tool Trace validation
- Evidence List validation
- Agent SSE streaming validation
- RAG search validation
- External API scenario evaluation based on `docs/06_EXTERNAL_API_SCENARIOS.md`
