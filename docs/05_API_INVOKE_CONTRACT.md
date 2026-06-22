# API-HUB 统一 API 调用与返回契约

## 0. 文档定位

本文档定义当前 API-HUB Agent 工程中 **Gateway Invoke、Mock Provider、Scenario Runner** 之间的调用契约。

本文只定义调用边界、ID 语义、接口职责和链路规则，不定义 Stats Aggregator、Alert Evaluator 或完整 Agent 诊断实现。

相关文档：

- `06_EXTERNAL_API_CATALOG.md`：外部业务 API 目录。
- `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md`：Mock Provider 与 Scenario Runner 设计。
- `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md`：Agent 诊断与评测流程。
- `09_VALIDATION_AND_SMOKE_GUIDE.md`：Smoke 与 Apifox 验收说明。

## 1. 三类接口边界

| 类型 | 所属模块 | 职责 | 是否直接写 `gateway_log` |
|---|---|---|---|
| Mock Provider 单个业务 API | `apihub-mock-provider` | 模拟一个外部业务 API 的一次真实返回 | 否 |
| Gateway Invoke | `apihub-server` | 代表 API-HUB 平台统一调用外部 API | 是 |
| Scenario Runner | `apihub-mock-provider` | 按业务场景生成一批调用计划并执行 | 否，必须通过 Gateway Invoke 间接写入 |

正确链路：

```text
Scenario Runner
-> API-HUB Gateway Invoke
-> Mock Provider 单个业务 API
-> API-HUB Gateway Invoke 写 gateway_log
-> 后续 Stats Aggregator / Alert Evaluator / Agent Tool 查询
```

错误链路：

```text
Scenario Runner 直接绕过 Gateway Invoke 调 Mock Provider 单个业务 API
```

错误链路会绕过 API-HUB 平台侧的调用记录，导致后续统计聚合、告警评估和 Agent Tool 查询缺少事实依据。

## 2. ID 与命名边界

| 字段 | 含义 | 生成/传递位置 | 说明 |
|---|---|---|---|
| `apiCode` | 外部业务 API 标识 | 调用方传入 | 例如 `AUTH_LOGIN`、`LECTURE_REGISTER`，用于选择具体业务 API |
| `appCode` | 调用方应用标识 | 调用方传入或 Scenario Runner 按 API 默认选择 | 用于授权和调用方归因 |
| `traceId` | 链路追踪 ID | 请求头传入；缺失时由服务生成 | 贯穿一次调用链路 |
| `requestId` | 单次请求 ID | 调用方传入；缺失时由 Gateway Invoke 生成 | 标识一次 Gateway Invoke 请求 |
| `scenarioRunId` | 场景运行批次 ID | Scenario Runner 创建 | 用于把一批 Gateway Invoke 请求归属到同一次场景运行 |

`apiCode` 与数据库内部主键不同。对外契约优先使用 `apiCode`、`appCode`、`traceId`、`requestId`、`scenarioRunId` 这类稳定标识。

## 3. Mock Provider 单个业务 API

Mock Provider 当前提供 7 个外部业务 API 的 mock：

| `apiCode` | Mock Provider 路径 | 默认授权 `appCode` |
|---|---|---|
| `AUTH_LOGIN` | `POST /mock-provider/auth/login` | `COURSE_HELPER` |
| `COURSE_TODAY` | `GET /mock-provider/course/today` | `COURSE_HELPER` |
| `LECTURE_LIST` | `GET /mock-provider/lecture/list` | `LECTURE_PORTAL` |
| `LECTURE_REGISTER` | `POST /mock-provider/lecture/register` | `LECTURE_PORTAL` |
| `CAMPUS_NOTICE` | `GET /mock-provider/notice/list` | `STUDENT_SERVICE` |
| `VENUE_RESERVE` | `POST /mock-provider/venue/reserve` | `CLUB_ACTIVITY` |
| `LIBRARY_BORROW` | `GET /mock-provider/library/borrow` | `LIBRARY_MINI` |

Mock Provider 单个 API 的职责：

- 默认返回 `NORMAL` 场景结果。
- 支持通过 `X-Mock-Scenario`、query 或 body 显式触发异常场景。
- 返回真实 HTTP 状态码，例如 403、429、504。
- 返回统一 mock 响应体。

Mock Provider 单个 API 不负责：

