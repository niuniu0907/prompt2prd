# Prompt2PRD 实施计划

> 面向 AI 开发者的分步实施指令。所有路径均以仓库根目录为基准。执行前完整阅读 `AGENTS.md`、`memory-bank/design-doc.md` 和 `memory-bank/tech-stack.md`。本计划只描述任务、文件和测试，不包含任何实现代码。

## 1. 目标与执行规则

**目标：** 交付一个本地优先的 Prompt2PRD MVP，使用户能够从模糊需求出发，完成动态澄清、结构化状态管理、架构确认、流程图生成、AI-ready PRD 编辑与 Markdown 导出。

**架构：** Vue 3 前端负责工作台、IndexedDB 项目数据和导出；Spring Boot WebFlux 后端负责模型代理、结构化校验、确定性状态合并、SSE、额度与错误处理。Vue 产物最终打包进同一个 Spring Boot JAR 和 Docker 容器。

**技术栈：** Java 21、Spring Boot 4.1.x、Spring AI 2.0.x、WebFlux、Vue 3、TypeScript、Vite、Pinia、Element Plus、Dexie、Vitest、JUnit 5、WebTestClient、Playwright、Maven、Docker。

执行时遵守以下规则：

1. 严格按步骤顺序实施；当前步骤测试未通过时不得进入下一步。
2. 每个模型相关测试使用可控的假 `ModelGateway`，自动测试不得调用真实模型或使用真实 API Key。
3. 每完成一个阶段，运行全部前端测试、全部后端测试和已有端到端测试。
4. Git 仓库已重新初始化；每个绿色步骤单独提交，默认分支使用 `main`，提交前确认只包含当前步骤文件。
5. 不引入 MySQL、PostgreSQL、Redis、消息队列、微服务、向量数据库、WebSocket、登录或多用户能力。
6. 不批量删除文件；若必须删除，只处理一个明确路径，并遵守 `AGENTS.md`。

## 2. 阶段一：仓库基线与可运行骨架

### 步骤 1：初始化实施跟踪文档

- **文件：** 修改 `memory-bank/architecture.md`、`memory-bank/progress.md`。
- **指令：** 在架构文件中记录当前单体边界、技术栈、尚未实现状态和里程碑更新规则；在进度文件中建立阶段、完成项、验证结果和下一步结构。产品事实只引用 `memory-bank/design-doc.md`，不复制整份设计文档。
- **验证：** 增加一次文档路径检查，确认 `AGENTS.md` 引用的五份 memory-bank 文档均存在、可读，并且架构与进度文件没有把规划能力描述为已实现。

### 步骤 2：建立 Maven 聚合工程

- **文件：** 创建 `pom.xml`、`backend/pom.xml`、`mvnw`、`mvnw.cmd`、`.mvn/wrapper/`；创建 `backend/src/main/java/com/prompt2prd/Prompt2PrdApplication.java`。
- **指令：** 锁定 Java 21、Spring Boot 4.1.x 和 Spring AI 2.0.x；仅加入 WebFlux、Validation、Actuator、Caffeine 和测试所需依赖，不加入数据库依赖。
- **验证：** 创建 Spring 上下文冒烟测试；运行 `.\mvnw.cmd test`，期望应用上下文启动且没有数据库自动配置。

### 步骤 3：建立 Vue 前端工程

- **文件：** 创建 `frontend/package.json`、lockfile、Vite/TypeScript/Vitest 配置、`frontend/src/main.ts`、`frontend/src/App.vue`。
- **指令：** 安装 Vue 3、Vue Router、Pinia、Element Plus、Dexie、markdown-it 和 Mermaid；配置 TypeScript 严格模式、Vitest、Vue Test Utils 与按需组件导入。
- **验证：** 创建应用挂载测试，确认根组件、Router 和 Pinia 正常初始化；运行 `npm --prefix frontend run test` 和 `npm --prefix frontend run build`，两者均应成功。

### 步骤 4：建立统一生产构建

- **文件：** 修改根 `pom.xml`、`backend/pom.xml`；创建构建资源复制配置。
- **指令：** 让 Maven 在生产打包时安装锁定依赖、构建 Vue，并把产物复制到 Spring Boot 静态资源目录；开发模式仍允许前后端分别启动。
- **验证：** 运行 `.\mvnw.cmd package`，启动生成的 JAR，确认根路径返回前端页面、`/actuator/health` 返回健康状态，且不存在跨域依赖。

### 步骤 5：建立工作台外壳与设计 Token

