# API-HUB Agent 项目总览与交接入口

## 0. 文档用途

本文是新同学接手 API-HUB Agent 项目时优先阅读的入口文档。它只做交接导览，不替代各个专题设计文档。

## 1. 项目一句话说明

API-HUB Agent 是在 API-HUB 接口平台基础上扩展的智能诊断模块，用于帮助平台管理员基于 API 调用日志、统计指标、告警事件和接口文档分析异常原因。

## 2. 当前项目定位

- 当前项目不是普通 ChatBot。
- 当前项目不是完整生产级 AIOps。
- 当前阶段重点是 API 管理平台 + 场景流量模拟 + 网关日志 + 后续 Agent 诊断证据链。
- 校园 API 是演示场景，不是项目上限。

## 3. 当前已完成成果

- Mock Provider 单 API 模拟。
- 7 个业务 API：`AUTH_LOGIN`、`COURSE_TODAY`、`LECTURE_LIST`、`LECTURE_REGISTER`、`CAMPUS_NOTICE`、`VENUE_RESERVE`、`LIBRARY_BORROW`。
- Gateway Invoke v1。
- `gateway_log` 落库。
- Scenario Runner v1。
- `scenario_run` / `scenario_call_sample` 持久化。
- Apifox 30s 讲座报名高峰报告。
- backend smoke 已通过。
- mock-provider smoke 已通过。
- `05` / `07` / `08` / `09` 文档已整理。

## 4. 当前已跑通链路

```text
Scenario Runner
-> API-HUB Gateway Invoke
-> Mock Provider 单个业务 API
-> gateway_log
-> scenario_run / scenario_call_sample
-> resultSummary / sample-calls
-> Apifox 报告
```

这是当前阶段最重要的可展示成果：一批场景流量可以通过 API-HUB 网关入口进入 Mock Provider，并留下后续统计、告警和 Agent 诊断可使用的事实数据。

## 5. 当前未完成能力

- Stats Aggregator 未完成。
- Alert Evaluator 未完成。
- Agent Run 当前仍是 skeleton。
- 没有完整真实 LLM Agent。
- 没有完整前端工作台。
- 没有自动修复。
- 没有生产级多 Agent。

## 6. 下一步开发重点

下一步只建议做 Stats Aggregator v1。

目标链路：

```text
gateway_log -> api_call_stat_hourly
```

不要在下一轮分散到：

- LLM。
- 前端。
- Alert。
- 多 Agent。
- Milvus/RAG。

## 7. 新同学推荐阅读顺序

1. `docs/00_PROJECT_OVERVIEW.md`
2. `docs/00_DOCUMENT_MAP.md`
3. `docs/06_EXTERNAL_API_CATALOG.md`
4. `docs/05_API_INVOKE_CONTRACT.md`
5. `docs/07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md`
6. `docs/09_VALIDATION_AND_SMOKE_GUIDE.md`
7. `docs/apifox/README.md`
8. `docs/01_DB_SCHEMA.md`
9. `docs/02_TOOL_CONTRACT.md`
10. `docs/08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md`

## 8. 当前验收资产

- backend smoke：`powershell -ExecutionPolicy Bypass -File .\scripts\check-backend-smoke.ps1`
- mock-provider smoke：`powershell -ExecutionPolicy Bypass -File .\scripts\check-mock-provider-smoke.ps1`
- Apifox 成功报告：`docs/apifox/reports/01_scenario_runner_lecture_peak_30s_report.html`
- Apifox failed 报告目录：`docs/apifox/reports/failed/`
- Apifox 资产说明：`docs/apifox/README.md`

## 9. 交接结论

当前适合交接给新同学继续做 Stats Aggregator v1。

不建议接手后立即做完整 Agent、前端或 LLM。原因是当前最缺的不是生成式回答能力，而是从 `gateway_log` 到 `api_call_stat_hourly` 的稳定统计事实层。
