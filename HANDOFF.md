# Prompt2PRD Handoff

> 更新时间：2026-07-17  
> 当前阶段：阶段一至阶段四已完成，下一步执行阶段五步骤 34

## 新对话读取顺序

1. 完整阅读 [`AGENTS.md`](./AGENTS.md) 并遵守仓库约束。
2. 阅读 [`memory-bank/architecture.md`](./memory-bank/architecture.md) 和 [`memory-bank/progress.md`](./memory-bank/progress.md) 获取当前状态。
3. 需要核对产品规则时阅读 [`memory-bank/design-doc.md`](./memory-bank/design-doc.md)。
4. 开始实现前阅读 [`memory-bank/tech-stack.md`](./memory-bank/tech-stack.md) 和 [`memory-bank/implementation-plan.md`](./memory-bank/implementation-plan.md)。

不要重新进行产品需求澄清，也不要从宠物寄养示例开始推导产品。现有文档已经完成冲突检查和技术决策收敛。

## 当前真实状态

- 工作目录：`D:\Code\Prompt2PRD`
- 当前分支：`niuniu0907`；仓库已有提交历史和未提交的连续批次改动，继续修改前先检查工作树，保留用户已有改动。
- 阶段一已建立 Maven/Spring Boot WebFlux 后端、Vue 3 前端和统一生产构建。
- 阶段二已建立领域类型、IndexedDB Schema v1、项目 Repository、项目首页、文字/文件创建、项目工作台导航、事务保存和版本恢复基础。
- 步骤 14 已建立统一 API 错误协议：八类稳定错误码、固定安全提示、请求 ID、真实输入校验异常映射和 WebTestClient 测试。
- 步骤 15 已建立厂商无关的 `ModelGateway`：统一结构化调用、文本流、连接测试、请求上下文、运行时端点、错误分类和取消信号；测试假网关覆盖成功、格式错误、延迟与取消。
- 步骤 16 已建立 Spring AI OpenAI 兼容适配器：支持 OpenAI、DeepSeek、通义千问预设和自定义地址，结构化完整聚合、文本真实流式输出、运行时参数映射及连接测试均已接入。
- 模型地址在请求前校验协议、凭据和解析后 IP；公网仅 HTTPS，本机仅 `localhost`/`127.0.0.1`/`::1` 允许 HTTP，其他私网 HTTPS 需环境变量白名单；实际 OkHttp 连接固定使用已验证 IP 并关闭重定向。
- 步骤 17 已建立 Key 来源与配置安全：服务端系统模式必须同时具备显式开关和环境变量密钥；用户 Key 只存在当前 Pinia 运行时和单次请求快照，刷新后消失；显式用户来源失败不会回退系统 Key。
- 步骤 18 已实现模型配置能力查询和连接测试接口，连接测试区分不可达、鉴权、模型不存在、限流、格式不兼容与超时；前端模型设置导航和页面已启用，支持三个预设服务及自定义兼容地址。
- 步骤 19 已实现单实例额度与频率限制：系统 Key 每 IP 每个 UTC 日默认 3 次分析、1 次完整 PRD，系统 Key 全局默认 100 次真实上游调用/日；用户 Key 绕过免费与全局额度，但两种来源均受默认 30 次/分钟的基础频率限制。
- 分块分析只扣一次 IP 分析额度，每个分块模型请求都扣全局预算。额度使用容量有界、自动过期的 Caffeine 存储，服务重启会清空；原始 IP 在入库前转换为带盐 SHA-256 摘要，当前只保证单实例。
- `GET /api/quota` 返回当前 IP 的系统 Key 可用状态与准确剩余额度；模型连接测试已接入基础频率和全局上游预算，但不扣分析或完整 PRD 免费次数。
- 步骤 20～25 已建立前后端共享分析契约、十维完整度计算、问题排序与去重、确定性状态合并、跳过/“不知道”策略和有界分析上下文。后端仍不持久化项目，未经结构校验的模型补丁不能进入合并器。
- 步骤 26～28 已实现结构化需求分析器、15 类 SSE 协议、单终态事件序列、5 秒心跳编排及 `/api/analysis`、`/api/analysis/answers` 两个 POST SSE 接口；取消信号会传到模型网关，额度在调用前执行。
- 步骤 29 已实现前端增量 SSE 解析和最新请求保护；重复/迟到/跨请求事件不会污染当前状态，乱序、非法已知事件和无终态断流会失败。
- 步骤 30 已用真实 `AnalysisView` 替换概览占位页，流式展示进度、目标、角色、需求和首轮问题。只有完成终态才事务写入 IndexedDB，刷新恢复最后有效状态，半成品不会覆盖本地状态。
- 步骤 31 已实现批量问题向导和幂等回答 Repository：四种输入、自定义回答、补充说明、单题/整轮跳过均可用；回答先本地保存，再调用 `/api/analysis/answers`，完整终态才校准 IndexedDB。
- 步骤 32 已实现冲突、假设、锁定及版本同步：核心冲突阻止完成但不阻止普通问答，锁定项必须解锁后编辑，所有决定都生成完整快照和字段变更。
- 步骤 33 已实现需求卡片编辑、失焦自动保存、固定权重完整度重算、受影响产物标识和版本历史恢复；恢复范围只包含当前已实现的数据，不引用流程图/PRD Repository。
- 当前验证：后端 113 项测试通过；前端 34 个测试文件、162 项测试通过；严格 TypeScript 检查和 Vite 生产构建成功。
- 下一步不是重新写计划，而是执行实施计划的**步骤 34：收集技术约束**。

