# 11. LLM Agent Readiness：正常/异常对照与证据契约收口

## 1. 文档目的

本文档记录 API-HUB Agent 在接入真实 LLM / DashScope Agent 之前已经具备的确定性基线。

当前项目已经具备从业务流量、网关日志、统计聚合、告警事件、确定性诊断、证据入库到 HTML/PDF 报告导出的后端闭环。后续 LLM 层必须消费这些事实，而不能编造指标、日志、告警、API 文档或生产事故。

本文档不是 LLM 接入实现文档。本阶段不引入：

```text
DashScope / OpenAI / 真实 LLM
Function Calling
ReAct
RAG / Milvus
多 Agent 运行时
完整前端工作台
自动修复 / 自动通知
```

LLM Prompt 的输入输出、JSON Schema、约束和评测规则详见：

```text
12_LLM_PROMPT_CONTRACT.md
```

---

## 2. 当前确定性链路

当前确定性链路如下：

```text
Scenario Runner 或 scripted Gateway Invoke traffic
-> Gateway Invoke
-> gateway_log
-> Stats Aggregator
-> api_call_stat_hourly
-> Alert Evaluator
-> alert_event
-> Agent Diagnosis Evidence
-> agent_report / evidence_item / tool_call_trace
-> Report Workbench HTML / browser PDF export
```

说明：

- Normal Baseline Control 使用脚本直接构造低峰 NORMAL Gateway Invoke 请求，而不是运行 `LECTURE_REGISTER_PEAK`。
- 低峰正常场景仍经过 Gateway Invoke、gateway_log、Stats Aggregator、Alert Evaluator、Agent Diagnosis 和 Report Workbench。
- Daily + Lecture Peak Pilot 使用日常流量叠加讲座报名高峰，用于验证异常风险是否能被识别。
- 以上两类场景都不接入真实 LLM，诊断结论来自确定性规则和真实落库证据。

---

## 3. 正常 / 异常对照

| 场景 | 流量模型 | 预期风险等级 | 实际风险等级 | 告警情况 | 报告路径 |
|---|---|---|---|---|---|
| Normal Baseline Control | 60s 低峰 NORMAL 请求，覆盖 AUTH_LOGIN、COURSE_TODAY、LECTURE_LIST、CAMPUS_NOTICE、LIBRARY_BORROW | NORMAL | NORMAL | alertCount=0，无 HIGH_FAILURE_RATE / HIGH_RATE_LIMIT | `D:\tmp\apihub-agent-normal-baseline-test\output\run-20260622-222433` |
| Daily + Lecture Peak Pilot Test | 60s 日常流量 + 120s 讲座报名高峰叠加 + 45s 峰后日常流量 | WARNING | WARNING | HIGH_FAILURE_RATE / HIGH_RATE_LIMIT | `D:\tmp\apihub-agent-pilot-test\output\run-20260622-205620` |

解释：

- Normal Baseline Control 证明系统不是“只要跑测试就报警”。
- Daily + Lecture Peak Pilot 证明可控高峰流量可以通过真实日志和告警评估链路触发风险报告。
- 如果 Normal Baseline Control 返回 WARNING，不能强行改成 NORMAL，应检查阈值、时间窗口污染、Mock 场景、历史告警或诊断规则。
- 如果高峰场景无法触发 WARNING，应检查 Scenario Runner 流量模型、Mock 场景权重、Alert Evaluator 阈值和诊断规则。

---

## 4. Tool Contract 收口

当前 LLM 接入前可用的 P0 Tool 为 7 个：

```text
queryApiInfo
queryApiCallStats
queryAlertEvents
queryGatewayLogs
queryRateLimitRule
queryCampusEvents
queryApiDocs
```

> 注：历史设计中曾有 `queryConsumerApp`，当前 LLM Readiness 阶段按已落入确定性诊断链路的 7 个 P0 Tool 收口。后续如重新启用调用方应用 Tool，应同步更新 `02_TOOL_CONTRACT.md`、本文档和 `12_LLM_PROMPT_CONTRACT.md`。

