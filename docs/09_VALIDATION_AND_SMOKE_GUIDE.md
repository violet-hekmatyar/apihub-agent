# API-HUB Agent 验收与 Smoke 指南

## 0. 文档定位

本文档用于记录当前项目的 smoke、Apifox、人工验收和回归测试方式。

本文档是验收指南，不是核心架构设计文档。核心设计请优先阅读：

```text
05_API_INVOKE_CONTRACT.md
06_EXTERNAL_API_CATALOG.md
07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md
08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md
```

---

## 1. 当前验证资产

| 类型 | 文件 |
|---|---|
| Agent 后端 OpenAPI | `docs/openapi/apihub-agent-api.yaml` |
| Mock Provider OpenAPI | `docs/openapi/apihub-mock-provider-api.yaml` |
| Agent 后端 smoke | `scripts/check-backend-smoke.ps1` |
| Mock Provider smoke | `scripts/check-mock-provider-smoke.ps1` |
| 外部 API 目录 | `06_EXTERNAL_API_CATALOG.md` |
| Mock Provider 与流量模拟设计 | `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md` |

---

## 2. 后端 smoke 范围

当前 `check-backend-smoke.ps1` 覆盖：

```text
基础健康检查
Demo 用户接口
7 个 P0 Tool debug API
Tool Chain Eval
Agent Run
Agent Run SSE
```

主要接口：

```text
GET  /api/health
GET  /api/health/db
GET  /api/users/current
GET  /api/users
POST /api/users/switch
POST /api/dev/tools/queryApiInfo
POST /api/dev/tools/queryApiCallStats
POST /api/dev/tools/queryGatewayLogs
POST /api/dev/tools/queryRateLimitRule
POST /api/dev/tools/queryAlertEvents
POST /api/dev/tools/queryCampusEvents
POST /api/dev/tools/queryApiDocs
GET  /api/dev/eval/tool-chain/scenarios
POST /api/dev/eval/tool-chain/run
POST /api/agent/run
POST /api/agent/run/stream
```

运行方式：

```powershell
cd D:\apihub-agent-dev
powershell -ExecutionPolicy Bypass -File .\scripts\check-backend-smoke.ps1
```

指定地址：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-backend-smoke.ps1 -BaseUrl http://localhost:8080
```

验收规则：

```text
所有 case 输出 [PASS]。
任一失败脚本 exit 1。
全部通过脚本 exit 0。
```

---

## 3. Mock Provider smoke 范围

当前 `check-mock-provider-smoke.ps1` 覆盖：

```text
健康检查
7 个外部 API 的 NORMAL 场景
关键异常场景
X-Mock-Scenario 优先级
HTTP 状态码和 body.code 一致性
traceId 返回
```

运行方式：

```powershell
cd D:\apihub-agent-dev
powershell -ExecutionPolicy Bypass -File .\scripts\check-mock-provider-smoke.ps1
```

指定地址：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-mock-provider-smoke.ps1 -BaseUrl http://localhost:8090
```

重点 case：

| API | 场景 | 预期 |
|---|---|---|
| `GET /mock-provider/health` | - | HTTP 200 |
| `POST /mock-provider/auth/login` | NORMAL | HTTP 200 |
| `POST /mock-provider/auth/login` | `SIGNATURE_MISMATCH` | HTTP 403 |
| `POST /mock-provider/auth/login` | `TOKEN_EXPIRED` | HTTP 401 |
| `GET /mock-provider/course/today` | NORMAL | HTTP 200 |
| `GET /mock-provider/course/today` | `COURSE_SYSTEM_TIMEOUT` | HTTP 504 |
| `POST /mock-provider/lecture/register` | NORMAL | HTTP 200 |
| `POST /mock-provider/lecture/register` | `RATE_LIMITED` | HTTP 429 |
| `POST /mock-provider/lecture/register` | `DUPLICATE_REQUEST` | HTTP 409 |
| `POST /mock-provider/venue/reserve` | `RESERVATION_CONFLICT` | HTTP 409 |
| `GET /mock-provider/library/borrow` | `DOWNSTREAM_TIMEOUT` | HTTP 504 |

---

## 4. Apifox 验收建议

