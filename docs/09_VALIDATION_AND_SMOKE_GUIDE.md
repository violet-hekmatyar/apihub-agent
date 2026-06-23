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

## 最新 Apifox 自动化测试报告

成功报告：

```text
docs/apifox/reports/01_scenario_runner_lecture_peak_30s_report.html
```

报告证明的链路：

```text
Scenario Runner
-> Gateway Invoke
-> Mock Provider
-> gateway_log
-> scenario_run
-> scenario_call_sample
-> resultSummary / sample-calls
```

失败报告目录：

```text
docs/apifox/reports/failed/
```

`failed` 目录用于保存测试配置迭代记录，不代表最终系统失败。当前结论是：模拟场景 API 调用链路已经通过 Apifox 自动化测试验证；下一步验收重点应转向 Stats Aggregator v1。

---

## 当前交接前验证状态

- backend smoke：已通过。
- mock-provider smoke：已通过。
- Apifox 30s Scenario Runner：已通过。
- Gateway Invoke smoke 曾因非法 `X-Trace-Id` 失败，已修复 smoke 脚本为合法 32 位小写 hex。
- 当前下一步验收重点是 Stats Aggregator v1。

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

### Stats Aggregator v1 后续验收

建议后续验收步骤：

1. 跑一次 Scenario Runner。
2. 调用后续新增的统计聚合接口。
3. 验证 `api_call_stat_hourly` 出现新统计。
4. 验证 `queryApiCallStats` 能查到新聚合数据。
5. 重复执行聚合不能导致统计翻倍。

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

---

## 7. Scenario Runner Persistence Validation Notes

Introducing `scenario_run` and `scenario_call_sample` increases the table count for newly initialized databases. Existing local databases must run `scripts/sql/dev_sync_scenario_run_schema.sql` before they have these two tables.

Gateway Invoke smoke does not depend on the new tables. Later Scenario Runner smoke will depend on them.

Backend database health smoke should accept table counts greater than or equal to the previous baseline, rather than requiring an exact table count. This keeps smoke stable whether the local database has already run the Scenario Runner schema sync script or not.

Scenario Runner v1 smoke is part of `scripts/check-mock-provider-smoke.ps1`. It requires:

```text
apihub-server running on 8080
apihub-mock-provider running on 8090
MySQL synchronized with scripts/sql/dev_sync_scenario_run_schema.sql
```

The smoke starts a small `LECTURE_REGISTER_PEAK` run, polls `GET /mock-provider/scenario-runs/{scenarioRunId}` until `COMPLETED`, then checks `/result` and `/sample-calls`. It does not validate `api_call_stat_hourly` or `alert_event`; those remain later Stats Aggregator and Alert Evaluator responsibilities.

The Scenario Runner smoke also checks:

```text
missing X-Trace-Id generates a 32-character lowercase hex trace id
invalid X-Trace-Id returns 400
literal {scenarioRunId} does not return 500
well-formed missing scenarioRunId returns 404
resultSummary distributions are full-run statistics
sample-calls returns representative samples
cancel marks a running run as CANCELLED
```

For a five-minute peak run, `maxConcurrency` must cap concurrent Gateway Invoke workers while allowing the scheduler to keep dispatching by target RPS. `resultSummary.totalSentRequests`, the sums of API/status/phase distributions, and `latencySummary.count` should all equal `totalSentRequests`.

---

## Stats Aggregator v1 验收

新增 smoke 脚本：

```text
scripts/check-stats-aggregator-smoke.ps1
```

默认运行方式：

```powershell
cd D:\apihub-agent-dev
powershell -ExecutionPolicy Bypass -File .\scripts\check-stats-aggregator-smoke.ps1
```

