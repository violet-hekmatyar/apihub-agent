# Agent 诊断执行链路与评测流程

## 当前实现状态

当前已实现：

- 7 个 P0 read-only Tools。
- Tool Chain Eval。
- Agent Run skeleton。
- Agent Run SSE skeleton。
- `agent_session` / `agent_message` / `agent_report` / `evidence_item` 表设计。
- `tool_call_trace` 记录。

当前未实现：

- 完整真实 LLM / DashScope Agent 接入。
- 基于 Scenario Runner 新生成日志的统计聚合。
- 基于真实统计的 Alert Evaluator。
- 基于真实统计和告警的完整 Agent 诊断闭环。
- 生产级多 Agent 编排。
- 完整前端工作台。

当前建议：不要直接扩展 LLM Agent。优先完成 Stats Aggregator v1 和 Alert Evaluator v1；等统计和告警证据稳定后，再做 `AUTH_LOGIN 403` 或 `LECTURE_REGISTER_PEAK` 的诊断证据链测试。

## 0. 文档定位

本文档定义当前 API-HUB Agent 的诊断执行链路，包括 P0 Tools、Tool Chain Eval、Agent Run、SSE 事件、报告和证据持久化。

本文档不定义外部 API 目录，不定义 Mock Provider 单次 API 行为，也不定义 Scenario Runner 流量比例。

---

## 1. 当前已实现能力

当前 `apihub-server` 已实现：

```text
7 个 P0 read-only Tools
Tool Chain Eval
Agent Run skeleton
Agent Run SSE skeleton
agent_session / agent_message / agent_report / evidence_item 持久化
tool_call_trace 记录
```

当前仍未实现：

```text
LLM / DashScope 调用
Milvus / Embedding 检索
真实生产外部 API
自动修复
多 Agent 协作
前端完整工作台
Stats Aggregator
Alert Evaluator
```

---

## 2. P0 Tools

当前 P0 Tools：

| Tool | 作用 |
|---|---|
| `queryApiInfo` | 查询 API 元信息、调用方、权限、限流摘要 |
| `queryApiCallStats` | 查询小时级调用统计、错误率、P95/P99、限流次数、风险等级 |
| `queryGatewayLogs` | 查询网关日志样本、状态码分布、错误消息、日志证据 |
| `queryRateLimitRule` | 查询限流规则、活跃状态、检查点、规则证据 |
| `queryAlertEvents` | 查询告警事件、严重程度、状态、告警证据 |
| `queryCampusEvents` | 查询校园业务事件、关联 API、业务风险上下文 |
| `queryApiDocs` | 查询 API 文档片段，目前为 MySQL keyword 检索，不是向量检索 |

Tool 的边界：

```text
Tool 负责查询事实。
Tool 不负责自由生成报告。
Tool debug 接口返回 evidenceItems，但不直接写 evidence_item。
Tool 调用会写 tool_call_trace。
```

---

## 3. Tool Chain Eval

### 3.1 定位

Tool Chain Eval 是确定性工具链评测，不是正式 Agent。

它用于验证：

```text
固定场景下，Tool 能否按预期顺序调用；
是否能得到稳定 evidence；
是否能生成模板化结论；
是否方便 smoke 和回归测试。
```

它不做：

```text
不调用 LLM；
不写 agent_report；
不写 evidence_item；
不发 SSE；
不调用 Milvus / Embedding / DashScope。
```

### 3.2 接口

```http
GET  /api/dev/eval/tool-chain/scenarios
POST /api/dev/eval/tool-chain/run
```

### 3.3 场景

#### `AUTH_LOGIN_403_DIAG`

| 项 | 内容 |
|---|---|
| 默认 API | `AUTH_LOGIN` |
| 背景 | 统一登录 403、签名失败、Token 过期、调用方配置不一致 |
| Tool 顺序 | `queryApiInfo` → `queryApiCallStats` → `queryGatewayLogs` → `queryAlertEvents` → `queryApiDocs` |
| 预期证据 | API 元信息、失败统计、403 日志、告警、签名/错误码文档 |
| 分析方向 | 检查签名算法、timestamp、nonce、token、caller secret 配置 |

#### `LECTURE_REGISTER_PEAK`

| 项 | 内容 |
|---|---|
| 默认 API | `LECTURE_REGISTER` |
| 背景 | 讲座报名开放窗口导致并发写入、延迟和 429 风险 |
| Tool 顺序 | `queryApiInfo` → `queryCampusEvents` → `queryApiCallStats` → `queryGatewayLogs` → `queryRateLimitRule` → `queryAlertEvents` → `queryApiDocs` |
| 预期证据 | 校园事件、关联 API、调用统计、429 日志、限流规则、告警、峰值文档 |
| 分析方向 | 判断业务高峰风险，联合观察登录、列表、报名 API |

#### `VENUE_RESERVE_IDEMPOTENCY`

