# API-HUB Agent 文档地图与边界说明

## 0. 文档定位

本文档用于说明 API-HUB Agent 当前工程文档的推荐分类、阅读顺序、文件边界和旧文档合并关系。

本轮调整的目标不是简单把旧的 `05-09` 顺延编号，而是按照工程依赖关系重新组织：

```text
统一调用契约
→ 外部 API 目录
→ Mock Provider 与场景流量模拟
→ Tool Chain / Agent Run 诊断链路
→ Smoke / 验收指南
```

调整后，`05` 不再作为 smoke 文档，而是放最关键的统一调用契约；smoke 作为工程验收资料移动到最后。

---

## 1. 推荐文档结构

| 编号 | 文件名 | 主要定位 | 重要程度 |
|---|---|---|---|
| `01` | `01_DB_SCHEMA.md` | 数据库表结构、字段约束、示例数据 | 高 |
| `02` | `02_TOOL_CONTRACT.md` | Tool 入参、出参、权限、错误码、Trace | 高 |
| `03` | `03_API_CONTRACT.md` | 后端 HTTP API 契约 | 高 |
| `04` | `04_VIBE_CODING_RULES.md` | Codex / Vibe Coding 开发规范 | 中 |
| `05` | `05_API_INVOKE_CONTRACT.md` | 统一 API 调用格式、返回格式、ID、Trace、Gateway Invoke 契约 | 高 |
| `06` | `06_EXTERNAL_API_CATALOG.md` | 被 API-HUB 管理的外部业务 API 目录与业务场景 | 高 |
| `07` | `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md` | Mock Provider、单次 API 模拟、Scenario Runner、真实流量模型 | 高 |
| `08` | `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md` | Tool Chain Eval、Agent Run、SSE、证据链与诊断流程 | 中高 |
| `09` | `09_VALIDATION_AND_SMOKE_GUIDE.md` | Smoke、Apifox、验收命令、人工检查清单 | 中 |

---

## 2. 旧文档合并关系

| 原文档 | 调整方式 | 新位置 |
|---|---|---|
| `05_EVAL_AND_SMOKE_CASES.md` | 降级为验收指南，不再放在核心设计前面 | `09_VALIDATION_AND_SMOKE_GUIDE.md` |
| `06_EXTERNAL_API_SCENARIOS.md` | 保留外部 API 目录和业务事件矩阵，去掉 Tool Chain / Smoke 重复内容 | `06_EXTERNAL_API_CATALOG.md` |
| `07_TOOL_CHAIN_EVAL_CASES.md` | 与 Agent Run/SSE 合并，统一描述诊断执行链路 | `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md` |
| `08_AGENT_RUN_SSE.md` | 与 Tool Chain Eval 合并，减少重复边界说明 | `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md` |
| `09_MOCK_PROVIDER_SCENARIOS.md` | 与场景流量模拟设计合并 | `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md` |
| `10_SCENARIO_TRAFFIC_SIMULATION_DESIGN.md` | 不再单独作为 10 号文档，核心内容并入 `07` | `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md` |
| `10_API_INVOKE_CONTRACT.md` | 变为新的核心 `05`，放在 Gateway Invoke 实现之前 | `05_API_INVOKE_CONTRACT.md` |

---

## 3. 各文档边界原则

### 3.1 `05_API_INVOKE_CONTRACT.md`

只回答：

```text
一次 API 调用应该长什么样？
请求头怎么统一？
返回结构怎么统一？
ID 用数字还是字符串？
Gateway Invoke 怎么包装外部 API 调用？
调用结果如何映射到 gateway_log？
```

不展开具体业务场景，不展开 Agent 诊断流程。

### 3.2 `06_EXTERNAL_API_CATALOG.md`

只回答：

```text
API-HUB 当前管理哪些外部业务 API？
这些 API 的业务用途是什么？
常见事故场景是什么？
哪些 Tool 会用到这些 API 的事实数据？
```

不写 mock provider 的实现细节，不写 Scenario Runner 参数。

### 3.3 `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md`

只回答：

```text
Mock Provider 如何模拟单个外部 API？
Scenario Runner 如何生成真实业务流量？
正常/异常比例如何设计？
Ramp-up / peak / ramp-down 如何设计？
为什么每次调用必须经过 Gateway Invoke？
```

不写 Agent 最终诊断答案，不写 Tool Chain 细节。

### 3.4 `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md`

只回答：

```text
Tool Chain Eval 如何串联 Tools？
Agent Run 如何复用 Tool Chain Eval？
SSE 发哪些事件？
报告、证据、会话如何落库？
```

不写外部 API 的完整目录，不写流量模拟参数。

### 3.5 `09_VALIDATION_AND_SMOKE_GUIDE.md`

只回答：

```text
怎么验收当前系统？
跑哪些 smoke 脚本？
Apifox 怎么导入？
哪些是当前已验证边界？
```

不作为核心架构设计文档。

---

## 4. 推荐阅读顺序

### 新同学接手开发

```text
00_DOCUMENT_MAP.md
→ 04_VIBE_CODING_RULES.md
→ 01_DB_SCHEMA.md
→ 05_API_INVOKE_CONTRACT.md
→ 06_EXTERNAL_API_CATALOG.md
→ 07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md
→ 02_TOOL_CONTRACT.md
→ 08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md
→ 09_VALIDATION_AND_SMOKE_GUIDE.md
```

### 下一步实现 Gateway Invoke

```text
05_API_INVOKE_CONTRACT.md
→ 01_DB_SCHEMA.md 中 gateway_log 表
→ 06_EXTERNAL_API_CATALOG.md
→ 07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md
→ 09_VALIDATION_AND_SMOKE_GUIDE.md
```

### 下一步实现 Scenario Runner

```text
07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md
→ 05_API_INVOKE_CONTRACT.md
→ 06_EXTERNAL_API_CATALOG.md
→ 09_VALIDATION_AND_SMOKE_GUIDE.md
```

### 准备面试讲项目

```text
06_EXTERNAL_API_CATALOG.md
→ 07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md
→ 08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md
```

---

## 5. 当前阶段建议

下一步不要直接继续堆 Agent 能力，而应先完成：

```text
apihub-server Gateway Invoke
→ 每一次外部 API 调用经过 API-HUB 平台
→ 记录 gateway_log
→ apihub-mock-provider Scenario Runner 调用 Gateway Invoke
```

完成后，项目从“seed 数据诊断”升级为：

```text
真实模拟调用
→ 真实调用日志
→ 后续统计聚合
→ 后续告警生成
→ Agent 基于新生成事实诊断
```