| Tool | 输入关键字段 | 输出关键字段 | 空结果语义 | 进入 LLM Prompt 的字段 | 不应让 LLM 编造的字段 |
|---|---|---|---|---|---|
| `queryApiInfo` | `apiCode`, `includeRateLimit`, `includeConsumerApps` | API 元信息、状态、负责人、风险等级、路由、可选限流和应用信息 | API 不存在或无权限是显式失败；可选数组为空表示没有匹配的可选数据 | `apiCode`, `apiName`, `status`, `riskLevel`, `route`, `rateLimitRules`, `consumerApps`, evidence title/content | 负责人、路由、状态、权限、限流规则 |
| `queryApiCallStats` | `apiCode`, `startTime`, `endTime` | 小时级统计、total/success/fail、failRate、latency、rateLimitCount、riskLevel | 指定范围内无小时统计，不等于工具异常 | `totalCallCount`, `totalFailCount`, `failRate`, `totalRateLimitedCount`, `maxP95LatencyMs`, `maxP99LatencyMs`, `hourlyStats` | 调用量、失败率、P95/P99、小时桶数据 |
| `queryAlertEvents` | `apiCode`, `startTime`, `endTime`, `alertType/eventType`, `severity`, `status`, `limit` | 告警列表、等级分布、open count、extraInfo 中的窗口指标 | 没有告警不是错误；可能表示正常窗口 | 聚合告警类型、等级、状态、actualValue、threshold、windowStart/windowEnd、total/fail/rateLimit count | 告警是否存在、等级、阈值、触发窗口 |
| `queryGatewayLogs` | `apiCode`, `startTime`, `endTime`, `httpStatus`, `keyword`, `limit` | 日志样本、状态码分布、错误码、延迟、traceId | 过滤条件下没有日志，不等于系统故障 | 摘要化样本、状态分布、traceId、errorCode/errorMessage、latency | 返回之外的日志总量、隐藏请求头、未返回 trace |
| `queryRateLimitRule` | `apiCode`, `includeInactive` | ruleId、qps/burst、状态、规则元数据 | 没有限流规则表示无配置，不等于异常 | active rule summary、qps、burst、status、建议检查点 | Gateway Invoke 是否已经本地执行限流 |
| `queryCampusEvents` | `apiCode`, `startTime`, `endTime`, `includeRelatedApis`, `limit` | 业务事件、关联 API、事件窗口 | 无业务事件不是错误 | eventType、title、time range、relatedApis、expectedTrafficLevel | 未返回的业务事件 |
| `queryApiDocs` | `apiCode`, `keyword`, `docType`, `limit` | MySQL keyword 文档片段和元数据 | 无命中文档不是错误 | documentTitle、chunkTitle、contentPreview、keyword score、docType | 向量检索命中、未返回文档、未索引知识 |

补充规则：

- `queryApiCallStats` 当前基于小时桶。短窗口诊断可查询扩展后的 hour-aligned range，并应在 evidence 中说明。
- `queryAlertEvents` 可能返回多个 30 秒短窗口告警。PromptBuilder 应在顶层推理时聚合同类告警，同时保留底层 evidence 明细。
- `queryRateLimitRule` 当前是诊断证据，不证明 Gateway Invoke 已经执行本地限流拦截。
- `queryApiDocs` 当前是 MySQL keyword 检索，不是 Milvus / RAG 向量检索。

---

## 5. Evidence Contract 收口

| evidenceType | 来源 Tool | 用途 | Prompt 字段 | 展示层用途 |
|---|---|---|---|---|
| `API_INFO` | `queryApiInfo` | API 身份、负责人、状态、路由、权限上下文 | title、content、metadata.apiCode/apiName/status/riskLevel | API 基础信息卡片 |
| `API_CALL_STAT` | `queryApiCallStats` 或 Agent Diagnosis 从 stats data 合成 | 调用量、失败率、限流次数、延迟证据 | content、metadata.totalCallCount/failRate/rateLimitCount/p95/p99/hourlyStats | 指标摘要和证据链 |
| `ALERT_EVENT` | `queryAlertEvents` | 告警窗口、阈值、实际值和等级 | metadata.alertType/severity/actualValue/threshold/windowStart/windowEnd/totalCount | 顶部聚合告警摘要 + 明细证据 |
| `GATEWAY_LOG_SAMPLE` | `queryGatewayLogs` | 具体请求样本、状态码、trace、错误信息 | metadata.httpStatus/traceId/requestTime/errorCode/latency | 证据链和可追溯样本 |
| `RATE_LIMIT_RULE` | `queryRateLimitRule` | 限流配置上下文 | qps、burst、status、ruleId、checkpoints | 规则说明和建议依据 |
| `CAMPUS_EVENT` | `queryCampusEvents` | 校园业务事件和关联 API | eventType、title、timeRange、relatedApis | 业务背景说明 |
| `API_DOC` | `queryApiDocs` | API 文档、错误码、签名规则、使用说明 | documentTitle、chunkTitle、contentPreview、keyword/docType | 文档证据区域 |

Evidence 要求：

- 每条 evidence 应尽量能追溯到 Tool 结果、sourceId 或持久化表记录。
- 展示层可以聚合 evidence，但不得删除底层 evidence 明细。
- PromptBuilder 应优先使用 evidence summary 和关键 metadata，不应将大量原始日志或长文档直接塞给 LLM。
- LLM 输出中的关键结论应能回指 evidenceRefs。

---

## 6. LLM 接入前约束

后续 LLM 层必须满足：

