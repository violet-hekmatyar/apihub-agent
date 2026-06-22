# 12. LLM Prompt Contract：Evidence-grounded Diagnosis v1

## 1. 文档目的

本文档定义 API-HUB Agent 第一版 LLM Diagnosis 的 Prompt Contract。

本阶段只定义：

- LLM 在系统中的职责边界；
- LLM 输入对象 `LlmDiagnosisInput`；
- evidence package 的组织方式；
- System Prompt 和 Diagnosis Prompt 的规则；
- LLM 输出对象 `LlmDiagnosisOutput`；
- JSON Schema；
- 风险等级约束；
- NORMAL / WARNING / CRITICAL / 证据不足场景的输出规则；
- Parser / Validator 后续实现要求；
- Eval Case 草案。

本阶段不做：

```text
不接入 DashScope / OpenAI / 真实 LLM
不实现 Function Calling / ReAct
不让 LLM 自主选择 Tool
不接入 RAG / Milvus
不开发完整前端
不实现自动修复 / 自动通知
```

一句话定位：

```text
LLM 不是事实来源；
LLM 是证据解释器和结构化诊断报告生成器。
```

---

## 2. LLM Diagnosis v1 定位

第一版 LLM Diagnosis 定义为：

```text
Evidence-grounded Report Generation
```

它的输入来自已有确定性链路：

```text
Gateway Invoke
-> gateway_log
-> Stats Aggregator
-> Alert Evaluator
-> Agent Diagnosis Evidence
-> evidence_item / tool_call_trace
```

LLM 的职责是：

```text
确定性诊断结果 + evidence package
-> 生成结构化诊断文本
-> 输出 JSON
-> 供 Parser / Validator 校验
-> 供 Report Workbench 展示
```

LLM 不负责：

```text
查询事实
生成告警
判断真实日志是否存在
创造指标
修改风险等级
执行修复动作
发送通知
```

第一版不做 Function Calling。7 个 P0 Tool 仍由后端确定性流程调用，LLM 只消费 Tool 返回的事实和 evidence。

---

## 3. 整体调用链路

建议后续实现链路如下：

```text
POST /api/dev/agent/diagnose
-> Agent Diagnosis Evidence
-> agent_report / evidence_item / tool_call_trace
-> PromptBuilder
-> LLM Diagnosis Client
-> LlmDiagnosisOutput JSON
-> Parser
-> Validator
-> LLM diagnosis result
-> agent_report 或 report extension
-> Report Workbench
```

第一版必须保留确定性诊断作为 fallback：

```text
LLM 成功：展示 deterministic diagnosis + LLM polished diagnosis + evidence chain
LLM 失败：展示 deterministic diagnosis + evidence chain
```

推荐实现顺序：

```text
阶段 2：PromptBuilder + Parser + Mock LLM Client
阶段 3：DashScope LLM Diagnosis v1
阶段 4：LLM Eval Cases v1
```

---

## 4. LlmDiagnosisInput 定义

`LlmDiagnosisInput` 是 PromptBuilder 组织给 LLM 的逻辑输入结构。

示例：