- **文件：** 创建 `frontend/src/styles/tokens.css`、`frontend/src/layouts/AppShell.vue`、`frontend/src/router/index.ts`、`frontend/src/views/ProjectHomeView.vue`。
- **指令：** 实现全局左导航、右侧主区域和浅色主题；只建立空路由和布局，不提前实现业务。主色、强调色、背景色和文字色必须来自设计文档 Token。
- **验证：** 创建组件测试，确认全部主导航入口存在、黄绿色按钮使用深色文字、没有深色主题开关；运行前端测试并进行一次 1280 像素宽截图检查。

## 3. 阶段二：本地项目与状态基础

### 步骤 6：定义前端领域类型

- **文件：** 创建 `frontend/src/features/projects/types.ts`、`frontend/src/features/requirements/types.ts`、`frontend/src/features/prd/types.ts`。
- **指令：** 定义项目阶段、需求状态 `INFERRED/PENDING/CONFIRMED/CONFLICTED`、独立 `locked` 布尔字段、来源、问题、答案、冲突、版本、完整度、架构候选和 PRD 章节类型；只有 `CONFIRMED` 项允许锁定，字段与 `memory-bank/design-doc.md` 保持一致。ID 使用 UUID，持久化时间使用 UTC ISO-8601。
- **验证：** 创建类型与工厂函数测试，确认非法状态不能进入有效对象、非确认项不能锁定、锁定项保留来源和更新时间；运行 Vitest 类型检查。

### 步骤 7：建立 IndexedDB Schema

- **文件：** 创建 `frontend/src/db/appDatabase.ts`、`frontend/src/db/schema.ts`、`frontend/src/db/appDatabase.spec.ts`。
- **指令：** 建立 `project`、`requirement_item`、`clarification_question`、`clarification_answer`、`requirement_conflict`、`requirement_version`、`requirement_change`、`prd_section`、`app_setting` 仓库及必要索引；设置数据库版本 1。
- **验证：** 使用隔离的测试数据库验证首次创建、关闭后重开和版本号；确认不存在保存 API Key 的表或字段。

### 步骤 8：实现项目 Repository

- **文件：** 创建 `frontend/src/db/repositories/projectRepository.ts` 及对应测试。
- **指令：** 分别实现创建、读取、列表、重命名、复制、归档、移入回收站、恢复和单项目永久删除；普通删除只写 `deletedAt`，复制必须生成新 ID。
- **验证：** 为每个操作编写事务测试；重点确认刷新式重读后状态仍在、副本修改不影响原项目、永久删除只清理目标项目关联数据。

### 步骤 9：实现项目首页

- **文件：** 创建 `frontend/src/features/projects/ProjectList.vue`、`ProjectCard.vue`、`ProjectEmptyState.vue`；修改 `ProjectHomeView.vue`。
- **指令：** 显示全部项目、已归档和回收站视图；项目卡片包含名称、阶段、完整度、待确认数和更新时间；操作按钮仅在悬停或菜单中出现。
- **验证：** 组件测试覆盖空状态、筛选、重命名、归档、删除和恢复；确认空状态没有示例项目或宠物寄养快捷填充。

### 步骤 10：实现新建项目与输入校验

- **文件：** 创建 `frontend/src/views/NewProjectView.vue`、`frontend/src/features/projects/ProjectCreateForm.vue` 及测试。
- **指令：** 支持不少于 5 个字符的文字输入，或有效文件输入；先用输入前 20 个字符建立临时名称，首次分析成功后仅在用户未手动改名时采用模型建议名称；创建成功后保存原始输入并进入分析工作台。
- **验证：** 测试短文本被拒绝、有效文本可创建、文件输入可绕过文字最短限制、模型命名成功、命名失败和用户已改名场景；创建失败不得留下半成品项目。

### 步骤 11：实现上传、预览和隐私确认

- **文件：** 创建 `frontend/src/features/projects/RequirementFileUpload.vue`、`frontend/src/features/projects/fileParser.ts` 及测试。
- **指令：** 只接收 UTF-8 Markdown/TXT，最大 2 MB；上传后先预览再确认；确认后按标题和段落拆成不超过 32,000 个 Unicode 字符的片段，全部依次分析并显示片段进度，不得静默截断；首次上传前显示第三方模型传输提示，并在 `app_setting` 记录确认状态。
- **验证：** 测试有效文件、空文件、错误扩展名、无法解码、超过 2 MB、多片段边界和中途取消；确认所有合法片段都被处理，第二次上传不重复弹窗但仍显示简短提醒。

### 步骤 12：建立项目工作台导航

