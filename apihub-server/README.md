# API-HUB Agent Server

Minimal Spring Boot backend for the API-HUB Agent first-stage skeleton.

This round implements only:

- `GET /api/health`
- `GET /api/health/db`
- `GET /api/users/current`
- `GET /api/users`
- `POST /api/users/switch`

It does not implement Agent, Tool Calling, SSE, RAG, JWT, Spring Security, Redis, Nacos, or Milvus.

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

It validates response `code` values and key fields such as `data.status`, `data.databaseName`, `data.tableCount`, demo user `id`, and page-size capping.
