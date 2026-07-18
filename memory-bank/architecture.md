# Prompt2PRD Architecture

> 当前状态：架构已确认，阶段一至阶段八已完成，阶段九步骤 50～54 已完成。取消与迟到结果保护、超时/重试/心跳、输入/渲染/日志安全、额度展示，以及核心端到端夹具和创建澄清、冲突历史、架构流程图 PRD、本地多项目回收站闭环均已实现并验证。下一步执行步骤 55（完成 JAR 与 Docker 交付）。产品事实以 `memory-bank/design-doc.md` 为准，实施顺序以 `memory-bank/implementation-plan.md` 为准。

## 交付形态

- Vue 3 + TypeScript 前端与 Java 21 + Spring Boot WebFlux 后端组成单体应用。
- Vue 产物打包进 Spring Boot，交付单个 JAR 和单容器 Docker 镜像。
- MVP 不使用服务端业务数据库、Redis、消息队列、微服务、向量数据库、登录或多人协作。

## 当前工程骨架

- 根 `pom.xml` 同时作为 Maven 聚合器和共享父工程，当前只聚合 `backend` 模块；`frontend` 保持独立 npm 工程，同时由根 Maven 的生产生命周期统一构建。
- 工程锁定 Java 21、Spring Boot 4.1.0 和 Spring AI BOM 2.0.0；Maven Wrapper 锁定 Maven 3.9.11。
- `backend` 是当前唯一可运行模块，入口为 `com.prompt2prd.Prompt2PrdApplication`，运行时采用 Spring WebFlux 和 Reactor Netty。
- 后端当前只引入 WebFlux、Validation、Actuator、Caffeine 和测试依赖；未引入 JDBC、JPA、R2DBC 或数据库驱动。
- `frontend` 当前是独立 npm/Vite 工程；浏览器入口 `main.ts` 按 Vue 应用、Pinia、Vue Router 的顺序完成初始化。
- 前端使用 Vue 3.5.40、Vite 8.1.5、Pinia 4.0.2、Vue Router 5.2.0，并已安装 Element Plus、Dexie、markdown-it 和 Mermaid；实际版本由 `package.json` 与 `package-lock.json` 精确锁定。
- TypeScript 使用严格模式并固定为 6.0.3；当前 `vue-tsc` 3.3.7 尚不兼容 TypeScript 7 的包导出结构，因此不能直接升级到 TypeScript 7。
- Element Plus 通过 `unplugin-auto-import`、`unplugin-vue-components` 和 `ElementPlusResolver` 按需导入，不在入口全量注册组件库。

## 统一生产构建

- 根 Maven 在 `prepare-package` 阶段通过 `frontend-maven-plugin` 1.15.4 安装隔离的 Node.js 24.14.0 与 npm 11.9.0，再依次执行 `npm ci` 和 `npm run build`；`test` 阶段不会触发前端生产构建。
- `npm ci` 只读取 `frontend/package-lock.json`，前端生产构建继续执行严格 TypeScript 检查，不在 Maven 构建中修改依赖版本。
- `backend` 在 `process-resources` 阶段使用 Maven Resources Plugin 3.5.0，将 `frontend/dist` 原样复制到 `${project.build.outputDirectory}/static`，不对静态文件执行 Maven 变量过滤。
- Spring Boot Maven Plugin 把静态资源和后端依赖重打包到 `backend-0.0.1-SNAPSHOT.jar`；运行时由同一个 Reactor Netty 端口提供前端页面、Actuator 和后续 API/SSE，不需要 CORS。
- 开发模式保持解耦：前端仍使用 `npm --prefix frontend run dev`，后端仍可单独从 IDE 或 Spring Boot 启动。

## 前端工作台外壳