- **文件：** 创建 `frontend/src/layouts/ProjectWorkspace.vue`、`frontend/src/features/projects/ProjectNavigation.vue`、`frontend/src/features/projects/ProjectHeader.vue`。
- **指令：** 建立需求概览、问题向导、需求卡片、架构建议、流程图和 PRD 二级导航；顶部展示名称、完整度、模型、保存状态和生成入口；右侧辅助面板可收起。
- **验证：** 组件测试确认各模块切换不离开工作台、当前项目 ID 保持不变、PRD 和流程图页面默认收起右侧面板。

### 步骤 13：实现事务保存与版本记录

- **文件：** 创建 `frontend/src/db/repositories/requirementRepository.ts`、`versionRepository.ts` 及测试。
- **指令：** 有效状态变化必须在一个事务中写入需求、完整项目快照、字段级变更和版本摘要；键盘输入只在显式保存、离开编辑模式或一次业务操作结束时生成版本；MVP 不自动清理历史。失败时回滚，并且界面不能显示“已保存”。
- **验证：** 注入 IndexedDB 写入失败，确认相关仓库均无部分数据；正常写入后关闭并重开数据库，确认完整快照可恢复、版本差异可读取且连续按键不会生成多个版本。

## 4. 阶段三：后端模型、错误与额度基础

### 步骤 14：统一 API 错误协议

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/common/api/` 下的错误码、异常映射和请求标识组件；创建对应 WebTestClient 测试。

- **指令：** 区分参数错误、地址不可达、鉴权失败、模型不存在、限流、格式不兼容、超时和内部错误；响应不得包含密钥、Authorization 或完整提示词。

- **验证：** 逐类触发异常并断言 HTTP 状态、稳定错误码、可操作提示和请求 ID；扫描响应体确认无敏感值。

### 步骤 15：定义模型网关边界

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/model/application/ModelGateway.java`、模型请求/结果类型和测试假实现。
- **指令：** 将结构化调用、文本流式调用、连接测试和取消信号定义在统一边界内；业务模块不得直接依赖具体厂商客户端。
- **验证：** 编写架构依赖测试，确认 `analysis`、`architecture` 和 `generation` 包只依赖模型网关；假网关能够返回成功、格式错误、延迟和取消四类结果。

### 步骤 16：实现 OpenAI 兼容适配器

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/model/adapter/SpringAiModelGateway.java` 及适配器测试。
- **指令：** 使用 Spring AI `ChatClient` 支持 DeepSeek、OpenAI 预设及其他 OpenAI 兼容地址；只允许 HTTP/HTTPS、禁止内嵌凭据和自动重定向，公网必须 HTTPS，仅本机回环地址允许 HTTP，其他私网地址必须在环境变量白名单中；请求前校验解析后 IP。结构化结果完整聚合后再转换，文本结果保持流式。
- **验证：** 使用本地模拟服务验证地址、模型名、参数和鉴权头映射；覆盖公网 HTTP、私网、云元数据地址、DNS 重绑定、重定向和白名单场景；确认结构化响应未完成前不会输出正式业务结果。

### 步骤 17：实现 Key 来源与配置安全

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/model/domain/ModelConfig.java`、`common/config/ModelProperties.java`；创建 `frontend/src/stores/modelConfigStore.ts`。
- **指令：** 系统 Key 仅从服务端环境变量读取，并且系统 Key 模式默认关闭，只有密钥和显式启用开关同时存在时才展示；用户 Key 仅保存在 Pinia 运行时内存并随单次请求发送；用户 Key 失败时不得自动切换系统 Key。
- **验证：** 验证未配置、只配置密钥、只启用开关和两者同时配置四种启动状态；刷新前端 Store 后确认用户 Key 消失，IndexedDB、缓存、错误响应和日志均不得出现测试密钥明文。

### 步骤 18：实现模型连接测试接口

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/model/api/ModelConfigController.java` 及测试；创建前端模型设置面板。
- **指令：** 实现 `POST /api/model-config/test`；前端提供系统 Key、用户 Key、预设服务和自定义服务配置，并清楚显示错误类别。
- **验证：** 使用假网关覆盖成功、不可达、401、模型不存在、429 和格式异常；组件测试确认当前明确选择的配置被发送且不会静默降级。

### 步骤 19：实现额度与频率限制

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/quota/` 下的策略、Caffeine 存储和 Controller；创建测试。
- **指令：** 系统 Key 启用后，每 IP 每日最多 3 次分析操作和 1 次完整 PRD，并受全局日上游调用次数限制；分块文件的一次用户分析只扣一次 IP 分析额度，但每个分块调用都计入全局预算。用户 Key 不消耗免费额度但仍受基础频率限制；IP 只保存摘要。
- **验证：** 使用可控时钟验证功能关闭、边界次数、分块计数、日期切换、自动过期、全局上限和用户 Key 旁路；调用 `GET /api/quota` 应返回准确剩余额度。

