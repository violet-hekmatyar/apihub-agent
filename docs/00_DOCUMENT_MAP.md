# API-HUB Agent 文档地图

本文档用于说明当前主线文档的定位、状态和推荐阅读时机。它是项目文档入口，不承载具体实现细节。

## 1. 主线文档清单

| 文档 | 定位 | 当前状态 | 建议阅读时机 |
|---|---|---|---|
| `00_PROJECT_OVERVIEW.md` | 项目总览与交接入口 | 当前交接入口 | 新同学第一篇阅读 |
| `00_DOCUMENT_MAP.md` | 文档地图和阅读顺序 | 当前文档 | 阅读入口文档后阅读 |
| `01_DB_SCHEMA.md` | 数据库表结构、核心字段和持久化边界 | 当前主线文档 | 修改表结构、统计聚合、报告落库前阅读 |
| `02_TOOL_CONTRACT.md` | Tool 入参、出参、权限、Trace、Evidence 契约 | 当前主线文档 | 修改 Tool 或 Agent 证据链前阅读 |
| `03_API_CONTRACT.md` | 后端 HTTP API 契约 | 当前主线文档 | 调整后端接口前阅读 |
| `04_VIBE_CODING_RULES.md` | Codex / Vibe Coding 开发规则 | 当前主线文档 | 每轮开发前参考 |
| `05_API_INVOKE_CONTRACT.md` | Gateway Invoke / Mock Provider / Scenario Runner 调用契约 | 当前主线文档 | 理解调用链路时优先阅读 |
| `06_EXTERNAL_API_CATALOG.md` | 外部业务 API 目录和业务风险场景 | 当前主线文档 | 理解 7 个业务 API 时阅读 |
| `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md` | Mock Provider 与 Scenario Runner 设计 | 当前主线文档 | 理解流量生成和 Mock 行为时阅读 |
| `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md` | Agent 诊断执行链路、确定性诊断、报告证据链 | 当前主线文档；当前仍非完整 LLM Agent | 理解 Agent 后续方向时阅读 |
| `09_VALIDATION_AND_SMOKE_GUIDE.md` | smoke / Apifox / pilot / normal baseline 验收指南 | 当前主线文档 | 运行验收或交接前阅读 |
| `10_EXCEPTION_SOURCE_AUDIT.md` | 异常来源审计，可控触发与直接伪造边界 | 当前主线文档 | 展示异常可信度、解释 409/429/告警来源前阅读 |
| `11_LLM_AGENT_READINESS.md` | 接入 LLM 前的确定性基线、正常/异常对照、Tool/Evidence 收口 | 当前主线文档 | 进入 LLM Prompt 设计前阅读 |
| `12_LLM_PROMPT_CONTRACT.md` | LLM Prompt 输入输出、JSON Schema、证据约束和评测规则 | 当前主线文档；阶段 1 产物 | 开发 PromptBuilder / Parser / DashScope 前阅读 |
| `docs/apifox/README.md` | Apifox 测试报告资产说明 | 当前验收资产说明 | 查看自动化测试报告前阅读 |
| `docs/archive/v1/` | 历史文档归档说明 | 历史追溯资料 | 不建议新同学优先阅读 |

---

## 2. 当前主线能力状态

当前主线已完成 API-HUB Agent 的后端确定性诊断闭环：

```text
Mock Provider / Scenario Runner
-> Gateway Invoke
-> gateway_log
-> Stats Aggregator
-> api_call_stat_hourly
-> Alert Evaluator
-> alert_event
-> Agent Diagnosis Evidence
-> agent_report / evidence_item / tool_call_trace
-> Report Workbench
-> HTML / browser PDF export
```

当前已经具备：

- 可控业务流量模拟。
- 网关代理调用和日志落库。
- 小时级统计聚合。
- 短窗口 / 小时级告警评估。
- 确定性 Agent 诊断报告。
- 证据链和工具调用轨迹持久化。
- 报告列表、详情、HTML 导出。
- 正常低峰对照组验证。
- 异常来源审计和可信度说明。
- 接入 LLM 前的 Tool / Evidence Contract 收口。
- LLM Prompt Contract v1 文档定义。

当前尚未完成：

- DashScope / 真实 LLM 调用。
- Function Calling / ReAct 式自主工具调用。
- PromptBuilder / Parser / Validator Java 实现。
- LLM Eval Cases 自动化评测。
- Milvus / Embedding / RAG 文档检索升级。
- 完整前端工作台。
- 多 Agent、自动修复、自动通知。

