# Adaptive Passive Alert Monitor

## Goal

Adaptive Passive Alert Monitor v1 lets `apihub-server` observe Gateway traffic after each request is completed. It does not depend on manual scenario analysis, `scenarioRunId` triggers, Agent, DashScope, LLM, MQ, Redis, or per-request Stats / Alert batch evaluation.

The v1 data path is:

```text
Gateway Invoke completed
-> gateway_log written
-> lightweight GatewayMonitoringSignal
-> in-memory sliding buckets
-> passive monitor rule evaluation
-> passive_monitor_event / passive_alert_snapshot
-> optional alert_event link for later Agent reuse
```

`gateway_log` remains the replayable fact source. Monitor tables store event lifecycle and metric snapshots, not a second full request log.

## Monitoring Signal

`GatewayInvokeService.invoke()` publishes a `GatewayMonitoringSignal` after `insertGatewayLog(...)` succeeds and before the response object is returned. The signal includes gateway log ID, trace ID, request ID, scenario context when present, API/app codes, HTTP status, business/error code, latency, failure source, request time, and masked extra context.

Signal handling is wrapped in `try-catch`. Monitor failures must not change Gateway responses.

## Sliding Window

v1 uses JVM memory buckets keyed by:

```text
apiCode + callerAppCode
```

Default config:

```text
enabled=false
bucketSeconds=5
shortWindowSeconds=30
baselineWindowSeconds=300
contextBeforeSeconds=60
cooldownSeconds=120
minRequestCount=20
minErrorCount=3
highErrorRateThreshold=0.10
highRateLimitThreshold=0.05
high5xxRateThreshold=0.05
authFailureThreshold=0.10
latencyThresholdMs=1000
```

Buckets retain only recent data. Request ID samples and latency samples are bounded to avoid unbounded heap growth.

## Rules

Rules require both enough traffic and a meaningful ratio:

```text
HIGH_ERROR_RATE:
requestCount >= minRequestCount
AND errorCount >= minErrorCount
AND errorRate >= highErrorRateThreshold

HIGH_RATE_LIMIT:
requestCount >= minRequestCount
AND rateLimitCount >= 3
AND rateLimitRate >= highRateLimitThreshold

AUTH_FAILURE_SPIKE:
apiCode = AUTH_LOGIN
AND requestCount >= 15
AND authFailureCount >= 3
AND authFailureRate >= authFailureThreshold

HIGH_5XX_RATE:
requestCount >= minRequestCount
AND 5xxCount >= 3
AND 5xxRate >= high5xxRateThreshold

TRAFFIC_SPIKE:
currentWindowRequestCount >= 30
AND baselineWindowRequestCount > 0
AND currentWindowRequestCount >= baselineWindowRequestCount * 2

HIGH_LATENCY:
requestCount >= minRequestCount
AND p95LatencyMs >= latencyThresholdMs
```

HTTP 504 counts as 5xx and as timeout evidence.

## Lifecycle

External statuses:

```text
FIRING
COOLDOWN
RESOLVED
```

Dedup key:

```text
apiCode + alertType + callerAppCode
```

When a rule first matches, v1 creates one `passive_monitor_event`, writes `TRIGGER_WINDOW` and `CONTEXT_BEFORE` snapshots, and attempts to create or update one related `alert_event`.

When the same dedup key continues to match, v1 updates the existing event and refreshes `last_trigger_time` / `cooldown_until` instead of creating duplicates.

When the rule stops matching, the event enters cooldown after `cooldown_until`. The dev close-check endpoint applies:

```text
quietPeriodSeconds = max(180, eventDurationSeconds * 0.5)
```

If the quiet period has passed, the event becomes `RESOLVED`, `duration_seconds` is set, the related `alert_event` is resolved, and a `CLOSE_SUMMARY` snapshot is written.

## Tables

`passive_monitor_event` records lifecycle, dedup, latest window metrics, context window, cooldown, and optional `alert_event_id`.

`passive_alert_snapshot` records trigger, context, recovery, and close-summary metric snapshots with bounded sample request IDs and distribution JSON.

`alert_event` remains the existing alert fact table for Agent tools. Passive Monitor links to it instead of replacing it.

## APIs

```http
GET  /api/dev/passive-monitor/status
POST /api/dev/passive-monitor/start
POST /api/dev/passive-monitor/stop
POST /api/dev/passive-monitor/config
GET  /api/dev/passive-monitor/events/recent?limit=20
GET  /api/dev/passive-monitor/events?range=24h
GET  /api/dev/passive-monitor/events?range=7d
GET  /api/dev/passive-monitor/events?startTime=...&endTime=...
GET  /api/dev/passive-monitor/events/{monitorEventId}
POST /api/dev/passive-monitor/events/close-check
```

`close-check` is dev-only. Production can later run the same close check from a scheduler.

## High-Concurrency Boundary

The request path still writes `gateway_log` synchronously to preserve facts. Passive Monitor adds only lightweight in-memory counting plus small event writes after a rule fires. It does not do heavy SQL joins in the Gateway path.

Future evolution can add:

```text
GatewayMonitoringSignalPublisher
MQ topic for gateway monitoring signals
async buffer
batch snapshot writer
batch alert_event writer
```

v1 intentionally does not use MQ to keep the local validation chain simple.

## LLM Boundary

This monitor never calls Agent, DashScope, OpenAI, or any LLM. It only creates monitor and alert facts. Later Agent diagnosis can start from `monitorEventId` or the linked `alert_event_id`.
