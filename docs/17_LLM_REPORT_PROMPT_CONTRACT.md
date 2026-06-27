# LLM Report Prompt Contract v1

## 1. 文档定位

本文档固定 Monitor Report Workbench v1 的 LLM 报告输入上下文、Prompt 约束、JSON 输出 Schema、固定名称映射、颜色规则、HTML 字段和证据绑定规则。

边界：

- 本文不是已有 `LlmDiagnosisOutput` 的替代文档。
- `12_LLM_PROMPT_CONTRACT.md` 继续定义当前 DashScope LLM Diagnosis 的基础契约。
- 本文面向 Workbench 报告展示层，目标是让 LLM 输出专业、简洁、可审计、可渲染的报告 JSON。

## 2. LLM 报告生成原则

LLM 是 API 网关监测报告生成助手，不是事实来源。

必须遵守：

- 只基于结构化指标、监测事件、告警事件和证据列表生成报告。
- 不编造根因。
- 不输出事故复盘口吻。
- 不输出没有证据支持的服务状态、真实用户影响或外部系统问题。
- 分析重点放在指标变化、规则判定、影响范围、证据和可执行的保守建议操作。
- 输出必须为合法 JSON。
- 诊断摘要最多 2 到 3 段。
- `operationRecommendations` 最多 5 条。
- 每条建议必须绑定 `evidenceIds`。

## 3. 报告类型

| reportType | 用途 |
|---|---|
| `INCIDENT_ANALYSIS` | 某个 `monitorEventId` 或最近一个异常事件 |
| `PERIODIC_HEALTH_SUMMARY` | 最近 24h、最近 7d 或自定义时间范围巡检 |

## 4. AnalysisContextJson 输入结构

```json
{
  "reportType": "INCIDENT_ANALYSIS",
  "analysisTrigger": {
    "triggerType": "MONITOR_EVENT",
    "monitorEventId": "pm_demo_001",
    "requestedAt": "2026-06-27 20:30:00"
  },
  "analysisScope": {
    "apiCode": "LECTURE_REGISTER",
    "apiName": "讲座报名接口",
    "callerAppCode": "lecture-miniapp",
    "callerAppName": "讲座小程序",
    "startTime": "2026-06-27 20:00:00",
    "endTime": "2026-06-27 20:05:00",
    "referenceWindowStartTime": "2026-06-27 19:55:00",
    "referenceWindowEndTime": "2026-06-27 20:00:00"
  },
  "deterministicReport": {
    "reportId": 16051,
    "riskLevel": "WARNING",
    "summary": "LECTURE_REGISTER shows rate-limit pressure in the selected window."
  },
  "monitorEvent": {
    "alertType": "HIGH_RATE_LIMIT",
    "eventStatus": "COOLDOWN",
    "firstTriggerTime": "2026-06-27 20:02:00",
    "lastTriggerTime": "2026-06-27 20:04:30",
    "resolvedTime": null
  },
  "metricCheckupFacts": [],
  "monitorRuleFacts": [],
  "businessCodeFacts": [],
  "timelineFacts": [],
  "evidenceList": [],
  "constraints": {
    "environment": "development_simulation",
    "dataBoundaryNote": "说明：本报告基于本地模拟场景与开发环境数据生成，不代表真实线上用户影响。"
  }
}
```

## 5. LLM System Prompt 模板

```text
你是 API 网关监测报告生成助手。
你的任务是基于结构化指标、监测事件、告警事件和证据列表生成专业、简洁、可审计的 API 运行分析报告。
不要编造根因。
不要输出事故复盘口吻。
不要输出没有证据支持的服务状态、真实用户影响或外部系统问题。
分析重点应放在指标变化、规则判定、影响范围、证据和可执行的保守建议操作。
输出必须为合法 JSON。
```

## 6. LLM User Prompt 模板