- `App.vue` 只承载 `RouterView`；`router/index.ts` 提供可注入 History 的路由工厂，当前包含项目首页 `/`、新建项目 `/projects/new` 和项目入口 `/projects/:projectId` 三条路由。
- `AppShell.vue` 负责稳定的全局左导航和主画布插槽；全部项目、已归档和回收站会发出生命周期导航事件并保持唯一当前项，模型设置已启用并进入独立的 `/settings/model` 页面。
- 路由已更新为嵌套结构：`/` 项目首页、`/projects/new` 新建项目、`/projects/:projectId` 项目工作台（含 6 个子路由 `overview`、`questions`、`requirements`、`architecture`、`flowchart`、`prd`），默认重定向至 `overview`。
- `ProjectWorkspace.vue` 是项目工作台的顶层布局：包裹 `AppShell`，自行从 IndexedDB 加载项目数据，三栏布局由 `ProjectNavigation`（左）| `<router-view>` 主画布（中）| 可收起辅助面板（右）组成。流程图和 PRD 页面右侧面板默认收起以释放主画布宽度。
- `ProjectNavigation.vue` 提供六个模块的二级导航，高亮当前活跃模块并发射 `navigate` 事件；`ProjectHeader.vue` 展示项目名称、阶段、完整度、模型、保存状态和"生成 PRD"按钮。
- `ProjectHomeView.vue` 通过可注入的 `ProjectHomeRepository` 加载真实 IndexedDB 摘要，管理活动/归档/回收站视图、请求迟到保护、操作忙碌状态以及读取/写入失败反馈；失败不会把已有项目伪装为空列表。
- `ProjectList.vue` 只负责网格布局和事件转发，`ProjectCard.vue` 展示名称、阶段、完整度、待确认数与更新时间，并把重命名、复制、归档、回收站、恢复和永久删除收进卡片菜单；永久删除仍需浏览器确认。
- `ProjectEmptyState.vue` 为三种生命周期视图提供不同空状态，只有活动视图显示创建入口；首页顶部与空状态两个入口均进入同一个新建项目页，源码不创建示例项目或示例需求。
- `ProjectCreateForm.vue` 负责 Unicode 字符计数、至少 5 字文字校验、20 字临时名称预览和创建请求规范化；它接收步骤 11 文件组件传入的已确认文件内容，并把文件原文与可选补充说明交给现有项目创建边界。创建成功进入 `overview` 后由 `AnalysisView` 自动启动首次分析。
- `RequirementFileUpload.vue` 负责 `.md`/`.txt` 文件选择、首次隐私确认、只读文本预览、片段准备进度与取消；只有全部片段完成本地顺序处理后才向新建项目页发出已确认文件，清除文件会恢复纯文字创建规则。
- `fileParser.ts` 使用严格 UTF-8 解码，拒绝错误扩展名、超过 2 MiB、无法解码和空白文件；统一换行后优先沿 Markdown 标题和自然段边界分块，超长语义块按 Unicode 码点硬拆，单片最多 32,000 字符且所有片段重新连接后与解析全文一致。
- `NewProjectView.vue` 在 Repository 创建成功后才跳转到带项目 UUID 的入口页，IndexedDB 失败时保留表单并提示未保存；`overview` 子路由现由 `AnalysisView.vue` 承载真实初始分析，其他未实现模块继续使用占位页。
- `questions` 子路由由 `QuestionWizardView.vue` 承载：按当前最高优先级批次展示至多 10 个问题，`QuestionBatch/QuestionCard/AnswerForm` 分别负责批次、问题说明和四类输入。回答先本地提交，再携带当前结构化状态调用下一轮分析。
- `requirements` 子路由由 `RequirementsView.vue` 承载：中央区域显示可编辑/可锁定需求卡片，页面辅助列显示冲突、AI 假设和版本历史；未解决核心冲突持续显示阻塞提示。
- `styles/tokens.css` 是当前前端视觉变量来源，严格使用设计文档的八个颜色 Token；主按钮和当前导航使用 `#c7eb64` 背景与 `#262b25` 文字，普通辅助文字使用 `#626d72`。
- MVP 当前只声明浅色 `color-scheme`，不提供深色模式或主题开关。界面使用结构化间距、细边框和轻阴影，不依赖外部字体或装饰图片。
- 桌面工作台基线为固定侧栏加弹性主画布，最小页面宽度 960 像素；真实项目卡片在 1280×800 验收时无横向溢出。

## 状态与持久化边界

