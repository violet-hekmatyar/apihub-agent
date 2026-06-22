# 11. LLM Agent Readiness: Normal/Abnormal Control and Evidence Contract Closure

## 1. Purpose

This document records the deterministic readiness baseline before adding a real LLM / DashScope Agent. The project already has a factual chain for traffic, logs, stats, alerts, diagnosis reports, evidence items, and report HTML export. The next LLM layer must consume these facts instead of inventing metrics, logs, alerts, or incidents.

This is not an LLM integration document. No DashScope, OpenAI, Function Calling, ReAct, RAG, Milvus, or multi-agent runtime is introduced in this stage.

## 2. Current Deterministic Chain

```text
Scenario Runner or scripted Gateway Invoke traffic
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

The normal baseline control uses scripted Gateway Invoke traffic rather than `LECTURE_REGISTER_PEAK`. It still goes through the same downstream evidence chain.

## 3. Normal / Abnormal Comparison

| Scenario | Traffic model | Expected risk | Actual risk | Alerts | Report path |
|---|---|---|---|---|---|
| Normal Baseline Control | 60s low-traffic NORMAL requests across AUTH_LOGIN, COURSE_TODAY, LECTURE_LIST, CAMPUS_NOTICE, LIBRARY_BORROW | NORMAL | Filled by `check-normal-baseline-control-smoke.ps1` run | Expected 0 HIGH_FAILURE_RATE / HIGH_RATE_LIMIT | Script summary output |
| Daily + Lecture Peak Pilot Test | 60s daily baseline + 120s lecture registration peak overlay + 45s post-peak baseline | WARNING | Last verified as WARNING | HIGH_FAILURE_RATE and HIGH_RATE_LIMIT | `D:\tmp\apihub-agent-pilot-test\output\run-20260622-205620` |

Interpretation:

- Normal control proves the system is not designed to alert merely because a test ran.
- Peak pilot proves controlled high-risk traffic can still trigger alerts through real logs and evaluators.
- If normal control returns WARNING, do not force it to NORMAL. Investigate thresholds, window pollution, mock scenarios, or diagnosis rules.

## 4. Tool Contract Closure

| Tool | Key inputs | Key outputs | Empty result meaning | Prompt-ready fields | Fields the LLM must not invent |
|---|---|---|---|---|---|
| `queryApiInfo` | `apiCode`, `includeRateLimit`, `includeConsumerApps` | API metadata, status, owner, riskLevel, route, optional rate-limit and consumer data | API not found or permission denied is explicit failure; missing optional arrays mean no matching optional data | `apiCode`, `apiName`, `status`, `riskLevel`, `rateLimitRules`, `consumerApps`, evidence title/content | Owner, route, status, permissions, rate-limit rules |
| `queryApiCallStats` | `apiCode`, `startTime`, `endTime` | hourly stats, total/success/fail counts, failRate, latency, rate-limit count, riskLevel | no hourly stats found for the selected range | `totalCallCount`, `totalFailCount`, `failRate`, `totalRateLimitedCount`, `maxP95LatencyMs`, `hourlyStats` | counts, rates, percentiles, hourly bucket data |
| `queryAlertEvents` | `apiCode`, `startTime`, `endTime`, `alertType/eventType`, `severity`, `status`, `limit` | alert rows, severity distribution, open count, extraInfo with window metrics | no alert event found; not itself an error | aggregated alert types, severity, status, actualValue, threshold, windowStart/windowEnd, total/fail/rate-limit counts | alert existence, severity, thresholds, trigger windows |
| `queryGatewayLogs` | `apiCode`, `startTime`, `endTime`, `httpStatus`, `keyword`, `limit` | sampled gateway logs, status distribution, error codes, latency, traceId | no matching logs in the requested filter | summarized samples, status distribution, trace IDs, error code/message, latency | raw log volume outside returned data, hidden request headers |
| `queryRateLimitRule` | `apiCode`, `includeInactive` | ruleId, qps/burst, status, rule metadata | no configured rule found | active rule summary, qps, burst, status, recommended checkpoints | whether Gateway Invoke actually enforced a rule |
| `queryCampusEvents` | `apiCode`, `startTime`, `endTime`, `includeRelatedApis`, `limit` | business events, related APIs, event windows | no business event found; not an error | event type, title, time range, related APIs | business events not returned by the tool |
| `queryApiDocs` | `apiCode`, `keyword`, `docType`, `limit` | MySQL keyword document chunks and metadata | no document chunk matched; not an error | document title, chunk title, content preview, keyword score | vector/RAG matches, docs not returned by MySQL keyword search |

Notes:

- `queryApiCallStats` is hourly-bucket based. Short-window diagnosis may query an expanded hour-aligned range and must say so in evidence.
- `queryAlertEvents` may return repeated 30-second short-window alerts. PromptBuilder should aggregate them for top-level reasoning while preserving individual evidence items.
- `queryRateLimitRule` is diagnostic evidence in the current implementation; it does not prove Gateway Invoke has local rate-limit enforcement.
- `queryApiDocs` is currently MySQL keyword retrieval, not Milvus/RAG.

## 5. Evidence Contract Closure

| evidenceType | Source Tool | Purpose | Prompt fields | Display fields |
|---|---|---|---|---|
| `API_INFO` | `queryApiInfo` | API identity, ownership, status, route, permissions context | title, content, metadata.apiCode/apiName/status/riskLevel | API card, basic facts |
| `API_CALL_STAT` | `queryApiCallStats` or Agent Diagnosis synthesized from stats data | traffic, failure rate, rate-limit count, latency evidence | content, metadata.totalCallCount/failRate/rateLimitCount/p95/p99/hourlyStats | metric summary and evidence chain |
| `ALERT_EVENT` | `queryAlertEvents` | triggered alert windows and thresholds | metadata.alertType/severity/actualValue/threshold/windowStart/windowEnd/totalCount | aggregated alert summary plus detailed evidence rows |
| `GATEWAY_LOG_SAMPLE` | `queryGatewayLogs` | concrete request samples, status, trace, errors | metadata.httpStatus/traceId/requestTime/errorCode/latency | evidence chain and traceability |
| `RATE_LIMIT_RULE` | `queryRateLimitRule` | configured rate-limit policy context | qps, burst, status, rule id, checkpoints | rule section and supporting evidence |
| `CAMPUS_EVENT` | `queryCampusEvents` | business event context and related APIs | event type, title, time range, related APIs | business context section |
| `API_DOC` | `queryApiDocs` | docs, error-code, signature, usage context | document title, chunk title, content preview, keyword | doc evidence section |

Evidence items must remain traceable to a tool result, source id, or persisted table row where available. Presentation aggregation must not delete underlying evidence rows.

## 6. Constraints Before LLM Integration

- LLM output may only be based on tool results and evidence items.
- LLM must not invent metrics, logs, alerts, API docs, owners, or production incidents.
- LLM must not describe simulated development validation as a real production outage.
- LLM must distinguish NORMAL, WARNING, and CRITICAL using provided deterministic evidence.
- If evidence is insufficient, LLM must say evidence is insufficient instead of guessing.
- LLM-generated `summary`, `rootCause`, and `recommendation` must be traceable back to evidence items.
- PromptBuilder should include the exception-source boundary from `10_EXCEPTION_SOURCE_AUDIT.md`.

## 7. Suggested PromptBuilder Input

Future PromptBuilder v1 should include:

- user question
- API code and time window
- deterministic diagnosis summary
- tool result summaries
- top evidence items, grouped by evidenceType
- alert aggregation for repeated short windows
- gateway log samples with trace IDs
- exception source boundary and simulation disclaimer
- normal/abnormal comparison context
- required output schema

## 8. Next Steps

- LLM Prompt Contract v1
- DashScope LLM Diagnosis v1
- LLM Eval Cases v1
- RAG / Milvus retrieval upgrade
- Minimal frontend workbench
