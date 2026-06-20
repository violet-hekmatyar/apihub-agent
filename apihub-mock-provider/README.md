# API-HUB Mock Provider

`apihub-mock-provider` is a standalone Spring Boot service for mocking external business APIs managed by API-HUB.

It is not part of `apihub-server`. It does not implement Agent, Tool, SSE, Report, Evidence, Tool Trace, LLM, DashScope, Milvus, Embedding, authentication, or database writes.

## Port

Default port: `8090`

## Build

```powershell
cd D:\apihub-agent-dev\apihub-mock-provider
mvn -q -DskipTests package
```

## Run

```powershell
cd D:\apihub-agent-dev\apihub-mock-provider
mvn spring-boot:run
```

Or run the packaged jar:

```powershell
java -jar target\apihub-mock-provider-0.0.1-SNAPSHOT.jar
```

## Smoke Test

Start the mock provider first, then run:

```powershell
cd D:\apihub-agent-dev
powershell -ExecutionPolicy Bypass -File .\scripts\check-mock-provider-smoke.ps1
```

Custom target:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-mock-provider-smoke.ps1 -BaseUrl http://localhost:8090
```

The smoke test does not require `apihub-server`, MySQL, Redis, Nacos, or any external system.

## Scenario Triggering

All APIs default to `NORMAL` successful behavior.

Exceptional scenarios are triggered explicitly by:

- Request header: `X-Mock-Scenario: SIGNATURE_MISMATCH`
- Query parameter or request body field: `mockScenario=SIGNATURE_MISMATCH`

Priority:

1. `X-Mock-Scenario`
2. `mockScenario`
3. `NORMAL`

## Mock APIs

- `GET /mock-provider/health`
- `POST /mock-provider/auth/login`
- `GET /mock-provider/course/today`
- `GET /mock-provider/lecture/list`
- `POST /mock-provider/lecture/register`
- `GET /mock-provider/notice/list`
- `POST /mock-provider/venue/reserve`
- `GET /mock-provider/library/borrow`

## Boundary

This module only simulates single API call behavior. It does not control traffic ratios, generate gateway logs, aggregate statistics, or produce alerts.

A later Scenario Runner can call this provider in realistic proportions, such as mostly normal traffic plus small percentages of authentication failures, rate limits, conflicts, timeouts, and dependency failures.