- IndexedDB 是项目、需求、问答、冲突、版本、流程图和 PRD 的唯一持久化位置。
- `features/projects/types.ts`、`features/requirements/types.ts` 和 `features/prd/types.ts` 定义当前持久化领域契约；项目与需求工厂在对象入库前校验 UUID、UTC ISO-8601 时间、枚举值和锁定规则。
- `db/appDatabase.ts` 使用 Dexie Schema v2；在 v1 的九个对象仓库基础上新增 `flowchart`，升级时保留既有项目数据。当前对象仓库为 `project`、`requirement_item`、`clarification_question`、`clarification_answer`、`requirement_conflict`、`requirement_version`、`requirement_change`、`flowchart`、`prd_section` 和 `app_setting`。
- 项目关联仓库使用 `projectId` 及状态、类型、批次、版本或章节键的复合索引，为后续多项目隔离查询和事务操作提供边界。
- `app_setting` 只保存非敏感应用状态：首次上传隐私确认及按项目保存的完整度维度快照；Schema 不包含 API Key、Authorization、凭据、密钥或 Token 仓库与字段。
- 文件上传隐私确认固定使用 `app_setting.key = uploadPrivacyNoticeAccepted`，只保存布尔确认值和 UTC 更新时间；文件预览与分块期间不向后端发送内容。
- `db/repositories/projectRepository.ts` 提供创建、读取、按生命周期列表、重命名、复制、归档、移入回收站、恢复和永久删除；生产实例使用共享数据库，测试通过构造器注入隔离数据库。创建由单次 IndexedDB 写入完成，失败不会留下第二个半成品项目。
- `applySuggestedName()` 为后续首次分析返回名称预留确定性写入边界：空建议保留临时名称，`userRenamed = true` 时拒绝迟到的模型命名，只有尚未手动改名的项目会更新时间和建议名称。
- `listSummaries()` 在只读事务中返回按更新时间倒序的项目及待确认数；待确认数严格等于该项目 `PENDING` 需求项与 `PENDING` 澄清问题之和。
- 重命名和生命周期变更使用项目表事务；普通删除只写 `status = DELETED` 与 `deletedAt`，不会清理关联数据，恢复会回到 `ACTIVE` 并清除归档和删除时间。
- 项目复制在一个跨仓库事务中生成新的项目、需求、问题批次、问题选项、答案、冲突、版本、变更、流程图和 PRD 章节 UUID，并重映射当前记录和版本快照内的引用；副本固定回到活动状态，与原项目相互独立。
- 永久删除只允许回收站项目，并在一个事务中按明确 `projectId` 清理八类关联记录及项目本身；不会提供批量清空回收站，也不会修改 `app_setting` 或其他项目。
- Vitest 使用 `fake-indexeddb` 为每个测试创建独立数据库，已验证首次打开、关闭后重开、版本号、Repository 事务行为及项目首页操作调用链。
- AI 候选补丁由后端完成校验、确定性合并、冲突检测和完整度计算；后端不保存项目。
- 用户确认、锁定、冲突解决和手动编辑由前端确定性处理并写入 IndexedDB。
- 需求状态为 `INFERRED`、`PENDING`、`CONFIRMED`、`CONFLICTED`；锁定使用独立 `locked` 字段。
- ID 使用 UUID，持久化时间使用 UTC ISO-8601；版本保存完整快照和字段级摘要，MVP 不自动清理。
- `db/repositories/requirementRepository.ts` 提供需求项的按 ID 读取、按项目列表、无版本单条更新和事务性批量保存。`save()` 在单个 Dexie 事务中完成：替换全部需求项、捕获项目/需求/问题/答案/冲突完整快照、创建版本摘要记录和字段级变更记录；任何写入失败都会回滚整个事务，不留部分数据。
- `updateRequirement()` 只写入 `requirement_item` 表且不创建版本，用于编辑过程中的草稿保存；连续多次调用不会生成历史版本，版本仅在显式调用 `save()` 时创建。
- `db/repositories/versionRepository.ts` 提供按项目倒序列出版本、按 ID 读取版本、读取版本及关联变更，以及恢复到指定历史版本。`restore()` 在单个事务中先保存当前完整状态为 RESTORE 类型版本，再将目标快照的项目/需求/问题/答案/冲突/流程图写回各表，最后创建恢复版本记录；旧版快照缺少 `flowcharts` 时按空数组兼容，恢复失败时当前状态不受影响。
- `ClarificationRepository.submitBatch()` 在一个事务中校验问题归属和选项归属，写入答案并更新问题状态；`[projectId+questionId]` 保证重复提交更新原答案 ID 而非追加重复答案。
- `RequirementInteractionRepository` 是本地冲突、假设和锁定的确定性写边界；每次有效变化都与项目完整快照、版本摘要和字段变更记录在同一事务中提交。只有 `CONFIRMED` 内容可锁定，所有编辑入口都检查锁定状态。
- `RequirementRepository.commitManualEdit()` 将人工编辑转为 `CONFIRMED/USER_EDIT`，保存受影响的未来产物标识，并通过前端镜像的十维固定权重算法更新项目完整度和 `analysisCompleteness:<projectId>` 快照。
- 流程图生成或确认替换时，`FlowchartRepository` 将图记录、项目阶段、完整快照版本和字段变更放在同一个事务中；版本恢复同步恢复流程图并校准项目完整度记录，PRD 仍等待后续持久化边界实现。

