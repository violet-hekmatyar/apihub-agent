# API-HUB 外部业务 API 目录与业务场景

## 0. 文档定位

本文档定义 API-HUB 当前管理的外部业务 API、业务用途、调用方、典型风险场景和 Tool 诊断方向。

本文档只回答“管理哪些外部 API、这些 API 在业务上代表什么”。

本文档不定义：

```text
统一调用格式；
Mock Provider 实现细节；
Scenario Runner 流量参数；
Agent SSE 事件；
Smoke 测试脚本。
```

---

## 1. 外部 API 与内部能力边界

`api_endpoint` 只存放被 API-HUB 管理的外部业务 API。

以下能力属于 API-HUB 内部能力，不应作为外部业务 API 写入 `api_endpoint`：

```text
RAG search
Agent Run
Tool Chain Eval
Trace query
Evidence query
Dashboard query
Report query
API 文档检索能力本身
Scenario Runner
Gateway Invoke
```

说明：

```text
queryApiDocs 是内部 Tool，用于读取外部 API 的文档。
文档描述的对象可以是 AUTH_LOGIN、LECTURE_REGISTER 等外部 API。
但“文档检索能力本身”不是外部 API。
```

---

## 2. 外部 API 清单

| API Code | API Name | 主要调用方 | 业务用途 | 常见风险场景 | 相关 Tool |
|---|---|---|---|---|---|
| `AUTH_LOGIN` | 统一登录 API | 课表助手、讲座门户、社团活动系统 | 校园账号登录、Token 刷新、统一身份认证 | 401/403 增多、签名失败、Token 过期、时间戳/nonce 异常、未知应用高频失败 | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryApiDocs`, `queryAlertEvents` |
| `COURSE_TODAY` | 今日课表 API | 课表助手小程序 | 学生日课表、教室、教学周信息 | 开学高峰、缓存压力、P95/P99 上升、教务系统慢 | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents` |
| `LECTURE_LIST` | 讲座列表 API | 学院公众号、社团活动系统、讲座门户 | 讲座列表、讲座详情、报名状态 | 通知发布后读流量上升、热点讲座查询、缓存命中下降 | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryCampusEvents` |
| `LECTURE_REGISTER` | 讲座报名 API | 讲座门户、活动报名系统 | 学生讲座报名、名额竞争 | 报名开放窗口高并发、限流、重复提交、名额竞争、P95 上升 | `queryApiInfo`, `queryApiCallStats`, `queryRateLimitRule`, `queryGatewayLogs`, `queryCampusEvents`, `queryApiDocs` |
| `CAMPUS_NOTICE` | 校园通知 API | 课表助手、学生服务门户、学院站点 | 校园公告、考试通知、学院通知 | 公告发布后读流量突增、缓存过期、热点通知慢响应 | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents` |
| `VENUE_RESERVE` | 场地预约 API | 学生服务门户、社团活动系统 | 教室、报告厅、实验室、活动场地预约 | 开放预约窗口高并发、重复提交、幂等风险、409/429 增多 | `queryApiInfo`, `queryApiCallStats`, `queryRateLimitRule`, `queryGatewayLogs`, `queryApiDocs`, `queryAlertEvents` |
| `LIBRARY_BORROW` | 图书借阅 API | 图书馆小程序、学习助手 | 借阅记录、到期提醒、续借状态 | 图书馆下游服务慢、依赖超时、5xx 增多、依赖不可用 | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryApiDocs`, `queryAlertEvents` |

---

## 3. 业务场景矩阵

| 场景类型 | 涉及 API | 主要事实来源 | 推荐 Tool | Agent 分析方向 |
|---|---|---|---|---|
| 认证失败 / 签名错误 | `AUTH_LOGIN` | 403/401 统计、网关日志、签名规则文档、告警 | `queryApiInfo`, `queryApiCallStats`, `queryGatewayLogs`, `queryApiDocs`, `queryAlertEvents` | 判断调用方集中度、错误码分布，检查签名算法、时间戳、nonce、Token、secret 配置 |
| 业务高峰 / 高并发 | `LECTURE_REGISTER`, `AUTH_LOGIN`, `LECTURE_LIST` | 校园事件、调用统计、429 日志、限流规则 | `queryCampusEvents`, `queryApiCallStats`, `queryRateLimitRule`, `queryGatewayLogs` | 判断是否由讲座报名窗口驱动，观察认证、列表查询、报名提交联合压力 |
| 缓存压力 / 慢响应 | `COURSE_TODAY`, `CAMPUS_NOTICE` | 高延迟统计、缓存 miss 日志、下游慢日志 | `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents` | 区分流量上升与失败率上升，关注缓存命中率和下游响应时间 |
| 限流触发 | `LECTURE_REGISTER`, `VENUE_RESERVE` | 限流规则、429 日志、rate limit count | `queryRateLimitRule`, `queryApiCallStats`, `queryGatewayLogs` | 判断限流阈值是否匹配开放窗口流量，识别被限流调用方 |
| 下游依赖异常 | `LIBRARY_BORROW` | 5xx/504 统计、超时日志、依赖告警、排障文档 | `queryApiCallStats`, `queryGatewayLogs`, `queryAlertEvents`, `queryApiDocs` | 归因到图书馆下游依赖，建议超时、降级和重试策略 |
| 重复请求 / 幂等风险 | `VENUE_RESERVE`, `LECTURE_REGISTER` | 409 日志、缺失幂等键、重复提交样本、文档 | `queryApiCallStats`, `queryGatewayLogs`, `queryRateLimitRule`, `queryApiDocs` | 检查前端重试、幂等键、业务锁、冲突处理 |
| 权限边界拒绝 | 任意跨团队 API 日志 | Tool 权限失败 trace | 对应 Tool | 普通用户不能查看无权限 API 日志，应返回 `PERMISSION_DENIED` |

---

## 4. 与 Mock Provider 的关系

Mock Provider 使用本文档中的 7 个 API 作为模拟对象。

对应关系：

| API Code | Mock Provider Path |
|---|---|
| `AUTH_LOGIN` | `POST /mock-provider/auth/login` |
| `COURSE_TODAY` | `GET /mock-provider/course/today` |
| `LECTURE_LIST` | `GET /mock-provider/lecture/list` |
| `LECTURE_REGISTER` | `POST /mock-provider/lecture/register` |
| `CAMPUS_NOTICE` | `GET /mock-provider/notice/list` |
| `VENUE_RESERVE` | `POST /mock-provider/venue/reserve` |
| `LIBRARY_BORROW` | `GET /mock-provider/library/borrow` |

Gateway Invoke 默认 appCode 映射如下，需与 seed 授权关系保持一致：

| API Code | Default App Code |
|---|---|
| `AUTH_LOGIN` | `COURSE_HELPER` |
| `COURSE_TODAY` | `COURSE_HELPER` |
| `LECTURE_LIST` | `LECTURE_PORTAL` |
| `LECTURE_REGISTER` | `LECTURE_PORTAL` |
| `CAMPUS_NOTICE` | `STUDENT_SERVICE` |
| `VENUE_RESERVE` | `CLUB_ACTIVITY` |
| `LIBRARY_BORROW` | `LIBRARY_MINI` |

注意：

```text
Mock Provider 的路径不是 api_endpoint 中的生产 URL。
它只是本项目用于模拟外部 API 行为的开发态服务。
真正的调用必须通过 API-HUB Gateway Invoke 代理。
```

---

## 5. 与 seed 数据和生成数据的关系

当前早期版本使用 MySQL seed 数据支持 Tool 查询和 Agent 骨架验证。

后续推荐逐步升级为：

```text
Scenario Runner 调用 Mock Provider
→ Gateway Invoke 记录 gateway_log
→ Stats Aggregator 聚合 api_call_stat_hourly
→ Alert Evaluator 生成 alert_event
→ Tools 查询新生成事实
→ Agent Run 诊断
```

因此本文档中的外部 API 目录既服务于当前 seed 数据，也服务于后续真实模拟调用链路。
