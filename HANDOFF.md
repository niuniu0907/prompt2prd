# Prompt2PRD Handoff

> 更新时间：2026-07-17  
> 当前阶段：需求、架构和实施计划已确认，尚未开始编码

## 新对话读取顺序

1. 完整阅读 [`AGENTS.md`](./AGENTS.md) 并遵守仓库约束。
2. 阅读 [`memory-bank/architecture.md`](./memory-bank/architecture.md) 和 [`memory-bank/progress.md`](./memory-bank/progress.md) 获取当前状态。
3. 需要核对产品规则时阅读 [`memory-bank/design-doc.md`](./memory-bank/design-doc.md)。
4. 开始实现前阅读 [`memory-bank/tech-stack.md`](./memory-bank/tech-stack.md) 和 [`memory-bank/implementation-plan.md`](./memory-bank/implementation-plan.md)。

不要重新进行产品需求澄清，也不要从宠物寄养示例开始推导产品。现有文档已经完成冲突检查和技术决策收敛。

## 当前真实状态

- 工作目录：`D:\Code\Prompt2PRD`
- 当前只有文档，没有前端、后端、数据库代码或可运行测试。
- Git 已重新初始化，当前为尚无提交的 `main` 分支。
- 当前文件均未提交；开始实现后按 Conventional Commits 分步骤提交。
- `architecture.md` 和 `progress.md` 已初始化，不再是空文件。
- 下一步不是重新写计划，而是执行实施计划的**步骤 2：建立 Maven 聚合工程**。

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
