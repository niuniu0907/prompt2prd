# Prompt2PRD Architecture

> 当前状态：架构已确认，阶段一已完成，阶段二的前端领域类型、IndexedDB Schema v1、项目 Repository、真实项目首页、文字新建项目流程、Markdown/TXT 上传、预览、隐私确认、文本分块、项目工作台导航、事务保存与版本记录已建立。产品事实以 `memory-bank/design-doc.md` 为准，实施顺序以 `memory-bank/implementation-plan.md` 为准。

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
- `AppShell.vue` 负责稳定的全局左导航和主画布插槽；全部项目、已归档和回收站会发出生命周期导航事件并保持唯一当前项，模型设置在对应功能实现前保持禁用。
- 路由已更新为嵌套结构：`/` 项目首页、`/projects/new` 新建项目、`/projects/:projectId` 项目工作台（含 6 个子路由 `overview`、`questions`、`requirements`、`architecture`、`flowchart`、`prd`），默认重定向至 `overview`。
- `ProjectWorkspace.vue` 是项目工作台的顶层布局：包裹 `AppShell`，自行从 IndexedDB 加载项目数据，三栏布局由 `ProjectNavigation`（左）| `<router-view>` 主画布（中）| 可收起辅助面板（右）组成。流程图和 PRD 页面右侧面板默认收起以释放主画布宽度。
- `ProjectNavigation.vue` 提供六个模块的二级导航，高亮当前活跃模块并发射 `navigate` 事件；`ProjectHeader.vue` 展示项目名称、阶段、完整度、模型、保存状态和"生成 PRD"按钮。
- `ProjectHomeView.vue` 通过可注入的 `ProjectHomeRepository` 加载真实 IndexedDB 摘要，管理活动/归档/回收站视图、请求迟到保护、操作忙碌状态以及读取/写入失败反馈；失败不会把已有项目伪装为空列表。
- `ProjectList.vue` 只负责网格布局和事件转发，`ProjectCard.vue` 展示名称、阶段、完整度、待确认数与更新时间，并把重命名、复制、归档、回收站、恢复和永久删除收进卡片菜单；永久删除仍需浏览器确认。
- `ProjectEmptyState.vue` 为三种生命周期视图提供不同空状态，只有活动视图显示创建入口；首页顶部与空状态两个入口均进入同一个新建项目页，源码不创建示例项目或示例需求。
- `ProjectCreateForm.vue` 负责 Unicode 字符计数、至少 5 字文字校验、20 字临时名称预览和创建请求规范化；它接收步骤 11 文件组件传入的已确认文件内容，并把文件原文与可选补充说明交给现有项目创建边界。
- `RequirementFileUpload.vue` 负责 `.md`/`.txt` 文件选择、首次隐私确认、只读文本预览、片段准备进度与取消；只有全部片段完成本地顺序处理后才向新建项目页发出已确认文件，清除文件会恢复纯文字创建规则。
- `fileParser.ts` 使用严格 UTF-8 解码，拒绝错误扩展名、超过 2 MiB、无法解码和空白文件；统一换行后优先沿 Markdown 标题和自然段边界分块，超长语义块按 Unicode 码点硬拆，单片最多 32,000 字符且所有片段重新连接后与解析全文一致。
- `NewProjectView.vue` 在 Repository 创建成功后才跳转到带项目 UUID 的入口页，IndexedDB 失败时保留表单并提示未保存；`ProjectStartView.vue` 从 IndexedDB 重读项目并明确标注真实 AI 分析流尚未接入，避免把后续能力伪装为已完成。
- `styles/tokens.css` 是当前前端视觉变量来源，严格使用设计文档的八个颜色 Token；主按钮和当前导航使用 `#c7eb64` 背景与 `#262b25` 文字，普通辅助文字使用 `#626d72`。
- MVP 当前只声明浅色 `color-scheme`，不提供深色模式或主题开关。界面使用结构化间距、细边框和轻阴影，不依赖外部字体或装饰图片。
- 桌面工作台基线为固定侧栏加弹性主画布，最小页面宽度 960 像素；真实项目卡片在 1280×800 验收时无横向溢出。

## 状态与持久化边界