指定服务地址：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-stats-aggregator-smoke.ps1 -BaseUrl http://localhost:8080 -MockProviderBaseUrl http://localhost:8090
```

该脚本验证链路：

```text
Scenario Runner
-> Gateway Invoke
-> gateway_log
-> Stats Aggregator
-> api_call_stat_hourly
-> queryApiCallStats
```

当前边界：

- 本阶段不生成 `alert_event`。
- 本阶段不接入 LLM Agent。
- 本阶段不验证前端工作台。
- 本阶段只证明 `gateway_log` 可以聚合为 `api_call_stat_hourly`，且 `queryApiCallStats` 能查询新聚合统计。

Stats Aggregator v1 开发态接口：

```http
POST /api/dev/stats/aggregate
```

示例请求：

```json
{
  "startTime": "2026-06-22 10:00:00",
  "endTime": "2026-06-22 11:00:00",
  "scenarioRunId": "sr_20260622_demo",
  "apiCode": "LECTURE_REGISTER",
  "forceRebuild": true
}
```

说明：

- 聚合维度为 `api_id + stat_time` 小时桶。
- 聚合事实源为 `gateway_log.request_time`，窗口条件为 `request_time >= startTime AND request_time < endTime`。
- `scenarioRunId` 只用于 smoke 定位和响应统计，不作为 `api_call_stat_hourly` 写入维度，避免把全局小时统计覆盖成局部场景统计。
- 重复执行同一窗口时，按聚合出来的 `api_id + stat_time` 删除旧统计后重建，避免统计翻倍。
---

## Alert Evaluator v1 验收

新增 smoke 脚本：

```text
scripts/check-alert-evaluator-smoke.ps1
```

默认运行方式：

```powershell
cd D:\apihub-agent-dev
powershell -ExecutionPolicy Bypass -File .\scripts\check-alert-evaluator-smoke.ps1
```

指定服务地址：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-alert-evaluator-smoke.ps1 -BaseUrl http://localhost:8080 -MockProviderBaseUrl http://localhost:8090
```

该脚本验证链路：

```text
Scenario Runner
-> Gateway Invoke
-> gateway_log
-> Alert Evaluator
-> alert_event
-> queryAlertEvents
```

Alert Evaluator v1 开发态接口：

```http
POST /api/dev/alerts/evaluate
```

示例请求：

```json
{
  "startTime": "2026-06-22 10:00:00",
  "endTime": "2026-06-22 10:05:00",
  "mode": "DEV_SHORT_WINDOW",
  "windowSeconds": 30,
  "scenarioRunId": "sr_20260622_demo",
  "apiCode": "LECTURE_REGISTER",
  "forceRebuild": true
}
```

说明：

- `HOURLY` 模式基于 `api_call_stat_hourly` 评估，窗口为小时级。
- `DEV_SHORT_WINDOW` 模式支持 `windowSeconds=30`，直接读取 `gateway_log`，不会写入或污染 `api_call_stat_hourly`。
- `scenarioRunId` 只用于短窗口 smoke/定位过滤，正式小时统计仍按 API 和时间窗口评估。
- 规则命中后写入 `alert_event`，`extra_info` 包含 `mode`、`windowSeconds`、`statSource`、`scenarioRunId`、阈值、实际值和证据摘要。
- `forceRebuild=true` 时，会删除同 API、同窗口、同告警类型、同模式和同窗口秒数的 Alert Evaluator v1 旧告警后重建，避免重复执行产生脏重复。
- `queryAlertEvents` 支持通过 `alertType` 或 `eventType` 查询新生成的告警，并在返回的 `alerts[].extraInfo` 中透出 evaluator 证据。

当前边界：

- 不接入 LLM Agent。
- 不做 RAG、前端工作台或自动修复。
- 不新增数据库表结构。
---

## Agent Diagnosis Evidence v1 验收

新增 smoke 脚本：

```text
scripts/check-agent-diagnosis-evidence-smoke.ps1
```

默认运行方式：

```powershell
cd D:\apihub-agent-dev
powershell -ExecutionPolicy Bypass -File .\scripts\check-agent-diagnosis-evidence-smoke.ps1
```

指定服务地址：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-agent-diagnosis-evidence-smoke.ps1 -BaseUrl http://localhost:8080 -MockProviderBaseUrl http://localhost:8090
```

该脚本验证链路：

```text
Scenario Runner
-> Gateway Invoke
-> gateway_log
-> Stats Aggregator
-> api_call_stat_hourly
-> Alert Evaluator
-> alert_event
-> queryApiCallStats / queryAlertEvents / queryGatewayLogs
-> Agent Diagnosis
-> agent_report / evidence_item / tool_call_trace
```

新增开发态接口：

```http
POST /api/dev/agent/diagnose
GET /api/dev/agent/reports/{reportId}
```

当前边界：

- 本阶段只做确定性规则诊断，不接入真实 LLM / DashScope。
- 本阶段不接入 Milvus、Embedding 或 RAG 向量检索。
- 本阶段不验证前端工作台。
- 本阶段不做多 Agent 编排、自动修复、自动通知或自动调参。
- smoke 必须通过查询接口确认 report、evidence 和 tool trace 可见，不直接插入诊断报告数据。
---

## Agent Report Workbench v1 验收

新增 smoke 脚本：

```text
scripts/check-agent-report-workbench-smoke.ps1
```

默认运行方式：

```powershell
cd D:\apihub-agent-dev
powershell -ExecutionPolicy Bypass -File .\scripts\check-agent-report-workbench-smoke.ps1
```

指定服务地址：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-agent-report-workbench-smoke.ps1 -BaseUrl http://localhost:8080 -MockProviderBaseUrl http://localhost:8090
```