## 5. 阶段四：结构化需求分析闭环

### 步骤 20：定义后端需求状态契约

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/analysis/domain/` 下的项目摘要、需求项、问题、答案、冲突和完整度 record；创建 Validation 测试。
- **指令：** 字段、枚举和前端类型一一对应；状态仅包含 `INFERRED/PENDING/CONFIRMED/CONFLICTED`，锁定使用独立布尔字段且仅允许确认项锁定；每条需求必须有 UUID、来源和 UTC 时间；请求只携带当前任务所需状态。
- **验证：** 对缺失 ID、未知状态、非确认项锁定、无来源需求和非法完整度分别验证拒绝；准备一份前后端共享契约样例并验证双方都能解析。

### 步骤 21：实现固定权重完整度计算

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/analysis/domain/CompletenessCalculator.java` 及测试。
- **指令：** 按设计文档十个维度权重计算；目标项中确认记 100%、推测记 40%、待确认和冲突记 0%，锁定不额外加分；分母包含已有项、待补充项和未回答关键问题，不适用维度排除，核心冲突使该维度最高 50 分；返回逐项原因并四舍五入为整数。
- **验证：** 测试所有维度满分、完全缺失、单个维度不适用、核心流程 15%、架构与功能各 12%、推测 40%、待确认 0%、核心冲突封顶和总分 0–100。

### 步骤 22：实现问题排序与去重

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/analysis/domain/QuestionSelector.java` 及测试。
- **指令：** 使用业务影响 40%、信息缺失 30%、依赖数量 20%、风险 10% 排序；默认选择 5–10 个，高价值问题不足时允许少于 5 个；将标准化后的维度、目标字段和模型返回的稳定 `semanticKey` 组成主去重键，再用标准化问题文本进行精确去重，不引入向量模型。
- **验证：** 构造不同评分、同语义键、同文本和近似但目标不同的问题，确认排序、数量边界和重复过滤；交易、权限、安全和核心状态问题在同等条件下优先。

### 步骤 23：实现确定性状态合并器

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/analysis/application/RequirementStateMerger.java` 及测试。
- **指令：** 只接受已完成结构校验的 AI 候选补丁；后端执行字段映射、重复过滤、来源优先级、锁定保护、冲突创建和完整度计算，并返回最终有效状态。用户确认、锁定、冲突解决和手动编辑由前端确定性逻辑处理；后端不持久化项目，前端不得直接保存未经后端校验的 AI 补丁。
- **验证：** 测试新增事实、重复事实、用户内容覆盖推测、模型不能覆盖锁定项、矛盾内容生成冲突、后端无项目持久化，以及失败时输入状态保持不变。

### 步骤 24：实现跳过与“不知道”规则

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/analysis/application/AnswerPolicy.java` 及测试。
- **指令：** 跳过的问题保留为待确认；“不知道”产生带影响说明的推荐方案并保持 `PENDING`，只有明确确认后转为 `CONFIRMED`。
- **验证：** 测试单题跳过、整轮跳过、接受推荐和拒绝推荐；确认前三种未确认状态均不提高完整度。

### 步骤 25：实现分析上下文构建器

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/analysis/application/AnalysisContextBuilder.java` 及测试。
- **指令：** 每次仅提供项目摘要、当前有效状态、锁定内容、最近一轮问答、信息缺口、语言和输出 Schema；禁止无限附加完整聊天历史。
- **验证：** 用长历史输入测试上下文边界，确认旧对话正文未被直接拼接、锁定内容和最新问答未遗漏、项目语言保持稳定。

### 步骤 26：实现需求分析器

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/analysis/application/RequirementAnalyzer.java`、结构化输出契约和测试。
- **指令：** 从输入提取事实、意图、假设、缺口、潜在冲突和候选问题；通过 Spring AI 结构化输出、Jakarta Validation 和业务校验后才交给合并器。
- **验证：** 假网关分别返回合法结果、缺字段结果、非法枚举和重复问题；缺字段与非法枚举返回可重试错误，重复问题经过校验后由选择器过滤，不得使整次分析失败。

### 步骤 27：定义统一 SSE 事件协议

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/stream/StreamEvent.java`、事件工厂和测试；创建 `frontend/src/api/streamEvents.ts`。
- **指令：** 支持设计文档列出的 15 类事件，包括章节开始、完成和失败；所有事件携带 `requestId` 和单请求递增 `eventId`；失败、完成和取消只能各出现一次终态事件。未知事件允许向前兼容忽略，已知事件字段非法才视为协议错误。
- **验证：** 生成完整事件序列，确认编号严格递增、字段可被前端解析、重复终态被拒绝、未知事件被记录并忽略、已知非法事件终止任务。