- IndexedDB 是项目、需求、问答、冲突、版本、流程图和 PRD 的唯一持久化位置。
- `features/projects/types.ts`、`features/requirements/types.ts` 和 `features/prd/types.ts` 定义当前持久化领域契约；项目与需求工厂在对象入库前校验 UUID、UTC ISO-8601 时间、枚举值和锁定规则。
- `db/appDatabase.ts` 使用 Dexie 声明数据库版本 1；对象仓库固定为 `project`、`requirement_item`、`clarification_question`、`clarification_answer`、`requirement_conflict`、`requirement_version`、`requirement_change`、`prd_section` 和 `app_setting`。
- 项目关联仓库使用 `projectId` 及状态、类型、批次、版本或章节键的复合索引，为后续多项目隔离查询和事务操作提供边界。
- `app_setting` 当前只允许持久化首次上传隐私提示确认状态；Schema 不包含 API Key、Authorization、凭据、密钥或 Token 仓库与字段。
- 文件上传隐私确认固定使用 `app_setting.key = uploadPrivacyNoticeAccepted`，只保存布尔确认值和 UTC 更新时间；文件预览与分块期间不向后端发送内容。
- `db/repositories/projectRepository.ts` 提供创建、读取、按生命周期列表、重命名、复制、归档、移入回收站、恢复和永久删除；生产实例使用共享数据库，测试通过构造器注入隔离数据库。创建由单次 IndexedDB 写入完成，失败不会留下第二个半成品项目。
- `applySuggestedName()` 为后续首次分析返回名称预留确定性写入边界：空建议保留临时名称，`userRenamed = true` 时拒绝迟到的模型命名，只有尚未手动改名的项目会更新时间和建议名称。
- `listSummaries()` 在只读事务中返回按更新时间倒序的项目及待确认数；待确认数严格等于该项目 `PENDING` 需求项与 `PENDING` 澄清问题之和。
- 重命名和生命周期变更使用项目表事务；普通删除只写 `status = DELETED` 与 `deletedAt`，不会清理关联数据，恢复会回到 `ACTIVE` 并清除归档和删除时间。
- 项目复制在一个跨仓库事务中生成新的项目、需求、问题批次、问题选项、答案、冲突、版本、变更和 PRD 章节 UUID，并重映射当前记录和版本快照内的引用；副本固定回到活动状态，与原项目相互独立。
- 永久删除只允许回收站项目，并在一个事务中按明确 `projectId` 清理七类关联记录及项目本身；不会提供批量清空回收站，也不会修改 `app_setting` 或其他项目。
- Vitest 使用 `fake-indexeddb` 为每个测试创建独立数据库，已验证首次打开、关闭后重开、版本号、Repository 事务行为及项目首页操作调用链。
- AI 候选补丁由后端完成校验、确定性合并、冲突检测和完整度计算；后端不保存项目。
- 用户确认、锁定、冲突解决和手动编辑由前端确定性处理并写入 IndexedDB。
- 需求状态为 `INFERRED`、`PENDING`、`CONFIRMED`、`CONFLICTED`；锁定使用独立 `locked` 字段。
- ID 使用 UUID，持久化时间使用 UTC ISO-8601；版本保存完整快照和字段级摘要，MVP 不自动清理。
- `db/repositories/requirementRepository.ts` 提供需求项的按 ID 读取、按项目列表、无版本单条更新和事务性批量保存。`save()` 在单个 Dexie 事务中完成：替换全部需求项、捕获项目/需求/问题/答案/冲突完整快照、创建版本摘要记录和字段级变更记录；任何写入失败都会回滚整个事务，不留部分数据。
- `updateRequirement()` 只写入 `requirement_item` 表且不创建版本，用于编辑过程中的草稿保存；连续多次调用不会生成历史版本，版本仅在显式调用 `save()` 时创建。
- `db/repositories/versionRepository.ts` 提供按项目倒序列出版本、按 ID 读取版本、读取版本及关联变更，以及恢复到指定历史版本。`restore()` 在单个事务中先保存当前完整状态为 RESTORE 类型版本，再将目标快照的项目/需求/问题/答案/冲突写回各表，最后创建恢复版本记录；恢复失败时当前状态不受影响。

## AI 与流式边界

- 业务模块只依赖 `ModelGateway`；自动测试使用假网关，不调用真实模型。
- 结构化模型结果完整聚合并校验后才进入状态合并器；PRD 文本使用真实流式输出。
- SSE 使用 `requestId` 和递增 `eventId` 幂等处理；未知事件记录后忽略，已知非法事件终止任务。
- 2 MB 内的 Markdown/TXT 按标题和段落拆成不超过 32,000 字符的片段，全部处理且不静默截断。
- 步骤 11 只建立可注入的逐片顺序处理与 `AbortSignal` 取消边界，默认执行本地片段准备；真实模型分析、上游调用进度和服务端取消将在后续分析与 SSE 步骤接入。

## 安全与运行约束

- 用户 Key 只在前端运行时内存和单次请求中存在；系统 Key 模式默认关闭。
- 公网模型地址必须 HTTPS；仅本机回环地址允许 HTTP，其他私网地址必须显式加入白名单。
- 支持最新两个稳定版本的 Chrome、Edge 和 Firefox；Playwright 完整测试使用 Chromium，Firefox执行核心冒烟测试。

## 里程碑维护规则

每完成重大功能后，只记录已经验证的实现、关键调用链、测试证据、已知限制和下一步；不得把计划中的能力写成已经完成。