### 4.1 Agent 后端接口

导入：

```text
docs/openapi/apihub-agent-api.yaml
```

环境：

```text
baseUrl = http://localhost:8080
```

重点检查：

```text
统一响应 code/message/data/traceId。
Tool debug API 成功和失败格式。
Tool Chain Eval 返回 steps 和 merged evidence。
Agent Run 返回 sessionId、reportId、finalAnswer。
SSE 返回事件文本。
```

### 4.2 Mock Provider 接口

导入：

```text
docs/openapi/apihub-mock-provider-api.yaml
```

环境：

```text
baseUrl = http://localhost:8090
```

重点检查：

```text
默认 NORMAL 是否正常。
异常是否必须显式触发。
HTTP 状态码是否真实。
body.code 是否和 HTTP 状态码一致。
traceId 是否返回。
是否无真实 token / secret / password。
```

---

## 5. 后续新增验收项

### 5.1 Gateway Invoke 验收

实现 `POST /api/dev/gateway/invoke` 后新增：

```text
调用 AUTH_LOGIN NORMAL，外层 code=200，data.httpStatus=200，gateway_log 有记录。
调用 LECTURE_REGISTER RATE_LIMITED，外层 code=200，data.httpStatus=429，gateway_log 有记录。
apiCode 不存在，外层业务失败。
traceId / requestId / scenarioRunId 能在 gateway_log 中关联。
```

Gateway Invoke smoke 应使用已授权 appCode：`AUTH_LOGIN`/`COURSE_TODAY` 使用 `COURSE_HELPER`，`LECTURE_LIST`/`LECTURE_REGISTER` 使用 `LECTURE_PORTAL`，`CAMPUS_NOTICE` 使用 `STUDENT_SERVICE`，`VENUE_RESERVE` 使用 `CLUB_ACTIVITY`，`LIBRARY_BORROW` 使用 `LIBRARY_MINI`。
新写入的 gateway_log 建议通过固定 traceId、requestId 或当前时间窗口验证。`queryApiCallStats` 暂时只反映 `api_call_stat_hourly`，不会自动体现 Gateway Invoke 新日志，需等待 Stats Aggregator。
Gateway Invoke v1 验收需要同时启动 `apihub-server` 8080 和 `apihub-mock-provider` 8090。该接口只负责代理调用 mock-provider 并写入 `gateway_log`，不会聚合 `api_call_stat_hourly`，也不会生成 `alert_event`。

### 5.2 Scenario Runner 验收

实现 `POST /mock-provider/scenarios/run` 后新增：

```text
NORMAL_DAY 能生成正常占多数的流量。
LECTURE_REGISTER_PEAK 能生成 AUTH_LOGIN、LECTURE_LIST、LECTURE_REGISTER 混合调用。
高峰场景具有 ramp-up / peak / ramp-down 阶段分布。
randomSeed 固定时结果可复现。
所有具体调用经过 /api/dev/gateway/invoke。
每次调用都能在 gateway_log 中找到。
```

### 5.3 Stats Aggregator 验收

实现后新增：

```text
gateway_log 能聚合到 api_call_stat_hourly。
能计算 total/success/fail/rateLimit/p95/p99。
聚合窗口正确。
重复执行具备可控行为。
```

### 5.4 Alert Evaluator 验收

实现后新增：

```text
403 比例高能生成认证失败告警。
429 数量高能生成限流告警。
P95 高能生成慢响应告警。
5xx/504 高能生成依赖异常告警。
告警不过度噪声化，具备可解释原因。
```

---

## 6. 当前边界

当前 smoke 可以证明：

```text
后端基础接口可用；
Tool debug API 可用；
Tool Chain Eval 可用；
Agent Run/SSE 骨架可用；
Mock Provider 单个 API 可用；
Mock Provider 正常和异常场景可显式触发。
```

当前 smoke 还不能证明：

```text
真实场景流量已经生成；
每次外部 API 调用已经经过 Gateway Invoke；
gateway_log 已由真实模拟调用产生；
api_call_stat_hourly 已由日志聚合产生；
alert_event 已由规则生成；
LLM 已生成正式诊断报告；
Milvus/RAG 向量检索已接入。
```