| 项 | 内容 |
|---|---|
| 默认 API | `VENUE_RESERVE` |
| 背景 | 场地预约开放窗口触发重复提交、预约冲突、幂等风险 |
| Tool 顺序 | `queryApiInfo` → `queryApiCallStats` → `queryGatewayLogs` → `queryCampusEvents` → `queryAlertEvents` → `queryApiDocs` |
| 预期证据 | 统计、重复提交日志、校园事件、告警、幂等文档 |
| 分析方向 | 检查前端重试、幂等键、业务锁、冲突处理 |

#### `LIBRARY_BORROW_DEPENDENCY`

| 项 | 内容 |
|---|---|
| 默认 API | `LIBRARY_BORROW` |
| 背景 | 图书馆下游服务超时或不可用导致 5xx 风险 |
| Tool 顺序 | `queryApiInfo` → `queryApiCallStats` → `queryGatewayLogs` → `queryAlertEvents` → `queryApiDocs` |
| 预期证据 | 统计、超时日志、告警、依赖排障文档 |
| 分析方向 | 检查下游健康、超时设置、降级策略 |

---

## 4. Agent Run

### 4.1 定位

Agent Run 是当前确定性 Agent 骨架，复用 Tool Chain Eval 作为证据基线，并补充 session、message、report、evidence 持久化。

接口：

```http
POST /api/agent/run
```

请求示例：

```json
{
  "scenarioCode": "LECTURE_REGISTER_PEAK",
  "question": "Analyze lecture registration peak risk",
  "startTime": "2026-06-19 00:00:00",
  "endTime": "2026-06-19 23:59:59"
}
```

响应 `data` 主要字段：

```text
success
runId
sessionId
reportId
scenarioCode
apiCode
steps
evidenceItems
riskLevel
riskReasons
finalAnswer
errorCode
errorMessage
traceId
latencyMs
```

### 4.2 场景选择

优先级：

```text
scenarioCode 明确指定 > apiCode/question 关键词匹配 > 无法匹配返回 SCENARIO_NOT_MATCHED
```

关键词匹配：

| 场景 | 关键词 |
|---|---|
| `AUTH_LOGIN_403_DIAG` | `AUTH_LOGIN`, `403`, signature, token, auth, login |
| `LECTURE_REGISTER_PEAK` | `LECTURE_REGISTER`, lecture, signup/register, peak, rate-limit, `429` |
| `VENUE_RESERVE_IDEMPOTENCY` | `VENUE_RESERVE`, venue, reserve, duplicate, idempotency, conflict |
| `LIBRARY_BORROW_DEPENDENCY` | `LIBRARY_BORROW`, library, borrow, timeout, dependency |

---

## 5. SSE Skeleton

接口：

```http
POST /api/agent/run/stream
```

当前事件类型：

```text
agent_start
stage
tool_step
evidence
risk
answer
error
done
```

说明：

```text
当前 SSE 用于验证前端流式集成形态。
不是 token-by-token LLM 输出。
不是 DashScope 流式结果。
```

---

## 6. 持久化关系

Agent Run 使用当前 schema，不新增 migration：

| 表 | 用途 |
|---|---|
| `agent_session` | 保存一次诊断会话 |
| `agent_message` | 保存用户问题和助手回答 |
| `agent_report` | 保存确定性 markdown 诊断报告 |
| `evidence_item` | 保存合并后的 Tool evidence |
| `tool_call_trace` | 底层 Tool 调用时写入，Agent Run 不直接写 |

说明：

```text
Tool Chain Eval 不写 agent_report / evidence_item。
Agent Run 会写 agent_report / evidence_item。
Tool 调用 trace 由底层 Tool 写入。
```

---

## 7. 与场景流量模拟的关系

当前 Tool 和 Agent 查询的事实主要来自 seed 数据。

后续完成 Gateway Invoke、Scenario Runner、Stats Aggregator、Alert Evaluator 后，执行链路应升级为：

```text
Scenario Runner 生成真实模拟调用
→ Gateway Invoke 记录 gateway_log
→ Stats Aggregator 聚合 api_call_stat_hourly
→ Alert Evaluator 生成 alert_event
→ Tool Chain Eval 查询新生成事实
→ Agent Run 输出诊断报告
```

因此本文档关注“诊断执行链路”，不关注“流量如何生成”。流量生成详见：

```text
07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md
```
---

## Agent Diagnosis Evidence v1

Agent Diagnosis Evidence v1 adds a dev-only deterministic diagnosis flow:

```http
POST /api/dev/agent/diagnose
GET /api/dev/agent/reports/{reportId}
```

This stage does not call an LLM, Milvus, embeddings, RAG, frontend workflow, multi-agent orchestration, auto-fix, or notification system. It creates an explainable diagnosis report from existing API-HUB tools and persists the result for inspection.

Evidence sources:

- `queryApiInfo` from `api_endpoint` and related API metadata.
- `queryApiCallStats` from `api_call_stat_hourly`.
- `queryAlertEvents` from `alert_event`.
- `queryGatewayLogs` from `gateway_log`.
- `queryRateLimitRule` from rate-limit configuration.
- `queryCampusEvents` from campus event context.
- `queryApiDocs` from local API documentation records.

