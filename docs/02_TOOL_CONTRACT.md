# 02_TOOL_CONTRACT.md
## 0. 文档定位
本文档是 API-HUB Agent 第一版 Tool Contract 设计说明。

本文档用于约束：

1. Agent 能调用哪些工具；
2. 每个 Tool 的业务用途；
3. Tool 的输入参数；
4. Tool 的统一输出结构；
5. Tool 失败时的错误表达；
6. Tool 调用如何记录 Trace；
7. Tool 结果如何形成 Evidence；
8. 不同业务闭环如何组合 Tool。

本文档不负责定义：

| 内容 | 归属文档 |
| --- | --- |
| 数据库表结构、字段、索引、示例数据 | `01_DB_SCHEMA.md` |
| 前后端 HTTP 接口、请求响应、SSE 事件 | `03_API_CONTRACT.md` |
| GPT / Codex 辅助开发规范 | `04_VIBE_CODING_RULES.md` |
| 具体 Java 类名、包名、实现细节 | 后端实现阶段确定 |
| 完整 Prompt 模板 | 后续 Agent Prompt 文档 |
| 面试问答 | 后续 `interview/`<br/> 文档 |


本文档核心原则：

```plain
01 定数据事实；
02 定 Agent 工具能力；
03 定前后端交互；
04 定 AI 辅助开发方式。
```

---

## 1. Tool 设计原则
### 1.1 第一版 Tool 以只读查询为主
第一版 Tool 主要用于：

```plain
查询 API 基础信息
查询调用统计
查询网关日志
查询调用方应用
查询限流规则
查询异常事件
查询校园事件
查询 RAG 文档
```

第一版不设计高风险写操作 Tool，例如：

```plain
自动修改限流规则
自动下线 API
自动修改 API 状态
自动通知调用方
自动重置密钥
自动调整缓存配置
自动执行修复脚本
```

这些动作第一版只生成建议，由平台管理者人工确认后处理。

---

### 1.2 Tool 必须结构化输入
Tool 不接收完全自由的自然语言作为唯一输入。

不推荐：

```json
{
  "query": "帮我看一下统一登录今天为什么 403 变多"
}
```

推荐：

```json
{
  "apiCode": "AUTH_LOGIN",
  "httpStatus": 403,
  "startTime": "2026-06-19 10:00:00",
  "endTime": "2026-06-19 11:00:00"
}
```

结构化输入的目的：

```plain
便于参数校验
便于测试
便于记录 Trace
便于生成 Evidence
便于后续自动化评测
```

---

### 1.3 Tool 必须统一输出
所有 Tool 返回统一 `ToolResult` 结构。

Tool 输出不能直接暴露数据库实体，也不能返回不稳定字段。

Tool 输出面向 Agent，数据库细节由 Tool 内部处理。

例如，Agent 输入使用：

```plain
apiCode
appCode
startTime
endTime
```

Tool 内部再转换为：

```plain
api_id
app_id
SQL 查询条件
```

---

### 1.4 Tool 结果必须可形成 Evidence
Agent 最终报告不能只依赖模型自由总结。

每个 Tool 返回结果中可以包含 `evidenceItems`，用于说明结论来源。

例如：

```plain
调用统计 → STAT Evidence
网关日志 → LOG Evidence
接口文档 → DOC Evidence
校园事件 → EVENT Evidence
限流规则 → RULE Evidence
异常事件 → ALERT Evidence
```

---

### 1.5 Tool 调用必须记录 Trace
每次 Tool 调用都要记录：

```plain
toolName
toolType
inputJson
outputJson
success
latencyMs
errorCode
errorMessage
traceId
spanId
```

对应数据库表：

```plain
tool_call_trace
```

---

### 1.6 权限由 Tool 层控制
Agent 不直接判断权限。

Tool 实现层根据当前用户判断可查询范围。

第一版用户类型：

| 用户类型 | 说明 |
| --- | --- |
| `MANAGER` | 平台管理者，可查看全局 API、日志、事件、报告 |
| `USER` | 普通用户，可查看自己提供的 API、自己拥有应用的调用数据、公开文档 |


---

### 1.7 Tool 描述必须可被模型正确理解
Tool 不只是后端函数，也是 Agent 做工具选择时的“能力说明”。每个 Tool 都必须有稳定的工具描述，避免模型因为描述模糊而误调工具。

每个 Tool 至少补充以下元数据：

| 字段 | 说明 |
| --- | --- |
| `name` | 工具唯一名称，必须和代码注册名称一致 |
| `purpose` | 工具解决的业务问题 |
| `when_to_use` | 什么问题应该调用该工具 |
| `when_not_to_use` | 什么问题不应该调用该工具 |
| `required_inputs` | 必填参数 |
| `optional_inputs` | 可选参数 |
| `max_result_size` | 最大返回结果规模 |
| `timeout_ms` | 超时时间 |

工具描述要面向 Agent，而不是面向前端用户。

---

### 1.8 Tool 输入输出建议同步维护 JSON Schema
本文档是人工阅读版契约。后续实现时，建议在 `docs/schemas/tools/` 下为每个 Tool 补充机器可读 JSON Schema。

推荐目录：

```plain
docs/schemas/tools
├─ queryApiInfo.input.schema.json
├─ queryApiInfo.output.schema.json
├─ queryApiCallStats.input.schema.json
├─ queryApiCallStats.output.schema.json
└─ ...
```

