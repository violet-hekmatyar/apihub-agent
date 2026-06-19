# 04_VIBE_CODING_RULES.md
## 0. 文档定位
本文档是 API-HUB Agent 项目的内部 AI 辅助开发规范。

本文档只规定：

1. 如何使用 GPT / Codex 辅助开发；
2. 如何组织 AI 开发上下文；
3. 如何按功能闭环推进；
4. 如何审查 AI 生成代码；
5. 如何进行自动化验收；
6. 如何进行 Git 留痕。

本文档不负责定义：

| 内容 | 归属文档 |
| --- | --- |
| 数据库表、字段、索引、初始化数据 | `01_DB_SCHEMA.md` |
| Agent Tool 输入输出协议 | `02_TOOL_CONTRACT.md` |
| 前后端接口路径、请求响应格式 | `03_API_CONTRACT.md` |
| 具体代码风格细则 | 后续 `CODE_STYLE.md`<br/> 或模块 README |
| 面试问答与项目表达 | 后续 `interview/`<br/> 文档 |


本文档的核心目标是：

```plain
让 GPT / Codex 成为受控的开发助手，而不是让 AI 自由生成项目。
```

---

## 1. 核心原则
### 1.1 人负责设计，AI 负责辅助实现
项目中的业务边界、数据库设计、Tool 协议、API 契约、验收标准和最终代码合并，由开发者控制。

AI 主要参与：

```plain
代码生成
局部重构
测试补充
错误排查
文档整理
重复样板代码生成
```

AI 不负责最终架构决策。

---

### 1.2 先定契约，再写代码
开发前必须先明确：

```plain
数据库 schema
Tool contract
API contract
功能验收方式
```

未确定契约前，不允许让 AI 直接生成大范围业务代码。

---

### 1.3 按功能闭环切片推进
本项目不按“单个类 / 单个文件 / 单个页面”孤立开发，而按功能闭环推进。

功能闭环指的是：

```plain
一个可演示、可测试、可追踪、可复盘的完整业务能力。
```

示例：

```plain
今日 API 巡检闭环
统一登录 403 诊断闭环
讲座报名高峰预警闭环
本周 API 复盘闭环
RAG 文档检索闭环
Tool Trace 展示闭环
Evidence List 展示闭环
```

每个闭环至少要考虑：

```plain
业务入口
数据来源
后端能力
Agent / Tool 使用方式
前端展示
Trace / Evidence
验收方式
```

---

### 1.4 AI 代码必须审查
AI 生成代码后，必须经过：

```plain
IDEA diff review
编译检查
接口测试
核心功能验证
人工判断是否符合项目目标
```

未经审查的 AI 代码不得直接提交。

---

### 1.5 未验收不得提交
一个功能闭环只有满足以下条件，才算完成：

```plain
代码能编译
服务能启动
接口能调用
页面能展示
核心链路能跑通
失败场景有错误提示
Git 提交信息清晰
```

---

### 1.6 不要过早多 Agent 化
第一版优先实现：

```plain
单 Agent
+ P0 Tools
+ Trace / Evidence
+ SSE 输出
+ 最小评测用例
```

在基础闭环跑通之前，不让 AI 自行引入复杂 Planner / Executor / Supervisor 多 Agent 编排。

只有出现以下情况时，才考虑拆分多 Agent：

```plain
工具数量过多导致选择不稳定
任务明显分为规划、执行、审核多个阶段
单个 Prompt 已经难以维护
需要人工确认或长流程状态管理
```

---

## 2. 工具分工
当前项目采用：

```plain
GPT + Codex CLI + IDEA + 自动化脚本
```

作为主开发方式。

### 2.1 GPT
GPT 主要用于：

```plain
需求拆解
功能闭环设计
数据库设计讨论
Tool 协议设计讨论
API 契约设计讨论
Prompt 设计
代码审查思路
错误日志分析
面试表达沉淀
开发文档整理
```

GPT 不直接操作本地项目文件。

GPT 的定位是：

```plain
架构设计与开发决策助手。
```

---

### 2.2 Codex CLI
Codex CLI 主要用于本地代码开发。

适合使用 Codex CLI 的任务：

```plain
在指定目录内生成代码
根据错误日志修复编译问题
生成单元测试
补充接口测试脚本
重构局部重复代码
解释已有代码
根据契约补齐 DTO / Service / Controller
根据 README 补充启动脚本
```

不适合使用 Codex CLI 的任务：

```plain
一次性生成整个项目
无边界重写模块
自行修改数据库 schema
自行变更 Tool / API 契约
自行引入大型依赖
自行删除已有代码
```

