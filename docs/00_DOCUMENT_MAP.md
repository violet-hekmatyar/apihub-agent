# API-HUB Agent 文档地图

本文档用于说明当前主线文档的定位、状态和推荐阅读时机。

| 文档 | 定位 | 当前状态 | 建议阅读时机 |
|---|---|---|---|
| `00_PROJECT_OVERVIEW.md` | 项目总览与交接入口 | 当前交接入口 | 新同学第一篇阅读 |
| `00_DOCUMENT_MAP.md` | 文档地图和阅读顺序 | 当前文档 | 阅读入口文档后阅读 |
| `01_DB_SCHEMA.md` | 数据库表结构、核心字段和持久化边界 | 当前主线文档 | 进入 Stats Aggregator v1 前阅读 |
| `02_TOOL_CONTRACT.md` | Tool 入参、出参、权限和错误契约 | 当前主线文档 | 修改 Tool 或 Agent 证据链前阅读 |
| `03_API_CONTRACT.md` | 后端 HTTP API 契约 | 当前主线文档 | 调整后端接口前阅读 |
| `04_VIBE_CODING_RULES.md` | Codex / Vibe Coding 开发规则 | 当前主线文档 | 每轮开发前参考 |
| `05_API_INVOKE_CONTRACT.md` | Gateway Invoke / Mock Provider / Scenario Runner 调用契约 | 当前主线文档 | 理解调用链路时优先阅读 |
| `06_EXTERNAL_API_CATALOG.md` | 外部业务 API 目录和业务风险场景 | 当前主线文档 | 理解 7 个业务 API 时阅读 |
| `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md` | Mock Provider 与 Scenario Runner 设计 | 当前主线文档 | 理解流量生成和 mock 行为时阅读 |
| `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md` | Agent 诊断规划与 skeleton 状态 | 当前主线文档；非完整 LLM Agent 实现 | 理解 Agent 后续方向时阅读 |
| `09_VALIDATION_AND_SMOKE_GUIDE.md` | smoke / Apifox 验收指南 | 当前主线文档 | 运行验收或交接前阅读 |
| `docs/apifox/README.md` | Apifox 测试报告资产说明 | 当前验收资产说明 | 查看自动化测试报告前阅读 |
| `docs/archive/v1/` | 历史文档归档说明 | 历史追溯资料 | 不建议新同学优先阅读 |

## 当前主线说明

- `05_API_INVOKE_CONTRACT.md` 是 Gateway Invoke / Mock Provider / Scenario Runner 调用契约。
- `06_EXTERNAL_API_CATALOG.md` 是外部业务 API 目录。
- `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md` 是 Mock Provider 与 Scenario Runner 设计。
- `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md` 是 Agent 诊断规划与 skeleton 说明，不代表完整真实 LLM Agent 已完成。
- `09_VALIDATION_AND_SMOKE_GUIDE.md` 是 smoke / Apifox 验收说明。
- `docs/apifox/` 是测试报告资产目录。
- `docs/archive/v1/` 是历史文档归档说明，不作为当前开发依据。

## 推荐阅读路径

```text
00_PROJECT_OVERVIEW
-> 00_DOCUMENT_MAP
-> 06_EXTERNAL_API_CATALOG
-> 05_API_INVOKE_CONTRACT
-> 07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION
-> 09_VALIDATION_AND_SMOKE_GUIDE
-> docs/apifox/README
-> 01_DB_SCHEMA
-> 02_TOOL_CONTRACT
-> 08_AGENT_DIAGNOSIS_AND_EVAL_FLOW
```

## 下一步开发定位

下一步建议只做 Stats Aggregator v1：

```text
gateway_log -> api_call_stat_hourly
```

在 Stats Aggregator v1 完成前，不建议优先扩展 LLM、前端、多 Agent、Milvus/RAG 或 Alert Evaluator。

## Exception Source Audit Update

- `10_EXCEPTION_SOURCE_AUDIT.md`: documents the confirmed sources of HTTP 409, HTTP 429, HIGH_FAILURE_RATE, and HIGH_RATE_LIMIT, including the boundary between controlled traffic injection and direct data fabrication.

## LLM Agent Readiness Update

- `11_LLM_AGENT_READINESS.md`: records the pre-LLM deterministic baseline, normal/abnormal comparison, Tool contract closure, Evidence contract closure, and constraints for future PromptBuilder / LLM diagnosis work.
- Next recommended development stage: LLM Prompt Contract v1, then DashScope LLM Diagnosis v1 after deterministic contracts remain green.