## 分析领域与应用边界

- `analysis/domain` 使用 Java record 与枚举镜像前端项目摘要、需求项、澄清问题与答案、冲突和完整度契约；`contracts/analysis-state.sample.json` 是前后端共同解析的契约样例。需求状态仅为 `INFERRED/PENDING/CONFIRMED/CONFLICTED`，只有确认项允许锁定。
- `CompletenessCalculator` 按十个固定维度权重计算：确认 100%、推测 40%、待确认与冲突 0%；缺口和未回答问题进入分母，不适用维度重归一化，未解决核心冲突使对应维度最高 50 分。
- `QuestionSelector` 使用业务影响 40%、信息缺失 30%、依赖数量 20%、风险 10% 的固定公式；以标准化维度、目标字段、稳定语义键为主键，再以标准化文本精确去重，最多选择 10 个问题，高价值不足时允许少于 5 个。
- `ValidatedRequirementPatch` 是模型候选补丁进入 `RequirementStateMerger` 的结构校验门；合并器执行重复过滤、来源优先级、锁定保护、矛盾冲突创建和完整度重算，采用 copy-on-write，失败不改变输入状态，且不依赖数据库或 Repository。
- `AnswerPolicy` 将单题/整轮跳过保留为未确认问题；“不知道”生成带影响说明的 `AI_RECOMMENDATION/PENDING` 需求，接受后才转为 `USER_ANSWER/CONFIRMED`，拒绝后继续保持待确认，因此未确认路径均不增加完整度。
- `AnalysisContextBuilder` 只向后续分析器提供项目摘要、当前需求、锁定需求、最新一轮问答、信息缺口、项目语言和输出 Schema；即使输入长历史，也不会把旧轮次正文拼入模型上下文。
- `RequirementAnalyzer` 通过 `ModelGateway.generateStructured()` 获取完整 `AnalysisModelOutput`，先执行 Bean Validation、枚举和状态来源约束，再转换为候选补丁与问题；正式状态仍由 `RequirementStateMerger`、`QuestionSelector` 和 `CompletenessCalculator` 决定，模型不能直接创建 `CONFIRMED` 内容。
- `AnalysisOrchestrator` 把一次分析组织为 Reactor `Flux<StreamEvent>`，发送开始、进度、领域增量与单一终态；空闲 5 秒发送进度心跳，订阅取消会触发 `ModelCancellationSignal`。`AnalysisController` 的两个 POST SSE 入口共享相同编排边界，并在模型调用前执行额度策略。

## 架构推荐与确认边界

