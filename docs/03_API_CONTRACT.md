# 03_API_CONTRACT
## 0. 文档定位
本文档是 API-HUB Agent 第一版前后端接口契约文档。

本文档用于约束：

1. 前端页面调用哪些后端接口；
2. 每个接口的路径、方法、请求参数、响应结构；
3. Agent 流式执行的 SSE 事件格式；
4. Trace / Evidence / Report 如何查询和展示；
5. RAG 文档如何上传、查询和检索；
6. 开发调试接口的边界。

本文档不负责定义：

| 内容 | 归属文档 |
| --- | --- |
| 数据库表结构 | `01_DB_SCHEMA.md` |
| Agent Tool 输入输出 | `02_TOOL_CONTRACT.md` |
| GPT / Codex 开发规范 | `04_VIBE_CODING_RULES.md` |
| Java Controller / Service 具体类名 | 后端实现阶段确定 |
| 前端 UI 样式 | 前端页面实现阶段确定 |


核心边界：

```plain
01_DB_SCHEMA.md 负责数据事实；
02_TOOL_CONTRACT.md 负责 Agent Tool 能力；
03_API_CONTRACT.md 负责前后端交互；
04_VIBE_CODING_RULES.md 负责 AI 辅助开发规范。
```

---

## 1. API 设计原则
### 1.1 前端不直接调用 Agent Tool
前端不直接调用：

```plain
queryApiInfo
queryApiCallStats
queryGatewayLogs
queryApiDocs
```

这些属于 Agent 内部 Tool。

前端调用的是后端 HTTP / SSE 接口，例如：

```plain
/api/agent/run/stream
/api/sessions/{sessionCode}/trace
/api/sessions/{sessionCode}/evidence
/api/reports/{reportCode}
```

---

### 1.2 Agent 入口统一
第一版只设计一个 Agent 执行入口：

```plain
POST /api/agent/run/stream
```

通过 `taskType` 区分任务类型：

```plain
CHAT
INSPECTION
DIAGNOSIS
WARNING
WEEKLY_REVIEW
```

不单独拆成多个 Agent Controller 接口，避免重复实现和前端复杂化。

---

### 1.3 普通查询走 HTTP，Agent 分析走流式返回
| 场景 | 协议 |
| --- | --- |
| Dashboard 数据 | HTTP |
| API 列表 / 详情 | HTTP |
| Report 查询 | HTTP |
| Trace / Evidence 查询 | HTTP |
| RAG 文档上传 | HTTP multipart |
| RAG 检索测试 | HTTP |
| Agent 执行过程 | SSE / fetch stream |


---

### 1.4 第一版只暴露低风险接口
第一版不提供以下接口：

```plain
修改限流配置
下线 API
删除网关日志
删除报告
重置应用密钥
自动通知调用方
自动执行修复脚本
```

普通用户只允许修改自己提供 API 的说明类信息，不允许修改平台治理配置。

---

## 2. 通用请求与响应规范
## 2.1 Base URL
本地开发环境：

```plain
http://localhost:8080/api
```

Nginx 代理后：

```plain
/api/**
```

前端统一通过相对路径调用：

```plain
/api/dashboard/overview
/api/agent/run/stream
/api/reports
```

---

## 2.2 通用请求头
| Header | 必须 | 说明 |
| --- | --- | --- |
| `Content-Type` | 否 | JSON 请求使用 `application/json` |
| `X-Demo-User-Id` | 否 | 第一版模拟当前用户，可选 |
| `X-Trace-Id` | 否 | 前端生成或后端生成请求追踪 ID |
| `X-Request-Id` | 否 | 单次 HTTP 请求 ID，用于接口日志定位 |
| `Idempotency-Key` | 否 | 幂等键，建议在保存报告、重试 Agent 请求时传入 |


说明：

```plain
第一版不实现完整登录和 JWT。
开发阶段可以通过 X-Demo-User-Id 或用户切换接口模拟当前用户。
```

---