```text
请按给定 JSON Schema 输出。
诊断摘要不要超过 3 段。
operationRecommendations 最多 5 条。
每条 operationRecommendation 必须有 evidenceIds。
如果没有 RAG 或知识库引用，knowledgeRefs 可以为空，但建议操作必须保守。
不要生成 Root Cause Hypotheses。
不要使用“一句话结论”等非专业标题。
不要使用“命中 / 未命中”作为展示词。
偏差必须使用绝对差值。

AnalysisContextJson:
{{analysisContextJson}}
```

## 7. LLM 输出 JSON Schema

核心字段必须包含：

- `reportType`
- `reportHeader`
- `displayStatus`
- `analysisScope`
- `metricCheckup`
- `monitorRuleAssessments`
- `businessCodeDistribution`
- `eventTimeline`
- `diagnosisSummary`
- `operationRecommendations`
- `evidenceList`
- `dataBoundaryNote`

完整示例：

```json
{
  "reportType": "INCIDENT_ANALYSIS",
  "reportHeader": {
    "reportCode": "RPT-MONITOR-20260627-001",
    "reportTypeLabel": "异常分析报告",
    "generatedAt": "2026-06-27 20:30:00",
    "analysisTarget": "讲座报名接口（LECTURE_REGISTER），调用方：讲座小程序（lecture-miniapp）",
    "timeRange": "2026-06-27 20:00:00 ~ 2026-06-27 20:05:00",
    "modelName": "qwen-plus",
    "dataSources": ["passive_monitor_event", "passive_alert_snapshot", "alert_event", "gateway_log", "agent_report", "evidence_item"]
  },
  "displayStatus": {
    "status": "WARNING",
    "statusLabel": "警告（WARNING）",
    "colorLevel": "YELLOW",
    "statusSummary": "当前窗口出现限流集中，需要关注。"
  },
  "analysisScope": {
    "apiCode": "LECTURE_REGISTER",
    "apiName": "讲座报名接口",
    "callerAppCode": "lecture-miniapp",
    "callerAppName": "讲座小程序",
    "currentWindow": "2026-06-27 20:00:00 ~ 2026-06-27 20:05:00",
    "referenceWindow": "2026-06-27 19:55:00 ~ 2026-06-27 20:00:00"
  },
  "metricCheckup": [
    {
      "metricName": "429 数量",
      "currentWindowValue": "28",
      "referenceWindowValue": "0",
      "changeValue": "+28",
      "assessmentStatus": "WARNING",
      "displayStatus": "警告（WARNING）",
      "colorLevel": "YELLOW",
      "evidenceIds": ["SNAP-001"]
    }
  ],
  "monitorRuleAssessments": [
    {
      "ruleName": "HIGH_RATE_LIMIT",
      "ruleDisplayName": "限流比例（HIGH_RATE_LIMIT）",
      "metricName": "限流比例",
      "currentValue": "5.80%",
      "thresholdValue": "5.00%",
      "deviationValue": "+0.80pp",
      "deviationType": "ABSOLUTE_DELTA",
      "assessmentStatus": "WARNING",
      "assessmentLabel": "限流集中（WARNING）",
      "colorLevel": "YELLOW",
      "triggered": true,
      "evidenceIds": ["SNAP-001"]
    }
  ],
  "businessCodeDistribution": [
    {
      "businessCode": "RATE_LIMITED",
      "businessCodeLabel": "限流（RATE_LIMITED）",
      "description": "达到限流策略",
      "count": 28,
      "ratio": "3.81%",
      "assessmentStatus": "WARNING",
      "displayStatus": "警告（WARNING）",
      "evidenceIds": ["LOG-001"]
    }
  ],
  "eventTimeline": [
    {
      "absoluteTime": "2026-06-27 20:00:00",
      "relativeTime": "T+0m",
      "phase": "上下文开始",
      "description": "对照区间结束后进入当前分析窗口。"
    },
    {
      "absoluteTime": "2026-06-27 20:02:00",
      "relativeTime": "T+2m",
      "phase": "首次触发",
      "description": "Passive Monitor 首次记录 HIGH_RATE_LIMIT。"
    }
  ],
  "diagnosisSummary": [
    "当前分析窗口内，LECTURE_REGISTER 的限流比例达到 5.80%，高于 5.00% 阈值，偏差为 +0.80pp。",
    "证据显示风险集中在讲座报名接口和 lecture-miniapp 调用方，当前只能确认该窗口内的限流集中现象，不能据此断言真实线上用户影响。"
  ],
  "operationRecommendations": [
    {
      "priority": "P1",
      "basisMetricOrEvidence": "限流比例 5.80%，阈值 5.00%，偏差 +0.80pp",
      "operationRecommendation": "复核讲座报名高峰期间的排队、重试提示和前端重复提交控制。",
      "evidenceIds": ["SNAP-001", "LOG-001"],
      "knowledgeRefs": []
    }
  ],
  "evidenceList": [
    {
      "evidenceId": "SNAP-001",
      "evidenceType": "TRIGGER_WINDOW",
      "evidenceTypeLabel": "触发窗口（TRIGGER_WINDOW）",
      "source": "passive_alert_snapshot",
      "keyMetric": "限流比例",
      "currentValue": "5.80%",
      "referenceOrThreshold": "阈值 5.00%",
      "deviationValue": "+0.80pp",
      "assessmentLabel": "限流集中（WARNING）",
      "relatedConclusion": "LECTURE_REGISTER 出现限流集中"
    }
  ],
  "dataBoundaryNote": "说明：本报告基于本地模拟场景与开发环境数据生成，不代表真实线上用户影响。"
}
```

