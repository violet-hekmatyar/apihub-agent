# 01_DB_SCHEMA.md
## 0. 文档定位
本文档是 API-HUB Agent 第一版数据库设计说明。

本文档用于约束：

1. 后端 Entity / Mapper / Service 开发；
2. Codex 生成代码时的数据字段依据；
3. Mock 数据初始化；
4. Agent Tool 查询实现；
5. 前端演示数据展示；
6. Trace / Evidence / Report 回放。

本文档只定义 MySQL 中的核心业务表、运行观测表、RAG 元数据表和 Agent 运行记录表。

本文档不展开：

| 内容 | 归属文档 |
| --- | --- |
| Tool 输入输出协议 | `02_TOOL_CONTRACT.md` |
| 前后端 API 契约 | `03_API_CONTRACT.md` |
| AI 辅助开发规范 | `04_VIBE_CODING_RULES.md` |
| Milvus collection schema | 后续 RAG 文档或 Milvus 初始化脚本 |
| Redis 缓存 Key 设计 | 后续缓存设计说明 |
| 完整权限系统 | 第一版暂不做 |


---

## 1. 设计目标与边界
### 1.1 设计目标
第一版数据库围绕以下功能闭环设计：

```plain
今日 API 巡检
统一登录 403 诊断
讲座报名高峰预警
本周 API 复盘
RAG 文档检索
Tool Trace 展示
Evidence List 展示
Agent 报告回放
```

数据库设计目标：

```plain
能支撑演示
能支撑 Agent Tool 查询
能支撑 Trace / Evidence
能支撑后续扩展
不做过度复杂的生产级权限和审计系统
```

---

### 1.2 用户模型边界
系统用户只分两类：

| 用户类型 | 说明 |
| --- | --- |
| `MANAGER` | 平台管理者，负责全局巡检、预警、诊断、复盘 |
| `USER` | 普通用户，可以提供 API，也可以使用 API |


API 提供者 / API 使用者不设计成独立系统角色，而通过资源关系表达：

```plain
用户提供 API：
sys_user.id = api_endpoint.provider_user_id

用户使用 API：
sys_user.id = api_consumer_app.owner_user_id

应用调用 API：
api_consumer_app.id + api_endpoint.id = api_authorization
```

第一版不设计：

```plain
sys_role
sys_user_role
sys_permission
sys_role_permission
```

---

### 1.3 分表边界
第一版不做真正的分库分表。

本文档中的“拆表”是领域拆表：

```plain
API 基础信息、调用统计、网关日志分开
Agent 会话、消息、工具调用、证据、报告分开
RAG 文档和 RAG chunk 元数据分开
```

不做：

```plain
按时间分表
按租户分库
按 API 分表
冷热数据归档表
```

---

## 2. 数据库与通用规范
### 2.1 数据库名
建议数据库名：

```sql
apihub_agent
```

---

### 2.2 表命名规范
```plain
表名：小写下划线
字段名：小写下划线
主键：id
状态字段：status
扩展字段：extra_info
创建时间：created_at
更新时间：updated_at
```

---

### 2.3 通用字段说明
大多数业务表包含以下字段：

| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 主键 |
| `status` | `VARCHAR(32)` | 是 | 视表而定 | 状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `remark` | `VARCHAR(512)` | 否 | `NULL` | 备注 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


说明：

```plain
extra_info 只保存低频、非核心、扩展字段。
禁止把核心查询字段放入 extra_info。
```

---

### 2.4 状态字段约定
| 状态值 | 说明 |
| --- | --- |
| `ACTIVE` | 正常 |
| `DISABLED` | 禁用 |
| `DELETED` | 逻辑删除 |
| `PENDING` | 待处理 |
| `SUCCESS` | 成功 |
| `FAILED` | 失败 |
| `RUNNING` | 运行中 |


具体表可以按业务缩小取值范围。

---

# 3. 表清单总览
## 3.1 P0 核心表
| 数据域 | 表名 | 作用 |
| --- | --- | --- |
| 用户域 | `sys_user` | 系统用户 |
| API 基础域 | `api_endpoint` | API 基础信息 |
| API 基础域 | `api_consumer_app` | 调用方应用 |
| API 基础域 | `api_authorization` | 应用调用 API 的授权关系 |
| API 基础域 | `rate_limit_rule` | 限流 / 降级规则 |
| 运行观测域 | `api_call_stat_hourly` | API 小时级调用统计 |
| 运行观测域 | `gateway_log` | 网关日志样例 |
| 运行观测域 | `alert_event` | 异常事件 |
| 校园事件域 | `campus_event` | 校园业务事件 |
| 校园事件域 | `event_api_relation` | 校园事件影响 API 关系 |
| RAG 元数据域 | `rag_document` | RAG 文档元数据 |
| RAG 元数据域 | `rag_chunk_meta` | RAG chunk 元数据 |
| Agent 运行域 | `agent_session` | Agent 会话 / 工作流 |
| Agent 运行域 | `agent_message` | Agent 对话消息 |
| Agent 运行域 | `tool_call_trace` | 工具调用 Trace |
| Agent 运行域 | `evidence_item` | 报告证据项 |
| Agent 运行域 | `agent_report` | Agent 报告 |


---

## 3.2 P1 可选表
| 数据域 | 表名 | 作用 |
| --- | --- | --- |
| 评测域 | `eval_case` | Agent 评测用例 |
| 评测域 | `eval_run_result` | Agent 评测结果 |


P1 表第一版可以不建完整评测系统，后续做小型评测集时再补。

但从开发和验收角度，建议先保留最小评测 / Smoke Case 清单，用于验证 Agent 是否能按预期选择工具、生成证据和保存报告。

## 3.3 P0.5 最小评测 / Smoke Case
第一版至少准备 5 条可重复执行的验收用例，不强制一开始建表，但建议后续可以平滑迁移到 `eval_case`。

| 用例 | 输入问题 | 期望工具 | 期望证据 | 期望结果 |
| --- | --- | --- | --- | --- |
| 统一登录 403 诊断 | 为什么统一登录 API 今天 403 变多了？ | `queryApiInfo`、`queryApiCallStats`、`queryGatewayLogs`、`queryApiDocs` | `API`、`STAT`、`LOG`、`DOC` | 能说明 403 是否升高、集中应用、疑似原因和建议 |
| 讲座报名高峰预警 | 本周讲座报名会影响哪些 API？ | `queryCampusEvents`、`queryApiCallStats`、`queryRateLimitRule` | `EVENT`、`STAT`、`RULE` | 能给出受影响 API、观察窗口和预警建议 |
| 今日 API 巡检 | 今天有哪些 API 需要关注？ | `queryApiCallStats`、`queryAlertEvents`、`queryGatewayLogs` | `STAT`、`ALERT`、`LOG` | 能输出高风险 API、失败率、P95 和日志样例 |
| RAG 签名规则检索 | 统一登录 API 签名失败怎么排查？ | `queryApiDocs` | `DOC` | 能命中签名规则文档并给出排查步骤 |
| 无权限查询拦截 | 普通用户查询其他团队 API 的日志 | `queryGatewayLogs` | 无 | 返回 `PERMISSION_DENIED`，不泄露日志内容 |

---

# 4. 用户域
## 4.1 `sys_user`
### 表用途
保存系统用户。第一版只区分平台管理者和普通用户。