- `architecture/api/TechnicalConstraintsRequest` 收集已掌握技术、自定义技术、目标终端、团队规模、用户量、关键能力、数据敏感度、部署、预算、周期和维护能力；除项目 UUID 外允许部分提交，所有未回答关键字段通过 `pendingFields` 原样返回，不由后端虚构默认答案。
- `ArchitectureRecommender` 使用确定性模板和七维 1～5 分评分生成三个可比较候选；候选固定包含前端、后端、存储、鉴权、文件、AI、部署、测试、职责、优缺点、限制和未选择原因。Vue/Java/Spring Boot、个人维护、单体 Docker 样例会优先推荐 Vue + Spring Boot，同时保留全栈 TypeScript 备选。
- `POST /api/architecture/recommend` 是无状态 JSON 接口，只校验约束并返回候选与待确认字段；后端不保存项目或替用户确认架构，也不消耗模型 Key。
- `TechnicalConstraintsForm.vue` 支持完整或部分约束、自定义技术和敏感数据选择；`ArchitectureComparison.vue` 展示全部技术职责和七个评分维度，允许接受推荐、选择备选或基于任一候选手动修改。
- 架构候选复用现有 `requirement_item` 仓库，以 `TECHNICAL_CONSTRAINT/PENDING` 和 `metadata.kind = ARCHITECTURE_CANDIDATE` 保存草稿，不新增 IndexedDB 对象仓库。未选择候选不会计入完整度或待确认数。
- `ArchitectureRepository.confirm()` 在单个 Dexie 事务中撤销旧主架构、写入唯一 `TECHNICAL_CONSTRAINT/CONFIRMED` 主架构、更新项目阶段与完整度快照，并创建完整版本和字段变更记录；切换确认可由既有版本恢复边界追踪。
- 每次本地确认返回统一形状的 `architecture_confirmed` 事件，携带 UUID 请求 ID、事件 ID 1、架构 ID 和 UTC 时间；后续 PRD 生成只应读取当前唯一确认项，其他候选保持备选草稿。

## 流程图生成与持久化边界

- `POST /api/generation/flowchart` 通过唯一 `ModelGateway.generateStructured()` 边界生成主流程与异常流程；模型输入只包含 `CONFIRMED` 需求，没有确认事实时不发起模型调用并返回待补充提示。
- 异常流程必须引用已确认且类型为 `EXCEPTION_SCENARIO` 的需求 ID；无已确认异常事实时不接受模型补写的责任规则。主图和每张异常图独立转换为成功或失败结果，单图失败不会丢弃其他有效图。
- `FlowchartView.vue` 支持全部生成、单图重新生成、严格 Mermaid 渲染、查看与复制源码；每张返回图先通过 `mermaid.parse()` 独立校验，已存在稳定键的图只有在新结果有效且用户确认后才替换。
- `FlowchartRepository` 使用稳定图键保存主流程和异常流程；批量生成只提交校验通过的图，保留失败兄弟图的错误信息。项目复制、永久删除、版本快照与版本恢复均已覆盖 `flowchart` 仓库。
- 流程图路由使用真实 `FlowchartView`，工作台右侧辅助面板在该宽画布页面默认收起。

## PRD 定义、生成、编辑与流式边界

