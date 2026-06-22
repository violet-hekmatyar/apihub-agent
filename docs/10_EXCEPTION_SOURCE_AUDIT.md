# 10. 异常来源审计：可控流量注入与告警可信度说明

## 1. 文档目的

本文说明 API-HUB Agent 演示环境中的异常不是手工伪造告警或报告，而是通过可控流量、Mock Provider 业务规则、Gateway Invoke、gateway_log、Stats Aggregator、Alert Evaluator 和 Agent Diagnosis 链路产生。

本审计面向最近的 Daily + Lecture Peak pilot test：日常流量持续存在，在此基础上叠加讲座报名高峰。

## 2. 审计对象

- HTTP 409
- HTTP 429
- HIGH_FAILURE_RATE
- HIGH_RATE_LIMIT
- LECTURE_REGISTER_PEAK
- Daily + Peak Pilot Test

## 3. 异常来源结论表

| 对象 | 来源 | 是否刻意制造 | 是否直接伪造 | 证据位置 | 结论等级 |
|---|---|---:|---:|---|---|
| 409 | Mock Provider `lectureRegister` 的 `DUPLICATE_REQUEST` / `SOLD_OUT` 场景返回 `HttpStatus.CONFLICT` | 是 | 否 | `apihub-mock-provider/.../MockProviderController.java`; `ScenarioCatalog.java` | CONFIRMED |
| 429 | Mock Provider `lectureRegister` 的 `RATE_LIMITED` 场景返回 `HttpStatus.TOO_MANY_REQUESTS`，Gateway Invoke 代理并写入 `gateway_log.http_status=429` | 是 | 否 | `MockProviderController.java`; `GatewayInvokeService.java` | CONFIRMED |
| HIGH_FAILURE_RATE | Alert Evaluator 在 `DEV_SHORT_WINDOW` 中从 `gateway_log` 聚合，`failRate >= 0.10` 触发 | 是 | 否 | `AlertEvaluateService.evaluateWindow` | CONFIRMED |
| HIGH_RATE_LIMIT | Alert Evaluator 在 `DEV_SHORT_WINDOW` 中统计 `http_status=429`，`rateLimitCount >= 5 OR rateLimitRate >= 0.05` 触发 | 是 | 否 | `AlertEvaluateService.evaluateWindow`; `ShortWindowBucket.add` | CONFIRMED |
| LECTURE_REGISTER_PEAK | Scenario Runner 按 API 权重和 mockScenario 权重生成请求计划，再调用 Gateway Invoke | 是 | 否 | `ScenarioRunService.buildPlan`; `ScenarioCatalog` | CONFIRMED |

说明：

- “刻意制造”指测试通过流量、权重和场景参数有意触发典型风险。
- “直接伪造”指绕过真实链路，直接插入日志、告警或报告。本项目本轮测试没有这样做。
- 可控触发不等于伪造；关键区别是是否经过真实执行链路并留下可追溯事实。

## 4. 409 来源说明

409 由 Mock Provider 的业务冲突模拟产生。`LECTURE_REGISTER` 接口在 `mockScenario=DUPLICATE_REQUEST` 时返回 `duplicate request`，在 `mockScenario=SOLD_OUT` 时返回 `quota full, lecture sold out`，HTTP 状态均为 409。

`LECTURE_REGISTER_PEAK` 场景中，`ScenarioCatalog` 对 `LECTURE_REGISTER` 配置了 `DUPLICATE_REQUEST` 和 `SOLD_OUT` 权重。因此 409 属于可控业务冲突模拟，类型是重复报名或名额冲突，不是脚本直接写入 `gateway_log`。

结论等级：CONFIRMED。

## 5. 429 来源说明

429 由 Mock Provider 的 `RATE_LIMITED` 场景返回。Gateway Invoke 当前没有执行独立的 QPS 限流拦截逻辑；它负责鉴权、路由到 Mock Provider、接收上游 HTTP 状态，并把 `http_status`、`error_code`、`error_message`、`latency_ms` 和 `extra_info.mockScenario` 写入 `gateway_log`。

`rate_limit_rule` 当前用于元数据查询和诊断证据，不是 Gateway Invoke 的实际拦截器。报告中应把 429 描述为“Mock Provider 限流场景经 Gateway Invoke 真实代理后落库”，而不是“Gateway 本地限流规则直接拦截”。

结论等级：CONFIRMED。

## 6. HIGH_FAILURE_RATE 触发说明

`DEV_SHORT_WINDOW` 模式直接读取 `gateway_log`，按 30 秒窗口聚合。`ShortWindowBucket.add` 将 `http_status >= 400` 计入 `failCount`，`evaluateWindow` 计算 `failRate = failCount / totalCount`，当 `failRate >= 0.10` 时生成 `HIGH_FAILURE_RATE`。

这些告警来自真实请求日志窗口统计，不是直接插入告警。

结论等级：CONFIRMED。

## 7. HIGH_RATE_LIMIT 触发说明

`DEV_SHORT_WINDOW` 模式同样直接读取 `gateway_log`。`ShortWindowBucket.add` 将 `http_status == 429` 计入 `rateLimitCount`，`evaluateWindow` 在 `rateLimitCount >= 5 OR rateLimitRate >= 0.05` 时生成 `HIGH_RATE_LIMIT`。

429 的根源是 Mock Provider 的 `RATE_LIMITED` 场景；HIGH_RATE_LIMIT 的根源是 Alert Evaluator 对这些 429 日志的真实短窗口聚合。

结论等级：CONFIRMED。

## 8. 正确对外表述

建议表述：

“本测试采用可控流量注入和业务高峰模拟来触发典型 API 风险。异常不是手工写入告警或报告，而是由请求流量经过 Gateway Invoke、Mock Provider 业务规则、gateway_log、Stats Aggregator 和 Alert Evaluator 后自然产生。该测试的目的不是证明线上真实故障已经发生，而是验证系统对限流、高失败率和业务冲突等典型风险的检测、诊断和报告能力。”

## 9. 当前可信度边界

- 当前仍是模拟开发环境。
- 异常触发具有可控性。
- 尚未接入真实生产 API。
- 日志、统计、告警、诊断和报告链路是真实执行的。
- Gateway Invoke 当前不执行独立限流拦截；429 来自 Mock Provider 限流场景。

## 10. 后续建议

- 增加正常场景对照组。
- 增加低峰 NORMAL 报告。
- 增加更多非脚本指定的随机用户行为。
- 接入真实或半真实 API 日志样本。
- 后续如需证明 Gateway 自身限流能力，应实现并单独审计 Gateway 限流拦截器。