普通用户既可以提供 API，也可以创建调用方应用来使用 API。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 用户 ID |
| `username` | `VARCHAR(64)` | 是 | 无 | 登录名，唯一 |
| `display_name` | `VARCHAR(64)` | 是 | 无 | 展示名 |
| `email` | `VARCHAR(128)` | 否 | `NULL` | 邮箱 |
| `phone` | `VARCHAR(32)` | 否 | `NULL` | 手机号 |
| `user_type` | `VARCHAR(32)` | 是 | `USER` | `MANAGER`<br/> / `USER` |
| `org_name` | `VARCHAR(128)` | 否 | `NULL` | 所属组织 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 用户状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `remark` | `VARCHAR(512)` | 否 | `NULL` | 备注 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_username` | `username` |
| `idx_user_type` | `user_type` |
| `idx_status` | `status` |
| `idx_started_at` | `started_at` |


### 示例数据
```json
{
  "id": 1,
  "username": "admin_apihub",
  "display_name": "API 平台管理员",
  "email": "admin@school.edu.cn",
  "phone": null,
  "user_type": "MANAGER",
  "org_name": "信息化中心",
  "status": "ACTIVE",
  "extra_info": {
    "avatar": "/avatar/admin.png"
  },
  "remark": "平台管理者账号"
}
```

---

# 5. API 基础域
## 5.1 `api_endpoint`
### 表用途
保存 API-HUB 中被管理的 API 基础信息。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | API ID |
| `api_code` | `VARCHAR(64)` | 是 | 无 | API 编码，唯一 |
| `api_name` | `VARCHAR(128)` | 是 | 无 | API 名称 |
| `api_type` | `VARCHAR(64)` | 是 | 无 | `AUTH`<br/> / `COURSE`<br/> / `ACTIVITY`<br/> / `NOTICE`<br/> / `RESOURCE` |
| `path` | `VARCHAR(255)` | 是 | 无 | API 路径 |
| `method` | `VARCHAR(16)` | 是 | `GET` | HTTP 方法 |
| `description` | `VARCHAR(1024)` | 否 | `NULL` | API 说明 |
| `provider_user_id` | `BIGINT` | 是 | 无 | 提供者用户 ID |
| `owner_team` | `VARCHAR(128)` | 否 | `NULL` | 维护团队 |
| `risk_level` | `VARCHAR(32)` | 是 | `LOW` | `LOW`<br/> / `MEDIUM`<br/> / `HIGH` |
| `online_status` | `VARCHAR(32)` | 是 | `ONLINE` | `ONLINE`<br/> / `OFFLINE`<br/> / `MAINTENANCE` |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 记录状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `remark` | `VARCHAR(512)` | 否 | `NULL` | 备注 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_api_code` | `api_code` |
| `idx_api_type` | `api_type` |
| `idx_provider_user` | `provider_user_id` |
| `idx_online_status` | `online_status` |


### 示例数据
```json
{
  "id": 1001,
  "api_code": "AUTH_LOGIN",
  "api_name": "学校账户授权登录",
  "api_type": "AUTH",
  "path": "/api/auth/login",
  "method": "POST",
  "description": "用于校内应用进行统一身份认证登录",
  "provider_user_id": 2,
  "owner_team": "统一身份认证中心",
  "risk_level": "HIGH",
  "online_status": "ONLINE",
  "status": "ACTIVE",
  "extra_info": {
    "tags": ["核心接口", "高频调用"],
    "doc_url": "/docs/auth-login"
  },
  "remark": "统一登录接口"
}
```

---

## 5.2 `api_consumer_app`
### 表用途
保存调用 API 的应用信息。

一个普通用户可以创建多个调用方应用。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 应用 ID |
| `app_code` | `VARCHAR(64)` | 是 | 无 | 应用编码，唯一 |
| `app_name` | `VARCHAR(128)` | 是 | 无 | 应用名称 |
| `owner_user_id` | `BIGINT` | 是 | 无 | 应用负责人用户 ID |
| `owner_team` | `VARCHAR(128)` | 否 | `NULL` | 应用维护团队 |
| `app_type` | `VARCHAR(64)` | 是 | `MINI_APP` | `MINI_APP`<br/> / `WEB`<br/> / `MOBILE`<br/> / `SERVICE` |
| `access_key` | `VARCHAR(128)` | 是 | 无 | 调用方 accessKey，演示环境可用假值 |
| `contact_email` | `VARCHAR(128)` | 否 | `NULL` | 联系邮箱 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 应用状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `remark` | `VARCHAR(512)` | 否 | `NULL` | 备注 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_app_code` | `app_code` |
| `uk_access_key` | `access_key` |
| `idx_owner_user` | `owner_user_id` |
| `idx_status` | `status` |
| `idx_started_at` | `started_at` |


### 示例数据
```json
{
  "id": 2001,
  "app_code": "COURSE_HELPER",
  "app_name": "课程助手小程序",
  "owner_user_id": 4,
  "owner_team": "课程助手团队",
  "app_type": "MINI_APP",
  "access_key": "ak_course_helper_demo",
  "contact_email": "course-helper@school.edu.cn",
  "status": "ACTIVE",
  "extra_info": {
    "scene": "课表查询、课程提醒"
  },
  "remark": "演示用调用方应用"
}
```

---

## 5.3 `api_authorization`
### 表用途
保存调用方应用和 API 的授权关系。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 授权 ID |
| `app_id` | `BIGINT` | 是 | 无 | 调用方应用 ID |
| `api_id` | `BIGINT` | 是 | 无 | API ID |
| `auth_status` | `VARCHAR(32)` | 是 | `APPROVED` | `APPROVED`<br/> / `PENDING`<br/> / `REJECTED`<br/> / `REVOKED` |
| `approved_by` | `BIGINT` | 否 | `NULL` | 审批人 |
| `approved_at` | `DATETIME` | 否 | `NULL` | 审批时间 |
| `expire_at` | `DATETIME` | 否 | `NULL` | 过期时间 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 记录状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `remark` | `VARCHAR(512)` | 否 | `NULL` | 备注 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_app_api` | `app_id`<br/>, `api_id` |
| `idx_api_id` | `api_id` |
| `idx_auth_status` | `auth_status` |


### 示例数据
```json
{
  "id": 3001,
  "app_id": 2001,
  "api_id": 1001,
  "auth_status": "APPROVED",
  "approved_by": 1,
  "approved_at": "2026-06-01 09:00:00",
  "expire_at": null,
  "status": "ACTIVE",
  "extra_info": {
    "quota_per_day": 200000
  },
  "remark": "课程助手已授权调用统一登录 API"
}
```

---

## 5.4 `rate_limit_rule`
### 表用途
保存 API 的限流、熔断、降级配置。

第一版只用于查询和建议，不自动修改生产配置。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 规则 ID |
| `api_id` | `BIGINT` | 是 | 无 | API ID |
| `rule_name` | `VARCHAR(128)` | 是 | 无 | 规则名称 |
| `qps_limit` | `INT` | 是 | `100` | QPS 限制 |
| `burst_limit` | `INT` | 是 | `200` | 突发流量上限 |
| `degrade_enabled` | `TINYINT` | 是 | `0` | 是否启用降级 |
| `fallback_message` | `VARCHAR(512)` | 否 | `NULL` | 降级提示 |
| `effective_start` | `DATETIME` | 否 | `NULL` | 生效开始时间 |
| `effective_end` | `DATETIME` | 否 | `NULL` | 生效结束时间 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 规则状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `remark` | `VARCHAR(512)` | 否 | `NULL` | 备注 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `idx_api_id` | `api_id` |
| `idx_status` | `status` |
| `idx_started_at` | `started_at` |


### 示例数据
```json
{
  "id": 4001,
  "api_id": 1001,
  "rule_name": "统一登录默认限流规则",
  "qps_limit": 300,
  "burst_limit": 600,
  "degrade_enabled": 1,
  "fallback_message": "统一登录服务繁忙，请稍后重试",
  "effective_start": null,
  "effective_end": null,
  "status": "ACTIVE",
  "extra_info": {
    "strategy": "gateway-token-bucket"
  },
  "remark": "核心登录接口限流配置"
}
```

