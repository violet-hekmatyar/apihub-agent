# DashScope Agent 业务诊断链路测试证据包

## 1. 定位

本证据包用于归档 API-HUB Agent 的一次开发态业务模拟诊断测试。测试重点是验证从业务流量、Gateway 日志、Stats 聚合、Alert 评估、Agent 确定性诊断到 DashScope LLM 结构化诊断输出的完整链路。

本测试不是生产压测，也不是线上真实流量分布复制。当前异常比例偏演示型，主要用于验证诊断链路是否完整、LLM 输出是否可控、Validator 是否能约束风险等级漂移和证据幻觉。

## 2. 基本信息

- runId: `manual_dashscope_15min_20260623-151937`
- 测试模式: 正式 15 分钟模式
- 流量持续时间: 900 秒
- BaseUrl: `http://localhost:8080`
- MockProviderBaseUrl: `http://localhost:8090`
- LLM Provider: DashScope
- LLM Model: `qwen-plus`
- 原始输出目录: `D:\tmp\apihub-agent-dashscope-15min-business-test\output\run-20260623-151937`

## 3. 关键结果

- totalRequests: 1926
- successCount: 1550
- failCount: 376
- statusCodeDistribution: 200=1550, 429=148, 409=118, 504=80, 401=19, 403=11
- deterministicReports: 7/7
- dashScopeReports: 7/7
- dashScopeValidationSuccess: 7/7
- dashScopeFallbackCount: 0
- alertCount: 98
- mainRisk: LECTURE_REGISTER CRITICAL
- authRisk: AUTH_LOGIN WARNING
- baselineRisk: NORMAL
- recoveryRisk: NORMAL

## 4. 测试链路

```text
业务流量模拟
-> POST /api/dev/gateway/invoke
-> Mock Provider
-> gateway_log
-> Stats Aggregator
-> Alert Evaluator
-> Agent deterministic diagnosis
-> DashScope LLM diagnosis
-> Parser / Validator
-> 证据归档
```

## 5. 文件说明

| 目录 | 文件 | 用途 |
|---|---|---|
| `scripts/` | `run-dashscope-15min-business-test.ps1` | 复现实验的 PowerShell 测试脚本 |
| `scripts/` | `15min-business-test-design.md` | 15 分钟业务测试设计说明 |
| `plan/` | `00-test-plan.json` | 测试计划、阶段设计、运行配置 |
| `plan/` | `01-business-timeline.md` | Phase A-F 业务时间线 |
| `traffic/` | `02-traffic-samples.jsonl` | 每次 Gateway 调用样本 |
| `traffic/` | `03-traffic-summary.json` | 流量统计摘要 |
| `pipeline/` | `04-stats-aggregator-responses.json` | Stats 聚合接口响应 |
| `pipeline/` | `05-alert-evaluator-responses.json` | Alert 评估接口响应 |
| `pipeline/` | `06-agent-diagnosis-report-index.json` | Agent 确定性诊断报告索引 |
| `llm/` | `08-dashscope-report-*.json` | 7 个 DashScope 结构化诊断结果 |
| `summary/` | `10-validation-summary.json` | 本次链路验证摘要 |
| `summary/` | `11-raw-api-errors.jsonl` | 原始 API 错误记录；本次为空文件 |
| `summary/` | `17-final-review.md` | 本次测试复盘摘要 |

## 6. Missing Optional Files

- `run-log/console-run-log.txt`: 未发现该 run 的独立原始控制台日志文件；未伪造控制台输出。复盘请以 `17-final-review.md`、`10-validation-summary.json` 和各 JSON 产物为准。

## 7. 已知局限

- 当前流量模型偏演示型，不代表真实生产流量分布。
- 总体失败率约 19.5%，高于真实日常流量。
- 429、409、504 的异常占比主要用于验证诊断链路。
- network-course / network-library 有 timeout 样本，但在当前阈值下未全部升级为 WARNING。
- 后续可以加入 normal / peak / incident 三类 profile，区分日常流量、业务高峰和真实事故演练。

## 8. 安全说明

- 本证据包不包含 `.env`。
- 本证据包不应包含 `DASHSCOPE_API_KEY`。
- 本证据包不包含 Authorization header。
- 本证据包不包含数据库 dump、`target/`、jar、class 或完整服务日志。