## 已确认产品边界

Prompt2PRD 是面向 vibe coding 用户的 AI 需求分析与工程规划工作台，将模糊想法经过动态追问转化为可供 Codex、Claude Code、Cursor 等 coding agent 使用的 AI-ready PRD。

MVP 不包含登录、多用户协作、云端项目同步、服务端业务数据库、微服务、Redis、消息队列、向量数据库、移动端专项适配、高保真设计稿和 Word/PDF 导出。

核心闭环：

`创建本地项目 → AI 提取事实与缺口 → 批量澄清问题 → 结构化需求与完整度 → 冲突和锁定 → 架构推荐与确认 → Mermaid 流程图 → PRD 生成、编辑、校验和 Markdown 导出`

## 已确认技术与架构

- 前端：Vue 3、TypeScript、Vite、Pinia、Element Plus、Dexie/IndexedDB。
- 后端：Java 21、Spring Boot 4.1.x、Spring AI 2.0.x、WebFlux、Reactor、SSE。
- 测试：Vitest、Vue Test Utils、JUnit 5、WebTestClient、Playwright。
- 构建：npm、Maven Wrapper、单个可运行 JAR、单容器 Docker。
- Vue 和 Spring Boot 最终同源运行；MVP 不设计跨域部署。

IndexedDB 是项目数据的唯一持久化位置。AI 候选补丁由后端完成校验、确定性合并、冲突检测和完整度计算；用户确认、锁定、冲突解决和手动编辑由前端确定性处理。后端不保存用户项目。

需求状态只有 `INFERRED`、`PENDING`、`CONFIRMED`、`CONFLICTED`；锁定使用独立 `locked` 字段。ID 使用 UUID，持久化时间使用 UTC ISO-8601。

## 关键实现决定

- 结构化模型输出必须完整聚合并校验后才能进入正式状态，不能解析半截 JSON。
- PRD 正文使用真实文本流；SSE 共 15 类事件，使用 `requestId` 和递增 `eventId` 幂等处理。
- 未知 SSE 事件记录警告后忽略；已知事件结构非法时终止当前任务。
- Markdown/TXT 最大 2 MB，按标题和段落拆成不超过 32,000 字符的片段，全部处理，不得静默截断。
- 完整度使用固定权重：确认项 100%、推测项 40%、待确认和冲突项 0%；核心冲突使对应维度最高 50 分。
- 用户 Key 只存在前端运行时内存和单次请求中。系统 Key 模式默认关闭，必须同时配置密钥和启用开关才显示。
- 额度日界线统一使用 UTC；Caffeine 只保存 IP 摘要和计数，默认额度参数可通过 `PROMPT2PRD_QUOTA_*` 环境变量覆盖。
- 公网模型地址必须 HTTPS；仅本机回环地址允许 HTTP；其他私网地址必须显式加入白名单。
- 自动化模型测试全部使用假 `ModelGateway`，不得调用真实模型或消耗真实 Key。

## UI 与交互方向

- 面向桌面浏览器的浅色工作台，不是聊天窗口，也不套用传统后台模板。
- 全局左导航、项目二级导航、顶部项目栏、中央主画布和可收起右侧辅助面板保持稳定。
- 使用 Element Plus 作为基础组件库，布局、卡片和品牌样式使用自己的 CSS Variables。
- 主色 `#c7eb64` 必须搭配深色文字 `#262b25`；MVP 不做深色主题。
- 支持最新两个稳定版本的 Chrome、Edge 和 Firefox；Playwright 完整套件使用 Chromium，Firefox运行核心冒烟测试。

## 执行约束

- 严格按照 `implementation-plan.md` 的 57 个步骤顺序推进；每一步都必须先写测试并验证。
- 当前步骤测试未通过时不得进入下一步。
- 每完成重大功能，更新 `memory-bank/architecture.md` 和 `memory-bank/progress.md`，只记录已经验证的事实。
- 禁止批量删除文件或目录；单文件删除必须使用明确路径。
- 不要把计划中的功能描述成已经实现。