---

# 6. API 运行观测域
## 6.1 `api_call_stat_hourly`
### 表用途
保存 API 小时级调用统计，用于巡检、诊断和周报。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 统计 ID |
| `api_id` | `BIGINT` | 是 | 无 | API ID |
| `stat_time` | `DATETIME` | 是 | 无 | 统计小时，例如 `2026-06-19 10:00:00` |
| `total_count` | `INT` | 是 | `0` | 总调用量 |
| `success_count` | `INT` | 是 | `0` | 成功次数 |
| `fail_count` | `INT` | 是 | `0` | 失败次数 |
| `error_4xx_count` | `INT` | 是 | `0` | 4xx 错误数 |
| `error_5xx_count` | `INT` | 是 | `0` | 5xx 错误数 |
| `rate_limit_count` | `INT` | 是 | `0` | 限流次数 |
| `avg_latency_ms` | `INT` | 是 | `0` | 平均响应耗时 |
| `p95_latency_ms` | `INT` | 是 | `0` | P95 响应耗时 |
| `p99_latency_ms` | `INT` | 是 | `0` | P99 响应耗时 |
| `max_latency_ms` | `INT` | 是 | `0` | 最大响应耗时 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 记录状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_api_stat_time` | `api_id`<br/>, `stat_time` |
| `idx_stat_time` | `stat_time` |
| `idx_api_time` | `api_id`<br/>, `stat_time` |


### 示例数据
```json
{
  "id": 5001,
  "api_id": 1001,
  "stat_time": "2026-06-19 10:00:00",
  "total_count": 18520,
  "success_count": 17100,
  "fail_count": 1420,
  "error_4xx_count": 1360,
  "error_5xx_count": 60,
  "rate_limit_count": 0,
  "avg_latency_ms": 96,
  "p95_latency_ms": 280,
  "p99_latency_ms": 520,
  "max_latency_ms": 1100,
  "status": "ACTIVE",
  "extra_info": {
    "top_error_code": "403"
  }
}
```

---

## 6.2 `gateway_log`
### 表用途
保存网关日志样例，用于异常诊断和 Evidence 引用。

第一版不存全量日志，只保存演示和诊断需要的结构化日志样例。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 日志 ID |
| `trace_id` | `VARCHAR(128)` | 是 | 无 | 请求链路 ID |
| `api_id` | `BIGINT` | 是 | 无 | API ID |
| `app_id` | `BIGINT` | 否 | `NULL` | 调用方应用 ID |
| `access_key` | `VARCHAR(128)` | 否 | `NULL` | 调用方 accessKey |
| `request_path` | `VARCHAR(255)` | 是 | 无 | 请求路径 |
| `request_method` | `VARCHAR(16)` | 是 | `GET` | 请求方法 |
| `http_status` | `INT` | 是 | `200` | HTTP 状态码 |
| `error_code` | `VARCHAR(64)` | 否 | `NULL` | 业务错误码 |
| `error_message` | `VARCHAR(512)` | 否 | `NULL` | 错误信息 |
| `latency_ms` | `INT` | 是 | `0` | 请求耗时 |
| `client_ip` | `VARCHAR(64)` | 否 | `NULL` | 客户端 IP |
| `request_time` | `DATETIME` | 是 | 无 | 请求时间 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 记录状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `idx_trace_id` | `trace_id` |
| `idx_api_time` | `api_id`<br/>, `request_time` |
| `idx_error_time` | `error_code`<br/>, `request_time` |
| `idx_app_time` | `app_id`<br/>, `request_time` |
| `idx_http_status` | `http_status` |


### 示例数据
```json
{
  "id": 6001,
  "trace_id": "tr_20260619_auth_001",
  "api_id": 1001,
  "app_id": 2001,
  "access_key": "ak_course_helper_demo",
  "request_path": "/api/auth/login",
  "request_method": "POST",
  "http_status": 403,
  "error_code": "SIGNATURE_INVALID",
  "error_message": "签名校验失败",
  "latency_ms": 88,
  "client_ip": "10.10.1.23",
  "request_time": "2026-06-19 10:12:30",
  "status": "ACTIVE",
  "extra_info": {
    "timestamp_diff_seconds": 420,
    "user_agent": "CourseHelper/1.0"
  }
}
```

---

## 6.3 `alert_event`
### 表用途
保存结构化异常事件，用于巡检、诊断和周报。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 告警事件 ID |
| `event_code` | `VARCHAR(64)` | 是 | 无 | 事件编码 |
| `api_id` | `BIGINT` | 是 | 无 | 相关 API |
| `event_type` | `VARCHAR(64)` | 是 | 无 | `HIGH_ERROR_RATE`<br/> / `HIGH_LATENCY`<br/> / `RATE_LIMIT_SPIKE`<br/> / `TRAFFIC_SPIKE` |
| `severity` | `VARCHAR(32)` | 是 | `MEDIUM` | `LOW`<br/> / `MEDIUM`<br/> / `HIGH` |
| `title` | `VARCHAR(255)` | 是 | 无 | 事件标题 |
| `description` | `VARCHAR(1024)` | 否 | `NULL` | 事件描述 |
| `start_time` | `DATETIME` | 是 | 无 | 开始时间 |
| `end_time` | `DATETIME` | 否 | `NULL` | 结束时间 |
| `resolved` | `TINYINT` | 是 | `0` | 是否已解决 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 记录状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_event_code` | `event_code` |
| `idx_api_time` | `api_id`<br/>, `start_time` |
| `idx_event_type` | `event_type` |
| `idx_severity` | `severity` |
| `idx_resolved` | `resolved` |


### 示例数据
```json
{
  "id": 7001,
  "event_code": "ALERT_AUTH_403_20260619",
  "api_id": 1001,
  "event_type": "HIGH_ERROR_RATE",
  "severity": "HIGH",
  "title": "统一登录 API 403 错误升高",
  "description": "10:00-11:00 期间统一登录 API 403 错误明显升高，主要集中在课程助手应用。",
  "start_time": "2026-06-19 10:00:00",
  "end_time": "2026-06-19 11:00:00",
  "resolved": 0,
  "status": "ACTIVE",
  "extra_info": {
    "main_error_code": "SIGNATURE_INVALID",
    "affected_app_id": 2001
  }
}
```

---