Codex CLI 的定位是：

```plain
本地执行助手。
```

---

### 2.3 IDEA
IDEA 是本项目的主开发工作台。

IDEA 主要用于：

```plain
查看项目结构
管理 Maven 项目
配置 JDK 17
运行 Spring Boot 服务
查看 Git diff
人工 review AI 修改
调试后端代码
运行单元测试
检查依赖和包结构
```

IDEA 的定位是：

```plain
人工确认与工程调试入口。
```

---

### 2.4 自动化脚本
`scripts` 目录用于存放项目检查脚本。

建议逐步补充：

```plain
scripts/check-backend.ps1
scripts/check-frontend.ps1
scripts/check-docker.ps1
scripts/check-smoke.ps1
scripts/check-demo.ps1
scripts/check-eval-smoke.ps1
```

脚本用于提交前验证，不替代人工 review。

第一版脚本优先级：

| 脚本 | 优先级 | 作用 |
| --- | --- | --- |
| `check-backend.ps1` | P0 | 后端编译、测试、启动检查 |
| `check-smoke.ps1` | P0 | 核心接口和 Agent SSE smoke test |
| `check-eval-smoke.ps1` | P0.5 | 验证 5 条最小评测用例 |
| `check-frontend.ps1` | P1 | 前端依赖、构建检查 |
| `check-docker.ps1` | P1 | 中间件容器状态检查 |
| `check-demo.ps1` | P1 | 演示链路一键检查 |

---

### 2.5 可选工具
Cursor / Windsurf 可以作为可选增强工具，但当前项目不依赖它们。

当前优先级：

```plain
GPT + Codex CLI + IDEA
> Cursor / Windsurf
```

---

## 3. 本地开发环境约定
### 3.1 项目根目录
当前项目根目录约定为：

```plain
D:\apihub-agent-dev
```

推荐目录结构：

```plain
D:\apihub-agent-dev
├─ docker
├─ apihub-server
├─ apihub-gateway
├─ apihub-ui
├─ docs
└─ scripts
```

---

### 3.2 Codex CLI 使用位置
Codex CLI 默认在项目根目录执行：

```powershell
cd D:\apihub-agent-dev
codex
```

如果只开发某个模块，也可以进入具体模块执行：

```powershell
cd D:\apihub-agent-dev\apihub-server
codex
```

推荐原则：

| 场景 | 执行位置 |
| --- | --- |
| 跨模块讨论或生成文档 | 项目根目录 |
| 后端功能开发 | `apihub-server` |
| 网关配置开发 | `apihub-gateway` |
| 前端页面开发 | `apihub-ui` |
| 脚本补充 | `scripts` |
| 文档整理 | `docs` |


---

### 3.3 IDEA 与 Codex CLI 配合方式
推荐工作流：

```plain
IDEA 打开项目
→ 在 IDEA 中查看结构和 diff
→ 在 Terminal 中运行 Codex CLI
→ Codex 修改局部代码
→ IDEA 中 review diff
→ IDEA / PowerShell 中运行测试
→ 人工确认后提交 Git
```

要求：

```plain
Codex 负责生成和修改代码；
IDEA 负责审查、运行、调试和提交确认。
```

---

### 3.4 环境变量与敏感信息
`.env` 可以在本地使用，但不得提交 Git。

必须加入 `.gitignore`：

```plain
.env
*.local
docker/data/
```

只允许提交：

```plain
.env.example
```

禁止把以下内容发送给 AI 或提交到仓库：

```plain
真实 DashScope API Key
数据库密码
Nacos 密钥
服务器内网地址
内网穿透 Token
个人账号 Cookie
```

需要给 AI 说明配置时，使用占位符：

```plain
DASHSCOPE_API_KEY=your_dashscope_api_key
MYSQL_PASSWORD=your_mysql_password
```

---

## 4. Context Pack 规范
每次让 AI 开发前，必须准备上下文包。

### 4.1 Context Pack 内容
一次 AI 开发请求至少包含：

```plain
当前功能目标
相关文档
允许修改范围
禁止修改范围
实现要求
验收方式
输出要求
```

---

### 4.2 推荐上下文文档
常用上下文文档：

```plain
docs/00_PROJECT_BRIEF.md
docs/01_DB_SCHEMA.md
docs/02_TOOL_CONTRACT.md
docs/03_API_CONTRACT.md
docs/04_VIBE_CODING_RULES.md
```

使用原则：