- 不直接写 `gateway_log`。
- 不聚合 `api_call_stat_hourly`。
- 不生成 `alert_event`。
- 不执行 Agent、Tool 或 Report。
- 不调用 LLM、DashScope、Milvus 或 Embedding。

## 4. Gateway Invoke v1

当前 Gateway Invoke 接口：

```http
POST /api/dev/gateway/invoke
```

职责：

1. 接收统一的外部 API 调用请求。
2. 根据 `apiCode` 找到 Mock Provider 的具体路径。
3. 校验 `appCode` 与 `apiCode` 的授权关系。
4. 转发请求到 Mock Provider 单个业务 API。
5. 记录一条 `gateway_log`。
6. 返回上游状态、业务 code/message、延迟、`gatewayLogId`、`traceId` 和 `requestId` 等结果。

示例请求：

```json
{
  "apiCode": "LECTURE_REGISTER",
  "appCode": "LECTURE_PORTAL",
  "mockScenario": "RATE_LIMITED",
  "scenarioRunId": "sr_20260621_lecture_peak_001",
  "body": {
    "lectureId": "lec_20260619_ai_001",
    "studentNo": "2023001001",
    "idempotencyKey": "idem_lecture_001"
  }
}
```

Gateway Invoke v1 的边界：

- 只负责单次代理调用和 `gateway_log` 记录。
- 不自动聚合 `api_call_stat_hourly`。
- 不自动生成 `alert_event`。
- 不直接输出 Agent 诊断报告。

## 5. Scenario Runner v1

当前 Scenario Runner v1 接口：

```http
POST /mock-provider/scenario-runs
GET  /mock-provider/scenario-runs/{scenarioRunId}
GET  /mock-provider/scenario-runs/{scenarioRunId}/result
GET  /mock-provider/scenario-runs/{scenarioRunId}/sample-calls
POST /mock-provider/scenario-runs/{scenarioRunId}/cancel
```

Scenario Runner v1 的职责：

- 异步启动一次场景运行。
- 按场景生成调用计划。
- 使用 `maxConcurrency` 控制并发 Gateway Invoke worker。
- 支持 `RAMP_UP`、`STEADY`、`RAMP_DOWN` 三个 phase。
- 每一次具体外部 API 调用都必须经过 `POST /api/dev/gateway/invoke`。
- 将运行状态写入 `scenario_run`。
- 将全量汇总写入 `scenario_run.result_summary`。
- 将代表性样本写入 `scenario_call_sample`。

`POST /mock-provider/scenario-runs` 只返回启动结果，不等待长时间运行完成。调用方应轮询状态、结果和样本接口。

示例请求：

```json
{
  "scenarioCode": "LECTURE_REGISTER_PEAK",
  "loadProfile": {
    "rampUpSeconds": 5,
    "steadySeconds": 20,
    "rampDownSeconds": 5,
    "baseRps": 2,
    "peakRps": 8,
    "maxConcurrency": 30,
    "randomSeed": 20260621
  }
}
```

示例启动结果：

```json
{
  "scenarioRunId": "sr_20260621_lecture_peak_001",
  "status": "RUNNING",
  "statusUrl": "/mock-provider/scenario-runs/sr_20260621_lecture_peak_001",
  "resultUrl": "/mock-provider/scenario-runs/sr_20260621_lecture_peak_001/result",
  "sampleCallsUrl": "/mock-provider/scenario-runs/sr_20260621_lecture_peak_001/sample-calls"
}
```

## 6. 当前已验证链路

当前 30s Apifox 成功报告证明的核心链路：

```text
Scenario Runner
-> Gateway Invoke
-> Mock Provider
-> gateway_log
-> scenario_run
-> scenario_call_sample
-> resultSummary / sample-calls
```

成功报告：

```text
docs/apifox/reports/01_scenario_runner_lecture_peak_30s_report.html
```

历史 5min 测试还验证过 3000+ 请求、全量 `resultSummary`、`sample-calls` 中包含 `gatewayLogId` 等关键结果。该结果用于说明 Scenario Runner 作为后续造数入口已经基本稳定。

## 7. 后续契约延伸

后续 Stats Aggregator v1 应从 `gateway_log` 聚合到 `api_call_stat_hourly`。

后续 Alert Evaluator v1 应基于聚合统计和规则生成 `alert_event`。

后续 Agent Tool 与 Agent Run 应查询统计、日志、规则、告警和文档证据，而不是让 Scenario Runner 或 Mock Provider 直接生成诊断结论。