# 7. 校园事件域
## 7.1 `campus_event`
### 表用途
保存校园业务事件，用于事件预警和 API 波动解释。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 校园事件 ID |
| `event_code` | `VARCHAR(64)` | 是 | 无 | 事件编码 |
| `event_name` | `VARCHAR(128)` | 是 | 无 | 事件名称 |
| `event_type` | `VARCHAR(64)` | 是 | 无 | `LECTURE_SIGNUP`<br/> / `SEMESTER_START`<br/> / `EXAM_NOTICE`<br/> / `ACTIVITY_CHECKIN` |
| `description` | `VARCHAR(1024)` | 否 | `NULL` | 事件说明 |
| `start_time` | `DATETIME` | 是 | 无 | 开始时间 |
| `end_time` | `DATETIME` | 否 | `NULL` | 结束时间 |
| `location` | `VARCHAR(128)` | 否 | `NULL` | 地点 |
| `expected_traffic_level` | `VARCHAR(32)` | 是 | `MEDIUM` | 预计流量等级 |
| `source` | `VARCHAR(128)` | 否 | `NULL` | 事件来源 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 事件状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `remark` | `VARCHAR(512)` | 否 | `NULL` | 备注 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_event_code` | `event_code` |
| `idx_event_time` | `start_time`<br/>, `end_time` |
| `idx_event_type` | `event_type` |


### 示例数据
```json
{
  "id": 8001,
  "event_code": "LECTURE_SIGNUP_20260620",
  "event_name": "人工智能前沿讲座报名开放",
  "event_type": "LECTURE_SIGNUP",
  "description": "讲座报名将在 12:00 开放，预计统一登录和讲座报名 API 调用量上升。",
  "start_time": "2026-06-20 12:00:00",
  "end_time": "2026-06-20 12:30:00",
  "location": "大学城校区",
  "expected_traffic_level": "HIGH",
  "source": "校园活动系统",
  "status": "ACTIVE",
  "extra_info": {
    "expected_participants": 800
  },
  "remark": "用于讲座报名高峰预警演示"
}
```

---

## 7.2 `event_api_relation`
### 表用途
保存校园事件和可能受影响 API 的关系。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 关系 ID |
| `event_id` | `BIGINT` | 是 | 无 | 校园事件 ID |
| `api_id` | `BIGINT` | 是 | 无 | 受影响 API ID |
| `impact_type` | `VARCHAR(64)` | 是 | `TRAFFIC_INCREASE` | 影响类型 |
| `impact_level` | `VARCHAR(32)` | 是 | `MEDIUM` | `LOW`<br/> / `MEDIUM`<br/> / `HIGH` |
| `reason` | `VARCHAR(512)` | 否 | `NULL` | 影响原因 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 记录状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_event_api` | `event_id`<br/>, `api_id` |
| `idx_api_id` | `api_id` |
| `idx_impact_level` | `impact_level` |


### 示例数据
```json
{
  "id": 9001,
  "event_id": 8001,
  "api_id": 1001,
  "impact_type": "TRAFFIC_INCREASE",
  "impact_level": "HIGH",
  "reason": "讲座报名开放前后大量用户需要先完成统一登录。",
  "status": "ACTIVE",
  "extra_info": {
    "observe_window_minutes": 30
  }
}
```

---

# 8. RAG 元数据域
## 8.1 `rag_document`
### 表用途
保存上传到知识库的文档元数据。

文档原文可以保存在本地文件或对象存储，向量写入 Milvus，MySQL 保存状态和溯源信息。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 文档 ID |
| `doc_code` | `VARCHAR(64)` | 是 | 无 | 文档编码，唯一 |
| `title` | `VARCHAR(255)` | 是 | 无 | 文档标题 |
| `doc_type` | `VARCHAR(64)` | 是 | 无 | `API_DOC`<br/> / `ERROR_CODE`<br/> / `SIGN_RULE`<br/> / `SDK_GUIDE` |
| `source_type` | `VARCHAR(64)` | 是 | `LOCAL_FILE` | `LOCAL_FILE`<br/> / `OBJECT_STORAGE`<br/> / `URL` |
| `source_path` | `VARCHAR(512)` | 否 | `NULL` | 文件保存路径或来源地址 |
| `file_name` | `VARCHAR(255)` | 否 | `NULL` | 文件名 |
| `file_ext` | `VARCHAR(32)` | 否 | `NULL` | 文件扩展名 |
| `file_size` | `BIGINT` | 是 | `0` | 文件大小 |
| `content_hash` | `VARCHAR(128)` | 否 | `NULL` | 文档内容哈希，用于判断重复上传和版本变化 |
| `version_no` | `INT` | 是 | `1` | 文档版本号，同一文档重新入库时递增 |
| `uploaded_by` | `BIGINT` | 是 | 无 | 上传人 |
| `chunk_count` | `INT` | 是 | `0` | chunk 数量 |
| `embedding_model` | `VARCHAR(128)` | 是 | `text-embedding-v4` | 索引时使用的 Embedding 模型 |
| `embedding_dim` | `INT` | 是 | `1024` | 向量维度，必须和 Milvus schema 保持一致 |
| `milvus_collection` | `VARCHAR(128)` | 是 | `biz` | 写入的 Milvus collection |
| `index_status` | `VARCHAR(32)` | 是 | `PENDING` | `PENDING`<br/> / `INDEXING`<br/> / `INDEXED`<br/> / `FAILED` |
| `last_indexed_at` | `DATETIME` | 否 | `NULL` | 最近一次索引完成时间 |
| `error_message` | `VARCHAR(1024)` | 否 | `NULL` | 索引失败原因 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 记录状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `remark` | `VARCHAR(512)` | 否 | `NULL` | 备注 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_doc_code` | `doc_code` |
| `idx_doc_type` | `doc_type` |
| `idx_uploaded_by` | `uploaded_by` |
| `idx_index_status` | `index_status` |
| `idx_content_hash` | `content_hash` |
| `idx_embedding_model` | `embedding_model` |
| `idx_milvus_collection` | `milvus_collection` |


### 示例数据
```json
{
  "id": 10001,
  "doc_code": "DOC_AUTH_SIGN_RULE",
  "title": "统一登录 API 签名规则说明",
  "doc_type": "SIGN_RULE",
  "source_type": "LOCAL_FILE",
  "source_path": "./uploads/auth-sign-rule.md",
  "file_name": "auth-sign-rule.md",
  "file_ext": "md",
  "file_size": 8420,
  "content_hash": "sha256:demo_auth_sign_rule",
  "version_no": 1,
  "uploaded_by": 1,
  "chunk_count": 6,
  "embedding_model": "text-embedding-v4",
  "embedding_dim": 1024,
  "milvus_collection": "biz",
  "index_status": "INDEXED",
  "last_indexed_at": "2026-06-19 12:00:10",
  "error_message": null,
  "status": "ACTIVE",
  "extra_info": {
    "related_api_code": "AUTH_LOGIN"
  },
  "remark": "用于 403 诊断场景"
}
```

---

## 8.2 `rag_chunk_meta`
### 表用途
保存 RAG chunk 元数据和 Milvus 主键映射。

向量本体不存 MySQL。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | chunk 元数据 ID |
| `document_id` | `BIGINT` | 是 | 无 | 文档 ID |
| `chunk_index` | `INT` | 是 | 无 | chunk 序号 |
| `chunk_title` | `VARCHAR(255)` | 否 | `NULL` | chunk 标题 |
| `content_preview` | `VARCHAR(512)` | 否 | `NULL` | 内容预览 |
| `start_offset` | `INT` | 否 | `NULL` | chunk 在原文中的起始位置 |
| `end_offset` | `INT` | 否 | `NULL` | chunk 在原文中的结束位置 |
| `content_hash` | `VARCHAR(128)` | 否 | `NULL` | chunk 内容哈希 |
| `milvus_collection` | `VARCHAR(128)` | 是 | `biz` | Milvus collection |
| `milvus_chunk_id` | `VARCHAR(128)` | 是 | 无 | Milvus 中的 chunk 主键 |
| `embedding_model` | `VARCHAR(128)` | 是 | `text-embedding-v4` | chunk 向量化模型 |
| `embedding_dim` | `INT` | 是 | `1024` | chunk 向量维度 |
| `token_count` | `INT` | 是 | `0` | token 估算数量 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 记录状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_doc_chunk` | `document_id`<br/>, `chunk_index` |
| `idx_milvus_chunk_id` | `milvus_chunk_id` |
| `idx_document_id` | `document_id` |
| `idx_chunk_content_hash` | `content_hash` |


### 示例数据
```json
{
  "id": 11001,
  "document_id": 10001,
  "chunk_index": 0,
  "chunk_title": "签名校验流程",
  "content_preview": "调用统一登录 API 时，请求方需要按照 accessKey、timestamp、nonce 和请求体生成签名...",
  "start_offset": 0,
  "end_offset": 820,
  "content_hash": "sha256:demo_chunk_auth_sign_0",
  "milvus_collection": "biz",
  "milvus_chunk_id": "DOC_AUTH_SIGN_RULE_0",
  "embedding_model": "text-embedding-v4",
  "embedding_dim": 1024,
  "token_count": 320,
  "status": "ACTIVE",
  "extra_info": {
    "start_offset": 0,
    "end_offset": 820
  }
}
```