## 2.3 统一响应结构
普通 HTTP API 统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "traceId": "trace_req_001"
}
```

字段说明：

| 字段 | 类型 | 必须 | 说明 |
| --- | --- | --- | --- |
| `code` | `number` | 是 | 业务状态码 |
| `message` | `string` | 是 | 响应说明 |
| `data` | `object` | 是 | 业务数据 |
| `traceId` | `string` | 否 | 请求追踪 ID |


---

## 2.4 统一分页结构
列表接口统一返回：

```json
{
  "items": [],
  "total": 100,
  "pageNo": 1,
  "pageSize": 20
}
```

分页请求参数：

| 参数 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `pageNo` | `number` | 否 | `1` | 页码 |
| `pageSize` | `number` | 否 | `20` | 每页数量，最大 100 |


---

## 2.5 时间格式
统一使用：

```plain
yyyy-MM-dd HH:mm:ss
```

示例：

```plain
2026-06-19 10:00:00
```

---

## 2.6 通用错误码
| code | message | 说明 |
| --- | --- | --- |
| `200` | `success` | 成功 |
| `400` | `invalid argument` | 参数错误 |
| `401` | `unauthorized` | 未登录或模拟用户缺失 |
| `403` | `permission denied` | 无权限 |
| `404` | `not found` | 资源不存在 |
| `500` | `internal error` | 服务异常 |


---

# 3. 用户与角色模拟接口
第一版不做完整登录，只做模拟用户切换。

## 3.1 查询当前用户
```plain
GET /api/users/current
```

### 响应 data
```json
{
  "id": 1,
  "username": "admin_apihub",
  "displayName": "API 平台管理员",
  "userType": "MANAGER",
  "orgName": "信息化中心",
  "status": "ACTIVE"
}
```

---

## 3.2 查询可切换用户列表
```plain
GET /api/users
```

### 响应 data
```json
{
  "items": [
    {
      "id": 1,
      "username": "admin_apihub",
      "displayName": "API 平台管理员",
      "userType": "MANAGER",
      "orgName": "信息化中心"
    },
    {
      "id": 4,
      "username": "consumer_course_helper",
      "displayName": "课程助手负责人",
      "userType": "USER",
      "orgName": "课程助手团队"
    }
  ],
  "total": 2,
  "pageNo": 1,
  "pageSize": 20
}
```

---

## 3.3 切换当前模拟用户
```plain
POST /api/users/switch
```

### 请求体
```json
{
  "userId": 1
}
```

### 响应 data
```json
{
  "id": 1,
  "username": "admin_apihub",
  "displayName": "API 平台管理员",
  "userType": "MANAGER",
  "orgName": "信息化中心"
}
```

---

# 4. Dashboard 接口
## 4.1 今日总览
```plain
GET /api/dashboard/overview
```

### 查询参数
| 参数 | 类型 | 必须 | 说明 |
| --- | --- | --- | --- |
| `date` | `string` | 否 | 日期，例如 `2026-06-19`<br/>，默认今天 |


### 响应 data
```json
{
  "apiCount": 12,
  "activeApiCount": 10,
  "totalCallCount": 235680,
  "failRate": 0.0214,
  "avgLatencyMs": 86,
  "p95LatencyMs": 260,
  "alertCount": 3,
  "highRiskApiCount": 2
}
```

---

## 4.2 风险 API 列表
```plain
GET /api/dashboard/risk-apis
```

### 响应 data
```json
{
  "items": [
    {
      "apiCode": "AUTH_LOGIN",
      "apiName": "学校账户授权登录",
      "riskLevel": "HIGH",
      "failRate": 0.0767,
      "p95LatencyMs": 280,
      "alertCount": 1,
      "mainRisk": "403 错误升高"
    }
  ],
  "total": 1,
  "pageNo": 1,
  "pageSize": 20
}
```

---

## 4.3 最近事件
```plain
GET /api/dashboard/recent-events
```

### 响应 data
```json
{
  "items": [
    {
      "eventType": "ALERT",
      "title": "统一登录 API 403 错误升高",
      "severity": "HIGH",
      "startTime": "2026-06-19 10:00:00",
      "status": "ACTIVE"
    },
    {
      "eventType": "CAMPUS_EVENT",
      "title": "人工智能前沿讲座报名开放",
      "severity": "MEDIUM",
      "startTime": "2026-06-20 12:00:00",
      "status": "ACTIVE"
    }
  ]
}
```

---

## 4.4 最近报告
```plain
GET /api/dashboard/latest-reports
```

### 响应 data
```json
{
  "items": [
    {
      "reportCode": "RPT_AUTH_403_20260619",
      "reportType": "DIAGNOSIS",
      "title": "统一登录 API 403 异常诊断报告",
      "riskLevel": "HIGH",
      "createdAt": "2026-06-19 11:05:18"
    }
  ]
}
```

---

# 5. API 管理接口
## 5.1 API 列表
```plain
GET /api/apis
```

### 查询参数
| 参数 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `keyword` | `string` | 否 | `null` | API 名称或编码 |
| `apiType` | `string` | 否 | `null` | API 类型 |
| `onlineStatus` | `string` | 否 | `null` | 在线状态 |
| `pageNo` | `number` | 否 | `1` | 页码 |
| `pageSize` | `number` | 否 | `20` | 页大小 |


### 响应 data
```json
{
  "items": [
    {
      "apiCode": "AUTH_LOGIN",
      "apiName": "学校账户授权登录",
      "apiType": "AUTH",
      "path": "/api/auth/login",
      "method": "POST",
      "ownerTeam": "统一身份认证中心",
      "riskLevel": "HIGH",
      "onlineStatus": "ONLINE",
      "status": "ACTIVE"
    }
  ],
  "total": 1,
  "pageNo": 1,
  "pageSize": 20
}
```

---

## 5.2 API 详情
```plain
GET /api/apis/{apiCode}
```

### 响应 data
```json
{
  "apiCode": "AUTH_LOGIN",
  "apiName": "学校账户授权登录",
  "apiType": "AUTH",
  "path": "/api/auth/login",
  "method": "POST",
  "description": "用于校内应用进行统一身份认证登录",
  "providerUserId": 2,
  "ownerTeam": "统一身份认证中心",
  "riskLevel": "HIGH",
  "onlineStatus": "ONLINE",
  "status": "ACTIVE",
  "extraInfo": {
    "tags": ["核心接口", "高频调用"],
    "docUrl": "/docs/auth-login"
  }
}
```

---

## 5.3 API 调用统计
```plain
GET /api/apis/{apiCode}/stats
```

### 查询参数
| 参数 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `startTime` | `string` | 是 | 无 | 开始时间 |
| `endTime` | `string` | 是 | 无 | 结束时间 |
| `granularity` | `string` | 否 | `HOURLY` | 第一版仅支持小时级 |


### 响应 data
```json
{
  "apiCode": "AUTH_LOGIN",
  "totalCount": 18520,
  "successCount": 17100,
  "failCount": 1420,
  "failRate": 0.0767,
  "avgLatencyMs": 96,
  "p95LatencyMs": 280,
  "p99LatencyMs": 520,
  "rateLimitCount": 0,
  "timeSeries": [
    {
      "statTime": "2026-06-19 10:00:00",
      "totalCount": 18520,
      "failCount": 1420,
      "failRate": 0.0767,
      "p95LatencyMs": 280
    }
  ]
}
```

---

## 5.4 API 日志样例
```plain
GET /api/apis/{apiCode}/logs
```

### 查询参数
| 参数 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `startTime` | `string` | 是 | 无 | 开始时间 |
| `endTime` | `string` | 是 | 无 | 结束时间 |
| `httpStatus` | `number` | 否 | `null` | HTTP 状态码 |
| `errorCode` | `string` | 否 | `null` | 错误码 |
| `appCode` | `string` | 否 | `null` | 应用编码 |
| `limit` | `number` | 否 | `20` | 最大 100 |


### 响应 data
```json
{
  "matchedCount": 20,
  "logs": [
    {
      "traceId": "tr_20260619_auth_001",
      "apiCode": "AUTH_LOGIN",
      "appCode": "COURSE_HELPER",
      "httpStatus": 403,
      "errorCode": "SIGNATURE_INVALID",
      "errorMessage": "签名校验失败",
      "latencyMs": 88,
      "requestTime": "2026-06-19 10:12:30"
    }
  ]
}
```

---

## 5.5 API 限流规则
```plain
GET /api/apis/{apiCode}/rules
```

### 响应 data
```json
{
  "apiCode": "AUTH_LOGIN",
  "rules": [
    {
      "ruleName": "统一登录默认限流规则",
      "qpsLimit": 300,
      "burstLimit": 600,
      "degradeEnabled": true,
      "fallbackMessage": "统一登录服务繁忙，请稍后重试",
      "status": "ACTIVE"
    }
  ]
}
```

---

## 5.6 修改自己提供 API 的说明
```plain
PATCH /api/apis/{apiCode}/description
```

### 权限说明
仅允许：

```plain
当前用户为 USER
且 api_endpoint.provider_user_id = 当前用户 id
```

允许修改：

```plain
description
extraInfo 中的文档链接、标签、示例说明
```

不允许修改：

```plain
限流规则
上线状态
风险等级
API 路径
HTTP 方法
提供者归属
```

### 请求体
```json
{
  "description": "用于校内应用进行统一身份认证登录，调用前需要完成签名校验。",
  "extraInfo": {
    "tags": ["核心接口", "统一认证"],
    "docUrl": "/docs/auth-login"
  }
}
```

### 响应 data
```json
{
  "apiCode": "AUTH_LOGIN",
  "description": "用于校内应用进行统一身份认证登录，调用前需要完成签名校验。",
  "updatedAt": "2026-06-19 12:00:00"
}
```

---

# 6. Agent SSE 接口
## 6.1 执行 Agent 任务
```plain
POST /api/agent/run/stream
```

### 说明
第一版使用 `fetch` + ReadableStream 读取流式返回，不强依赖浏览器原生 `EventSource`，因为原生 `EventSource` 不支持 POST 请求体。

响应格式采用 SSE 风格：

```plain
event: message
data: {"type":"content_delta","payload":{}}
```

### 响应头
后端 SSE 响应建议统一设置：

```plain
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache
Connection: keep-alive
X-Accel-Buffering: no
```

如果经过 Nginx 代理，必须关闭代理缓冲，否则前端可能无法实时收到 token。

### 幂等与重试
当前第一版不做自动断点续跑，但前端重试同一 Agent 任务时建议传入 `Idempotency-Key`，避免 `saveReport=true` 时重复生成报告。

---

## 6.2 Agent 任务类型
| taskType | 说明 |
| --- | --- |
| `CHAT` | 普通问答 |
| `INSPECTION` | 今日巡检 |
| `DIAGNOSIS` | 异常诊断 |
| `WARNING` | 事件预警 |
| `WEEKLY_REVIEW` | 周报复盘 |


---

## 6.3 请求体
```json
{
  "requestId": "req_auth_403_20260619_001",
  "taskType": "DIAGNOSIS",
  "question": "为什么统一登录 API 今天 403 变多了？",
  "targetApiCode": "AUTH_LOGIN",
  "targetAppCode": "COURSE_HELPER",
  "timeRange": {
    "startTime": "2026-06-19 10:00:00",
    "endTime": "2026-06-19 11:00:00"
  },
  "options": {
    "saveReport": true,
    "includeTrace": true,
    "includeEvidence": true
  }
}
```

### 字段说明
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `requestId` | `string` | 否 | 后端生成 | 请求 ID，便于日志定位和幂等处理 |
| `taskType` | `string` | 是 | 无 | 任务类型 |
| `question` | `string` | 是 | 无 | 用户问题 |
| `targetApiCode` | `string` | 否 | `null` | 目标 API |
| `targetAppCode` | `string` | 否 | `null` | 目标应用 |
| `timeRange.startTime` | `string` | 否 | 由任务类型决定 | 开始时间 |
| `timeRange.endTime` | `string` | 否 | 由任务类型决定 | 结束时间 |
| `options.saveReport` | `boolean` | 否 | `true` | 是否保存报告 |
| `options.includeTrace` | `boolean` | 否 | `true` | 是否返回 Trace 事件 |
| `options.includeEvidence` | `boolean` | 否 | `true` | 是否返回 Evidence 事件 |


---

## 6.4 SSE 统一事件结构
所有 SSE 数据统一使用：

```json
{
  "eventId": "evt_trace_auth_403_0003",
  "seq": 3,
  "type": "tool_start",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "timestamp": "2026-06-19 11:05:01",
  "payload": {}
}
```

字段说明：

| 字段 | 必须 | 说明 |
| --- | --- | --- |
| `eventId` | 是 | 事件 ID，建议由 `traceId + seq` 生成 |
| `seq` | 是 | 事件序号，从 1 递增，用于前端排序和后续断线恢复 |
| `type` | 是 | 事件类型 |
| `sessionCode` | 是 | 会话编码 |
| `traceId` | 是 | Agent 工作流 traceId |
| `timestamp` | 是 | 事件时间 |
| `payload` | 是 | 事件载荷 |


---

## 6.5 SSE 事件类型
| type | 说明 |
| --- | --- |
| `session_start` | 会话开始 |
| `content_delta` | 模型文本增量 |
| `tool_start` | Tool 开始调用 |
| `tool_result` | Tool 调用完成 |
| `evidence_item` | 新增证据 |
| `report_saved` | 报告保存完成 |
| `error` | 执行异常 |
| `done` | 任务完成 |


---

## 6.6 `session_start`
```json
{
  "eventId": "evt_trace_auth_403_0001",
  "seq": 1,
  "type": "session_start",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "timestamp": "2026-06-19 11:05:00",
  "payload": {
    "taskType": "DIAGNOSIS",
    "title": "统一登录 API 403 异常诊断"
  }
}
```

---

## 6.7 `content_delta`
```json
{
  "eventId": "evt_trace_auth_403_0002",
  "seq": 2,
  "type": "content_delta",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "timestamp": "2026-06-19 11:05:02",
  "payload": {
    "delta": "我将先查询统一登录 API 在该时间段的调用统计，判断 403 是否明显升高。"
  }
}
```

---

## 6.8 `tool_start`
```json
{
  "eventId": "evt_trace_auth_403_0003",
  "seq": 3,
  "type": "tool_start",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "timestamp": "2026-06-19 11:05:03",
  "payload": {
    "spanId": "span_query_api_stats_001",
    "toolName": "queryApiCallStats",
    "toolType": "LOCAL",
    "inputPreview": {
      "apiCode": "AUTH_LOGIN",
      "startTime": "2026-06-19 10:00:00",
      "endTime": "2026-06-19 11:00:00"
    }
  }
}
```

---

## 6.9 `tool_result`
```json
{
  "eventId": "evt_trace_auth_403_0004",
  "seq": 4,
  "type": "tool_result",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "timestamp": "2026-06-19 11:05:04",
  "payload": {
    "spanId": "span_query_api_stats_001",
    "toolName": "queryApiCallStats",
    "success": true,
    "latencyMs": 128,
    "summary": "统一登录 API 在 10:00-11:00 期间失败率为 7.67%，主要为 4xx 错误。"
  }
}
```

---

## 6.10 `evidence_item`
```json
{
  "eventId": "evt_trace_auth_403_0005",
  "seq": 5,
  "type": "evidence_item",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "timestamp": "2026-06-19 11:05:05",
  "payload": {
    "sourceType": "STAT",
    "sourceId": "5001",
    "title": "统一登录 API 10:00-11:00 调用统计",
    "content": "统一登录 API 在 10:00-11:00 总调用量为 18520 次，失败 1420 次，失败率 7.67%，P95 为 280ms。",
    "confidence": null
  }
}
```

---

## 6.11 `report_saved`
```json
{
  "eventId": "evt_trace_auth_403_0018",
  "seq": 18,
  "type": "report_saved",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "timestamp": "2026-06-19 11:05:18",
  "payload": {
    "reportCode": "RPT_AUTH_403_20260619",
    "reportType": "DIAGNOSIS",
    "title": "统一登录 API 403 异常诊断报告"
  }
}
```

---

## 6.12 `error`
```json
{
  "eventId": "evt_trace_auth_403_0010",
  "seq": 10,
  "type": "error",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "timestamp": "2026-06-19 11:05:10",
  "payload": {
    "errorCode": "TOOL_TIMEOUT",
    "errorMessage": "查询网关日志超时，已基于调用统计和已有证据继续分析。",
    "recoverable": true,
    "failedToolName": "queryGatewayLogs"
  }
}
```

---

## 6.13 `done`
```json
{
  "eventId": "evt_trace_auth_403_0020",
  "seq": 20,
  "type": "done",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "timestamp": "2026-06-19 11:05:20",
  "payload": {
    "status": "SUCCESS",
    "reportCode": "RPT_AUTH_403_20260619",
    "durationMs": 20000,
    "lastEventSeq": 20
  }
}
```

---

## 6.14 SSE 断线与恢复策略
第一版不实现复杂断点续跑，明确采用以下策略：

```plain
SSE 连接中断后，前端不自动续跑 Agent；
前端可以根据 sessionCode 查询已保存的 Trace / Evidence / Report；
如果任务未完成，用户需要重新发起任务；
重新发起任务时建议带上相同 Idempotency-Key，避免重复保存报告；
后续可基于 eventId / seq / lastEventSeq 增加断点恢复。
```

断线后前端推荐展示：

```plain
当前分析连接已中断，已保存的工具调用和证据可以在 Trace 面板查看；如需继续，请重新发起分析。
```

---

# 7. Session / Trace / Evidence 接口
## 7.1 查询会话详情
```plain
GET /api/sessions/{sessionCode}
```

### 响应 data
```json
{
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "userId": 1,
  "userType": "MANAGER",
  "sessionType": "DIAGNOSIS",
  "title": "统一登录 API 403 异常诊断",
  "status": "SUCCESS",
  "startedAt": "2026-06-19 11:05:00",
  "finishedAt": "2026-06-19 11:05:18"
}
```

---

## 7.2 查询会话消息
```plain
GET /api/sessions/{sessionCode}/messages
```

### 响应 data
```json
{
  "items": [
    {
      "messageRole": "USER",
      "content": "为什么统一登录 API 今天 403 变多了？",
      "messageOrder": 1,
      "createdAt": "2026-06-19 11:05:00"
    },
    {
      "messageRole": "ASSISTANT",
      "content": "统一登录 API 今日 403 错误升高，主要集中在课程助手应用...",
      "messageOrder": 2,
      "createdAt": "2026-06-19 11:05:18"
    }
  ]
}
```

---

## 7.3 查询 Tool Trace
```plain
GET /api/sessions/{sessionCode}/trace
```

### 响应 data
```json
{
  "items": [
    {
      "spanId": "span_query_api_stats_001",
      "parentSpanId": null,
      "toolName": "queryApiCallStats",
      "toolType": "LOCAL",
      "success": true,
      "latencyMs": 128,
      "summary": "统一登录 API 在 10:00-11:00 期间失败率为 7.67%。",
      "errorCode": null,
      "errorMessage": null,
      "createdAt": "2026-06-19 11:05:03"
    }
  ]
}
```

---

## 7.4 查询 Evidence
```plain
GET /api/sessions/{sessionCode}/evidence
```

### 响应 data
```json
{
  "items": [
    {
      "sourceType": "STAT",
      "sourceId": "5001",
      "title": "统一登录 API 10:00-11:00 调用统计",
      "content": "统一登录 API 在 10:00-11:00 总调用量为 18520 次，失败 1420 次，失败率 7.67%。",
      "confidence": null,
      "createdAt": "2026-06-19 11:05:05"
    }
  ]
}
```

---

# 8. Report 接口
## 8.1 报告列表
```plain
GET /api/reports
```

### 查询参数
| 参数 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `reportType` | `string` | 否 | `null` | 报告类型 |
| `riskLevel` | `string` | 否 | `null` | 风险等级 |
| `keyword` | `string` | 否 | `null` | 标题关键词 |
| `pageNo` | `number` | 否 | `1` | 页码 |
| `pageSize` | `number` | 否 | `20` | 页大小 |


### 响应 data
```json
{
  "items": [
    {
      "reportCode": "RPT_AUTH_403_20260619",
      "reportType": "DIAGNOSIS",
      "title": "统一登录 API 403 异常诊断报告",
      "summary": "统一登录 API 403 错误主要集中在课程助手应用。",
      "riskLevel": "HIGH",
      "createdAt": "2026-06-19 11:05:18"
    }
  ],
  "total": 1,
  "pageNo": 1,
  "pageSize": 20
}
```

---

## 8.2 报告详情
```plain
GET /api/reports/{reportCode}
```

### 响应 data
```json
{
  "reportCode": "RPT_AUTH_403_20260619",
  "sessionCode": "sess_auth_403_20260619",
  "traceId": "trace_auth_403_20260619",
  "reportType": "DIAGNOSIS",
  "title": "统一登录 API 403 异常诊断报告",
  "summary": "统一登录 API 403 错误主要集中在课程助手应用，主要原因疑似签名校验失败。",
  "riskLevel": "HIGH",
  "contentMd": "## 结论\n统一登录 API 今日 403 错误升高...\n\n## 关键证据\n1. 网关日志显示 SIGNATURE_INVALID...",
  "createdAt": "2026-06-19 11:05:18"
}
```

---

## 8.3 报告证据
```plain
GET /api/reports/{reportCode}/evidence
```

### 响应 data
```json
{
  "items": [
    {
      "sourceType": "LOG",
      "sourceId": "6001",
      "title": "统一登录 API 403 日志样例",
      "content": "10:12:30 课程助手应用调用统一登录 API 返回 403。",
      "confidence": null
    }
  ]
}
```

---

## 8.4 报告 Trace
```plain
GET /api/reports/{reportCode}/trace
```

### 响应 data
```json
{
  "items": [
    {
      "toolName": "queryGatewayLogs",
      "toolType": "LOCAL",
      "success": true,
      "latencyMs": 126,
      "summary": "查询到 20 条 403 日志样例。"
    }
  ]
}
```

---

# 9. RAG 文档接口
## 9.1 上传文档
```plain
POST /api/rag/documents
```

### 请求类型
```plain
multipart/form-data
```

### 表单字段
| 字段 | 类型 | 必须 | 说明 |
| --- | --- | --- | --- |
| `file` | `file` | 是 | 文档文件 |
| `title` | `string` | 是 | 文档标题 |
| `docType` | `string` | 是 | `API_DOC`<br/> / `ERROR_CODE`<br/> / `SIGN_RULE`<br/> / `SDK_GUIDE` |
| `relatedApiCode` | `string` | 否 | 关联 API 编码 |


### 响应 data
```json
{
  "docCode": "DOC_AUTH_SIGN_RULE",
  "title": "统一登录 API 签名规则说明",
  "docType": "SIGN_RULE",
  "fileName": "auth-sign-rule.md",
  "contentHash": "sha256:demo_auth_sign_rule",
  "versionNo": 1,
  "embeddingModel": "text-embedding-v4",
  "embeddingDim": 1024,
  "indexStatus": "PENDING",
  "createdAt": "2026-06-19 12:00:00"
}
```

---

## 9.2 文档列表
```plain
GET /api/rag/documents
```

### 查询参数
| 参数 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `docType` | `string` | 否 | `null` | 文档类型 |
| `indexStatus` | `string` | 否 | `null` | 索引状态 |
| `keyword` | `string` | 否 | `null` | 标题关键词 |
| `pageNo` | `number` | 否 | `1` | 页码 |
| `pageSize` | `number` | 否 | `20` | 页大小 |


### 响应 data
```json
{
  "items": [
    {
      "docCode": "DOC_AUTH_SIGN_RULE",
      "title": "统一登录 API 签名规则说明",
      "docType": "SIGN_RULE",
      "fileName": "auth-sign-rule.md",
      "versionNo": 1,
      "chunkCount": 6,
      "embeddingModel": "text-embedding-v4",
      "indexStatus": "INDEXED",
      "createdAt": "2026-06-19 12:00:00"
    }
  ],
  "total": 1,
  "pageNo": 1,
  "pageSize": 20
}
```

---

## 9.3 文档详情
```plain
GET /api/rag/documents/{docCode}
```

### 响应 data
```json
{
  "docCode": "DOC_AUTH_SIGN_RULE",
  "title": "统一登录 API 签名规则说明",
  "docType": "SIGN_RULE",
  "fileName": "auth-sign-rule.md",
  "fileSize": 8420,
  "contentHash": "sha256:demo_auth_sign_rule",
  "versionNo": 1,
  "chunkCount": 6,
  "embeddingModel": "text-embedding-v4",
  "embeddingDim": 1024,
  "indexStatus": "INDEXED",
  "errorMessage": null,
  "createdAt": "2026-06-19 12:00:00"
}
```

---

## 9.4 RAG 检索测试
```plain
POST /api/rag/search
```

### 请求体
```json
{
  "query": "统一登录 API 403 签名校验失败排查",
  "apiCode": "AUTH_LOGIN",
  "docType": "SIGN_RULE",
  "topK": 3
}
```

### 响应 data
```json
{
  "query": "统一登录 API 403 签名校验失败排查",
  "topK": 3,
  "hits": [
    {
      "documentId": 10001,
      "documentTitle": "统一登录 API 签名规则说明",
      "docType": "SIGN_RULE",
      "chunkId": "DOC_AUTH_SIGN_RULE_0",
      "chunkTitle": "签名校验流程",
      "score": 0.87,
      "contentPreview": "调用统一登录 API 时，请求方需要按照 accessKey、timestamp、nonce 和请求体生成签名..."
    }
  ]
}
```

---

# 10. Dev Tool 调试接口，可选
## 10.1 直接调试 Tool
```plain
POST /api/dev/tools/{toolName}
```

### 说明
该接口只用于开发阶段直接调试 Tool，不作为正式前端能力。

限制：

```plain
仅 dev profile 启用；
Nginx 不对外暴露；
生产和正式演示环境默认关闭。
```

### 请求体示例
```json
{
  "context": {
    "traceId": "trace_dev_001",
    "sessionId": 12001,
    "userId": 1,
    "userType": "MANAGER"
  },
  "input": {
    "apiCode": "AUTH_LOGIN",
    "startTime": "2026-06-19 10:00:00",
    "endTime": "2026-06-19 11:00:00"
  }
}
```

### 响应 data
返回 `02_TOOL_CONTRACT.md` 中定义的 `ToolResult`。

---

# 11. 前端页面与接口对应关系
| 页面 | 主要接口 |
| --- | --- |
| 用户切换 | `/api/users/current`<br/>、`/api/users`<br/>、`/api/users/switch` |
| Dashboard | `/api/dashboard/overview`<br/>、`/api/dashboard/risk-apis`<br/>、`/api/dashboard/recent-events`<br/>、`/api/dashboard/latest-reports` |
| API 列表 / 详情 | `/api/apis`<br/>、`/api/apis/{apiCode}` |
| API 观测详情 | `/api/apis/{apiCode}/stats`<br/>、`/api/apis/{apiCode}/logs`<br/>、`/api/apis/{apiCode}/rules` |
| Agent 分析页 | `/api/agent/run/stream` |
| Tool Trace 面板 | SSE `tool_start`<br/> / `tool_result`<br/> + `/api/sessions/{sessionCode}/trace` |
| Evidence 面板 | SSE `evidence_item`<br/> + `/api/sessions/{sessionCode}/evidence` |
| Report 页 | `/api/reports`<br/>、`/api/reports/{reportCode}` |
| RAG 文档页 | `/api/rag/documents`<br/>、`/api/rag/search` |


---

# 12. Nginx 代理约定
第一版外网演示只暴露 Nginx。

推荐代理关系：

```plain
/              → apihub-ui
/api/**        → apihub-gateway 或 apihub-server
```

禁止外网直接暴露：

```plain
MySQL
Redis
Nacos
Milvus
MinIO
Attu
数据库管理页面
后端 dev-only Tool 调试接口
```

SSE 相关代理需要注意：

```plain
关闭代理缓冲；
保持长连接；
适当调大读取超时时间。
```

示例说明，不作为最终配置：

```plain
proxy_buffering off;
proxy_read_timeout 300s;
```

---

# 13. 禁止事项
## 13.1 接口设计禁止事项
禁止第一版提供：

```plain
DELETE /api/reports/{reportCode}
DELETE /api/rag/documents/{docCode}
PATCH /api/apis/{apiCode}/rate-limit
PATCH /api/apis/{apiCode}/online-status
POST /api/notifications/send
POST /api/dev/tools/{toolName} 对外暴露
```

## 13.2 数据返回禁止事项
禁止返回：

```plain
真实 API Key
数据库密码
DashScope API Key
Nacos 密钥
内网服务器地址
完整 accessSecret
用户敏感信息
```

`accessKey` 如需展示，只允许展示演示假值或脱敏值。

## 13.3 SSE 禁止事项
SSE 中禁止输出：

```plain
完整 Prompt
完整系统消息
真实密钥
未脱敏日志
数据库异常堆栈
```

---

# 14. 与其他文档的边界
```plain
01_DB_SCHEMA.md：
定义数据库表、字段、索引、示例数据。

02_TOOL_CONTRACT.md：
定义 Agent Tool 的输入输出、错误码、Trace / Evidence 规范。

03_API_CONTRACT.md：
定义前端如何通过 HTTP / SSE 调用后端能力。

04_VIBE_CODING_RULES.md：
定义 GPT / Codex 如何基于这些文档受控开发。
```

一句话：

```plain
03 只关心前后端交互契约，不直接定义数据库和 Agent Tool 内部实现。
```
## Mock Scenario Runner v1 API

### 8090 Scenario Client

- `GET /api/mock/health`
- `GET /api/mock/scenario-profiles`
- `POST /api/mock/scenario-runs`
- `GET /api/mock/scenario-runs/{scenarioRunId}`
- `POST /api/mock/scenario-runs/{scenarioRunId}/stop`
- `GET /api/mock/scenario-runs/{scenarioRunId}/sender-summary`
- `GET /api/mock/scenario-runs/{scenarioRunId}/reconciliation-summary`

Start request:

```json
{
  "profileCode": "LECTURE_REGISTRATION_PEAK",
  "mode": "FAST_DEMO",
  "targetGatewayBaseUrl": "http://localhost:8080",
  "randomSeed": 20260626,
  "rpsScale": 1.0,
  "includeTrafficSamples": true
}
```

### 8091 Mock Campus API

- `GET /api/mock-campus/health`
- `POST /api/mock-campus/invoke`
- `GET /api/mock-campus/scenario-runs/{scenarioRunId}/upstream-summary`

`mockScenario` to HTTP status mapping:

| mockScenario | HTTP |
|---|---:|
| NORMAL | 200 |
| TOKEN_EXPIRED | 401 |
| SIGNATURE_MISMATCH | 403 |
| RATE_LIMITED | 429 |
| DUPLICATE_REQUEST | 409 |
| SOLD_OUT | 409 |
| DOWNSTREAM_TIMEOUT | 504 |
| COURSE_SYSTEM_TIMEOUT | 504 |
| UPSTREAM_INTERNAL_ERROR | 500 |

### 8080 Gateway Summary

- `GET /api/dev/gateway/scenario-runs/{scenarioRunId}/gateway-summary`

This endpoint reads `gateway_log.extra_info.scenarioRunId` and returns Gateway-side counts for reconciliation.

## Adaptive Passive Alert Monitor v1 API

All endpoints are dev-only and served by `apihub-server` on 8080. They do not call Agent, DashScope, LLM, Stats Aggregator, or Alert Evaluator batch APIs.

### Status

```http
GET /api/dev/passive-monitor/status
```

Returns enabled state, runtime status, current config, active/cooldown event counts, bucket key count, last signal time, and last event time.

### Start / Stop

```http
POST /api/dev/passive-monitor/start
POST /api/dev/passive-monitor/stop
```

`stop` prevents new Gateway monitoring signals from being processed. Historical events remain queryable.

### Config

```http
POST /api/dev/passive-monitor/config
```

Request body may include:

```json
{
  "enabled": true,
  "bucketSeconds": 5,
  "shortWindowSeconds": 30,
  "baselineWindowSeconds": 300,
  "contextBeforeSeconds": 60,
  "cooldownSeconds": 120,
  "minRequestCount": 20,
  "minErrorCount": 3,
  "highErrorRateThreshold": 0.10,
  "highRateLimitThreshold": 0.05,
  "high5xxRateThreshold": 0.05,
  "authFailureThreshold": 0.10,
  "latencyThresholdMs": 1000
}
```

### Event Queries

```http
GET /api/dev/passive-monitor/events/recent?limit=20
GET /api/dev/passive-monitor/events?range=24h
GET /api/dev/passive-monitor/events?range=7d
GET /api/dev/passive-monitor/events?startTime=2026-06-27 10:00:00&endTime=2026-06-27 11:00:00
GET /api/dev/passive-monitor/events/{monitorEventId}
```

Supported filters: `apiCode`, `alertType`, `riskLevel`, `eventStatus`, `callerAppCode`, `limit`.

Detail returns the event row, snapshots, and linked `alert_event` when present.

### Close Check

```http
POST /api/dev/passive-monitor/events/close-check
```

Runs dev-only lifecycle closure logic. It checks `FIRING` / `COOLDOWN` events and resolves events that have passed the quiet period. It is not a force close endpoint.