---

## 3. 推荐阅读路径

### 3.1 新同学快速理解项目

```text
00_PROJECT_OVERVIEW
-> 00_DOCUMENT_MAP
-> 06_EXTERNAL_API_CATALOG
-> 05_API_INVOKE_CONTRACT
-> 07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION
-> 09_VALIDATION_AND_SMOKE_GUIDE
```

目标：先理解项目做什么、有哪些校园 API、流量如何生成、如何验收。

### 3.2 理解后端确定性诊断闭环

```text
01_DB_SCHEMA
-> 02_TOOL_CONTRACT
-> 08_AGENT_DIAGNOSIS_AND_EVAL_FLOW
-> 09_VALIDATION_AND_SMOKE_GUIDE
-> 10_EXCEPTION_SOURCE_AUDIT
```

目标：理解日志、统计、告警、诊断、证据链、报告如何串起来。

### 3.3 接入 LLM Agent 前阅读

```text
11_LLM_AGENT_READINESS
-> 12_LLM_PROMPT_CONTRACT
-> 02_TOOL_CONTRACT
-> 08_AGENT_DIAGNOSIS_AND_EVAL_FLOW
-> 10_EXCEPTION_SOURCE_AUDIT
```

目标：确认 LLM 的输入事实、输出结构、证据边界、禁止编造规则和评测基线。

### 3.4 做验收 / 回归测试前阅读

```text
09_VALIDATION_AND_SMOKE_GUIDE
-> 10_EXCEPTION_SOURCE_AUDIT
-> 11_LLM_AGENT_READINESS
```

目标：确认正常/异常对照、pilot 报告、smoke、HTML/PDF 产物和可信度说明。

---

## 4. 当前开发定位

当前项目已经从“确定性诊断闭环”进入“LLM 接入准备阶段”。

下一步建议不是直接接 DashScope，而是先做：

```text
PromptBuilder + Parser + Mock LLM Client v1
```

建议开发顺序：

```text
1. PromptBuilder + Parser + Mock LLM Client v1
2. DashScope LLM Diagnosis v1
3. LLM Eval Cases v1
4. RAG / Milvus 文档检索升级
5. 最小前端工作台
6. 多 Agent / 自动修复 / 通知扩展
```

---

## 5. 关键边界说明

### 5.1 `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md`

该文档描述当前 Agent 诊断执行链路和确定性诊断能力，不代表完整真实 LLM Agent 已完成。

### 5.2 `10_EXCEPTION_SOURCE_AUDIT.md`

该文档说明当前 409、429、HIGH_FAILURE_RATE、HIGH_RATE_LIMIT 的来源边界：

```text
可控流量 / Mock 业务场景触发
不是绕过真实链路直接伪造日志、告警或报告
```

同时需要注意：

```text
Gateway Invoke 当前不执行独立本地限流拦截；
429 来自 Mock Provider RATE_LIMITED 场景，经 Gateway Invoke 代理并写入 gateway_log。
```

### 5.3 `11_LLM_AGENT_READINESS.md`

该文档说明接入 LLM 前的确定性基线：

```text
Normal Baseline Control = NORMAL + 0 alerts
Daily + Lecture Peak Pilot = WARNING + HIGH_FAILURE_RATE / HIGH_RATE_LIMIT
```

并收口 7 个 P0 Tool 与 7 类 Evidence 的 Prompt 可用字段。

### 5.4 `12_LLM_PROMPT_CONTRACT.md`

该文档定义 LLM Diagnosis v1 的提示词契约：

```text
LLM 不是事实来源；
LLM 不是告警判定器；
LLM 不自主调用工具；
LLM 只基于 evidence package 生成结构化诊断文本。
```

第一版 LLM 输出必须是结构化 JSON，后续由 Parser / Validator 校验。

---

## 6. 文档维护原则

- 架构和设计变更优先更新主线文档。
- smoke 或 pilot 结果更新优先写入 `09_VALIDATION_AND_SMOKE_GUIDE.md` 或阶段性 summary。
- Tool 入参、出参、权限、Evidence 变化必须同步 `02_TOOL_CONTRACT.md`。
- LLM 输入输出、Prompt、JSON Schema、评测规则变化必须同步 `12_LLM_PROMPT_CONTRACT.md`。
- 历史设计不要混入主线，可归档到 `docs/archive/v1/`。