---

# 9. Agent 运行域
## 9.1 `agent_session`
### 表用途
保存一次 Agent 对话或一次 Agent 工作流。

一次巡检、诊断、预警、周报都可以对应一条 session。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 会话 ID |
| `session_code` | `VARCHAR(128)` | 是 | 无 | 会话编码，唯一 |
| `trace_id` | `VARCHAR(128)` | 是 | 无 | 一次 Agent 工作流 trace ID |
| `user_id` | `BIGINT` | 是 | 无 | 发起用户 |
| `user_type` | `VARCHAR(32)` | 是 | `USER` | 用户类型快照 |
| `session_type` | `VARCHAR(64)` | 是 | 无 | `CHAT`<br/> / `INSPECTION`<br/> / `DIAGNOSIS`<br/> / `WARNING`<br/> / `WEEKLY_REPORT` |
| `title` | `VARCHAR(255)` | 否 | `NULL` | 会话标题 |
| `workflow_name` | `VARCHAR(128)` | 否 | `NULL` | 工作流名称 |
| `status` | `VARCHAR(32)` | 是 | `RUNNING` | `RUNNING`<br/> / `SUCCESS`<br/> / `FAILED`<br/> / `CANCELLED` |
| `error_code` | `VARCHAR(64)` | 否 | `NULL` | 错误码 |
| `error_message` | `VARCHAR(1024)` | 否 | `NULL` | 错误信息 |
| `duration_ms` | `INT` | 是 | `0` | 本次 Agent 运行耗时 |
| `retry_count` | `INT` | 是 | `0` | 重试次数 |
| `last_event_seq` | `INT` | 是 | `0` | 已发送的最大 SSE 事件序号 |
| `cancelled_at` | `DATETIME` | 否 | `NULL` | 取消时间 |
| `started_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 开始时间 |
| `finished_at` | `DATETIME` | 否 | `NULL` | 结束时间 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_session_code` | `session_code` |
| `uk_trace_id` | `trace_id` |
| `idx_user_id` | `user_id` |
| `idx_session_type` | `session_type` |
| `idx_status` | `status` |
| `idx_started_at` | `started_at` |


### 示例数据
```json
{
  "id": 12001,
  "session_code": "sess_auth_403_20260619",
  "trace_id": "trace_auth_403_20260619",
  "user_id": 1,
  "user_type": "MANAGER",
  "session_type": "DIAGNOSIS",
  "title": "统一登录 API 403 异常诊断",
  "workflow_name": "login_403_diagnosis",
  "status": "SUCCESS",
  "error_code": null,
  "error_message": null,
  "duration_ms": 18000,
  "retry_count": 0,
  "last_event_seq": 18,
  "cancelled_at": null,
  "started_at": "2026-06-19 11:05:00",
  "finished_at": "2026-06-19 11:05:18",
  "extra_info": {
    "question": "为什么统一登录 API 今天 403 变多了？"
  }
}
```

---

## 9.2 `agent_message`
### 表用途
保存 Agent 会话中的用户消息和助手回答。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 消息 ID |
| `session_id` | `BIGINT` | 是 | 无 | 会话 ID |
| `message_role` | `VARCHAR(32)` | 是 | 无 | `USER`<br/> / `ASSISTANT`<br/> / `SYSTEM` |
| `content` | `TEXT` | 是 | 无 | 消息内容 |
| `message_order` | `INT` | 是 | `0` | 会话内顺序 |
| `status` | `VARCHAR(32)` | 是 | `SUCCESS` | 消息状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `idx_session_order` | `session_id`<br/>, `message_order` |
| `idx_message_role` | `message_role` |


### 示例数据
```json
{
  "id": 13001,
  "session_id": 12001,
  "message_role": "USER",
  "content": "为什么统一登录 API 今天 403 变多了？",
  "message_order": 1,
  "status": "SUCCESS",
  "extra_info": {
    "user_type": "MANAGER"
  }
}
```

---

## 9.3 `tool_call_trace`
### 表用途
记录 Agent 调用了哪些工具、参数、结果、耗时和错误。

这是第一版 Agent 工程化展示的核心表。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | Trace 记录 ID |
| `session_id` | `BIGINT` | 是 | 无 | 会话 ID |
| `trace_id` | `VARCHAR(128)` | 是 | 无 | Agent 工作流 trace ID |
| `span_id` | `VARCHAR(128)` | 是 | 无 | 当前工具调用 span ID |
| `parent_span_id` | `VARCHAR(128)` | 否 | `NULL` | 父 span ID |
| `tool_name` | `VARCHAR(128)` | 是 | 无 | 工具名称 |
| `tool_type` | `VARCHAR(64)` | 是 | `LOCAL` | `LOCAL`<br/> / `RAG`<br/> / `MCP`<br/> / `REPORT` |
| `input_json` | `JSON` | 否 | `NULL` | 工具输入 |
| `output_json` | `JSON` | 否 | `NULL` | 工具输出 |
| `latency_ms` | `INT` | 是 | `0` | 耗时 |
| `success` | `TINYINT` | 是 | `1` | 是否成功 |
| `error_code` | `VARCHAR(64)` | 否 | `NULL` | 错误码 |
| `error_message` | `VARCHAR(1024)` | 否 | `NULL` | 错误信息 |
| `status` | `VARCHAR(32)` | 是 | `SUCCESS` | `SUCCESS`<br/> / `FAILED` |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `idx_session_id` | `session_id` |
| `idx_trace_id` | `trace_id` |
| `idx_tool_name` | `tool_name` |
| `idx_status` | `status` |
| `idx_started_at` | `started_at` |


### 示例数据
```json
{
  "id": 14001,
  "session_id": 12001,
  "trace_id": "trace_auth_403_20260619",
  "span_id": "span_query_gateway_logs_001",
  "parent_span_id": null,
  "tool_name": "queryGatewayLogs",
  "tool_type": "LOCAL",
  "input_json": {
    "apiCode": "AUTH_LOGIN",
    "errorCode": "403",
    "startTime": "2026-06-19 10:00:00",
    "endTime": "2026-06-19 11:00:00"
  },
  "output_json": {
    "matchedCount": 20,
    "mainErrorCode": "SIGNATURE_INVALID",
    "topAppCode": "COURSE_HELPER"
  },
  "latency_ms": 126,
  "success": 1,
  "error_code": null,
  "error_message": null,
  "status": "SUCCESS",
  "extra_info": {
    "evidenceGenerated": true
  }
}
```

---

## 9.4 `evidence_item`
### 表用途
保存 Agent 报告中的证据项。

Evidence 用来说明报告结论来自哪里，避免 Agent 编造结论。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 证据 ID |
| `session_id` | `BIGINT` | 是 | 无 | 会话 ID |
| `trace_id` | `VARCHAR(128)` | 是 | 无 | trace ID |
| `report_id` | `BIGINT` | 否 | `NULL` | 报告 ID |
| `source_type` | `VARCHAR(64)` | 是 | 无 | `STAT`<br/> / `LOG`<br/> / `DOC`<br/> / `EVENT`<br/> / `RULE`<br/> / `ALERT` |
| `source_id` | `VARCHAR(128)` | 否 | `NULL` | 来源记录 ID |
| `title` | `VARCHAR(255)` | 是 | 无 | 证据标题 |
| `content` | `TEXT` | 是 | 无 | 证据内容 |
| `confidence` | `DECIMAL(5,4)` | 否 | `NULL` | 置信度，RAG 可使用 |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 记录状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `idx_session_id` | `session_id` |
| `idx_trace_id` | `trace_id` |
| `idx_report_id` | `report_id` |
| `idx_source_type` | `source_type` |


