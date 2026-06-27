# Mock Provider 与 Scenario Runner 设计

## 0. 文档定位

本文档定义 `apihub-mock-provider` 的模块边界、7 个外部业务 API 的 mock 行为、Scenario Runner 的流量生成方式，以及它与 `apihub-server` Gateway Invoke 的关系。

本文不定义完整 Agent 诊断流程，不定义 Stats Aggregator 或 Alert Evaluator 的实现，也不定义 smoke 脚本细节。

相关文档：

- `05_API_INVOKE_CONTRACT.md`：Gateway Invoke、Mock Provider、Scenario Runner 调用契约。
- `06_EXTERNAL_API_CATALOG.md`：外部 API 目录与业务风险。
- `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md`：Agent 诊断与评测流程。
- `09_VALIDATION_AND_SMOKE_GUIDE.md`：Smoke 与 Apifox 验收说明。

## 1. Mock Provider 职责

`apihub-mock-provider` 负责：

- 提供 7 个外部业务 API 的 mock。
- 默认返回 `NORMAL` 场景。
- 支持显式异常场景。
- 提供 Scenario Runner。
- Scenario Runner 必须调用 `apihub-server` 的 Gateway Invoke，不直接绕过 API-HUB 调单个 mock API。

`apihub-mock-provider` 不负责：

- 不直接写 `gateway_log`。
- 不聚合 `api_call_stat_hourly`。
- 不生成 `alert_event`。
- 不执行 Agent、Tool 或 Report。
- 不调用 LLM、DashScope、Milvus 或 Embedding。

`apihub-server` 负责 Gateway Invoke、`gateway_log` 写入、后续统计聚合、告警生成以及 Tool/Agent 查询事实。

## 2. 单个外部 API mock

所有单个 API 默认场景为 `NORMAL`。

异常场景触发优先级：

```text
X-Mock-Scenario > body/query mockScenario > NORMAL
```

| `apiCode` | Mock Path | NORMAL 行为 | 主要异常场景 |
|---|---|---|---|
| `AUTH_LOGIN` | `POST /mock-provider/auth/login` | 返回 mock token、学生信息、过期时间 | `SIGNATURE_MISMATCH`、`TOKEN_EXPIRED`、`TIMESTAMP_EXPIRED`、`NONCE_REPLAY`、`UNKNOWN_APP` |
| `COURSE_TODAY` | `GET /mock-provider/course/today` | 返回固定课表 | `SLOW_RESPONSE`、`DOWNSTREAM_SLOW`、`COURSE_SYSTEM_TIMEOUT`、`CACHE_MISS` |
| `LECTURE_LIST` | `GET /mock-provider/lecture/list` | 返回固定讲座列表 | `HOT_READ`、`SLOW_RESPONSE`、`SERVICE_BUSY` |
| `LECTURE_REGISTER` | `POST /mock-provider/lecture/register` | 返回报名成功和 mock ticket | `RATE_LIMITED`、`DUPLICATE_REQUEST`、`SOLD_OUT`、`IDEMPOTENCY_KEY_MISSING`、`SLOW_RESPONSE`、`SERVICE_BUSY` |
| `CAMPUS_NOTICE` | `GET /mock-provider/notice/list` | 返回固定通知列表 | `HOT_NOTICE`、`CACHE_MISS`、`SLOW_RESPONSE`、`SERVICE_BUSY` |
| `VENUE_RESERVE` | `POST /mock-provider/venue/reserve` | 返回预约成功和 mock reserveNo | `RESERVATION_CONFLICT`、`DUPLICATE_REQUEST`、`IDEMPOTENCY_KEY_MISSING`、`RATE_LIMITED`、`SLOW_RESPONSE` |
| `LIBRARY_BORROW` | `GET /mock-provider/library/borrow` | 返回固定借阅记录 | `DOWNSTREAM_TIMEOUT`、`DEPENDENCY_UNAVAILABLE`、`SLOW_RESPONSE`、`SERVICE_ERROR` |

单个 API 不随机异常。随机比例、阶段分布和调用总量由 Scenario Runner 控制，以保证 smoke 和回归测试稳定。

## 3. Gateway Invoke 调用关系

Scenario Runner 的正确调用链：

```text
Scenario Runner 生成调用计划
-> 调用 apihub-server POST /api/dev/gateway/invoke
-> Gateway Invoke 根据 apiCode 调 Mock Provider 单个 API
-> Gateway Invoke 写 gateway_log
-> Scenario Runner 汇总运行结果并保存 scenario_run / scenario_call_sample
```

禁止链路：

```text
Scenario Runner 直接调 /mock-provider/auth/login 等单个业务 API
```

如果绕过 Gateway Invoke，`gateway_log` 不会产生，后续 Stats Aggregator、Alert Evaluator 和 Agent Tool 就无法基于真实调用事实工作。

Scenario Runner 调用 Gateway Invoke 时应使用已授权的默认 `appCode`：

| `apiCode` | 默认 `appCode` |
|---|---|
| `AUTH_LOGIN` | `COURSE_HELPER` |
| `COURSE_TODAY` | `COURSE_HELPER` |
| `LECTURE_LIST` | `LECTURE_PORTAL` |
| `LECTURE_REGISTER` | `LECTURE_PORTAL` |
| `CAMPUS_NOTICE` | `STUDENT_SERVICE` |
| `VENUE_RESERVE` | `CLUB_ACTIVITY` |
| `LIBRARY_BORROW` | `LIBRARY_MINI` |

## 4. Scenario Runner v1