- `PrdDefinition` 固定设计文档要求的 17 个有序章节，并为需求、用户故事、业务规则、接口、页面、验收和实施阶段提供确定性编号前缀；追溯检查要求每项核心功能至少关联一个用户故事、业务规则和验收条件。确定性指令只使用 `MUST`、`SHOULD` 和 `MUST NOT`。
- `PrdGenerator.plan()` 只把 `CONFIRMED` 需求和唯一 `metadata.kind = ARCHITECTURE_CANDIDATE` 的确认架构送入章节提示词。完整度低于 80、没有或存在多个确认主架构、存在未解决核心冲突、或调用方仍提供缺失项时，整份计划固定为 `DRAFT` 并列出原因；生成过程不修改输入需求状态。
- 每个章节通过 `ModelGateway.streamText()` 独立生成。`PrdStreamOrchestrator` 顺序发送 `section_started`、有序 `section_delta`、`section_completed` 或 `section_failed`；单章节失败不丢弃已完成章节，也不阻止后续章节，总任务只产生一个完成、失败或取消终态。
- `POST /api/generation/prd` 生成全部 17 章并按一次完整 PRD 操作执行额度策略；`POST /api/generation/prd/sections/{sectionId}` 只生成一个稳定章节键并仅执行频率与真实上游调用预算检查。连接取消会向共享模型取消信号传播。
- 前端 `PrdView.vue` 组合章节列表、编辑/预览切换和全部/单章生成；`PrdEditor.vue` 支持 Markdown 编辑和 2 秒失焦自动保存；`PrdPreview.vue` 使用 `markdown-it({ html: false })` 安全渲染，禁止原始 HTML。
- `PrdRepository` 通过 IndexedDB 持久化 17 个章节，支持初始化、内容更新、状态变更、锁定切换、显式保存和生成内容写入；保存操作在事务中同步创建版本记录并推进项目阶段。锁定章节的内容更新和重新生成均被拒绝。
- `PrdRegenerationDialog.vue` 在重新生成前展示旧版备份提示和人工内容覆盖警告，要求用户显式勾选确认；`PrdRepository.saveBeforeRegeneration()` 在重生成前创建历史版本以便恢复。
- 后端 `PrdChangeAnalyzer` 在保存或退出编辑模式时分析片段差异，提取编号事实和数值变化匹配已确认需求；唯一匹配自动同步，多匹配或锁定目标生成待确认变更或冲突警告。对应前端导出 `analyzePrdChanges()`。
- 后端 `PrdValidator` 验证 17 章节完整性、稳定编号、交叉引用、架构标记、验收 Given/When/Then 和实施阶段编号；备选架构混入最终文档时产生警告。对应前端 `exportPrdMarkdown()` 合并已完成/草稿章节为 Markdown 文件，`sanitizeFileName()` 清理非法文件名字符，`downloadPrdFile()` 触发浏览器下载。
- 后端继续无状态，不保存 PRD 或反向修改项目。前端 PRD 路由已从占位页切换到 `PrdView`，阶段七全部完成。

## AI 与流式边界