| 文档 | 用途 |
| --- | --- |
| `00_PROJECT_BRIEF.md` | 说明项目定位和业务边界 |
| `01_DB_SCHEMA.md` | 说明数据库设计 |
| `02_TOOL_CONTRACT.md` | 说明 Agent Tool 输入输出 |
| `03_API_CONTRACT.md` | 说明前后端接口契约 |
| `04_VIBE_CODING_RULES.md` | 说明 AI 辅助开发规范 |


Codex 执行开发任务时，应优先读取相关文档，而不是自行推断设计。

---

### 4.3 Context Pack 示例
```plain
当前目标：
实现统一登录 403 诊断闭环中的 queryGatewayLogs 能力。

相关文档：
- docs/01_DB_SCHEMA.md
- docs/02_TOOL_CONTRACT.md
- docs/03_API_CONTRACT.md
- docs/04_VIBE_CODING_RULES.md

允许修改：
- apihub-server/src/main/java/**/tool/**
- apihub-server/src/main/java/**/service/**
- apihub-server/src/main/java/**/controller/**

禁止修改：
- 数据库 schema
- Tool 返回结构
- API 路径
- .env
- docker/data

实现要求：
- 查询网关日志
- 返回统一 ToolResult
- 记录 tool trace
- 异常时返回 errorCode 和 errorMessage

验收方式：
- 后端编译通过
- 测试接口能调用
- 异常参数能返回错误结构
```

---

## 5. 功能闭环切片规范
### 5.1 功能闭环定义
功能闭环不是简单代码任务，而是一条能被演示和验证的业务链路。

一个功能闭环至少包含：

```plain
业务问题
用户入口
数据来源
后端处理
Agent / Tool 行为
结果输出
Trace / Evidence
验收用例
```

---

### 5.2 推荐闭环拆分
第一阶段优先围绕以下闭环推进：

```plain
今日 API 巡检闭环
统一登录 403 诊断闭环
讲座报名高峰预警闭环
本周 API 复盘闭环
RAG 文档检索闭环
Tool Trace 展示闭环
Evidence List 展示闭环
```

---

### 5.3 闭环开发顺序原则
不规定具体日期，只规定推进顺序：

```plain
先保证数据可查
再保证工具可调
再保证 Agent 可用
再保证前端可展示
最后补 Trace / Evidence / 自动化验收
```

通用顺序：

```plain
数据准备
→ Tool / Service 能力
→ API 测试入口
→ Agent 接入
→ 前端展示
→ Trace / Evidence
→ 自动化验收
```

---

### 5.4 推荐开发顺序
第一版建议按以下顺序让 Codex 开发，避免过早进入复杂 UI 或复杂 Agent 编排：

```plain
1. 初始化数据库、建表 SQL 和 seed.sql
2. 生成 Entity / Mapper / Service 基础层
3. 实现 queryApiInfo / queryApiCallStats 两个 P0 Tool
4. 实现 dev-only Tool 调试接口
5. 实现 tool_call_trace / evidence_item 入库
6. 实现 queryGatewayLogs / queryConsumerApp
7. 实现 Agent SSE 入口和基础事件流
8. 实现 Dashboard / Trace / Evidence 前端页面
9. 实现 RAG 文档上传、索引状态和检索测试
10. 实现统一登录 403 诊断闭环
11. 实现今日巡检、讲座预警、周报复盘闭环
12. 补 smoke test、演示脚本和面试表达材料
```

每一步都要有可运行结果，不允许让 Codex 一次性生成整个系统。

---

### 5.5 闭环完成标准
一个功能闭环完成时，至少满足：

```plain
有明确业务问题
有测试数据
有后端接口或 Agent 入口
有前端展示方式
有 Trace 或日志记录
有验收命令
有 Git commit
```

---

## 6. Prompt 编写规范
### 6.1 禁止开放式 Prompt
禁止使用：

```plain
帮我写一个 API-HUB Agent 项目。
帮我把后端都写好。
帮我生成完整前端。
帮我随便优化一下项目。
```

原因：

```plain
范围过大
容易引入无关设计
容易破坏已有契约
难以 review
难以测试
```

---

### 6.2 标准 Prompt 模板
推荐使用：

```plain
当前目标：
说明这次要完成的功能闭环或局部能力。

项目背景：
简要说明 API-HUB Agent 场景。

相关文档：
列出需要读取的 docs 文件。

允许修改：
明确允许修改的目录或文件。

禁止修改：
明确禁止修改的目录或文件。

实现要求：
说明必须遵守的契约、返回结构、异常处理、Trace 要求。

验收方式：
说明需要通过哪些命令、接口或页面验证。

输出要求：
说明需要输出修改摘要、测试命令、注意事项。
```

---