该脚本验证接口：

```http
GET /api/dev/agent/reports
GET /api/dev/agent/reports/{reportId}
GET /api/dev/agent/reports/{reportId}/html
```

验证链路：

```text
Agent Diagnosis
-> agent_report
-> evidence_item
-> tool_call_trace
-> report list
-> report detail
-> HTML report
-> Edge / Chrome print to PDF ready
```

PDF 导出说明：

1. 打开 `GET /api/dev/agent/reports/{reportId}/html`。
2. 使用 Microsoft Edge 或 Chrome。
3. 按 `Ctrl + P`。
4. 打印机选择“另存为 PDF”。
5. 纸张选择 A4。
6. 保存。

当前边界：

- 本阶段不直接生成 PDF 文件。
- 本阶段不开发完整 Vue / React 前端。
- 本阶段不接入 LLM / DashScope。
- 本阶段不接入 Milvus / Embedding / RAG。

## Pilot Report Quality Checklist

For Daily + Peak pilot validation, check the generated Agent report for:

- API_CALL_STAT evidence should be present when hourly stats exist. Short diagnosis windows may query an expanded hour-aligned bucket window.
- The HTML metric / alert summary should aggregate repeated 30-second HIGH_FAILURE_RATE and HIGH_RATE_LIMIT windows instead of listing duplicate rows at the top.
- The HTML report should include a "测试场景与异常来源说明" section clarifying controlled traffic injection, Mock Provider / Gateway Invoke / gateway_log / Stats Aggregator / Alert Evaluator provenance, and the simulated development-environment boundary.
- The evidence chain should still retain individual 30-second alert windows; aggregation is presentation-only.

A normal low-traffic control run is recommended as future work to demonstrate separation between normal and abnormal scenarios.

## Normal Baseline Control Smoke

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-normal-baseline-control-smoke.ps1
```

Purpose:

- Prove the system does not over-alert on low-traffic daily NORMAL requests.
- Exercise the same factual chain as abnormal pilots: Gateway Invoke -> gateway_log -> Stats Aggregator -> Alert Evaluator -> Agent Diagnosis -> Report Workbench HTML/PDF.
- Expected result: `riskLevel=NORMAL`, alert count `0`, reportId present, HTML report present, PDF present when Edge / Chrome headless export is available.

Outputs are written under:

```text
D:\tmp\apihub-agent-normal-baseline-test\output\run-<timestamp>
```

The summary markdown records riskLevel, alert count, reportId, HTML path, PDF path, and raw JSON path.

## LLM Prompt Builder Mock Smoke

Run after `apihub-server` has the latest code deployed on port 8080:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-llm-prompt-builder-mock-smoke.ps1 -IncludePrompt
```

To target a specific deterministic diagnosis report:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-llm-prompt-builder-mock-smoke.ps1 -ReportId 16051 -IncludePrompt
```

The smoke verifies:

```text
GET  /api/health
GET  /api/dev/agent/reports?pageNo=1&pageSize=1
POST /api/dev/agent/diagnose/llm/mock
```

Expected result:

- `data.success=true`
- `data.fallbackUsed=false`
- `data.validation.success=true`
- `data.output.riskLevel` exists
- `data.rawResponse` exists
- `data.prompt.inputJson` exists when `-IncludePrompt` is used

Boundary:

- The smoke reuses an existing deterministic diagnosis report.
- It does not run traffic generation or long pilot scenarios.
- It does not call DashScope, OpenAI, or any external LLM API.

## DashScope LLM Diagnosis Smoke

Run after `apihub-server` has the latest code deployed on port 8080:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-dashscope-llm-diagnosis-smoke.ps1 -IncludePrompt
```

To target a specific deterministic diagnosis report:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-dashscope-llm-diagnosis-smoke.ps1 -ReportId 16059 -IncludePrompt
```

The script reads `D:\apihub-agent-dev\docker\.env` into the current PowerShell process but does not print `DASHSCOPE_API_KEY`.

Verified endpoints:

```text
GET  /api/health
GET  /api/dev/agent/reports?pageNo=1&pageSize=1
POST /api/dev/agent/diagnose/llm/dashscope
```

Expected normal result:

- `data.provider=DASHSCOPE`
- `data.fallbackUsed=false`
- `data.validation.success=true`
- `data.output.riskLevel` exists
- response must not contain the API key

If DashScope returns invalid JSON or violates validation rules, the endpoint returns a fallback-aware result with `fallbackUsed=true`; the smoke marks that as FAIL so the prompt/client can be tuned.