### 示例数据
```json
{
  "id": 15001,
  "session_id": 12001,
  "trace_id": "trace_auth_403_20260619",
  "report_id": null,
  "source_type": "LOG",
  "source_id": "6001",
  "title": "统一登录 API 403 日志样例",
  "content": "10:12:30 课程助手应用调用统一登录 API 返回 403，错误码为 SIGNATURE_INVALID。",
  "confidence": null,
  "status": "ACTIVE",
  "extra_info": {
    "traceId": "tr_20260619_auth_001",
    "appCode": "COURSE_HELPER"
  }
}
```

---

## 9.5 `agent_report`
### 表用途
保存 Agent 生成的巡检报告、诊断报告、预警报告和周报。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 报告 ID |
| `report_code` | `VARCHAR(128)` | 是 | 无 | 报告编码，唯一 |
| `session_id` | `BIGINT` | 是 | 无 | 所属会话 |
| `trace_id` | `VARCHAR(128)` | 是 | 无 | trace ID |
| `report_type` | `VARCHAR(64)` | 是 | 无 | `DAILY_INSPECTION`<br/> / `DIAGNOSIS`<br/> / `WARNING`<br/> / `WEEKLY_REVIEW` |
| `title` | `VARCHAR(255)` | 是 | 无 | 报告标题 |
| `summary` | `VARCHAR(1024)` | 否 | `NULL` | 摘要 |
| `risk_level` | `VARCHAR(32)` | 是 | `LOW` | `LOW`<br/> / `MEDIUM`<br/> / `HIGH` |
| `content_md` | `MEDIUMTEXT` | 是 | 无 | Markdown 报告正文 |
| `created_by` | `BIGINT` | 是 | 无 | 创建人 |
| `evidence_count` | `INT` | 是 | `0` | 报告引用的证据数量 |
| `tool_call_count` | `INT` | 是 | `0` | 报告关联的工具调用数量 |
| `duration_ms` | `INT` | 是 | `0` | 报告生成耗时 |
| `status` | `VARCHAR(32)` | 是 | `SUCCESS` | `GENERATING`<br/> / `SUCCESS`<br/> / `FAILED` |
| `error_code` | `VARCHAR(64)` | 否 | `NULL` | 生成失败错误码 |
| `error_message` | `VARCHAR(1024)` | 否 | `NULL` | 生成失败错误信息 |
| `generated_at` | `DATETIME` | 否 | `NULL` | 报告生成完成时间 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `remark` | `VARCHAR(512)` | 否 | `NULL` | 备注 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 主要索引
| 索引 | 字段 |
| --- | --- |
| `uk_report_code` | `report_code` |
| `idx_session_id` | `session_id` |
| `idx_trace_id` | `trace_id` |
| `idx_report_type` | `report_type` |
| `idx_created_by` | `created_by` |
| `idx_generated_at` | `generated_at` |


### 示例数据
```json
{
  "id": 16001,
  "report_code": "RPT_AUTH_403_20260619",
  "session_id": 12001,
  "trace_id": "trace_auth_403_20260619",
  "report_type": "DIAGNOSIS",
  "title": "统一登录 API 403 异常诊断报告",
  "summary": "统一登录 API 403 错误主要集中在课程助手应用，主要原因疑似签名校验失败。",
  "risk_level": "HIGH",
  "content_md": "## 结论\n统一登录 API 今日 403 错误升高...\n\n## 关键证据\n1. 网关日志显示 SIGNATURE_INVALID...",
  "created_by": 1,
  "evidence_count": 3,
  "tool_call_count": 4,
  "duration_ms": 18000,
  "status": "SUCCESS",
  "error_code": null,
  "error_message": null,
  "generated_at": "2026-06-19 11:05:18",
  "extra_info": {
    "source": "agent_sse"
  },
  "remark": "演示用诊断报告"
}
```

---

# 10. P1 评测域，可选
## 10.1 `eval_case`
### 表用途
保存 Agent 小型评测用例。

第一版可以不建完整评测系统，但建议至少用 seed 数据、脚本或 Markdown 清单保留最小评测用例。后续需要评测平台时，再迁移到本表。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 用例 ID |
| `case_code` | `VARCHAR(128)` | 是 | 无 | 用例编码 |
| `question` | `VARCHAR(1024)` | 是 | 无 | 用户问题 |
| `expected_tools` | `JSON` | 否 | `NULL` | 期望调用工具 |
| `expected_evidence_types` | `JSON` | 否 | `NULL` | 期望证据类型 |
| `expected_keywords` | `JSON` | 否 | `NULL` | 期望回答关键词 |
| `expected_report_saved` | `TINYINT` | 是 | `0` | 是否期望生成报告 |
| `case_type` | `VARCHAR(64)` | 是 | 无 | `DIAGNOSIS`<br/> / `RAG`<br/> / `WARNING` |
| `status` | `VARCHAR(32)` | 是 | `ACTIVE` | 状态 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |


### 示例数据
```json
{
  "id": 17001,
  "case_code": "CASE_AUTH_403_001",
  "question": "为什么统一登录 API 今天 403 变多了？",
  "expected_tools": ["queryApiInfo", "queryApiCallStats", "queryGatewayLogs", "queryApiDocs"],
  "expected_evidence_types": ["API", "STAT", "LOG", "DOC"],
  "expected_keywords": ["403", "签名校验失败", "课程助手"],
  "expected_report_saved": 1,
  "case_type": "DIAGNOSIS",
  "status": "ACTIVE",
  "extra_info": {
    "difficulty": "medium"
  }
}
```

---

## 10.2 `eval_run_result`
### 表用途
保存某次评测运行结果。

### 字段设计
| 字段 | 类型 | 必须 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 是 | 自增 | 结果 ID |
| `case_id` | `BIGINT` | 是 | 无 | 评测用例 ID |
| `session_id` | `BIGINT` | 否 | `NULL` | 对应 Agent session |
| `run_status` | `VARCHAR(32)` | 是 | `SUCCESS` | 运行状态 |
| `tool_match_score` | `DECIMAL(5,4)` | 否 | `NULL` | 工具选择得分 |
| `answer_score` | `DECIMAL(5,4)` | 否 | `NULL` | 回答质量得分 |
| `error_message` | `VARCHAR(1024)` | 否 | `NULL` | 错误信息 |
| `extra_info` | `JSON` | 否 | `NULL` | 扩展信息 |
| `created_at` | `DATETIME` | 是 | `CURRENT_TIMESTAMP` | 创建时间 |


### 示例数据
```json
{
  "id": 18001,
  "case_id": 17001,
  "session_id": 12001,
  "run_status": "SUCCESS",
  "tool_match_score": 1.0000,
  "answer_score": 0.8500,
  "error_message": null,
  "extra_info": {
    "missedKeywords": [],
    "actualTools": ["queryApiCallStats", "queryGatewayLogs", "queryApiDocs"]
  }
}
```

---

## 10.3 最小评测结果判断
第一版评测不追求复杂自动打分，优先判断以下 4 类结果：

| 维度 | 判断方式 | 说明 |
| --- | --- | --- |
| 工具选择 | 实际调用工具是否覆盖 `expected_tools` | 少调关键工具通常说明 Prompt 或 Tool 描述不足 |
| 证据生成 | Evidence 类型是否覆盖 `expected_evidence_types` | 防止 Agent 只自由总结、不留依据 |
| 结论关键词 | 回答或报告是否包含 `expected_keywords` | 用于快速 smoke test，不替代人工评估 |
| 安全边界 | 是否拒绝越权或高风险动作 | 第一版必须优先保证只读、安全、可解释 |