JSON Schema 用于：

```plain
参数校验
Codex 生成 DTO
单元测试构造样例
后续接入 MCP / OpenAPI 风格工具描述
```

第一版可以先用文档约束，核心 Tool 跑通后再补 schema 文件。

---

### 1.9 Tool 必须有超时、重试和降级策略
Tool 不允许无限等待，也不允许把底层异常直接抛给 Agent。

第一版推荐规则：

| Tool 类型 | timeout_ms | retry | fallback |
| --- | ---: | ---: | --- |
| MySQL 查询类 | 3000 | 0 | 返回 `TOOL_TIMEOUT` 或 `DATA_SOURCE_ERROR` |
| RAG 检索类 | 5000 | 1 | 返回 `RAG_SEARCH_FAILED`，保留已获得的其他证据 |
| MCP / 外部平台类 | 8000 | 1 | 返回 `MCP_TOOL_FAILED`，提示当前外部工具不可用 |
| 报告保存类 | 3000 | 0 | 返回 `REPORT_SAVE_FAILED`，但不影响已生成文本展示 |

Tool 失败后仍然要记录 Trace。Agent 可以基于已有证据继续分析，但最终回答必须说明“哪些证据缺失”。

---

### 1.10 Tool 结果必须裁剪和脱敏
日志、文档、Trace 类结果容易过长，第一版统一限制：

```plain
日志类 Tool 最多返回 20 条样例；
文档类 Tool 默认返回 topK=3，最大不超过 5；
统计类 Tool 返回聚合结果，不返回全量明细；
ToolResult.summary 不得包含 accessSecret、完整 token、完整请求头；
outputJson 只保存摘要，不保存大段原始日志或完整文档正文。
```

如果结果超过限制，Tool 应返回裁剪后的结构，并在 `extraInfo.truncated=true` 中说明。

---

## 2. 统一运行上下文
每次 Tool 调用都应携带运行上下文。

### 2.1 ToolContext
```json
{
  "traceId": "trace_auth_403_20260619",
  "sessionId": 12001,
  "userId": 1,
  "userType": "MANAGER",
  "requestTime": "2026-06-19 11:05:00"
}
```

### 2.2 字段说明
| 字段 | 必须 | 说明 |
| --- | --- | --- |
| `traceId` | 是 | 一次 Agent 工作流 ID |
| `sessionId` | 是 | Agent 会话 ID |
| `userId` | 是 | 当前用户 ID |
| `userType` | 是 | `MANAGER`<br/> / `USER` |
| `requestTime` | 否 | 调用时间 |


### 2.3 使用要求
Tool 实现时必须使用上下文完成：

```plain
权限判断
Trace 记录
Evidence 关联
日志定位
```

---

## 3. 统一 ToolResult 结构
### 3.1 成功返回结构
```json
{
  "success": true,
  "toolName": "queryApiCallStats",
  "summary": "统一登录 API 在 10:00-11:00 期间失败率明显升高，主要为 4xx 错误。",
  "data": {},
  "evidenceItems": [],
  "trace": {
    "traceId": "trace_auth_403_20260619",
    "spanId": "span_query_api_stats_001",
    "latencyMs": 128
  },
  "errorCode": null,
  "errorMessage": null,
  "extraInfo": {}
}
```

### 3.2 失败返回结构
```json
{
  "success": false,
  "toolName": "queryGatewayLogs",
  "summary": "查询网关日志失败。",
  "data": {},
  "evidenceItems": [],
  "trace": {
    "traceId": "trace_auth_403_20260619",
    "spanId": "span_query_gateway_logs_001",
    "latencyMs": 3000
  },
  "errorCode": "TOOL_TIMEOUT",
  "errorMessage": "查询网关日志超时",
  "extraInfo": {}
}
```

### 3.3 字段说明
| 字段 | 必须 | 说明 |
| --- | --- | --- |
| `success` | 是 | 工具是否成功 |
| `toolName` | 是 | 工具名称 |
| `summary` | 是 | 给 Agent 使用的简短结论 |
| `data` | 是 | 结构化结果 |
| `evidenceItems` | 否 | 可入库的证据项 |
| `trace` | 是 | Trace 信息 |
| `errorCode` | 否 | 错误码 |
| `errorMessage` | 否 | 错误信息 |
| `extraInfo` | 否 | 扩展信息 |


### 3.4 设计要求
```plain
data 必须是结构化 JSON；
summary 面向 Agent，不面向前端；
evidenceItems 可为空数组；
errorCode 成功时为 null；
失败时 data 使用空对象；
失败时不得抛出裸异常给 Agent；
结果过长时必须裁剪，并在 `extraInfo.truncated` 中说明。
```

---

## 4. 统一 EvidenceItem 结构
### 4.1 EvidenceItem 示例
```json
{
  "sourceType": "LOG",
  "sourceId": "6001",
  "title": "统一登录 API 403 日志样例",
  "content": "10:12:30 课程助手应用调用统一登录 API 返回 403，错误码为 SIGNATURE_INVALID。",
  "confidence": null,
  "extraInfo": {
    "traceId": "tr_20260619_auth_001",
    "appCode": "COURSE_HELPER"
  }
}
```