当前 Scenario Runner v1 支持：

- 异步启动。
- 查询运行状态。
- 查询 `resultSummary`。
- 查询 `sample-calls`。
- `cancel` 取消运行。
- `maxConcurrency` 控制并发 Gateway Invoke 请求。
- `RAMP_UP`、`STEADY`、`RAMP_DOWN` 三阶段流量。
- `sample-calls` 覆盖失败样本和不同 phase。

当前接口：

```http
POST /mock-provider/scenario-runs
GET  /mock-provider/scenario-runs/{scenarioRunId}
GET  /mock-provider/scenario-runs/{scenarioRunId}/result
GET  /mock-provider/scenario-runs/{scenarioRunId}/sample-calls
POST /mock-provider/scenario-runs/{scenarioRunId}/cancel
```

`POST /mock-provider/scenario-runs` 返回 `scenarioRunId`、状态查询地址、结果查询地址和样本查询地址。长时间运行不会在启动接口同步返回完整结果。

`scenario_run.result_summary` 是全量运行统计，不是样本统计。API 分布、状态码分布、mock 场景分布、phase 分布和延迟统计应与 `totalSentRequests` 对齐。

`scenario_call_sample` 只保存代表性样本，用于验收、排查和展示，不替代全量统计。

## 5. 当前场景

当前重点场景是 `LECTURE_REGISTER_PEAK`。

该场景用于模拟讲座报名高峰，混合调用：

- `AUTH_LOGIN`
- `LECTURE_LIST`
- `LECTURE_REGISTER`

典型风险：

- 登录认证失败增多。
- 讲座列表热点读取。
- 报名提交出现 429 限流。
- 重复提交或名额售罄导致失败。
- 高峰期 P95/P99 延迟升高。

当前实现还保留了普通工作日、认证异常、场地预约冲突、图书借阅依赖异常等场景定义，用于后续扩展和验证。

## 6. 已验证结果

30s Apifox 成功报告已经验证核心链路：

```text
Scenario Runner
-> Gateway Invoke
-> Mock Provider
-> gateway_log
-> scenario_run
-> scenario_call_sample
-> resultSummary / sample-calls
```

成功报告路径：

```text
docs/apifox/reports/01_scenario_runner_lecture_peak_30s_report.html
```

历史 5min 测试验证过：

- 3000+ 请求可完成。
- `resultSummary` 使用全量运行结果。
- `sample-calls` 中包含 `gatewayLogId`。
- `maxConcurrency` 能限制并发 worker，同时调度器按目标 RPS 派发请求。

## 7. 下一步

Scenario Runner 已作为造数入口稳定下来。后续重点不应继续堆叠 Apifox 场景，而应转向：

1. Stats Aggregator v1：从 `gateway_log` 聚合到 `api_call_stat_hourly`。
2. Alert Evaluator v1：基于统计和规则生成 `alert_event`。
3. Agent 诊断证据链：在统计和告警稳定后，再验证 `AUTH_LOGIN 403` 或 `LECTURE_REGISTER_PEAK` 的诊断闭环。
## Mock Scenario Runner v1

Mock Scenario Runner v1 splits the old mixed mock capability into two local modules plus the existing API-HUB Gateway:

```text
8090 apihub-mock-scenario-client
-> 8080 apihub-server /api/dev/gateway/invoke
-> 8091 apihub-mock-campus-api /api/mock-campus/invoke
-> 8080 gateway_log / stats / alert / diagnosis
-> 8090 sender and reconciliation summary
```

### Module Boundaries

- `apihub-mock-scenario-client` on 8090 simulates callers such as students, campus mini apps, and third-party apps. It actively sends requests to Gateway and records sender-side logs in `mock_scenario_client_request_log`.
- `apihub-mock-campus-api` on 8091 simulates upstream campus APIs. It receives Gateway-forwarded requests, returns business responses by `mockScenario`, and records upstream logs in `mock_campus_api_request_log`.
- `apihub-server` on 8080 remains a passive Gateway and governance system. Agent diagnosis is still after-the-fact and never participates in synchronous request responses.

### Profiles

| profileCode | FAST_DEMO | NORMAL_DEMO | Intent |
|---|---:|---:|---|
| `NORMAL_DAILY_INSPECTION` | 60s | 300s | Normal daily baseline, low noise, should remain NORMAL. |
| `LECTURE_REGISTRATION_PEAK` | 300s | 900s | Lecture signup warmup, peak, relief, and recovery. AUTH_LOGIN weight rises during peak. |
| `AUTH_FAILURE_LOCALIZED` | 180s | 480s | Localized token/signature failure around AUTH_LOGIN. |
| `DOWNSTREAM_TIMEOUT_DEGRADATION` | 180s | 600s | Timeout-oriented degradation around LIBRARY_BORROW. |

The v1 lecture peak profile intentionally keeps the overall failure rate lower than the earlier 15-minute stress-style demo. Phase C concentrates `RATE_LIMITED`, `DUPLICATE_REQUEST`, and `SOLD_OUT` on `LECTURE_REGISTER`, while `AUTH_LOGIN` weight increases to reflect signup-driven login traffic.

### Reconciliation

The three-way reconciliation compares:

- sender request/response counts from 8090;
- Gateway received/forwarded counts from `gateway_log.extra_info.scenarioRunId`;
- upstream received/error counts from 8091.

v1 still allows some gateway-like mock scenarios such as 401/403/429 to be returned by 8091 when Gateway policy interception is not yet implemented. This is documented as a demo compromise; later versions can move those policies into Gateway.