### 6.3 后端开发 Prompt 示例
```plain
当前目标：
基于 Tool Contract 实现 queryApiCallStats 后端能力。

项目背景：
API-HUB Agent 需要支持平台管理者查询 API 调用量、失败率、P95、P99，用于日常巡检和异常诊断。

相关文档：
- docs/01_DB_SCHEMA.md
- docs/02_TOOL_CONTRACT.md
- docs/04_VIBE_CODING_RULES.md

允许修改：
- apihub-server/src/main/java/**/tool/**
- apihub-server/src/main/java/**/service/**
- apihub-server/src/main/java/**/controller/**

禁止修改：
- 数据库 schema
- ToolResult 结构
- .env
- docker/data

实现要求：
- 返回结构必须符合 ToolResult
- 查询失败不能抛出裸异常
- 需要记录 tool trace
- 不要引入新的第三方依赖

验收方式：
- mvn test 通过
- mvn package 通过
- 提供 curl 测试命令

输出要求：
- 列出修改文件
- 说明实现逻辑
- 给出测试命令
```

---

### 6.4 前端开发 Prompt 示例
```plain
当前目标：
实现 Tool Trace 展示面板。

项目背景：
API-HUB Agent 需要让用户看到 Agent 调用了哪些工具、每个工具的输入摘要、状态、耗时和错误信息。

相关文档：
- docs/03_API_CONTRACT.md
- docs/04_VIBE_CODING_RULES.md

允许修改：
- apihub-ui/src/**

禁止修改：
- 后端接口路径
- API 返回字段
- .env
- docker 配置

实现要求：
- 展示 toolName、status、latencyMs、errorMessage
- 接口异常时页面不能白屏
- 不引入未确认 UI 依赖

验收方式：
- npm run build 通过
- 页面能展示 mock 或真实 trace 数据

输出要求：
- 列出修改文件
- 说明组件结构
- 给出验证方式
```

---

## 7. AI 输出审查规范
AI 修改代码后，必须在 IDEA 中进行 diff review。

### 7.1 基础审查项
必须检查：

```plain
是否只修改了允许范围内的文件
是否修改了禁止修改的文件
是否引入未确认依赖
是否破坏已有包结构
是否有明显重复代码
是否有未使用 import
是否有硬编码密钥或地址
是否有临时代码未清理
```

Codex 每次完成修改后，必须在回答或开发记录中说明：

```plain
修改了哪些文件
新增了哪些类或方法
是否修改了 DB / Tool / API 契约
是否新增依赖
如何编译和验证
还有哪些 TODO 或风险点
```

这些信息用于人工 review，不作为最终文档内容直接照搬。

---

### 7.2 契约审查项
必须检查：

```plain
是否符合 DB Schema
是否符合 Tool Contract
是否符合 API Contract
是否符合统一返回结构
是否符合错误处理规则
是否影响已有接口兼容性
```

---

### 7.3 Agent / Tool 审查项
涉及 Agent / Tool 时，额外检查：

```plain
Tool 输入是否结构化
Tool 输出是否稳定
Tool 失败是否返回可解释错误
是否记录工具调用 Trace
是否生成 Evidence
Prompt 是否限制无证据编造
是否避免自动执行高风险动作
```

---

### 7.4 安全审查项
必须检查：

```plain
不得提交真实密钥
不得提交 docker/data
不得提交本地数据库文件
不得暴露内网地址
不得在日志中输出敏感配置
不得让外网直接访问数据库或中间件
```

---

## 8. 自动化验收规范
### 8.1 基础门禁
后端基础门禁：

```powershell
cd D:\apihub-agent-dev\apihub-server
mvn test
mvn package
```

前端基础门禁：

```powershell
cd D:\apihub-agent-dev\apihub-ui
npm install
npm run build
```

Docker 基础门禁：

```powershell
cd D:\apihub-agent-dev\docker
docker compose ps
```

---

### 8.2 功能门禁
每个功能闭环至少需要验证：

```plain
核心接口能调用
核心页面能打开
核心数据能展示
异常输入有错误提示
服务端日志无明显异常
```

---

### 8.3 Agent 门禁
涉及 Agent 的功能，必须验证：

```plain
Agent 能返回结果
需要工具时能调用工具
工具失败时有兜底提示
结果中不能编造不存在的数据
Trace 能看到工具调用记录
Evidence 能看到报告依据
```

---

### 8.4 RAG 门禁
涉及 RAG 的功能，必须验证：

```plain
文档能上传
文档能分片
Embedding 能生成
Milvus 能写入
query 能召回相关 chunk
召回结果包含来源信息
检索失败有错误提示
```