Persistence:

- `agent_report` stores the deterministic diagnosis summary, risk level, markdown content, tool-call count, and evidence count.
- `evidence_item` stores merged evidence items with normalized evidence types such as `API_INFO`, `API_CALL_STAT`, `ALERT_EVENT`, `GATEWAY_LOG_SAMPLE`, `RATE_LIMIT_RULE`, `CAMPUS_EVENT`, and `API_DOC`.
- `tool_call_trace` is written by `ToolService` for each reused tool call. The diagnosis service creates one diagnosis `agent_session` and passes the same `trace_id` and `session_id` through all tool calls.

Rule mapping:

- `HIGH_RATE_LIMIT` or rate-limit counts produce a `WARNING` or `CRITICAL` rate-limit diagnosis.
- `HIGH_FAILURE_RATE` or `failRate >= 10%` produces an elevated failure-rate diagnosis.
- `HIGH_LATENCY` or high P95 latency produces a latency diagnosis.
- `HIGH_5XX` or elevated 5xx count produces a downstream/server-error diagnosis.
- `AUTH_LOGIN` plus `AUTH_FAILURE_SPIKE` produces an authentication-failure diagnosis.
- If no alert or threshold breach exists, the service still writes a normal deterministic report and records empty evidence markers for tools that returned no evidence.

Next stages remain LLM-generated natural-language report refinement, RAG/Milvus evidence retrieval, and frontend report browsing.
---

## Agent Report Workbench v1

Agent Report Workbench v1 turns persisted diagnosis reports into frontend-ready and print-ready artifacts.

Dev endpoints:

```http
GET /api/dev/agent/reports
GET /api/dev/agent/reports/{reportId}
GET /api/dev/agent/reports/{reportId}/html
```

Capabilities:

- Report list supports filtering by `apiCode`, `riskLevel`, `status`, `startTime`, `endTime`, `keyword`, `pageNo`, and `pageSize`.
- Report detail aggregates `agent_report`, `evidence_item`, and `tool_call_trace`.
- Tool traces are associated by `agent_report.session_id + agent_report.trace_id`, because `tool_call_trace` has no direct `report_id`.
- HTML export returns self-contained `text/html; charset=UTF-8` with inline CSS and no external CDN, CSS, JS, chart library, or PDF library.
- HTML is optimized for browser preview and Edge / Chrome print-to-PDF with `@media print` and `@page { size: A4; margin: 14mm; }`.
- The backend does not generate PDF files directly in this stage.

HTML report sections:

- Header with report code, report id, generated time, and risk badge.
- Overview cards for API, window, scenarioRunId, status, evidence count, tool-call count, and diagnosis mode.
- Diagnosis conclusion: summary, root cause, and recommendation.
- Alert and metric summary from `ALERT_EVENT` and `API_CALL_STAT` evidence.
- Evidence table.
- Tool trace table with request and response summaries.
- Footer with Microsoft Edge / Chrome print-to-PDF instructions.

Next stages can add a full frontend workbench page or LLM-based natural-language polishing, while keeping this deterministic report artifact as the evidence baseline.

## Exception Source Credibility Update

The current deterministic diagnosis flow is validated with controlled traffic injection. Exceptions are not inserted directly into reports or alerts. Requests flow through Scenario Runner, Gateway Invoke, Mock Provider, gateway_log, Stats Aggregator, Alert Evaluator, and Agent Diagnosis before appearing in evidence or HTML/PDF reports.

For the current implementation, HTTP 409 is produced by Mock Provider business-conflict scenarios such as duplicate request or sold out. HTTP 429 is produced by Mock Provider RATE_LIMITED scenarios and then recorded by Gateway Invoke in gateway_log. Gateway Invoke currently records and proxies these statuses; it does not yet enforce an independent local rate-limit rule.

Short-window alerts use real gateway_log aggregation: HIGH_FAILURE_RATE is triggered by failRate >= 0.10, and HIGH_RATE_LIMIT is triggered by rateLimitCount >= 5 or rateLimitRate >= 0.05. See `10_EXCEPTION_SOURCE_AUDIT.md` for the full audit.

## Adaptive Passive Alert Monitor Boundary

Adaptive Passive Alert Monitor v1 creates passive monitor facts from Gateway request-completion signals:

```text
Gateway Invoke
-> gateway_log
-> GatewayMonitoringSignal
-> passive_monitor_event / passive_alert_snapshot
-> optional alert_event link
```

`monitor_event` is a future Agent diagnosis entry point. A later Agent flow can accept `monitorEventId` or the linked `alert_event_id`, derive `apiCode` and the event window, then reuse existing tools such as `queryGatewayLogs`, `queryApiCallStats`, and `queryAlertEvents`.

This monitor does not automatically call Agent, DashScope, LLM, ReAct, tools, auto-fix, or notification logic. It only creates observable facts and snapshots.