### 4.2 字段说明
| 字段 | 必须 | 说明 |
| --- | --- | --- |
| `sourceType` | 是 | 证据来源类型 |
| `sourceId` | 否 | 来源记录 ID |
| `title` | 是 | 证据标题 |
| `content` | 是 | 证据内容 |
| `confidence` | 否 | 置信度，RAG 结果可使用 |
| `extraInfo` | 否 | 扩展信息 |


### 4.3 sourceType 取值
| sourceType | 说明 |
| --- | --- |
| `STAT` | API 调用统计 |
| `LOG` | 网关日志 |
| `DOC` | RAG 文档 |
| `EVENT` | 校园事件 |
| `RULE` | 限流 / 降级规则 |
| `ALERT` | 异常事件 |
| `APP` | 调用方应用 |
| `API` | API 基础信息 |


### 4.4 入库映射
EvidenceItem 对应数据库表：

```plain
evidence_item
```

字段映射：

| EvidenceItem 字段 | 数据库字段 |
| --- | --- |
| `sourceType` | `source_type` |
| `sourceId` | `source_id` |
| `title` | `title` |
| `content` | `content` |
| `confidence` | `confidence` |
| `extraInfo` | `extra_info` |


---

## 5. P0 Tool 清单
第一版 P0 Tool 共 8 个。

| Tool 名称 | 作用 | 主要数据来源 |
| --- | --- | --- |
| `queryApiInfo` | 查询 API 基础信息 | `api_endpoint` |
| `queryApiCallStats` | 查询 API 调用统计 | `api_call_stat_hourly` |
| `queryGatewayLogs` | 查询网关日志 | `gateway_log` |
| `queryConsumerApp` | 查询调用方应用与授权关系 | `api_consumer_app`<br/>、`api_authorization` |
| `queryRateLimitRule` | 查询限流 / 降级规则 | `rate_limit_rule` |
| `queryAlertEvents` | 查询异常事件 | `alert_event` |
| `queryCampusEvents` | 查询校园事件及影响 API | `campus_event`<br/>、`event_api_relation` |
| `queryApiDocs` | 查询接口文档 / 错误码 / 签名规则 | `rag_document`<br/>、`rag_chunk_meta`<br/>、Milvus |

## 5.1 P0 Tool Metadata 摘要
| Tool | when_to_use | when_not_to_use | timeout_ms | max_result_size |
| --- | --- | --- | ---: | --- |
| `queryApiInfo` | 询问 API 基础信息、负责人、状态、路径、风险等级 | 询问实时调用量、日志、文档细节 | 3000 | 单个 API |
| `queryApiCallStats` | 询问调用量、失败率、P95/P99、错误码分布 | 询问单条日志、签名规则全文 | 3000 | 最多 24 个小时聚合点 |
| `queryGatewayLogs` | 询问具体错误样例、请求失败原因、调用方分布 | 询问纯业务事件或文档知识 | 3000 | 最多 20 条日志样例 |
| `queryConsumerApp` | 询问调用方应用、负责人、授权关系 | 询问 API 文档或校园事件 | 3000 | 最多 20 个应用 |
| `queryRateLimitRule` | 询问限流、降级、QPS 阈值、保护策略 | 询问历史调用统计 | 3000 | 单个 API 的规则列表 |
| `queryAlertEvents` | 询问当前或历史异常事件 | 询问接口文档解释 | 3000 | 最多 20 条事件 |
| `queryCampusEvents` | 询问讲座、开学、考试等业务事件影响 | 询问网关日志细节 | 3000 | 最多 20 条校园事件 |
| `queryApiDocs` | 询问签名规则、错误码说明、SDK 使用、接口文档 | 询问实时统计和日志 | 5000 | 默认 topK=3，最大 5 |

---

## 6. Tool 详细契约
# 6.1 `queryApiInfo`
## 6.1.1 作用
查询某个 API 的基础信息，包括名称、路径、类型、提供者、风险等级、在线状态。

## 6.1.2 典型使用场景
```plain
这个接口是谁维护的？
讲座报名 API 当前是什么状态？
统一登录 API 是不是高风险接口？
某个 API 的路径和方法是什么？
```

## 6.1.3 输入参数
```json
{
  "apiCode": "AUTH_LOGIN"
}
```

| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `apiCode` | `string` | 是 | 无 | API 编码 |


## 6.1.4 输出 data
```json
{
  "apiCode": "AUTH_LOGIN",
  "apiName": "学校账户授权登录",
  "apiType": "AUTH",
  "path": "/api/auth/login",
  "method": "POST",
  "providerUserId": 2,
  "ownerTeam": "统一身份认证中心",
  "riskLevel": "HIGH",
  "onlineStatus": "ONLINE",
  "description": "用于校内应用进行统一身份认证登录"
}
```

## 6.1.5 Evidence
默认生成 `API` 类型证据。

```json
{
  "sourceType": "API",
  "sourceId": "1001",
  "title": "API 基础信息：学校账户授权登录",
  "content": "学校账户授权登录是 AUTH 类型高风险接口，当前状态为 ONLINE，维护团队为统一身份认证中心。",
  "confidence": null,
  "extraInfo": {
    "apiCode": "AUTH_LOGIN"
  }
}
```

## 6.1.6 失败情况
| 场景 | errorCode |
| --- | --- |
| `apiCode`<br/> 为空 | `INVALID_ARGUMENT` |
| API 不存在 | `API_NOT_FOUND` |
| 无权限查看 | `PERMISSION_DENIED` |
| 数据源异常 | `DATA_SOURCE_ERROR` |