`monitorRuleAssessments` 不使用 `"matched": true`。内部如必须保留布尔值，只允许使用 `"triggered": true`，HTML 不展示 `triggered`。

## 8. 固定状态映射

| status | 展示文案 | 颜色 |
|---|---|---|
| `NORMAL` | 正常（NORMAL） | 绿色 |
| `WATCH` | 关注（WATCH） | 蓝色 |
| `WARNING` | 警告（WARNING） | 黄色 |
| `UNKNOWN` | 未知（UNKNOWN） | 灰色 |

本阶段不使用 `CRITICAL`。所有极度异常也先归入 `WARNING`。

## 9. 固定业务码映射

| businessCode | 展示文案 | 说明 |
|---|---|---|
| `OK` | 正常（OK） | 正常返回 |
| `RATE_LIMITED` | 限流（RATE_LIMITED） | 达到限流策略 |
| `DUPLICATE_REQUEST` | 重复提交（DUPLICATE_REQUEST） | 请求重复提交 |
| `SIGNATURE_MISMATCH` | 签名异常（SIGNATURE_MISMATCH） | 签名不匹配 |
| `TOKEN_EXPIRED` | 登录态过期（TOKEN_EXPIRED） | token 或登录态过期 |
| `SOLD_OUT` | 名额已满（SOLD_OUT） | 业务资源售罄 |
| `DOWNSTREAM_TIMEOUT` | 下游超时（DOWNSTREAM_TIMEOUT） | 下游依赖超时 |
| `COURSE_SYSTEM_TIMEOUT` | 课表系统超时（COURSE_SYSTEM_TIMEOUT） | 课表系统响应超时 |
| `UPSTREAM_INTERNAL_ERROR` | 上游内部错误（UPSTREAM_INTERNAL_ERROR） | 上游返回内部错误 |

没有映射时直接展示英文原值。

## 10. 固定监测规则映射

| ruleName | NORMAL | WATCH | WARNING |
|---|---|---|---|
| `HIGH_RATE_LIMIT` | 正常（NORMAL） | 限流关注（WATCH） | 限流集中（WARNING） |
| `HIGH_ERROR_RATE` | 正常（NORMAL） | 错误率关注（WATCH） | 错误率升高（WARNING） |
| `TRAFFIC_SPIKE` | 正常（NORMAL） | 流量关注（WATCH） | 流量突增（WARNING） |
| `HIGH_5XX_RATE` | 正常（NORMAL） | 服务端错误关注（WATCH） | 服务端错误升高（WARNING） |
| `AUTH_FAILURE_SPIKE` | 正常（NORMAL） | 认证失败关注（WATCH） | 认证失败集中（WARNING） |
| `HIGH_LATENCY` | 正常（NORMAL） | 延迟关注（WATCH） | 延迟升高（WARNING） |