### 步骤 28：实现分析编排与接口

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/analysis/application/AnalysisOrchestrator.java`、`analysis/api/AnalysisController.java` 及 WebTestClient 测试。
- **指令：** 实现 `POST /api/analysis` 和 `POST /api/analysis/answers`；按开始、进度、需求补丁、问题、冲突、完整度、完成的顺序发送事件；每 5 秒至少发送进度或心跳。
- **验证：** 使用假网关验证首次分析和下一轮分析的事件顺序、内容类型、心跳、错误终态和取消；500 毫秒目标只在独立的非模型接口测试中验证。

### 步骤 29：实现前端 SSE 客户端

- **文件：** 创建 `frontend/src/api/sseClient.ts`、`frontend/src/api/analysisApi.ts` 及测试。
- **指令：** 使用 `fetch` POST 读取 SSE；按 `requestId/eventId` 去重和拒绝迟到事件；仅在完成事件后用最终状态校准 IndexedDB；失败时保留已确认状态。
- **验证：** 模拟分片、同一事件重复、乱序、旧请求迟到、断流、未知事件和已知非法事件；确认未知事件只记录警告，界面草稿不重复，旧结果不覆盖新状态，非法事件不会写入 IndexedDB。

### 步骤 30：实现初始分析界面

- **文件：** 创建 `frontend/src/features/analysis/AnalysisProgress.vue`、`RequirementSummary.vue`、`AnalysisView.vue` 及测试。
- **指令：** 创建项目后立即显示分析状态，逐步展示产品目标、角色、需求卡片和第一轮问题；每条内容显示来源与状态。
- **验证：** 组件测试按事件逐个推送，确认无需刷新即可出现卡片、进度和问题；刷新后从 IndexedDB 恢复最后有效状态。

### 步骤 31：实现批量问题向导

- **文件：** 创建 `frontend/src/features/analysis/QuestionBatch.vue`、`QuestionCard.vue`、`AnswerForm.vue` 及测试。
- **指令：** 每轮展示 5–10 个高价值问题；支持单选、多选、文字、自定义答案、补充说明、跳过单题和跳过整轮；显示提问原因及选项影响。
- **验证：** 覆盖所有输入类型、混合预设与自定义答案、跳过和重复提交；确认重复提交不会产生重复需求项。

### 步骤 32：实现冲突、假设与锁定交互

- **文件：** 创建 `frontend/src/features/requirements/ConflictPanel.vue`、`AssumptionPanel.vue`、`RequirementCard.vue` 及测试。
- **指令：** 右侧面板展示冲突双方、来源、影响和处理方式；假设可确认或拒绝；确认内容可锁定，锁定后必须先解锁才能编辑。
- **验证：** 测试非核心冲突不阻止继续问答、核心冲突阻止完成、锁定内容不被流式补丁覆盖、冲突解决后状态与版本记录同步。

### 步骤 33：实现需求编辑、自动保存与版本恢复

- **文件：** 创建 `frontend/src/features/requirements/RequirementEditor.vue`、`frontend/src/features/history/VersionHistory.vue` 及测试。
- **指令：** 手动编辑后标记为用户确认、重新计算完整度并记录未来可能受影响的产物标识；恢复历史版本前先保存当前状态，恢复操作自身也生成版本。此步骤只恢复当前已实现的项目与需求数据，流程图和 PRD 的恢复测试分别在对应模块完成后补充。
- **验证：** 测试编辑后刷新保留、锁定项编辑被阻止、项目与需求完整快照恢复、恢复失败时原状态不丢失；不得引用尚未实现的流程图或 PRD 仓库。

## 6. 阶段五：架构推荐与确认

### 步骤 34：收集技术约束

- **文件：** 创建 `frontend/src/features/architecture/TechnicalConstraintsForm.vue`；创建后端约束 DTO 与测试。
- **指令：** 收集已掌握技术、终端、团队规模、用户量、关键能力、敏感程度、部署、预算、周期和维护能力；未回答的关键约束必须进入待确认状态。
- **验证：** 表单测试覆盖完整提交、部分提交、自定义技术和敏感数据选择；后端拒绝缺少项目 ID 或非法枚举的请求。

### 步骤 35：实现架构候选生成与评分

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/architecture/application/ArchitectureRecommender.java` 及测试。
- **指令：** 生成 2–3 个候选，每个包含前端、后端、存储、鉴权、文件、AI、部署、测试、职责、优缺点、限制和未选择原因；按学习、速度、部署、成本、维护、扩展和 AI 支持评分。
- **验证：** 使用“会 Vue/Java/Spring Boot、个人、单体 Docker”样例，确认优先推荐匹配现有技能的方案，并保留全栈 JavaScript 等备选而非按热度强推。