---

# 6.2 `queryApiCallStats`
## 6.2.1 作用
查询 API 在指定时间范围内的调用量、成功数、失败数、失败率、4xx / 5xx、P95、P99、限流次数等统计信息。

## 6.2.2 典型使用场景
```plain
今天哪些 API 状态异常？
统一登录 API 今天 403 是否明显升高？
讲座报名 API 中午 P95 是否上涨？
本周哪个 API 失败率最高？
```

## 6.2.3 输入参数
```json
{
  "apiCode": "AUTH_LOGIN",
  "startTime": "2026-06-19 10:00:00",
  "endTime": "2026-06-19 11:00:00",
  "granularity": "HOURLY"
}
```

| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `apiCode` | `string` | 否 | `null` | API 编码；为空时查询范围内所有 API 汇总 |
| `startTime` | `string` | 是 | 无 | 开始时间 |
| `endTime` | `string` | 是 | 无 | 结束时间 |
| `granularity` | `string` | 否 | `HOURLY` | 第一版仅支持 `HOURLY` |
| `topN` | `number` | 否 | `10` | 查询 Top API 时使用，最大 50 |


## 6.2.4 输出 data
```json
{
  "apiCode": "AUTH_LOGIN",
  "totalCount": 18520,
  "successCount": 17100,
  "failCount": 1420,
  "error4xxCount": 1360,
  "error5xxCount": 60,
  "rateLimitCount": 0,
  "failRate": 0.0767,
  "avgLatencyMs": 96,
  "p95LatencyMs": 280,
  "p99LatencyMs": 520,
  "maxLatencyMs": 1100,
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

## 6.2.5 Evidence
默认生成 `STAT` 类型证据。

```json
{
  "sourceType": "STAT",
  "sourceId": "5001",
  "title": "统一登录 API 10:00-11:00 调用统计",
  "content": "统一登录 API 在 10:00-11:00 总调用量为 18520 次，失败 1420 次，失败率 7.67%，P95 为 280ms。",
  "confidence": null,
  "extraInfo": {
    "apiCode": "AUTH_LOGIN",
    "failRate": 0.0767
  }
}
```

## 6.2.6 失败情况
| 场景 | errorCode |
| --- | --- |
| 时间范围为空 | `INVALID_ARGUMENT` |
| `startTime`<br/> 晚于 `endTime` | `INVALID_ARGUMENT` |
| API 不存在 | `API_NOT_FOUND` |
| 无权限查看 | `PERMISSION_DENIED` |
| 无统计数据 | `DATA_NOT_FOUND` |
| 数据源异常 | `DATA_SOURCE_ERROR` |


---

# 6.3 `queryGatewayLogs`
## 6.3.1 作用
查询网关日志样例，用于定位错误原因、异常调用方、错误码分布和请求链路。

## 6.3.2 典型使用场景
```plain
403 是权限不足还是签名失败？
哪个调用方异常最多？
最近有没有 5xx 错误样例？
某个 traceId 对应的错误是什么？
```

## 6.3.3 输入参数
```json
{
  "apiCode": "AUTH_LOGIN",
  "appCode": "COURSE_HELPER",
  "httpStatus": 403,
  "errorCode": "SIGNATURE_INVALID",
  "startTime": "2026-06-19 10:00:00",
  "endTime": "2026-06-19 11:00:00",
  "limit": 20
}
```

| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `apiCode` | `string` | 否 | `null` | API 编码 |
| `appCode` | `string` | 否 | `null` | 调用方应用编码 |
| `httpStatus` | `number` | 否 | `null` | HTTP 状态码 |
| `errorCode` | `string` | 否 | `null` | 业务错误码 |
| `traceId` | `string` | 否 | `null` | 请求链路 ID |
| `startTime` | `string` | 是 | 无 | 开始时间 |
| `endTime` | `string` | 是 | 无 | 结束时间 |
| `limit` | `number` | 否 | `20` | 返回条数，最大 100 |


## 6.3.4 输出 data
```json
{
  "matchedCount": 20,
  "topErrorCode": "SIGNATURE_INVALID",
  "topAppCode": "COURSE_HELPER",
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

## 6.3.5 Evidence
默认生成 `LOG` 类型证据。

```json
{
  "sourceType": "LOG",
  "sourceId": "6001",
  "title": "统一登录 API 403 日志样例",
  "content": "10:12:30 课程助手应用调用统一登录 API 返回 403，错误码为 SIGNATURE_INVALID，错误信息为签名校验失败。",
  "confidence": null,
  "extraInfo": {
    "traceId": "tr_20260619_auth_001",
    "appCode": "COURSE_HELPER"
  }
}
```

## 6.3.6 失败情况
| 场景 | errorCode |
| --- | --- |
| 时间范围为空 | `INVALID_ARGUMENT` |
| limit 超过最大值 | `INVALID_ARGUMENT` |
| API 不存在 | `API_NOT_FOUND` |
| 调用方应用不存在 | `APP_NOT_FOUND` |
| 无权限查看 | `PERMISSION_DENIED` |
| 无日志数据 | `DATA_NOT_FOUND` |
| 查询超时 | `TOOL_TIMEOUT` |
| 数据源异常 | `DATA_SOURCE_ERROR` |


---

# 6.4 `queryConsumerApp`
## 6.4.1 作用
查询调用方应用信息、负责人、应用状态，以及该应用对某个 API 的授权状态。

## 6.4.2 典型使用场景
```plain
哪个应用导致 403 增多？
课程助手是否有统一登录 API 的授权？
调用方负责人是谁？
这个 accessKey 属于哪个应用？
```

## 6.4.3 输入参数
```json
{
  "appCode": "COURSE_HELPER",
  "apiCode": "AUTH_LOGIN",
  "accessKey": "ak_course_helper_demo"
}
```

| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `appCode` | `string` | 否 | `null` | 应用编码 |
| `apiCode` | `string` | 否 | `null` | API 编码，用于查询授权关系 |
| `accessKey` | `string` | 否 | `null` | 调用方 accessKey |


说明：

```plain
appCode 和 accessKey 至少传一个。
```

## 6.4.4 输出 data
```json
{
  "appCode": "COURSE_HELPER",
  "appName": "课程助手小程序",
  "ownerUserId": 4,
  "ownerTeam": "课程助手团队",
  "appType": "MINI_APP",
  "accessKey": "ak_course_helper_demo",
  "status": "ACTIVE",
  "authorization": {
    "apiCode": "AUTH_LOGIN",
    "authStatus": "APPROVED",
    "expireAt": null
  }
}
```

## 6.4.5 Evidence
默认生成 `APP` 类型证据。

```json
{
  "sourceType": "APP",
  "sourceId": "2001",
  "title": "调用方应用信息：课程助手小程序",
  "content": "课程助手小程序当前状态为 ACTIVE，已授权调用统一登录 API。",
  "confidence": null,
  "extraInfo": {
    "appCode": "COURSE_HELPER",
    "apiCode": "AUTH_LOGIN"
  }
}
```

## 6.4.6 失败情况
| 场景 | errorCode |
| --- | --- |
| appCode 和 accessKey 均为空 | `INVALID_ARGUMENT` |
| 应用不存在 | `APP_NOT_FOUND` |
| API 不存在 | `API_NOT_FOUND` |
| 无授权关系 | `DATA_NOT_FOUND` |
| 无权限查看 | `PERMISSION_DENIED` |
| 数据源异常 | `DATA_SOURCE_ERROR` |


---

# 6.5 `queryRateLimitRule`
## 6.5.1 作用
查询 API 当前限流、熔断、降级配置，用于巡检、预警和处理建议生成。

## 6.5.2 典型使用场景
```plain
讲座报名 API 是否需要提前限流？
统一登录 API 当前 QPS 限制是多少？
某个 API 是否启用了降级？
高峰前是否需要增加缓存或调整限流？
```

## 6.5.3 输入参数
```json
{
  "apiCode": "AUTH_LOGIN"
}
```

| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `apiCode` | `string` | 是 | 无 | API 编码 |


## 6.5.4 输出 data
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
      "effectiveStart": null,
      "effectiveEnd": null,
      "status": "ACTIVE"
    }
  ]
}
```

## 6.5.5 Evidence
默认生成 `RULE` 类型证据。

```json
{
  "sourceType": "RULE",
  "sourceId": "4001",
  "title": "统一登录 API 限流规则",
  "content": "统一登录 API 当前 QPS 限制为 300，突发流量上限为 600，已启用降级。",
  "confidence": null,
  "extraInfo": {
    "apiCode": "AUTH_LOGIN",
    "qpsLimit": 300
  }
}
```

## 6.5.6 失败情况
| 场景 | errorCode |
| --- | --- |
| apiCode 为空 | `INVALID_ARGUMENT` |
| API 不存在 | `API_NOT_FOUND` |
| 无规则数据 | `DATA_NOT_FOUND` |
| 无权限查看 | `PERMISSION_DENIED` |
| 数据源异常 | `DATA_SOURCE_ERROR` |


---

# 6.6 `queryAlertEvents`
## 6.6.1 作用
查询结构化异常事件，例如失败率升高、P95 升高、限流次数激增、流量突增。

## 6.6.2 典型使用场景
```plain
今天有哪些异常事件？
本周有哪些高风险 API？
统一登录 API 是否已有告警？
哪些异常还没有解决？
```

## 6.6.3 输入参数
```json
{
  "apiCode": "AUTH_LOGIN",
  "eventType": "HIGH_ERROR_RATE",
  "severity": "HIGH",
  "startTime": "2026-06-19 00:00:00",
  "endTime": "2026-06-19 23:59:59",
  "resolved": false
}
```

| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `apiCode` | `string` | 否 | `null` | API 编码 |
| `eventType` | `string` | 否 | `null` | 异常类型 |
| `severity` | `string` | 否 | `null` | `LOW`<br/> / `MEDIUM`<br/> / `HIGH` |
| `startTime` | `string` | 是 | 无 | 开始时间 |
| `endTime` | `string` | 是 | 无 | 结束时间 |
| `resolved` | `boolean` | 否 | `null` | 是否已解决 |


## 6.6.4 输出 data
```json
{
  "matchedCount": 1,
  "events": [
    {
      "eventCode": "ALERT_AUTH_403_20260619",
      "apiCode": "AUTH_LOGIN",
      "eventType": "HIGH_ERROR_RATE",
      "severity": "HIGH",
      "title": "统一登录 API 403 错误升高",
      "description": "10:00-11:00 期间统一登录 API 403 错误明显升高，主要集中在课程助手应用。",
      "startTime": "2026-06-19 10:00:00",
      "endTime": "2026-06-19 11:00:00",
      "resolved": false
    }
  ]
}
```

## 6.6.5 Evidence
默认生成 `ALERT` 类型证据。

```json
{
  "sourceType": "ALERT",
  "sourceId": "7001",
  "title": "统一登录 API 403 错误升高",
  "content": "10:00-11:00 期间统一登录 API 403 错误明显升高，异常级别为 HIGH，当前未解决。",
  "confidence": null,
  "extraInfo": {
    "eventCode": "ALERT_AUTH_403_20260619",
    "severity": "HIGH"
  }
}
```

## 6.6.6 失败情况
| 场景 | errorCode |
| --- | --- |
| 时间范围为空 | `INVALID_ARGUMENT` |
| API 不存在 | `API_NOT_FOUND` |
| 无权限查看 | `PERMISSION_DENIED` |
| 无异常事件 | `DATA_NOT_FOUND` |
| 数据源异常 | `DATA_SOURCE_ERROR` |


---

# 6.7 `queryCampusEvents`
## 6.7.1 作用
查询校园业务事件，以及事件可能影响的 API，用于事件预警和 API 波动解释。

## 6.7.2 典型使用场景
```plain
本周有哪些校园活动可能影响 API？
讲座报名会影响哪些接口？
开学第一周哪些 API 要重点关注？
考试安排发布是否会带来访问高峰？
```

## 6.7.3 输入参数
```json
{
  "eventType": "LECTURE_SIGNUP",
  "startTime": "2026-06-20 00:00:00",
  "endTime": "2026-06-20 23:59:59",
  "includeImpactedApis": true
}
```

| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `eventType` | `string` | 否 | `null` | 校园事件类型 |
| `startTime` | `string` | 是 | 无 | 开始时间 |
| `endTime` | `string` | 是 | 无 | 结束时间 |
| `includeImpactedApis` | `boolean` | 否 | `true` | 是否返回受影响 API |


## 6.7.4 输出 data
```json
{
  "matchedCount": 1,
  "events": [
    {
      "eventCode": "LECTURE_SIGNUP_20260620",
      "eventName": "人工智能前沿讲座报名开放",
      "eventType": "LECTURE_SIGNUP",
      "description": "讲座报名将在 12:00 开放，预计统一登录和讲座报名 API 调用量上升。",
      "startTime": "2026-06-20 12:00:00",
      "endTime": "2026-06-20 12:30:00",
      "expectedTrafficLevel": "HIGH",
      "impactedApis": [
        {
          "apiCode": "AUTH_LOGIN",
          "apiName": "学校账户授权登录",
          "impactType": "TRAFFIC_INCREASE",
          "impactLevel": "HIGH",
          "reason": "报名开放前后大量用户需要先完成统一登录。"
        }
      ]
    }
  ]
}
```

## 6.7.5 Evidence
默认生成 `EVENT` 类型证据。

```json
{
  "sourceType": "EVENT",
  "sourceId": "8001",
  "title": "校园事件：人工智能前沿讲座报名开放",
  "content": "讲座报名将在 12:00 开放，预计影响统一登录 API 和讲座报名 API，流量等级为 HIGH。",
  "confidence": null,
  "extraInfo": {
    "eventCode": "LECTURE_SIGNUP_20260620"
  }
}
```

## 6.7.6 失败情况
| 场景 | errorCode |
| --- | --- |
| 时间范围为空 | `INVALID_ARGUMENT` |
| 无校园事件 | `DATA_NOT_FOUND` |
| 数据源异常 | `DATA_SOURCE_ERROR` |


---

# 6.8 `queryApiDocs`
## 6.8.1 作用
通过 RAG 查询接口文档、错误码说明、签名规则、SDK 使用说明等知识。

该 Tool 需要调用：

```plain
MySQL 文档元数据
Milvus 向量检索
Embedding 模型
```

## 6.8.2 典型使用场景
```plain
403 签名失败怎么排查？
统一登录 API 的签名规则是什么？
讲座报名 API 如何调用？
某个错误码是什么意思？
```

## 6.8.3 输入参数
```json
{
  "query": "统一登录 API 403 签名校验失败排查",
  "apiCode": "AUTH_LOGIN",
  "docType": "SIGN_RULE",
  "topK": 3
}
```

| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `query` | `string` | 是 | 无 | 检索问题 |
| `apiCode` | `string` | 否 | `null` | 限定 API |
| `docType` | `string` | 否 | `null` | `API_DOC`<br/> / `ERROR_CODE`<br/> / `SIGN_RULE`<br/> / `SDK_GUIDE` |
| `topK` | `number` | 否 | `3` | 返回条数，最大 10 |


## 6.8.4 输出 data
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

## 6.8.5 Evidence
默认生成 `DOC` 类型证据。

```json
{
  "sourceType": "DOC",
  "sourceId": "DOC_AUTH_SIGN_RULE_0",
  "title": "统一登录 API 签名规则说明：签名校验流程",
  "content": "调用统一登录 API 时，请求方需要按照 accessKey、timestamp、nonce 和请求体生成签名...",
  "confidence": 0.87,
  "extraInfo": {
    "documentId": 10001,
    "docType": "SIGN_RULE",
    "apiCode": "AUTH_LOGIN"
  }
}
```

## 6.8.6 失败情况
| 场景 | errorCode |
| --- | --- |
| query 为空 | `INVALID_ARGUMENT` |
| topK 超过最大值 | `INVALID_ARGUMENT` |
| API 不存在 | `API_NOT_FOUND` |
| 文档未完成索引 | `RAG_INDEX_NOT_READY` |
| RAG 检索失败 | `RAG_SEARCH_FAILED` |
| 无检索结果 | `DATA_NOT_FOUND` |
| 无权限查看 | `PERMISSION_DENIED` |


---

## 7. Tool 组合策略
## 7.1 今日 API 巡检闭环
目标：

```plain
发现今日 API 运行中的异常、风险和需要关注的接口。
```

推荐 Tool 顺序：

```plain
queryApiCallStats
→ queryAlertEvents
→ queryRateLimitRule
→ Agent 生成巡检摘要
```

可选补充：

```plain
queryGatewayLogs
queryApiInfo
```

输出重点：

```plain
调用量 Top API
失败率 Top API
P95 / P99 高的 API
限流次数异常 API
待关注 API
处理建议
```

---

## 7.2 统一登录 403 诊断闭环
目标：

```plain
解释统一登录 API 403 错误升高的原因。
```

推荐 Tool 顺序：

```plain
queryApiInfo
→ queryApiCallStats
→ queryGatewayLogs
→ queryConsumerApp
→ queryApiDocs
→ Agent 生成诊断结论
```

输出重点：

```plain
是否真的 403 升高
主要错误码
异常调用方
是否授权正常
是否与签名规则相关
建议联系谁处理
后续观察指标
```

---

## 7.3 讲座报名高峰预警闭环
目标：

```plain
在讲座报名等校园事件前，提前判断可能受影响的 API。
```

推荐 Tool 顺序：

```plain
queryCampusEvents
→ queryApiInfo
→ queryApiCallStats
→ queryRateLimitRule
→ Agent 生成预警建议
```

输出重点：

```plain
事件时间
受影响 API
历史调用趋势
当前限流配置
建议观察指标
是否需要提前缓存或限流
```

---

## 7.4 本周 API 复盘闭环
目标：

```plain
汇总一周内 API 运行状态、异常事件、高并发事件和改进建议。
```

推荐 Tool 顺序：

```plain
queryApiCallStats
→ queryAlertEvents
→ queryCampusEvents
→ queryRateLimitRule
→ Agent 生成周报
```

输出重点：

```plain
本周调用量最高 API
本周失败率异常 API
本周高并发事件
校园事件与 API 波动关联
下周重点关注项
```

---

## 7.5 RAG 文档问答闭环
目标：

```plain
回答接口文档、错误码、签名规则、SDK 调用相关问题。
```

推荐 Tool 顺序：

```plain
queryApiDocs
→ Agent 基于文档片段回答
```

可选补充：

```plain
queryApiInfo
queryGatewayLogs
```

输出重点：

```plain
文档命中内容
文档来源
相关 API
排查步骤
注意事项
```

---

## 8. Tool 运行控制规范
## 8.1 超时与重试
Tool 实现层必须显式设置超时，不依赖默认连接超时。

| Tool | timeout_ms | retry |
| --- | ---: | ---: |
| `queryApiInfo` | 3000 | 0 |
| `queryApiCallStats` | 3000 | 0 |
| `queryGatewayLogs` | 3000 | 0 |
| `queryConsumerApp` | 3000 | 0 |
| `queryRateLimitRule` | 3000 | 0 |
| `queryAlertEvents` | 3000 | 0 |
| `queryCampusEvents` | 3000 | 0 |
| `queryApiDocs` | 5000 | 1 |

## 8.2 降级原则
```plain
单个 Tool 失败，不直接中断整个 Agent；
如果关键 Tool 失败，最终回答必须说明证据缺失；
权限失败不允许重试；
参数错误不允许重试；
超时可以按 Tool 类型决定是否重试一次。
```

## 8.3 结果裁剪
| Tool 类型 | 裁剪规则 |
| --- | --- |
| 日志类 | 最多 20 条，隐藏 token、secret、完整请求头 |
| 文档类 | 默认 topK=3，最大 topK=5，正文只返回片段和来源 |
| 统计类 | 返回聚合值和分布，不返回全量小时明细之外的数据 |
| 事件类 | 最多 20 条，按时间和影响级别排序 |

---

## 9. 错误码规范
所有 Tool 统一错误码。

| errorCode | 说明 |
| --- | --- |
| `INVALID_ARGUMENT` | 参数缺失、格式错误、范围非法 |
| `PERMISSION_DENIED` | 当前用户无权限访问 |
| `API_NOT_FOUND` | API 不存在 |
| `APP_NOT_FOUND` | 调用方应用不存在 |
| `DATA_NOT_FOUND` | 没有查询到数据 |
| `DATA_SOURCE_ERROR` | 数据源异常 |
| `RAG_INDEX_NOT_READY` | 文档未完成索引 |
| `RAG_SEARCH_FAILED` | RAG 检索失败 |
| `MCP_TOOL_FAILED` | MCP 或外部工具调用失败 |
| `TOOL_TIMEOUT` | 工具调用超时 |
| `RESULT_TOO_LARGE` | 工具结果超过允许范围，已被裁剪 |
| `REPORT_SAVE_FAILED` | 报告保存失败 |
| `UNKNOWN_ERROR` | 未知错误 |


错误处理要求：

```plain
Tool 内部捕获异常；
转换为统一 ToolResult；
记录 tool_call_trace；
不得把裸异常直接暴露给 Agent；
errorMessage 保持可读，但不暴露敏感信息。
```

---

## 10. 权限策略
第一版使用简单权限策略，不做完整 RBAC。

## 10.1 用户类型
| 用户类型 | 说明 |
| --- | --- |
| `MANAGER` | 平台管理者 |
| `USER` | 普通用户 |


## 10.2 Tool 权限表
| Tool | MANAGER | USER |
| --- | --- | --- |
| `queryApiInfo` | 全部 API | 自己提供的 API + 已授权调用的 API |
| `queryApiCallStats` | 全部 API | 自己提供的 API + 自己应用调用的 API |
| `queryGatewayLogs` | 全部日志 | 自己应用或自己提供 API 的相关日志 |
| `queryConsumerApp` | 全部应用 | 自己拥有的应用 |
| `queryRateLimitRule` | 全部 API | 自己提供的 API 只读 |
| `queryAlertEvents` | 全部事件 | 自己相关 API 事件 |
| `queryCampusEvents` | 全部事件 | 公开校园事件 |
| `queryApiDocs` | 全部文档 | 公开文档 + 自己相关 API 文档 |


## 10.3 USER 数据范围判断
普通用户可访问的数据范围：

```plain
自己提供的 API：
api_endpoint.provider_user_id = currentUser.id