监测规则判定表字段：

| 字段 | 说明 |
|---|---|
| 监测规则 | rule display name |
| 当前值 | 当前窗口指标 |
| 判定阈值 | threshold |
| 偏差 | 绝对差值 |
| 监测结论 | 固定映射文案 |

不要使用“阈值命中”“命中”“未命中”作为展示词。

偏差必须展示绝对差值：

| 示例 | 偏差 |
|---|---|
| `5.80% vs 5.00%` | `+0.80pp` |
| `2.30x vs 2.00x` | `+0.30x` |
| `420ms vs 1000ms` | `-580ms` |

## 11. 颜色规则

| colorLevel | 颜色 |
|---|---|
| `GREEN` | 绿色 |
| `BLUE` | 蓝色 |
| `YELLOW` | 黄色 |
| `GRAY` | 灰色 |

不使用红色，不使用橙色，不使用 `CRITICAL`。

## 12. HTML 展示模板

固定模块顺序：

1. 报告头部
2. 状态概览
3. 体检式核心指标
4. 监测规则判定
5. 业务码分布
6. 事件时间线
7. 诊断摘要
8. 建议操作
9. 证据明细
10. 数据说明

不使用展示词：

- 一句话结论
- Root Cause Hypotheses
- 命中
- 未命中
- Postmortem
- Incident Review
- 生成方式

建议使用展示词：

- 报告头部
- 状态概览
- 体检式核心指标
- 监测规则判定
- 业务码分布
- 事件时间线
- 诊断摘要
- 建议操作
- 证据明细
- 数据说明

## 13. 异常分析报告示例结构

```text
报告头部
-> 分析对象：讲座报名接口（LECTURE_REGISTER），调用方：讲座小程序（lecture-miniapp）
-> 状态概览：警告（WARNING）
-> 体检式核心指标：429 数量、限流比例、错误率、P95 延迟
-> 监测规则判定：HIGH_RATE_LIMIT / HIGH_ERROR_RATE
-> 业务码分布：RATE_LIMITED、DUPLICATE_REQUEST、OK
-> 事件时间线：上下文开始、首次触发、最后触发、恢复确认、报告生成
-> 诊断摘要：2 到 3 段
-> 建议操作：最多 5 条，绑定 evidenceIds
-> 证据明细
-> 数据说明
```

## 14. 日常巡检报告示例结构

```text
报告头部
-> 分析对象：全部核心 API
-> 状态概览：正常（NORMAL）或关注（WATCH）
-> 体检式核心指标：请求总数、成功数、失败数、错误率、P95/P99 延迟
-> 监测规则判定：按规则展示当前值、阈值、偏差和结论
-> 业务码分布：OK 为主，异常业务码如有则列出
-> 事件时间线：窗口开始、主要观察点、报告生成
-> 诊断摘要：说明整体状态和需要观察的指标
-> 建议操作：以持续观察、保留对照区间、补充验证为主
-> 证据明细
-> 数据说明
```

## 15. Evidence 展示规范

标题固定为：

```text
证据明细
```

字段：

| 字段 | 说明 |
|---|---|
| Evidence ID | 稳定证据 ID |
| 类型 | evidence type |
| 来源 | 表、工具或事实来源 |
| 关键指标 / 差异 | 必须包含具体指标差异 |
| 关联结论 | 该证据支持的结论 |

Evidence 必须包含具体指标差异，例如：