```json
{
  "task": {
    "question": "Analyze API risk for LECTURE_REGISTER",
    "apiCode": "LECTURE_REGISTER",
    "apiName": "Lecture Register",
    "startTime": "2026-06-22 20:04:15",
    "endTime": "2026-06-22 20:08:25",
    "scenarioRunId": "sr_20260622200526_8637ee68",
    "scenarioType": "ABNORMAL_PEAK",
    "environment": "development_simulation"
  },
  "deterministicDiagnosis": {
    "riskLevel": "WARNING",
    "summary": "LECTURE_REGISTER shows rate-limit pressure in the selected diagnosis window.",
    "rootCause": "Peak requests are concentrated enough to trigger rate-limit policy or 429 gateway responses.",
    "recommendation": "Review rate-limit thresholds, queue hints, caching, async handling, and temporary capacity settings.",
    "reportId": 16055
  },
  "toolSummaries": [
    {
      "toolName": "queryAlertEvents",
      "success": true,
      "summary": "HIGH_FAILURE_RATE and HIGH_RATE_LIMIT alerts were found in repeated short windows.",
      "evidenceCount": 9
    }
  ],
  "evidenceGroups": {
    "API_INFO": [],
    "API_CALL_STAT": [],
    "ALERT_EVENT": [],
    "GATEWAY_LOG_SAMPLE": [],
    "RATE_LIMIT_RULE": [],
    "CAMPUS_EVENT": [],
    "API_DOC": []
  },
  "constraints": {
    "riskLevelPolicy": "LLM must not override deterministic riskLevel in v1.",
    "exceptionSourceBoundary": "Exceptions are controlled traffic / Mock scenario triggered, not direct fabricated alerts.",
    "rateLimitBoundary": "HTTP 429 currently comes from Mock Provider RATE_LIMITED scenario proxied by Gateway Invoke, not Gateway local limiter.",
    "documentationBoundary": "queryApiDocs currently uses MySQL keyword retrieval, not Milvus/RAG."
  }
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `task` | 用户问题、API、时间窗口、场景、环境信息 |
| `deterministicDiagnosis` | 确定性诊断输出，是第一版风险等级主来源 |
| `toolSummaries` | Tool 调用摘要，避免直接塞入过多原始数据 |
| `evidenceGroups` | 按 evidenceType 分组后的证据 |
| `constraints` | 模拟环境、异常来源、风险等级、文档检索边界 |

---

## 5. scenarioType 定义

| scenarioType | 说明 | 输出要求 |
|---|---|---|
| `NORMAL_BASELINE` | 正常低峰对照场景 | 不得输出异常根因；重点说明当前窗口未观察到明显风险 |
| `ABNORMAL_PEAK` | 高峰、限流、失败率升高等异常验证场景 | 基于告警、统计、日志解释风险 |
| `AUTH_FAILURE` | 登录、签名、token、权限异常场景 | 重点围绕认证链路和调用方配置 |
| `DEPENDENCY_FAILURE` | 下游超时、5xx、依赖异常场景 | 重点围绕依赖健康、超时、降级 |
| `UNKNOWN` | 无法明确归类 | 保守输出，不得扩大结论 |

---

## 6. Evidence Package 组织规则

### 6.1 分组方式

PromptBuilder 应按 evidenceType 分组：

```text
API_INFO
API_CALL_STAT
ALERT_EVENT
GATEWAY_LOG_SAMPLE
RATE_LIMIT_RULE
CAMPUS_EVENT
API_DOC
```

每条 evidence 建议转成 Prompt 可读结构：

```json
{
  "evidenceRef": "ALERT_EVENT#1",
  "evidenceType": "ALERT_EVENT",
  "title": "HIGH_RATE_LIMIT on LECTURE_REGISTER",
  "content": "rateLimitRate reached 14.8% in a 30s window.",
  "metadata": {
    "alertType": "HIGH_RATE_LIMIT",
    "severity": "WARNING",
    "actualValue": 0.148,
    "threshold": 0.05,
    "windowStart": "2026-06-22 20:05:15",
    "windowEnd": "2026-06-22 20:05:45"
  }
}
```

### 6.2 聚合规则

- `ALERT_EVENT`：重复 30 秒短窗口告警应先聚合给 LLM，再保留明细 evidenceRefs。
- `GATEWAY_LOG_SAMPLE`：只提供摘要化样本、状态码分布、关键 traceId，不塞大量原始日志。
- `API_CALL_STAT`：提供总量、失败率、限流次数、P95/P99、小时桶摘要。
- `API_DOC`：提供文档标题、片段摘要、docType，不得把 MySQL keyword 命中写成向量 RAG 命中。
- `RATE_LIMIT_RULE`：说明当前规则是诊断证据，不代表 Gateway Invoke 已执行本地限流。

### 6.3 evidenceRefs 规则

每条关键结论必须引用 evidenceRefs。

推荐格式：

```text
API_INFO#1
API_CALL_STAT#1
ALERT_EVENT#1
GATEWAY_LOG_SAMPLE#3
RATE_LIMIT_RULE#1
CAMPUS_EVENT#1
API_DOC#2
```

要求：

- evidenceRefs 必须来自输入 evidence package。
- LLM 不得引用不存在的 evidenceRef。
- Parser / Validator 后续应校验 evidenceRefs 是否可回指。
- 如果没有证据支撑，必须放入 `uncertainties` 或 `followUpChecks`，不能写成确定结论。

---

## 7. System Prompt Contract

System Prompt 只放不可违反的边界规则。

建议模板：

```text
你是 API-HUB Agent 的诊断报告生成模块。

