# API-HUB Agent Server

Minimal Spring Boot backend for the API-HUB Agent first-stage skeleton.

This round implements only:

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

It does not implement Agent, SSE, RAG, JWT, Spring Security, Redis, Nacos, Milvus, or real external business APIs.

## Build

```powershell
cd D:\apihub-agent-dev\apihub-server
mvn -q -DskipTests package
```

## Run

Pass database connection information through environment variables. Do not hard-code real passwords in source code.

```powershell
$env:APIHUB_DB_URL="jdbc:mysql://127.0.0.1:3306/apihub_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:APIHUB_DB_USERNAME="root"
$env:APIHUB_DB_PASSWORD="<your local mysql password>"
mvn spring-boot:run
```

## Endpoints

```powershell
curl http://localhost:8080/api/health
curl http://localhost:8080/api/health/db
curl -H "X-Demo-User-Id: 1" http://localhost:8080/api/users/current
curl http://localhost:8080/api/users
curl -X POST http://localhost:8080/api/users/switch -H "Content-Type: application/json" -d "{\"userId\":1}"
```

All normal HTTP APIs return:

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "traceId": "trace_req_xxx"
}
```

## OpenAPI And Apifox

The OpenAPI 3.0 file is:

```text
D:\apihub-agent-dev\docs\openapi\apihub-agent-api.yaml
```

To import it into Apifox:

1. Choose OpenAPI import.
2. Select `docs/openapi/apihub-agent-api.yaml`.
3. Configure environment variable `baseUrl=http://localhost:8080`.
4. Execute the collection or debug the APIs one by one.

No private Apifox configuration file is required.

## Backend Smoke Test

Start `apihub-server` first, then run from the repository root:

```powershell
cd D:\apihub-agent-dev
powershell -ExecutionPolicy Bypass -File .\scripts\check-backend-smoke.ps1
```

Optional custom base URL:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-backend-smoke.ps1 -BaseUrl http://localhost:8080
```

The smoke script covers:

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

It validates response `code` values and key fields such as `data.status`, `data.databaseName`, `data.tableCount`, demo user `id`, and page-size capping.
For Tool debug APIs it validates `ToolResult.success`, representative result fields, business failures, permission denial, and non-empty in-response evidence for log/rule success cases.

## Dev-Only Tool Debug APIs

These endpoints are for backend development and smoke validation only. They are not formal frontend entry points. Later Agent code should call the Tool service internally.

```powershell
curl -X POST http://localhost:8080/api/dev/tools/queryApiInfo `
  -H "Content-Type: application/json" `
  -H "X-Demo-User-Id: 1" `
  -d "{\"apiCode\":\"AUTH_LOGIN\",\"includeRateLimit\":true,\"includeConsumerApps\":true}"

curl -X POST http://localhost:8080/api/dev/tools/queryApiCallStats `
  -H "Content-Type: application/json" `
  -H "X-Demo-User-Id: 1" `
  -d "{\"apiCode\":\"LECTURE_REGISTER\",\"startTime\":\"2026-06-19 00:00:00\",\"endTime\":\"2026-06-19 23:59:59\"}"

curl -X POST http://localhost:8080/api/dev/tools/queryGatewayLogs `
  -H "Content-Type: application/json" `
  -H "X-Demo-User-Id: 1" `
  -d "{\"apiCode\":\"AUTH_LOGIN\",\"startTime\":\"2026-06-19 00:00:00\",\"endTime\":\"2026-06-19 23:59:59\",\"httpStatus\":403,\"limit\":20}"

curl -X POST http://localhost:8080/api/dev/tools/queryRateLimitRule `
  -H "Content-Type: application/json" `
  -H "X-Demo-User-Id: 1" `
  -d "{\"apiCode\":\"LECTURE_REGISTER\",\"includeInactive\":false}"

curl -X POST http://localhost:8080/api/dev/tools/queryAlertEvents `
  -H "Content-Type: application/json" `
  -H "X-Demo-User-Id: 1" `
  -d "{\"apiCode\":\"LECTURE_REGISTER\",\"startTime\":\"2026-06-19 00:00:00\",\"endTime\":\"2026-06-19 23:59:59\",\"limit\":20}"

curl -X POST http://localhost:8080/api/dev/tools/queryCampusEvents `
  -H "Content-Type: application/json" `
  -H "X-Demo-User-Id: 1" `
  -d "{\"apiCode\":\"LECTURE_REGISTER\",\"startTime\":\"2026-06-19 00:00:00\",\"endTime\":\"2026-06-19 23:59:59\",\"includeRelatedApis\":true,\"limit\":20}"

curl -X POST http://localhost:8080/api/dev/tools/queryApiDocs `
  -H "Content-Type: application/json" `
  -H "X-Demo-User-Id: 1" `
  -d "{\"apiCode\":\"AUTH_LOGIN\",\"keyword\":\"signature\",\"limit\":5}"
```

Tool business failures such as `API_NOT_FOUND`, `INVALID_ARGUMENT`, and `PERMISSION_DENIED` are returned as `ToolResult.success=false` inside an outer `code=200` response. Each Tool call writes a `tool_call_trace` row. Because the current database schema requires `tool_call_trace.session_id`, dev-only calls create or reuse a lightweight dev session when no Agent session exists.

`queryApiDocs` currently searches MySQL `rag_document` and `rag_chunk_meta` with keyword filters and returns `searchMode=MYSQL_KEYWORD`. It does not use Milvus, embeddings, DashScope, Agent, or SSE.

`queryGatewayLogs`, `queryRateLimitRule`, `queryAlertEvents`, `queryCampusEvents`, and `queryApiDocs` return `evidenceItems` in the `ToolResult` response for later Agent/report assembly. This round does not persist those generated evidence items into the `evidence_item` table. Formal Agent integration will later organize Evidence, Trace, and reports through Agent Run.
