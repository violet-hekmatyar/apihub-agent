# Monitor Report Workbench v1

## 1. 文档定位

本文档定义 Monitor Report Workbench v1 的产品目标、主动触发方式、报告类型、数据来源、调用链路、API 规划、HTML 展示结构和 PDF 导出原则。

边界：

- `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md` 继续负责 Agent deterministic diagnosis、Evidence、tool trace 和已有 report HTML 基线。
- `12_LLM_PROMPT_CONTRACT.md` 继续负责现有 LLM Diagnosis 的 PromptBuilder / Parser / Validator / DashScope 契约。
- `15_ADAPTIVE_PASSIVE_ALERT_MONITOR.md` 继续负责 Passive Monitor 的实时监测、滑动窗口、事件生命周期和表结构。
- 本文只负责把 Passive Monitor、deterministic report、optional LLM report、HTML preview、PDF export 串成一个主动分析工作台契约。

## 2. Report Workbench 目标

Report Workbench v1 的目标是把已经落库的监测事实组织成可读、可审计、可导出的 API 运行分析报告。

报告阅读体验优先：

- 第一屏让阅读者一眼判断状态、风险、分析对象、关键异常指标和是否恢复。
- 尽量使用结构化表格和指标卡片，减少读者计算成本。
- 分析文本短，避免长篇自然语言。
- 指标展示当前区间、对照区间（Reference Window）、变化值和状态。
- 时间线同时展示绝对时间和相对时间。
- Evidence 展示关键指标差异和关联结论。

## 3. 主动触发原则

LLM 分析必须由用户主动触发，不自动实时运行。

允许的主动触发方式：

- 分析某个 `monitorEventId`。
- 分析最近异常。
- 分析最近 24h。
- 分析最近 7d。
- 分析自定义时间范围。

严格边界：

- LLM 不参与同步业务请求返回链路。
- LLM 不参与 Passive Monitor 实时阈值触发链路。
- LLM 只消费已经落库的事实和 deterministic report。
- Workbench 不伪造日志、告警、统计或真实线上影响。

## 4. 支持的报告类型

| reportType | 适用场景 | 说明 |
|---|---|---|
| `INCIDENT_ANALYSIS` | 某个 `monitorEventId`；最近一个异常事件 | 面向已触发监测事件的异常分析报告 |
| `PERIODIC_HEALTH_SUMMARY` | 最近 24h；最近 7d；自定义时间范围 | 即使没有异常，也生成周期巡检型报告 |

`PERIODIC_HEALTH_SUMMARY` 不应被写成事故复盘。它用于说明整体状态、指标摘要、无重大异常或需要持续观察的事项。

## 5. 数据来源

Workbench v1 只消费已落库事实：

| 数据来源 | 用途 |
|---|---|
| `passive_monitor_event` | 监测事件生命周期、风险等级、触发窗口、恢复状态 |
| `passive_alert_snapshot` | 触发窗口、对照区间（Reference Window）、关闭摘要、样本请求 ID 和分布 |
| `alert_event` | 可复用的告警事实，供 Agent 工具查询 |
| `gateway_log` | 可回放的请求事实、状态、业务码、延迟、trace |
| `api_call_stat_hourly` | 小时级 API 指标汇总 |
| `agent_report` | deterministic report 和后续 report metadata |
| `evidence_item` | 报告证据明细 |
| `tool_call_trace` | 工具调用轨迹 |

## 6. 主要调用链路

### 6.1 从监测事件生成报告

```text
monitorEventId
-> passive_monitor_event
-> passive_alert_snapshot
-> linked alert_event
-> derive apiCode / callerAppCode / startTime / endTime / alertId
-> deterministic Agent Diagnosis
-> optional DashScope LLM report generation
-> Workbench response
-> HTML preview
-> optional PDF export script
```

### 6.2 分析最近异常

```text
query latest WARNING monitor event
-> if found, run from-monitor-event flow
-> if not found, return no anomaly or run PERIODIC_HEALTH_SUMMARY when requested
```

### 6.3 分析时间范围

```text
range = 24h / 7d / custom startTime-endTime
-> aggregate passive monitor events, alert events, stats, logs
-> deterministic range summary report
-> optional DashScope LLM report generation
-> HTML preview
```

## 7. API 规划

v1 建议新建 dev-only workbench 入口，但复用已有 Agent report 查询能力。

```http
POST /api/dev/report-workbench/from-monitor-event
POST /api/dev/report-workbench/analyze-latest-anomaly
POST /api/dev/report-workbench/analyze-range
GET  /api/dev/report-workbench/reports/recent
GET  /api/dev/report-workbench/reports/{reportId}
GET  /api/dev/report-workbench/reports/{reportId}/html
```

`reports/{reportId}` 和 `reports/{reportId}/html` 可以委托已有：

```http
GET /api/dev/agent/reports/{reportId}
GET /api/dev/agent/reports/{reportId}/html
```

## 8. HTML 展示结构

HTML 主报告固定模块：

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

主报告不展示状态码分布。如果业务码分布和状态码分布重复，主报告只保留业务码分布。状态码可后续放在详情折叠区或 Evidence 中。

报告头部字段：

| 字段 | 说明 |
|---|---|
| 报告编号 | reportCode 或 reportId |
| 报告类型 | `INCIDENT_ANALYSIS` / `PERIODIC_HEALTH_SUMMARY` |
| 生成时间 | 报告生成时间 |
| 分析对象 | API、调用方或全部核心 API |
| 时间范围 | 当前分析窗口 |
| 模型名称 | 如 `qwen-plus` 或 `deterministic-only` |
| 数据来源 | gateway_log、passive monitor、agent report、evidence 等 |

报告头部不展示“生成方式”。

## 9. PDF 导出原则

v1 推荐：

```text
HTML endpoint + scripts/export-monitor-report-pdf.ps1
```

原则：

- HTML 是权威展示产物。
- PDF 由 Edge / Chrome headless print-to-PDF 生成。
- v1 不引入后端 Java PDF 依赖。
- PDF 脚本只读取 HTML URL 或本地 HTML 文件，不重新计算报告结论。

建议脚本参数：

| 参数 | 必须 | 说明 |
|---|---|---|
| `ReportHtmlUrl` | 是 | 报告 HTML URL |
| `OutputPath` | 是 | PDF 输出路径 |
| `EdgePath` | 否 | 自定义 Edge / Chrome 路径 |
| `WaitSeconds` | 否 | 打印前等待时间 |

## 10. 不做事项

v1 不做：

- 自动实时 LLM 分析。
- 接入同步业务请求链路。
- 接入 Passive Monitor 实时阈值判断链路。
- 服务端直接生成 PDF。
- 新增数据库表作为强依赖。
- 自动通知。
- 自动修复。
- 复杂图表和完整前端工作台。
- RAG / Milvus 事实扩展。

## 11. 后续实现计划

建议顺序：

1. `MonitorReportWorkbenchController`
2. `MonitorReportWorkbenchService`
3. `MonitorReportContextBuilder`
4. `ReportRangeSummaryBuilder`
5. optional LLM report invocation
6. `ReportWorkbenchHtmlRenderer`
7. `scripts/check-monitor-report-workbench-smoke.ps1`
8. `scripts/export-monitor-report-pdf.ps1`
9. `docs/09_VALIDATION_AND_SMOKE_GUIDE.md` 增加验收说明