自己拥有的应用：
api_consumer_app.owner_user_id = currentUser.id

自己应用调用的 API：
api_consumer_app.owner_user_id = currentUser.id
AND api_authorization.app_id = api_consumer_app.id
```

---

## 11. Trace / Evidence 记录规范
## 11.1 Trace 记录要求
每个 Tool 调用都必须写入：

```plain
tool_call_trace
```

必须记录：

```plain
sessionId
traceId
spanId
parentSpanId
toolName
toolType
inputJson
outputJson
latencyMs
success
errorCode
errorMessage
status
```

## 11.2 spanId 生成规则
建议格式：

```plain
span_{tool_name}_{short_uuid}
```

示例：

```plain
span_query_gateway_logs_001
span_query_api_docs_002
```

## 11.3 outputJson 存储要求
`outputJson` 可以保存 Tool 结构化结果摘要，不建议保存过长全文。

对于日志和文档类结果：

```plain
只保存摘要、命中数量、top 结果；
原始日志或文档正文通过 sourceId 关联；
如发生裁剪，记录 `truncated=true`、`originalCount`、`returnedCount`。
```

## 11.4 Evidence 生成要求
能够支撑报告结论的 Tool 结果，应生成 Evidence。

默认规则：

| Tool | Evidence |
| --- | --- |
| `queryApiInfo` | `API` |
| `queryApiCallStats` | `STAT` |
| `queryGatewayLogs` | `LOG` |
| `queryConsumerApp` | `APP` |
| `queryRateLimitRule` | `RULE` |
| `queryAlertEvents` | `ALERT` |
| `queryCampusEvents` | `EVENT` |
| `queryApiDocs` | `DOC` |


## 11.5 Evidence 入库时机
建议在 Tool 返回后由后端统一处理：

```plain
Tool 执行完成
→ 生成 ToolResult
→ 写入 tool_call_trace
→ 提取 evidenceItems
→ 写入 evidence_item
→ Agent 基于 ToolResult 继续推理
```

---

## 12. 关于报告生成
第一版不设计 `generateReport` Tool。

原因：

```plain
Agent 最终回答本身就是报告生成过程；
如果再设计 generateReport Tool，容易混淆模型生成和工具查询边界；
报告保存属于后端业务能力，不应暴露为模型可自由调用的 Tool。
```

第一版设计：

| 能力 | 归属 |
| --- | --- |
| 查询统计、日志、文档、事件、规则 | Tool |
| 组织最终回答 / 报告正文 | Agent / LLM |
| 报告保存 | 后端 ReportService |
| 报告查询 | HTTP API，归属 `03_API_CONTRACT.md` |


报告生成流程：

```plain
Agent 调用查询类 Tool
→ Tool 返回 data + evidenceItems
→ Agent 生成结构化报告内容
→ 后端保存 agent_report
→ 前端通过 API 展示报告
```

---

## 13. Tool 禁止事项
禁止设计以下 Tool：

```plain
updateRateLimitRule
disableApiEndpoint
resetAppSecret
sendNotificationToProvider
deleteGatewayLogs
deleteRagDocument
executeRepairScript
```

如后续确实需要写操作能力，必须满足：

```plain
单独设计审批流程；
单独设计权限控制；
单独设计操作日志；
默认需要人工确认；
不能由 Agent 自动执行高风险动作。
```

---

## 14. 与其他文档的边界
```plain
01_DB_SCHEMA.md：
定义 Tool 查询所依赖的数据表和字段。

02_TOOL_CONTRACT.md：
定义 Agent 能调用哪些工具、输入输出是什么、失败如何表达、Trace / Evidence 如何记录。

03_API_CONTRACT.md：
定义前端如何调用后端接口、报告如何查询、SSE 如何返回。

04_VIBE_CODING_RULES.md：
定义 GPT / Codex 如何基于这些文档受控开发。
```

一句话：

```plain
02 只关心 Agent Tool 能力边界，不关心 HTTP 接口和具体 Java 实现。
```