---

# 11. 核心查询模式
## 11.1 今日巡检
### 查询目标
查看今天哪些 API 调用量高、失败率高、P95 高、限流次数多。

### 涉及表
```plain
api_endpoint
api_call_stat_hourly
alert_event
rate_limit_rule
```

### 典型查询条件
```plain
stat_time between 今天 00:00 and 当前时间
status = ACTIVE
```

---

## 11.2 统一登录 403 诊断
### 查询目标
解释统一登录 API 的 403 错误为什么升高。

### 涉及表
```plain
api_endpoint
api_call_stat_hourly
gateway_log
api_consumer_app
rag_document
rag_chunk_meta
tool_call_trace
evidence_item
agent_report
```

### 典型查询条件
```plain
api_code = AUTH_LOGIN
http_status = 403
request_time between 指定时间范围
```

---

## 11.3 讲座报名高峰预警
### 查询目标
结合讲座报名事件，判断哪些 API 可能受影响。

### 涉及表
```plain
campus_event
event_api_relation
api_endpoint
api_call_stat_hourly
rate_limit_rule
```

---

## 11.4 周报复盘
### 查询目标
汇总一周内 API 运行情况、高并发事件和异常事件。

### 涉及表
```plain
api_call_stat_hourly
alert_event
campus_event
event_api_relation
agent_report
```

---

## 11.5 Trace / Evidence 回放
### 查询目标
展示某次 Agent 分析调用了哪些工具、每个结论的证据是什么。

### 涉及表
```plain
agent_session
tool_call_trace
evidence_item
agent_report
```

---

# 12. 初始化数据设计
第一版 seed 数据必须覆盖以下场景。

## 12.1 用户数据
| 用户 | 用户类型 | 组织 |
| --- | --- | --- |
| `admin_apihub` | `MANAGER` | 信息化中心 |
| `provider_auth` | `USER` | 统一身份认证中心 |
| `provider_lecture` | `USER` | 学术讲座系统团队 |
| `consumer_course_helper` | `USER` | 课程助手团队 |
| `consumer_club` | `USER` | 社团活动系统 |


---

## 12.2 API 数据
| API | 类型 | 提供者 | 关注点 |
| --- | --- | --- | --- |
| 学校账户授权登录 | `AUTH` | `provider_auth` | 401 / 403、签名失败、Token 过期 |
| 今日课表 | `COURSE` | `provider_auth`<br/> 或教务团队 | 开学高峰、缓存压力 |
| 讲座活动 | `ACTIVITY` | `provider_lecture` | 报名高峰、响应变慢 |
| 讲座报名 | `ACTIVITY` | `provider_lecture` | 并发报名、限流 |
| 校园通知 | `NOTICE` | 信息化中心 | 通知发布后短时间访问增加 |


---

## 12.3 演示异常数据
| 场景 | 数据要求 |
| --- | --- |
| 统一登录 403 诊断 | 10:00-11:00 403 增多，集中在课程助手应用 |
| 讲座报名高峰 | 12:00 后讲座报名相关 API 调用量上升，P95 上升 |
| 今日课表巡检 | 今日课表 API 调用量高，但失败率稳定 |
| 周报复盘 | 一周内有 2～3 个高并发事件和 1～2 个异常事件 |
| RAG 检索 | 准备签名规则、错误码说明、讲座报名 API 文档 |


---

# 13. Schema 变更约束
后续使用 Codex 或手工修改 schema 时，必须遵守：

```plain
1. 不允许随意删除字段。
2. 不允许随意改字段含义。
3. 新增核心字段必须更新本文档。
4. 新增字段如参与查询，必须补充索引说明。
5. 只作为扩展展示的信息放 extra_info。
6. 核心查询字段不得放入 extra_info。
7. 表结构变更后必须同步更新 seed.sql。
8. Tool Contract 和 API Contract 中引用字段时，必须和本文档保持一致。
```

---

# 14. 与其他文档的边界
```plain
01_DB_SCHEMA.md：
定义数据事实。

02_TOOL_CONTRACT.md：
定义 Agent 能调用哪些工具、输入输出是什么。

03_API_CONTRACT.md：
定义前后端接口路径、请求体、响应体和 SSE 事件。

04_VIBE_CODING_RULES.md：
定义 GPT / Codex 如何受控参与开发。
```

一句话：

```plain
01 约束数据结构；
02 约束工具能力；
03 约束接口交互；
04 约束开发方式。
```

---

# 15. Scenario Runner Persistence Tables

This section records the Scenario Runner persistence tables introduced for later asynchronous traffic simulation. The first version still keeps scenario definitions in code configuration. It does not add a `scenario_definition` table.

## 15.1 `scenario_run`

Purpose: one row per Scenario Runner batch. It stores launch parameters, run status, progress counters, timestamps, and the final run-level summary.

Status values:

```text
PENDING
RUNNING
COMPLETED
FAILED
CANCELLED
```

Fields:

| Field | Type | Description |
| --- | --- | --- |
| `id` | `BIGINT` | Internal primary key |
| `scenario_run_id` | `VARCHAR(64)` | Unique external run ID returned to callers |
| `scenario_id` | `VARCHAR(64)` | Scenario code maintained by code config |
| `status` | `VARCHAR(32)` | Run status |
| `target_gateway_base_url` | `VARCHAR(255)` | Target apihub-server Gateway Invoke base URL |
| `logical_duration_seconds` | `INT` | Logical scenario duration |
| `time_scale` | `DECIMAL(10,2)` | Time compression ratio |
| `ramp_up_seconds` | `INT` | Ramp-up logical seconds |
| `steady_seconds` | `INT` | Steady or peak logical seconds |
| `ramp_down_seconds` | `INT` | Ramp-down logical seconds |
| `base_rps` | `DECIMAL(10,2)` | Base requests per second |
| `peak_rps` | `DECIMAL(10,2)` | Peak requests per second |
| `max_concurrency` | `INT` | Maximum concurrent calls |
| `random_seed` | `BIGINT` | Seed for repeatable traffic |
| `total_planned_requests` | `INT` | Planned request count |
| `total_sent_requests` | `INT` | Sent request count |
| `success_count` | `INT` | Successful call count |
| `fail_count` | `INT` | Failed call count |
| `started_at` | `DATETIME` | Run start time |
| `finished_at` | `DATETIME` | Run finish time |
| `result_summary` | `JSON` | Final summarized result |
| `error_message` | `VARCHAR(1024)` | Run-level error message |
| `extra_info` | `JSON` | Extension info |
| `created_at` | `DATETIME` | Created time |
| `updated_at` | `DATETIME` | Updated time |

Indexes:

| Index | Fields |
| --- | --- |
| `uk_scenario_run_id` | `scenario_run_id` |
| `idx_scenario_id_status` | `scenario_id`, `status` |
| `idx_status_created_at` | `status`, `created_at` |
| `idx_created_at` | `created_at` |

## 15.2 `scenario_call_sample`

Purpose: a small sampled subset of calls made during a Scenario Runner batch. Full per-call facts remain in `gateway_log`; this table is only for quick inspection and does not replace `gateway_log`.

Fields:

| Field | Type | Description |
| --- | --- | --- |
| `id` | `BIGINT` | Internal primary key |
| `scenario_run_id` | `VARCHAR(64)` | Related Scenario Runner run ID |
| `sequence_no` | `INT` | Sequence number inside the run |
| `api_code` | `VARCHAR(64)` | API code |
| `app_code` | `VARCHAR(64)` | Consumer app code |
| `mock_scenario` | `VARCHAR(64)` | Mock scenario code |
| `phase` | `VARCHAR(32)` | Traffic phase such as ramp-up, steady, peak, or ramp-down |
| `trace_id` | `VARCHAR(128)` | Trace ID returned by Gateway Invoke |
| `request_id` | `VARCHAR(64)` | Request ID sent to Gateway Invoke |
| `gateway_log_id` | `BIGINT` | Related `gateway_log.id` when available |
| `upstream_status` | `INT` | Upstream HTTP status |
| `latency_ms` | `INT` | Gateway Invoke latency |
| `success` | `TINYINT(1)` | Whether the sampled call succeeded |
| `error_code` | `VARCHAR(128)` | Gateway or upstream error code |
| `called_at` | `DATETIME` | Call time |
| `extra_info` | `JSON` | Extension info |
| `created_at` | `DATETIME` | Created time |

Indexes:

| Index | Fields |
| --- | --- |
| `idx_run_sequence` | `scenario_run_id`, `sequence_no` |
| `idx_run_api` | `scenario_run_id`, `api_code` |
| `idx_trace_id` | `trace_id` |
| `idx_gateway_log_id` | `gateway_log_id` |
| `idx_called_at` | `called_at` |

## 15.3 Relationship to runtime observation tables

- `gateway_log`: full fact table for every Gateway Invoke call.
- `scenario_run`: one Scenario Runner batch status and summary row.
- `scenario_call_sample`: small sampled rows for inspection; it does not replace `gateway_log`.
- `api_call_stat_hourly`: still populated later by Stats Aggregator from `gateway_log`.
- `alert_event`: still populated later by Alert Evaluator.
## Mock Scenario Runner v1 Tables

### mock_scenario_run

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| id | BIGINT | Yes | auto | Primary key |
| scenario_run_id | VARCHAR(96) | Yes | - | External run ID, unique |
| profile_code | VARCHAR(64) | Yes | - | Scenario profile code |
| mode | VARCHAR(32) | Yes | - | FAST_DEMO / NORMAL_DEMO |
| status | VARCHAR(32) | Yes | - | RUNNING / COMPLETED / FAILED / STOPPED |
| target_gateway_base_url | VARCHAR(255) | Yes | - | Target 8080 Gateway base URL |
| duration_seconds | INT | Yes | - | Planned duration |
| random_seed | BIGINT | No | NULL | Repeatable random seed |
| rps_scale | DECIMAL(10,2) | Yes | 1.00 | RPS scale |
| start_time / end_time | DATETIME | No | NULL | Run timestamps |
| total_request_count / success_count / fail_count | INT | Yes | 0 | Sender counters |
| extra_json | JSON | No | NULL | Extension fields |

Indexes: `uk_scenario_run_id`, `idx_profile_mode`, `idx_status`, `idx_created_at`.

Example: `mock_lecture_registration_peak_xxx`, profile `LECTURE_REGISTRATION_PEAK`, mode `FAST_DEMO`, duration `300`.

### mock_scenario_client_request_log

Records every request sent by 8090.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| scenario_run_id | VARCHAR(96) | Yes | - | Run ID |
| request_id | VARCHAR(96) | Yes | - | Sender request ID |
| trace_id | VARCHAR(96) | No | NULL | Gateway trace ID |
| profile_code / mode / phase_code | VARCHAR | Yes | - | Scenario context |
| api_code / caller_app_code / mock_scenario | VARCHAR | Yes | - | Target API and scenario |
| target_gateway_url | VARCHAR(255) | Yes | - | Gateway invoke URL |
| send_time | DATETIME | Yes | - | Send time |
| gateway_response_status / gateway_response_code | INT / VARCHAR | No | NULL | Gateway result |
| gateway_latency_ms | INT | No | NULL | Sender observed latency |
| success | TINYINT(1) | Yes | 0 | Sender success flag |
| error_message | VARCHAR(512) | No | NULL | Sender error |
| extra_json | JSON | No | NULL | Extension fields |

Indexes: `idx_scenario_run_id`, `idx_request_id`, `idx_phase_api`, `idx_mock_scenario`.

### mock_campus_api_request_log

Records requests actually received by 8091.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| scenario_run_id / request_id / trace_id | VARCHAR | Yes/No | - | Correlation IDs |
| phase_code / api_code / mock_scenario | VARCHAR | Yes | - | Scenario context |
| receive_time | DATETIME | Yes | - | Upstream receive time |
| response_status | INT | Yes | - | Returned HTTP status |
| business_code | VARCHAR(64) | No | NULL | Business response code |
| latency_ms | INT | No | NULL | Mock latency |
| response_type | VARCHAR(64) | Yes | - | NORMAL / AUTH / RATE_LIMIT / TIMEOUT / SERVER_ERROR |
| failure_source | VARCHAR(32) | Yes | NONE | NONE / GATEWAY / UPSTREAM / CALLER |
| extra_json | JSON | No | NULL | Extension fields |

Indexes: `idx_scenario_run_id`, `idx_request_id`, `idx_phase_api`, `idx_response_status`, `idx_failure_source`.

## Adaptive Passive Alert Monitor v1 Tables

### passive_monitor_event

Purpose: stores the lifecycle of passive monitor events created from Gateway request-completion signals. It is not a replacement for `gateway_log`; it stores alert-level summaries and state.

Key fields:

| Field | Type | Notes |
| --- | --- | --- |
| monitor_event_id | VARCHAR(96) | External event ID |
| alert_event_id | BIGINT | Optional linked `alert_event.id` |
| alert_type | VARCHAR(64) | HIGH_ERROR_RATE / HIGH_RATE_LIMIT / AUTH_FAILURE_SPIKE / HIGH_5XX_RATE / TRAFFIC_SPIKE / HIGH_LATENCY |
| risk_level | VARCHAR(32) | WARNING / CRITICAL |
| event_status | VARCHAR(32) | FIRING / COOLDOWN / RESOLVED |
| api_code / caller_app_code | VARCHAR | Grouping dimensions |
| dedup_key | VARCHAR(192) | `apiCode + alertType + callerAppCode` |
| first_trigger_time / last_trigger_time / resolved_time | DATETIME | Lifecycle timestamps |
| window_start_time / window_end_time | DATETIME | Latest trigger window |
| context_start_time / context_end_time | DATETIME | Baseline/context window |
| request_count / error_count / rate fields | INT / DECIMAL | Latest window metrics |
| p95_latency_ms | INT | Latest window p95 |
| cooldown_until | DATETIME | Runtime cooldown boundary |
| extra_json | JSON | Threshold, evidence and MQ-reserve notes |

Indexes: `uk_monitor_event_id`, `idx_dedup_status`, `idx_api_time`, `idx_status_time`, `idx_alert_type_time`.

### passive_alert_snapshot

Purpose: stores bounded metric snapshots for trigger/context/recovery/close summary. Full request replay remains in `gateway_log`.

Snapshot types:

```text
TRIGGER_WINDOW
CONTEXT_BEFORE
RECOVERY_WINDOW
CLOSE_SUMMARY
```

Key fields:

| Field | Type | Notes |
| --- | --- | --- |
| snapshot_id | VARCHAR(96) | External snapshot ID |
| monitor_event_id | VARCHAR(96) | Parent event ID |
| snapshot_time / snapshot_type | DATETIME / VARCHAR | Snapshot metadata |
| api_code / caller_app_code | VARCHAR | Dimensions |
| window_start_time / window_end_time | DATETIME | Snapshot window |
| request_count / success_count / error_count / error_rate | INT / DECIMAL | Snapshot metrics |
| *_distribution_json | JSON | Status, business code, caller app distributions |
| sample_request_ids_json | JSON | Bounded request ID sample |
| threshold_snapshot_json | JSON | Rule threshold at trigger time |

Indexes: `uk_snapshot_id`, `idx_monitor_event_id`, `idx_snapshot_type`, `idx_snapshot_time`.