- LLM 输出只能基于 Tool results 和 evidence items。
- LLM 不得编造指标、日志、告警、API 文档、负责人、限流规则、业务事件或生产事故。
- LLM 不得把模拟开发环境验证描述成真实生产事故。
- LLM 必须区分 NORMAL、WARNING、CRITICAL、UNKNOWN。
- LLM 第一版不得擅自修改 deterministic riskLevel。
- 如果证据不足，LLM 必须说明证据不足，而不是猜测根因。
- LLM 生成的 `summary`、`rootCause`、`recommendation` 必须可追溯到 evidence items。
- PromptBuilder 应包含来自 `10_EXCEPTION_SOURCE_AUDIT.md` 的异常来源边界。
- PromptBuilder 应包含当前环境边界，例如 `development_simulation`、`controlled traffic injection`、`not fabricated alert`。

---

## 7. 建议的 PromptBuilder 输入

未来 PromptBuilder v1 应包含：

- 用户问题。
- API 编码和时间窗口。
- scenarioRunId 和 scenarioType。
- deterministic diagnosis summary。
- deterministic riskLevel。
- tool result summaries。
- 按 evidenceType 分组后的 top evidence items。
- 重复短窗口告警的聚合摘要。
- 带 traceId 的网关日志样本摘要。
- 异常来源边界和模拟环境声明。
- normal/abnormal comparison context。
- 必须输出的 JSON Schema。

逻辑输入结构详见：

```text
12_LLM_PROMPT_CONTRACT.md
```

---

## 8. 阶段 1：LLM Prompt Contract v1

LLM Prompt Contract v1 的目标是定义：

```text
LlmDiagnosisInput
LlmDiagnosisOutput
System Prompt Contract
Diagnosis Prompt Contract
JSON Schema
风险等级约束
正常/异常/证据不足输出规则
Parser / Validator 要求
Eval Case 草案
```

该阶段不接入真实 LLM，不写正式模型调用代码。

LLM 第一版定位为：

```text
Evidence-grounded Report Generation
```

也就是：

```text
确定性诊断结果 + evidence package
-> LLM
-> 结构化 JSON 诊断文本
```

它不是：

```text
自主 Tool Calling Agent
ReAct Agent
事实判定器
告警生成器
自动修复执行器
```

详细规则见：

```text
12_LLM_PROMPT_CONTRACT.md
```

---

## 9. 后续计划

推荐顺序：

```text
1. PromptBuilder + Parser + Mock LLM Client v1
2. DashScope LLM Diagnosis v1
3. LLM Eval Cases v1
4. RAG / Milvus 检索升级
5. 最小前端工作台
6. 多 Agent / 自动修复 / 通知扩展
```

每一步都应保留确定性诊断作为 fallback。LLM 层只增强表达和报告质量，不替代事实采集与规则诊断。

---

## 10. Implementation Status - PromptBuilder + Parser + Mock Client v1

Status: implemented locally, still no real LLM integration.

Code:

```text
apihub-server/src/main/java/com/apihub/agent/dev/llm/
```

Tests:

```text
apihub-server/src/test/java/com/apihub/agent/dev/llm/LlmDiagnosisCoreTest.java
```

Implemented readiness checkpoint:

```text
deterministic report/evidence/tool_trace
-> LlmDiagnosisInput
-> PromptBuilder
-> MockLlmDiagnosisClient
-> Parser
-> Validator
-> deterministic fallback if parse/validation fails
```

This stage proves prompt construction, structured JSON parsing, evidence reference validation, risk-level preservation, normal wording guardrails, and local fallback behavior before any DashScope client is added.

---

## 11. Implementation Status - DashScope LLM Diagnosis v1

DashScope LLM Diagnosis v1 has been added as the first real provider path after the local mock stage.

Entry points:

```text
POST /api/dev/agent/diagnose/llm/mock
POST /api/dev/agent/diagnose/llm/dashscope
```

Configuration:

```text
DASHSCOPE_API_KEY
AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_CHAT_MODEL=qwen-plus
AI_LLM_TIMEOUT_SECONDS=60
AI_LLM_TEMPERATURE=0.1
```

The backend first reads Spring/process/system configuration and can fall back to the local dev `docker/.env` file. The API key is not printed or returned.

Optional proxy fallback configuration:

```text
AI_LLM_PROXY_ENABLED=true|false
AI_LLM_PROXY_HOST=127.0.0.1
AI_LLM_PROXY_PORT=10808
AI_LLM_PROXY_SCHEME=http
AI_LLM_PROXY_FALLBACK_ENABLED=true|false
AI_LLM_DIRECT_RETRY_COUNT=2
```

Proxy fallback is never the first attempt. The DashScope client tries direct calls first, retries direct calls for retryable network / 429 / 5xx failures, then uses the configured proxy at most once as a last fallback. These values are read from Spring/process/system configuration or `docker/.env` if present; the application does not write them into `docker/.env`.

Readiness boundary remains:

```text
DashScope is a structured JSON report generator over deterministic evidence.
It is not the source of facts, not a risk-level authority, and not an autonomous tool caller.
```