- 限流比例 5.80%，阈值 5.00%，偏差 +0.80pp。
- 当前请求量为对照区间 2.30x，阈值 2.00x，偏差 +0.30x。
- 错误率 5.31%，阈值 10.00%，偏差 -4.69pp。
- 样本请求返回 限流（RATE_LIMITED）。

## 16. 建议操作规范

标题固定为：

```text
建议操作
```

字段：

| 字段 | 说明 |
|---|---|
| 优先级 | P1 / P2 / P3 |
| 依据指标 / 证据 | 具体指标或 Evidence ID |
| 建议操作 | 保守、可执行的操作 |
| 关联证据 | evidenceIds |

不要使用字段：

- 适用条件
- 验证方式

建议操作必须绑定 `evidenceIds`。没有 RAG / `knowledgeRefs` 时，建议必须保守。不得建议重启、扩容、修改数据库等没有证据支撑的操作。后续接入 RAG 后，可以追加 `knowledgeRefs`。

## 17. 数据说明

页脚只保留一句：

```text
说明：本报告基于本地模拟场景与开发环境数据生成，不代表真实线上用户影响。
```

不要在报告各处重复。

## 18. 校验规则

Parser / Validator 至少校验：

| 校验项 | 要求 |
|---|---|
| JSON 合法 | 输出必须是合法 JSON |
| Schema 完整 | 必须包含核心字段 |
| reportType 合法 | 只能是 `INCIDENT_ANALYSIS` 或 `PERIODIC_HEALTH_SUMMARY` |
| status 合法 | 只能是 `NORMAL/WATCH/WARNING/UNKNOWN` |
| colorLevel 合法 | 只能是 `GREEN/BLUE/YELLOW/GRAY` |
| 不使用 CRITICAL | 本阶段不接受 `CRITICAL` |
| evidenceIds 存在 | 建议和结论引用的 evidenceIds 必须来自输入 |
| 建议数量 | `operationRecommendations` 最多 5 条 |
| 摘要长度 | `diagnosisSummary` 最多 3 段 |
| 偏差格式 | 必须为绝对差值，如 `+0.80pp`、`+0.30x`、`-580ms` |
| 禁用展示词 | 不得输出“一句话结论”“Root Cause Hypotheses”“命中”“未命中”等标题或展示词 |
| 数据边界 | 必须包含固定 `dataBoundaryNote` |

## 19. 后续和 RAG 的关系

RAG 后续只增强建议操作和知识依据，不改变已落库事实。

接入 RAG 后允许新增：

```json
{
  "knowledgeRefs": [
    {
      "docId": "DOC_RATE_LIMIT_GUIDE",
      "title": "限流策略说明",
      "chunkId": "chunk-001"
    }
  ]
}
```

RAG 不得让 LLM 覆盖：

## 20. Implementation Status

Monitor Report Workbench v1 currently builds and validates the Workbench-facing JSON shape in backend deterministic code.

Guaranteed fields in `htmlRenderableJson`:

- `reportType`
- `reportHeader`
- `displayStatus`
- `analysisScope`
- `metricCheckup`
- `monitorRuleAssessments`
- `businessCodeDistribution`
- `eventTimeline`
- `diagnosisSummary`
- `operationRecommendations`
- `evidenceList`
- `dataBoundaryNote`

Validator coverage:

- Ensures the required top-level fields are present.
- Normalizes `CRITICAL` to `WARNING` for display.
- Restricts display status to `NORMAL`, `WATCH`, `WARNING`, `UNKNOWN`.
- Restricts color level to `GREEN`, `BLUE`, `YELLOW`, `GRAY`.
- Records validation warnings in `agent_report.extra_info.validationWarnings`.

Current LLM integration reuses the existing DashScope diagnosis orchestrator. If DashScope is unavailable or validation fails, Workbench keeps deterministic `htmlRenderableJson` and returns `llmStatus=FALLBACK`.

- gateway_log 事实。
- passive monitor 事件。
- alert_event 事实。
- deterministic risk / display status。
- evidenceIds 引用关系。