- `model/application/ModelGateway` 是业务模块调用模型的唯一生产边界，统一提供完整结构化结果 `Mono`、有序文本块 `Flux` 和连接测试 `Mono`；接口及其请求/结果类型不暴露 Spring AI 或厂商客户端类型。
- `ModelCallContext` 统一携带 UUID 请求 ID、运行时模型端点和 `ModelCancellationSignal`；端点对象的字符串表示固定隐藏 API Key 和参数值，用户 Key 仍只允许存在于单次请求内存中。
- 结构化请求通过 `responseType` 与 `outputSchema` 声明完整结果契约，`StructuredModelResult` 不表达半截 JSON；文本流以从 1 开始的序号和请求 ID 返回有序片段。
- `ModelGatewayException.Kind` 区分不可达、鉴权、模型不存在、限流、格式不兼容、超时、取消和内部错误，供后续适配器与 API 错误协议确定性映射。
- 测试侧 `FakeModelGateway` 可确定性模拟成功、格式错误、延迟和取消，不访问真实模型；架构测试阻止 `analysis`、`architecture` 和 `generation` 包依赖模型适配器或厂商客户端。
- `model/adapter/SpringAiModelGateway` 使用 Spring AI 2.0 `OpenAiChatModel` 与 `ChatClient` 实现运行时 OpenAI 兼容调用；OpenAI、DeepSeek、通义千问预设只提供标准兼容地址，自定义服务继续使用同一适配边界。
- 结构化调用通过 `BeanOutputConverter` 在完整响应到达后一次转换为 DTO；同步 Spring AI 调用运行在 bounded-elastic 调度器，文本生成使用 `ChatClient.stream().content()` 保持真实 `Flux` 增量并生成递增片段序号。
- `EndpointAddressPolicy` 在每次调用前拒绝非 HTTP(S)、内嵌凭据、查询/片段和危险解析地址；公网只允许 HTTPS，本机只有 `localhost`、`127.0.0.1`、`::1` 可使用 HTTP，其他私网 HTTPS 仅在 `PROMPT2PRD_MODEL_PRIVATE_HOST_ALLOWLIST` 显式允许时可用。
- `SecureOpenAiHttpClient` 将 OkHttp DNS 固定到策略已验证的同一组 IP，并关闭 HTTP/HTTPS 自动重定向与连接失败重试，避免校验后再次解析导致 DNS 重绑定或重定向绕过。
- `ModelProperties` 只绑定服务端 `PROMPT2PRD_MODEL_SYSTEM_KEY_ENABLED` 与 `PROMPT2PRD_MODEL_SYSTEM_API_KEY`；系统 Key 只有在开关为真且密钥非空时才可用，缺少任一条件均保持关闭。
- `ModelConfig` 为单次调用保存明确的 `SYSTEM` 或 `USER` 来源并在字符串表示中隐藏密钥；来源解析严格按当前选择执行，用户 Key 无效时不会读取或回退到系统 Key。
- 前端 `modelConfigStore` 使用未持久化的 Pinia 运行时状态保存用户 Key；新建应用 Store 后 Key 恢复为空，Store 不写 localStorage、sessionStorage 或 IndexedDB，系统 Key 也不会下发到浏览器。
- `ModelGatewayConfiguration` 将 OpenAI 兼容适配器注册为生产 `ModelGateway`；`ModelConfigController` 提供 `GET /api/model-config` 安全能力查询和 `POST /api/model-config/test` 连接测试，只返回系统 Key 是否可用，不返回系统密钥。
- 连接测试请求显式携带 `SYSTEM/USER` 来源、服务商、模型名、自定义地址和参数；后端按当前来源解析凭据并调用统一网关，不做来源降级。网关失败映射为统一的不可达、鉴权、模型不存在、限流、格式不兼容和超时错误码。
- 前端模型设置页提供 OpenAI、DeepSeek、通义千问和自定义兼容服务，展示安全地址限制与 Key 隐私说明；服务商、地址、模型和参数保存在当前 Pinia 运行时，用户 Key 仍只随明确的单次请求发送。
- `quota/QuotaService` 是模型代理的额度策略边界：系统 Key 每个 IP 的 UTC 自然日额度固定为 3 次分析和 1 次完整 PRD；用户 Key 绕过免费额度与系统全局预算，但系统/用户两种来源都受每 IP 固定分钟窗口的基础频率限制。
- 分块分析的调用方只在用户发起分析时调用一次 `beginOperation(...)`，每个真实模型分块另行调用 `acquireUpstreamCalls(...)`；因此一次分块文件只扣一次 IP 分析额度，但全部分块都会进入系统 Key 全局预算。
- `CaffeineQuotaStore` 以 UTC 日期和固定分钟窗口保存原子计数，缓存有最大容量并自动过期；服务重启会清空额度，当前只保证单实例部署，不声称支持多实例共享。
- `ClientIpDigest` 只读取实际连接的远端地址，忽略可伪造的转发头，并在进入额度存储前转换为带进程盐值的 SHA-256 摘要；缓存不保存原始 IP、Key、提示词或项目正文。
- `GET /api/quota` 只返回系统 Key 能力开关、当前 IP 剩余分析次数、完整 PRD 次数和进程级全局调用数，不返回 IP 摘要。当前模型连接测试已接入基础频率和真实上游调用预算，但不扣分析或 PRD 免费次数。
- 额度默认配置为分析 3 次/日、完整 PRD 1 次/日、系统 Key 全局 100 次上游调用/日、基础频率 30 次/分钟、最多追踪 10,000 个条目；部署者可通过 `PROMPT2PRD_QUOTA_*` 环境变量覆盖，全局预算和所有容量/频率值必须为正整数。
- 后端统一错误响应固定为 `code/message/requestId/timestamp`；八类错误使用稳定错误码和类别级安全提示，不能把异常原文、上游响应、完整提示词或凭据复制到响应。
- `RequestIdWebFilter` 为每个请求生成 UUID，同时写入 `X-Request-Id` 和 Reactor Context；错误响应体的 `requestId` 必须与响应头一致。
- `ServerWebInputException`（包含请求体校验失败和畸形 JSON）统一映射为 `BAD_REQUEST`；未识别异常统一映射为 `INTERNAL_ERROR`。
- 错误日志只记录请求 ID、错误码、HTTP 状态和异常类型，不记录异常消息或堆栈中的潜在敏感正文。
- 业务模块只依赖 `ModelGateway`；自动测试使用假网关，不调用真实模型。
- 结构化模型结果完整聚合并校验后才进入状态合并器；PRD 文本使用真实流式输出。
- SSE 使用 `requestId` 和递增 `eventId` 幂等处理；未知事件记录后忽略，已知非法事件终止任务。
- 后端 `StreamEventSequence` 固定 15 类事件、每请求从 1 递增且终态后拒绝继续发出事件。前端 `consumePostSse()` 增量解析任意分片，绑定首个请求 ID，忽略重复/迟到或其他请求事件，拒绝序号缺口、非法已知事件和无终态断流。
- 前端 `createAnalysisClient()` 保证同一客户端只有最新分析可交付事件与结果；`AnalysisView` 先展示流式临时状态，只有 `generation_completed.finalState` 才调用 `AnalysisStateRepository.saveFinal()`。该 Repository 在单个 Dexie 事务内更新项目摘要、需求、问题、答案、冲突和完整度快照，失败不会把半成品覆盖到最后有效状态。
- 后端 `GenerationTaskRegistry` 按项目跟踪当前生成请求；新请求会取代旧请求，调用方必须在持久化前检查请求仍为当前任务。前端 `useGenerationTask()` 统一管理 `AbortController`、任务版本号、完成/失败/取消状态，分析、架构、流程图和 PRD 结果都通过版本检查丢弃迟到写回。
- `GenerationProperties` 与 `StreamRetryConfig` 集中描述连接超时、总超时、最大重试次数、退避和心跳窗口；只对可恢复网络错误执行有限重试，鉴权、参数和结构业务错误不重试，流式空闲期间继续发送心跳。
- `InputSanitizer` 统一限制单字段文本、上传大小和请求体大小；`LogSanitizer` 只允许记录请求 ID、任务类型、耗时、错误类别和计数，并会裁剪或脱敏用户文本、Bearer、API Key、Authorization、Token、密码等敏感片段。
- `QuotaIndicator.vue` 在模型设置中显示系统 Key 的分析、完整 PRD 和全局调用剩余额度；额度耗尽时提示系统 Key 调用被禁止，并提供切换到用户 Key 的入口。用户 Key 失败仍保持当前来源，不回退系统 Key。
- 2 MB 内的 Markdown/TXT 按标题和段落拆成不超过 32,000 字符的片段，全部处理且不静默截断。
- 步骤 11 只建立可注入的逐片顺序处理与 `AbortSignal` 取消边界，默认执行本地片段准备；真实模型分析、上游调用进度和服务端取消将在后续分析与 SSE 步骤接入。