你只能基于输入的 deterministic diagnosis、tool summaries 和 evidence package 生成诊断结果。
你不得编造输入中不存在的指标、日志、告警、API 文档、业务事件、负责人、限流规则或生产事故。
你不得把开发态模拟验证描述为真实生产故障。
如果证据不足，必须明确说明证据不足，并列出需要补充的检查项。
第一版中，riskLevel 必须等于 deterministicRiskLevel，不得擅自升级或降级。
你必须输出符合指定 JSON Schema 的 JSON。
不得输出 Markdown、解释文字、代码块或 JSON 之外的其他文本。
每个关键结论必须能追溯到 evidenceRefs；没有证据支撑的内容必须放入 uncertainties。
```

---

## 8. Diagnosis Prompt Contract

Diagnosis Prompt 负责描述本次任务、输入内容和输出要求。

建议模板：

```text
请基于以下 API 诊断上下文生成结构化诊断结果。

任务信息：
{{task}}

确定性诊断：
{{deterministicDiagnosis}}

工具摘要：
{{toolSummaries}}

证据分组：
{{evidenceGroups}}

边界约束：
{{constraints}}

输出要求：
1. 输出必须是 JSON。
2. riskLevel 必须等于 deterministicDiagnosis.riskLevel。
3. executiveSummary 面向管理者，简洁说明结论。
4. technicalSummary 面向工程排查，说明指标、告警和日志证据。
5. rootCause 必须基于 evidenceRefs；证据不足时写“证据不足”。
6. impactScope 只能描述 evidence 中支持的影响范围。
7. recommendations 中每条建议都必须包含 reason 和 evidenceRefs。
8. uncertainties 中列出无法确认的点。
9. simulationBoundaryStatement 必须说明当前是否为开发态模拟验证。
10. followUpChecks 给出下一步可执行检查项。
```

---

## 9. LlmDiagnosisOutput 定义

LLM 必须输出结构化 JSON。

逻辑结构：

```json
{
  "riskLevel": "WARNING",
  "riskLevelChanged": false,
  "riskLevelChangeReason": "",
  "executiveSummary": "Selected API shows rate-limit pressure during the diagnosis window.",
  "technicalSummary": "Repeated HIGH_RATE_LIMIT and HIGH_FAILURE_RATE alerts were observed in short windows, supported by gateway log samples and API call statistics.",
  "rootCause": "Peak requests concentrated on the lecture registration API triggered Mock Provider RATE_LIMITED and business-conflict scenarios.",
  "impactScope": "The evidence is limited to LECTURE_REGISTER in the selected diagnosis window.",
  "recommendations": [
    {
      "priority": "P1",
      "action": "Review lecture registration peak handling and queueing strategy.",
      "reason": "Repeated rate-limit and failure-rate alerts were observed during peak windows.",
      "evidenceRefs": ["ALERT_EVENT#1", "API_CALL_STAT#1"]
    }
  ],
  "evidenceUsage": [
    {
      "evidenceRef": "ALERT_EVENT#1",
      "usedFor": "supports rate-limit pressure conclusion"
    }
  ],
  "uncertainties": [
    "Gateway Invoke does not currently enforce a local limiter; 429 comes from Mock Provider RATE_LIMITED scenario."
  ],
  "simulationBoundaryStatement": "This report is based on development-environment controlled traffic validation, not a real production incident.",
  "followUpChecks": [
    "Run a normal baseline control periodically to verify alert noise remains low."
  ]
}
```

---

## 10. LlmDiagnosisOutput JSON Schema

阶段 2 可基于以下 Schema 实现 Parser / Validator。

```json
{
  "type": "object",
  "required": [
    "riskLevel",
    "riskLevelChanged",
    "executiveSummary",
    "technicalSummary",
    "rootCause",
    "impactScope",
    "recommendations",
    "evidenceUsage",
    "uncertainties",
    "simulationBoundaryStatement",
    "followUpChecks"
  ],
  "properties": {
    "riskLevel": {
      "type": "string",
      "enum": ["NORMAL", "WARNING", "CRITICAL", "UNKNOWN"]
    },
    "riskLevelChanged": {
      "type": "boolean",
      "const": false
    },
    "riskLevelChangeReason": {
      "type": "string"
    },
    "executiveSummary": {
      "type": "string",
      "minLength": 1
    },
    "technicalSummary": {
      "type": "string",
      "minLength": 1
    },
    "rootCause": {
      "type": "string",
      "minLength": 1
    },
    "impactScope": {
      "type": "string",
      "minLength": 1
    },
    "recommendations": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["priority", "action", "reason", "evidenceRefs"],
        "properties": {
          "priority": {
            "type": "string",
            "enum": ["P1", "P2", "P3"]
          },
          "action": {
            "type": "string",
            "minLength": 1
          },
          "reason": {
            "type": "string",
            "minLength": 1
          },
          "evidenceRefs": {
            "type": "array",
            "items": {
              "type": "string"
            }
          }
        }
      }
    },
    "evidenceUsage": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["evidenceRef", "usedFor"],
        "properties": {
          "evidenceRef": {
            "type": "string"
          },
          "usedFor": {
            "type": "string"
          }
        }
      }
    },
    "uncertainties": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "simulationBoundaryStatement": {
      "type": "string",
      "minLength": 1
    },
    "followUpChecks": {
      "type": "array",
      "items": {
        "type": "string"
      }
    }
  },
  "additionalProperties": false
}
```

---

## 11. 风险等级约束

第一版风险等级策略：

```text
riskLevel = deterministicDiagnosis.riskLevel
riskLevelChanged = false
```

LLM 可以解释风险等级，但不能覆盖确定性系统判断。

| deterministicRiskLevel | LLM 应做 | LLM 不应做 |
|---|---|---|
| `NORMAL` | 说明当前窗口未观察到明显风险，给出观察建议 | 不得输出异常根因，不得写成事故 |
| `WARNING` | 基于告警、统计、日志解释风险和建议 | 不得夸大成生产事故或 CRITICAL |
| `CRITICAL` | 强调紧急排查方向和证据依据 | 不得编造业务损失 |
| `UNKNOWN` | 说明证据不足和需要补充的检查项 | 不得硬给根因 |

后续版本如允许 LLM 提出风险等级调整建议，应增加：

```json
{
  "suggestedRiskLevel": "WARNING",
  "suggestedRiskReason": "..."
}
```

但不能直接覆盖确定性 riskLevel。

---

## 12. NORMAL 场景输出规则

当满足：

```text
scenarioType = NORMAL_BASELINE
riskLevel = NORMAL
alertCount = 0
```

LLM 必须：

- 说明当前窗口未观察到明显失败率、限流、延迟或告警风险。
- 不得输出异常根因。
- 不得写“系统故障”“事故”“服务异常”等表述。
- recommendations 应以持续观察、保留基线、后续对照为主。
- uncertainties 可说明：当前结论仅覆盖本测试窗口，不代表长期无风险。
- simulationBoundaryStatement 必须说明这是开发态正常基线验证。

示例表达：

```text
当前低峰 NORMAL 请求窗口内未观察到明显异常。Alert Evaluator 未生成告警，Agent Diagnosis 输出 NORMAL。该结果可作为后续高峰或异常场景的对照基线。
```

---

## 13. WARNING / CRITICAL 场景输出规则

当 riskLevel 为 `WARNING` 或 `CRITICAL`：

LLM 必须：

- 优先引用 `ALERT_EVENT`、`API_CALL_STAT`、`GATEWAY_LOG_SAMPLE`。
- 说明告警类型、触发窗口、实际值和阈值。
- 说明影响范围只限于 evidence 支持的 API、时间窗口和场景。
- recommendations 必须引用 evidenceRefs。
- 不得编造未出现的 HIGH_5XX、AUTH_FAILURE_SPIKE、生产事故或用户影响。
- 不得把 Mock 场景写成真实生产故障。
- 如果 429 来自 Mock Provider RATE_LIMITED，应明确当前边界。

示例边界：

```text
本报告基于开发态可控流量验证。HTTP 429 来自 Mock Provider RATE_LIMITED 场景，并经 Gateway Invoke 写入 gateway_log；当前不能据此声称 Gateway 本地限流器已经触发。
```

---

## 14. 证据不足输出规则

当出现以下情况：

```text
缺少 API_CALL_STAT
缺少 ALERT_EVENT
缺少 GATEWAY_LOG_SAMPLE
Tool 调用失败
Evidence 数量不足
时间窗口无数据
```

LLM 必须：

- 在 `rootCause` 中说明“证据不足，无法确认根因”。
- 在 `uncertainties` 中列出缺失证据。
- 在 `followUpChecks` 中提出补充查询或重新运行 smoke 的建议。
- 不得根据常识猜测根因。
- 不得输出确定性结论。

示例：

```json
{
  "rootCause": "证据不足，当前缺少 API 调用统计和网关日志样本，无法确认失败率变化或具体错误来源。",
  "uncertainties": [
    "No API_CALL_STAT evidence was provided.",
    "No GATEWAY_LOG_SAMPLE evidence was provided."
  ],
  "followUpChecks": [
    "Run Stats Aggregator for the selected window.",
    "Query gateway logs with apiCode and time range."
  ]
}
```

---

## 15. 模拟环境边界声明

PromptBuilder 必须向 LLM 提供环境边界：

```text
environment = development_simulation
exceptionSourceBoundary = controlled traffic injection, not direct fabricated alert
rateLimitBoundary = 429 comes from Mock Provider RATE_LIMITED scenario unless future gateway limiter evidence exists
```

LLM 必须在 `simulationBoundaryStatement` 中说明：

- 当前报告是否来自开发态模拟验证；
- 异常是否是可控触发；
- 不能把模拟验证描述为真实生产事故；
- 结论只覆盖当前 evidence 和时间窗口。

推荐表达：

```text
This diagnosis is based on development-environment controlled traffic validation. The alerts and report were generated through the real Gateway Invoke, gateway_log, Stats Aggregator, Alert Evaluator, and Agent Diagnosis chain, but they do not represent a real production incident.
```

---

## 16. Parser / Validator 要求

阶段 2 实现 Parser / Validator 时，应至少校验：

| 校验项 | 要求 |
|---|---|
| JSON 可解析 | LLM 输出必须是合法 JSON |
| Schema 合法 | 必须满足 `LlmDiagnosisOutput JSON Schema` |
| riskLevel 合法 | 必须在 `NORMAL/WARNING/CRITICAL/UNKNOWN` 内 |
| riskLevel 不变 | 第一版必须等于 deterministic riskLevel |
| riskLevelChanged | 第一版必须为 `false` |
| evidenceRefs 存在 | 所有 evidenceRefs 必须能在输入 evidence package 中找到 |
| 禁止编造告警 | 不得出现输入中没有的 alertType |
| 禁止编造指标 | 不得出现输入中没有的具体 count/rate/latency |
| 模拟边界 | development_simulation 场景必须有边界说明 |
| 输出长度 | 单字段过长应裁剪或拒绝保存 |
| 失败降级 | 解析失败时 fallback 到 deterministic diagnosis |

Parser / Validator 不应只检查 JSON 格式，还要检查 evidence grounding。

---

## 17. Eval Case 草案

阶段 4 可实现以下 Eval Cases。阶段 1 仅定义草案。

| Case | 输入特征 | 期望输出 | 禁止输出 |
|---|---|---|---|
| `NORMAL_BASELINE` | riskLevel=NORMAL，alertCount=0，低峰 NORMAL 请求 | 输出正常结论、观察建议、无异常根因 | “系统故障”“限流异常”“生产事故” |
| `LECTURE_REGISTER_PEAK` | riskLevel=WARNING，HIGH_RATE_LIMIT / HIGH_FAILURE_RATE | 输出限流压力和失败率风险，引用告警与统计证据 | 编造 Gateway 本地限流、编造真实事故 |
| `AUTH_LOGIN_403` | 403 / 签名 / token / 调用方异常 | 输出认证链路排查方向 | 编造密码泄露、数据库故障 |
| `LIBRARY_5XX` | 5xx / timeout / dependency evidence | 输出下游依赖异常方向 | 编造未出现的限流或认证问题 |
| `INSUFFICIENT_EVIDENCE` | 缺少 stats/logs/alerts | 输出证据不足和补充检查项 | 硬给根因 |
| `DOC_ONLY_QA` | 仅 API_DOC 命中 | 只基于文档片段回答 | 编造实时运行状态 |
| `MIXED_ALERT_WINDOWS` | 多个重复短窗口告警 | 顶层聚合，明细引用 evidenceRefs | 平铺大量重复窗口导致不可读 |

评测维度：

- JSON 是否可解析。
- Schema 是否通过。
- riskLevel 是否保持确定性等级。
- 是否引用 evidenceRefs。
- 是否出现不存在的指标或告警。
- 是否把模拟验证说成生产事故。
- NORMAL 场景是否避免异常化表达。
- 证据不足场景是否避免猜测。

---

## 18. 后续实现计划

### 阶段 2：PromptBuilder + Parser + Mock LLM Client

产物：

```text
LlmDiagnosisInput DTO
LlmDiagnosisOutput DTO
PromptBuilder
LlmDiagnosisOutputParser
LlmDiagnosisValidator
MockLlmDiagnosisClient
单元测试
```

目标：

```text
不用真实 LLM，也能构造输入、解析固定 JSON、校验证据引用和 fallback。
```

### 阶段 3：DashScope LLM Diagnosis v1

产物：

```text
DashScopeLlmClient
LlmDiagnosisService
POST /api/dev/agent/diagnose/llm
LLM 结果持久化
deterministic fallback
```

目标：

```text
在确定性证据链基础上生成更自然的结构化诊断文本。
```

### 阶段 4：LLM Eval Cases v1

产物：

```text
Eval case JSON
自动化评测脚本
Prompt 输出质量检查
幻觉检测规则
```

目标：

```text
保证 LLM 不编造、不乱改风险等级、不把模拟说成生产事故。
```

### 阶段 5：RAG / Milvus 检索升级

目标：

```text
把 queryApiDocs 从 MySQL keyword 检索升级为 Milvus / Embedding 检索。
```

### 阶段 6：最小前端工作台

目标：

```text
展示 deterministic diagnosis、LLM diagnosis、evidence chain、tool trace、HTML/PDF 导出。
```