### 步骤 36：实现架构接口与确认

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/architecture/api/ArchitectureController.java`；创建前端 `ArchitectureComparison.vue` 及测试。
- **指令：** 实现 `POST /api/architecture/recommend`；允许接受推荐、选择备选或手动修改；确认前只保存候选，确认后写入唯一主架构并产生 `architecture_confirmed` 事件。
- **验证：** 测试 2–3 个候选展示、对比维度完整、一次只能确认一个主架构、切换确认生成版本、未确认时最终技术内容保持草稿标记。

## 7. 阶段六：流程图生成

### 步骤 37：实现流程图生成服务

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/generation/flowchart/` 下的生成器、DTO、Controller 和测试。
- **指令：** 实现 `POST /api/generation/flowchart`；分别生成主流程和异常流程；输入只读取已确认状态，异常信息不足时返回待补充提示，不允许模型虚构责任规则。
- **验证：** 假网关测试正常主流程、多个异常流程、缺少异常事实和单图失败；确认一张图失败不会丢弃其他有效图。

### 步骤 38：实现 Mermaid 校验与流程图页面

- **文件：** 创建 `frontend/src/features/flowchart/FlowchartView.vue`、`mermaidValidator.ts` 及测试。
- **指令：** 保存前独立校验每张 Mermaid 图；页面支持查看、复制源码和单图重新生成；重新生成结果通过校验且经用户确认后才替换旧图。
- **验证：** 测试合法语法、非法语法、多图部分成功、复制源码和拒绝替换；组件测试确认宽画布下右侧面板默认收起。

## 8. 阶段七：AI-ready PRD