## 端到端测试边界

- `frontend/playwright.config.ts` 使用 Vite dev server 和无网络 mock API 夹具运行端到端测试；模型分析、回答、架构推荐、流程图生成、PRD 流、校验、变更分析、模型连接和额度接口均由 `frontend/e2e/fixtures/mock-server.ts` 确定性响应。
- Chromium 项目运行 50～54 的完整 e2e 套件，覆盖冲突/锁定/版本、创建/澄清、架构/流程图/PRD 和本地多项目/回收站。
- Firefox 项目运行创建/澄清、架构/流程图/PRD/导出和本地多项目/回收站冒烟测试；当前浏览器启动在普通 Codex 沙箱内会因 `spawn EPERM` 失败，需要提升权限运行 Playwright。

## 安全与运行约束

- 用户 Key 只在前端 Pinia 运行时内存和单次请求中存在；系统 Key 模式默认关闭，且系统 Key 永不下发到前端。
- 公网模型地址必须 HTTPS；仅本机回环地址允许 HTTP，其他私网地址必须显式加入白名单。
- 支持最新两个稳定版本的 Chrome、Edge 和 Firefox；Playwright 完整测试使用 Chromium，Firefox 执行创建、澄清、PRD/导出和本地项目核心冒烟测试。

## 里程碑维护规则

每完成重大功能后，只记录已经验证的实现、关键调用链、测试证据、已知限制和下一步；不得把计划中的能力写成已经完成。