---

### 8.5 SSE 门禁
涉及 SSE 的功能，必须验证：

```plain
前端能收到流式内容
连接失败有错误提示
后端异常不会导致页面卡死
Nginx 代理下仍能正常返回
```

---

### 8.6 Smoke Test 脚本规划
建议后续补充：

```plain
scripts/check-backend.ps1
scripts/check-frontend.ps1
scripts/check-docker.ps1
scripts/check-smoke.ps1
scripts/check-demo.ps1
```

其中：

| 脚本 | 目标 |
| --- | --- |
| `check-backend.ps1` | 检查后端编译、测试、启动 |
| `check-frontend.ps1` | 检查前端依赖和构建 |
| `check-docker.ps1` | 检查 MySQL、Redis、Nacos、Milvus、Nginx |
| `check-smoke.ps1` | 检查核心接口 |
| `check-demo.ps1` | 检查演示链路 |


---

## 9. Git 留痕规范
### 9.1 提交粒度
一个 commit 尽量对应：

```plain
一个功能闭环
一个明确工具能力
一个明确接口能力
一个明确页面能力
一个明确 bug 修复
一个明确文档更新
```

避免：

```plain
多个无关功能混在一个 commit
一次性提交大量 AI 生成代码
提交信息只写 update / fix bug
```

---

### 9.2 Commit Message 前缀
统一使用：

```plain
feat:
fix:
refactor:
test:
docs:
chore:
```

示例：

```plain
feat(tool): add api call stats query tool
feat(agent): support login 403 diagnosis flow
feat(trace): record agent tool call trace
feat(rag): add document upload and vector indexing
feat(ui): add tool trace panel
fix(sse): handle stream connection error
test(tool): add api stats tool tests
docs: update vibe coding rules
```

---

### 9.3 AI 参与记录
重要功能建议在开发记录中保留：

```plain
功能目标
AI 参与内容
人工调整内容
测试方式
最终提交 commit
```

模板：

```markdown
## 开发记录：功能名称

### 1. 功能目标

### 2. 使用的上下文文档

### 3. AI 参与内容

### 4. 人工调整内容

### 5. 验收方式

### 6. 关联 commit
```

该记录只服务内部复盘，不需要写成长篇面试文档。

---

## 10. 禁止事项
### 10.1 AI 使用禁止事项
禁止：

```plain
让 AI 一次性生成整个项目
让 AI 自行设计数据库结构
让 AI 自行变更 Tool Contract
让 AI 自行变更 API Contract
让 AI 自行引入未确认依赖
让 AI 删除未知用途代码
让 AI 直接生成带真实密钥的配置
让 AI 绕过测试直接提交
```

---

### 10.2 代码提交禁止事项
禁止提交：

```plain
.env
*.local
docker/data/
真实 API Key
数据库密码
本地日志大文件
IDEA 临时文件
node_modules
target
dist
```

---

### 10.3 演示安全禁止事项
外网演示时禁止：

```plain
暴露 MySQL
暴露 Redis
暴露 Nacos
暴露 Milvus
暴露 MinIO
暴露 Attu
暴露数据库管理页面
暴露真实密钥和内部配置
```

外网演示只允许：

```plain
穿透域名
→ Nginx
→ 前端页面
→ /api/** 反向代理
→ 后端服务
```

---

### 10.4 项目表达禁止事项
禁止把未完成能力说成已完成。

尤其注意：

```plain
mock 数据不能说成真实生产数据
测试版本不能说成生产级平台
建议生成不能说成自动修复
工具调用日志不能说成完整审计系统
本地演示不能说成完整云原生部署
```

内部开发中可以使用 mock，但代码、文档和演示时必须保留清晰边界。

---

## 11. 当前推荐开发方式
本项目当前推荐工作方式：

```plain
GPT 负责方案和规范
Codex CLI 负责本地代码执行
IDEA 负责 review、运行和调试
PowerShell / scripts 负责自动化验收
Git 负责过程留痕
```

标准流程：

```plain
确认功能闭环
→ 准备 Context Pack
→ 用 GPT 明确实现思路
→ 用 Codex CLI 修改本地代码
→ 要求 Codex 输出修改文件、验证方式和风险点
→ 在 IDEA 中 review diff
→ 运行自动化检查
→ 手工验证核心功能
→ 提交 Git
→ 记录关键开发过程
```

该流程适用于后续所有 API-HUB Agent 功能开发。

---

## 12. 一句话执行准则
```plain
AI 可以写代码，但不能替我们决定项目边界、契约、验收和提交。
```