### 步骤 39：定义 PRD 章节与追溯规则

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/generation/prd/PrdDefinition.java`、追溯检查测试；同步前端章节类型。
- **指令：** 固定设计文档要求的 17 类内容；需求、规则、接口、页面、验收和实施阶段使用稳定编号；确定性指令区分 MUST、SHOULD、MUST NOT。
- **验证：** 用完整状态生成章节清单，确认所有必需章节存在、编号唯一、核心功能能追溯到业务规则和验收标准。

### 步骤 40：实现分章节 PRD 生成

- **文件：** 创建 `backend/src/main/java/com/prompt2prd/generation/prd/PrdGenerator.java` 及测试。
- **指令：** 只读取当前有效需求和已确认架构；按章节分别生成，不允许文档结果反向修改需求状态；未确认架构或完整度不足时必须明确标为草稿并列出缺失项。
- **验证：** 测试最终文档、80% 以下草稿、未确认架构草稿和核心冲突场景；确认最终模式被阻止时已有需求状态不变化。

### 步骤 41：实现 PRD 流式接口

- **文件：** 创建 PRD Controller、章节流式适配和 WebTestClient 测试。
- **指令：** 实现 `POST /api/generation/prd` 与 `POST /api/generation/prd/sections/{sectionId}`；正文使用 `section_started`、`section_delta`、`section_completed` 和 `section_failed` 表达章节生命周期，总任务使用统一完成、失败或取消事件。
- **验证：** 模拟多段文本流，确认片段顺序、章节归属、终态唯一、单章节失败不破坏已完成章节，并验证停止后不继续追加内容。

### 步骤 42：实现 PRD 编辑、预览与保存

- **文件：** 创建 `frontend/src/features/prd/PrdEditor.vue`、`PrdPreview.vue`、`PrdSectionList.vue` 及测试。
- **指令：** 支持编辑/预览切换、章节状态、生成进度和本地自动保存；markdown-it 禁止原始 HTML；不得在每次按键时调用模型。
- **验证：** 测试编辑后保存、刷新恢复、历史版本可恢复 PRD 章节、Markdown 脚本不执行、生成中片段正确追加、IndexedDB 失败时显示未保存。

### 步骤 43：实现章节锁定与重新生成

- **文件：** 创建 `frontend/src/features/prd/PrdRegenerationDialog.vue` 及测试。
- **指令：** 章节可锁定；重新生成前保存旧版本并提示可能覆盖人工内容；新内容完成校验后由用户确认替换，取消时保留旧内容。
- **验证：** 测试锁定章节不能直接重生成、确认替换、拒绝替换、生成失败和中途停止；所有场景均应保留可恢复旧版本。

### 步骤 44：实现 PRD 修改到需求的同步分析

- **文件：** 创建后端 `generation/prd/PrdChangeAnalyzer.java`、前端变更确认面板及测试。
- **指令：** 仅在保存或退出编辑模式时分析差异；能唯一映射且不涉及锁定项的事实可同步，存在歧义或影响多个规则时生成待确认变更或冲突。
- **验证：** 用“退款时限 24 小时改为 48 小时”验证唯一映射自动同步；用多规则歧义和锁定规则验证不会静默覆盖。

### 步骤 45：实现一致性校验与 Markdown 导出

- **文件：** 创建后端 PRD Validator Controller、前端 `prdExporter.ts` 及测试。
- **指令：** 实现 `POST /api/generation/prd/validate`；检查编号、字段、枚举、接口引用、架构、阶段和验收一致性；导出当前有效版本，文件名为清理非法字符后的“项目名称-PRD.md”。
- **验证：** 测试一致文档通过、断链引用被阻止、候选架构混入最终指令被阻止、导出包含流程图/假设/待确认项，且非法文件名字符被清理。

## 9. 阶段八：取消、可靠性、安全与额度体验

### 步骤 46：贯通停止与迟到结果保护

- **文件：** 创建后端 `stream/GenerationTaskRegistry.java`、前端 `useGenerationTask.ts` 及测试。
- **指令：** 前端通过 `AbortController` 停止；WebFlux 取消信号终止上游模型流；新请求替代旧请求后，旧请求迟到结果必须被丢弃。
- **验证：** 测试分析、架构、流程图和 PRD 四类任务的取消；确认收到 `generation_aborted` 后停止动画，未完成结构片段不入库，已确认内容不丢失。

### 步骤 47：实现超时、有限重试与心跳

- **文件：** 创建后端 `common/config/GenerationProperties.java`、重试策略和测试。
- **指令：** 集中配置连接超时、总超时、最大重试次数和退避；只对可恢复网络错误重试，鉴权、参数和结构业务错误不重试；流式空闲期间发送心跳。
- **验证：** 使用虚拟时间测试可恢复错误重试到上限、不可恢复错误零重试、超时终止和 5 秒内心跳；确认不存在无限重试。

### 步骤 48：完成输入、渲染与日志安全

- **文件：** 创建后端输入过滤与日志脱敏组件、前端 Markdown 安全配置及测试；创建 `.env.example` 和 `.gitignore`。
- **指令：** 限制请求和上传长度；禁止 Markdown 原始 HTML；落实公网 HTTPS、本机回环 HTTP 例外、私网白名单和重定向禁用；日志只记录请求 ID、任务 ID、耗时和错误类别，不记录 Key、Authorization 或完整需求正文。
- **验证：** 提交脚本型 Markdown、超长输入、测试密钥和受限模型地址；确认脚本不执行、超长请求与危险地址被明确拒绝、日志与构建产物中不存在敏感字符串。

### 步骤 49：实现额度展示与切换引导

- **文件：** 创建 `frontend/src/features/model/QuotaIndicator.vue` 及测试。
- **指令：** 操作前显示系统 Key 剩余分析和 PRD 次数；额度为零时禁止系统 Key 调用并引导用户填写自己的 Key；用户 Key 失败不得回退。
- **验证：** 模拟满额、剩一次、耗尽和全局预算耗尽；确认按钮状态、提示和请求 Key 来源正确。

## 10. 阶段九：端到端验收与交付

### 步骤 50：建立核心端到端测试夹具

- **文件：** 创建 `frontend/playwright.config.ts`、`frontend/e2e/fixtures/` 和模型模拟服务。
- **指令：** 让端到端测试在无网络、无真实 Key 环境运行；为结构化分析、文本流、错误、断流和延迟提供确定性夹具。Chromium 运行完整套件，Firefox 运行创建、澄清和导出冒烟流程。
- **验证：** 连续运行 Chromium 夹具测试三次并运行 Firefox 冒烟测试，确认事件顺序和结果一致、没有外部网络请求、没有真实凭据依赖。

### 步骤 51：验证创建与澄清闭环

- **文件：** 创建 `frontend/e2e/create-and-clarify.spec.ts`。
- **指令：** 覆盖文字创建、文件加补充说明、第一轮问题、自定义答案、跳过、“不知道”推荐、实时卡片和分维度完整度。
- **验证：** 运行该端到端测试，期望所有状态在刷新后恢复，重复问题不出现，达到 80% 后同时显示继续回答和生成 PRD。

### 步骤 52：验证冲突、锁定与版本闭环

- **文件：** 创建 `frontend/e2e/conflict-and-history.spec.ts`。
- **指令：** 覆盖锁定需求、模型矛盾输出、右侧冲突处理、手动编辑、版本差异和恢复。
- **验证：** 运行该端到端测试，确认锁定内容不被覆盖、核心冲突阻止完成、恢复后刷新仍保持目标版本。

### 步骤 53：验证架构、流程图与 PRD 闭环

- **文件：** 创建 `frontend/e2e/generate-prd.spec.ts`。
- **指令：** 覆盖技术约束、2–3 个候选、架构确认、主/异常流程图、PRD 流、停止、重新生成、编辑、校验和导出。
- **验证：** 运行该端到端测试，确认导出文档只包含已确认架构对应的技术决策摘要，包含稳定编号、接口示例、双格式用户故事与验收、实施阶段和 coding agent 约束。

### 步骤 54：验证本地多项目与回收站

- **文件：** 创建 `frontend/e2e/local-projects.spec.ts`。
- **指令：** 覆盖两个项目隔离、重命名、复制、归档、移入回收站、恢复和单项目永久删除；不实现批量清空。
- **验证：** 运行测试并多次刷新，确认项目互不污染、副本 ID 不同、普通删除可恢复、永久删除只影响明确目标。

### 步骤 55：完成 JAR 与 Docker 交付

- **文件：** 创建根 `Dockerfile`、`.dockerignore`；完善 Maven 打包配置。
- **指令：** 使用分阶段构建产出单容器；运行时只需要 Java 和环境变量；同一端口提供前端、API、SSE 和健康检查。
- **验证：** 分别启动 JAR 和 Docker 镜像，运行健康检查及三条冒烟流程；确认刷新前端深层路由不返回 404，容器中不包含开发密钥或本地项目数据。

### 步骤 56：完成开源文档与安全检查

- **文件：** 创建或更新 `README.md`、`.env.example`、`memory-bank/architecture.md`、`memory-bank/progress.md`。
- **指令：** 写明环境要求、本地开发、测试、生产构建、JAR、Docker、模型配置、数据只在浏览器本地保存及已知限制；加入真实截图或演示链接占位要求，不伪造已完成能力。
- **验证：** 在全新目录按 README 完成一次无真实 Key的构建和测试；扫描仓库确认没有 API Key、本地 IndexedDB 数据、用户生成 PRD 或未脱敏模型载荷。

### 步骤 57：执行最终验收矩阵

- **文件：** 创建 `docs/acceptance-matrix.md`；更新 `memory-bank/architecture.md` 和 `memory-bank/progress.md` 的最终实现状态。
- **指令：** 将 AC-001 至 AC-037 逐项映射到自动测试或明确的人工检查；记录测试文件、结论和未满足项，不得用“已实现”代替证据。
- **验证：** 运行 `.\mvnw.cmd test`、`npm --prefix frontend run test`、`npm --prefix frontend run build` 和 Playwright 全套测试；只有全部必需验收项有证据且无失败时，MVP 才能标记完成。

## 11. 阶段完成标准

- **阶段一完成：** 前后端可独立开发，Maven 可产出包含前端的 JAR。
- **阶段二完成：** 多项目、上传、工作台、事务保存和版本基础可离线运行。
- **阶段三完成：** 模型适配、Key 安全、错误协议和额度经过假服务测试。
- **阶段四完成：** 创建项目到多轮澄清形成稳定的结构化需求闭环。
- **阶段五完成：** 候选架构可比较、可确认，且不会被模型代替用户决定。
- **阶段六完成：** 主流程和异常流程可独立生成、校验和重试。
- **阶段七完成：** PRD 可流式生成、编辑、校验、追溯并安全导出。
- **阶段八完成：** 取消、超时、重试、心跳、限流和敏感信息保护通过测试。
- **阶段九完成：** JAR、Docker、README、安全扫描和 AC-001 至 AC-037 验收证据齐全。

## 12. 关键风险与处理原则

- **模型结构化输出不稳定：** 完整聚合、Schema 校验、业务校验和有限重试后才能合并；失败保留旧状态。
- **前后端契约漂移：** 固定事件和 DTO 契约样例，并在两端测试中共同验证。
- **IndexedDB 数据损坏或迁移失败：** 所有跨仓库修改使用事务；每次 Schema 升级都新增迁移测试，不修改已发布版本。
- **用户 Key 泄露：** 只存在运行时内存和单次请求；日志、错误、缓存、导出和持久化均建立负向测试。
- **计划范围膨胀：** 严守 MVP 非目标；只有现有验收标准无法满足时才增加依赖或基础设施。
- **文档与实现漂移：** 每个里程碑更新 `memory-bank/architecture.md` 和 `memory-bank/progress.md`，但产品事实仍以 `memory-bank/design-doc.md` 为准。
