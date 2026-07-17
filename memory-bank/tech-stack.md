# Prompt2PRD 技术栈

> 依据：`design-doc.md` V1.1  
> 目标：个人开发可控、单机部署简单，同时保证 AI 输出、流式交互和本地数据不会轻易失控。

## 1. 总体方案

采用 **Vue 3 + Spring Boot 的前后端单体架构**：

- 浏览器负责界面、需求状态和项目数据持久化。
- Spring Boot 只负责 AI 调用、结果校验、SSE 流式输出和额度控制。
- Vue 构建产物打包进 Spring Boot，最终交付一个可运行 JAR 或单个 Docker 容器。
- MVP 不引入登录、服务端业务数据库、Redis、消息队列、微服务和向量数据库。

## 2. 技术选型

| 层级 | 技术 | 用途与选择理由 |
|---|---|---|
| 开发语言 | Java 21 LTS、TypeScript | Java 21 成熟稳定；TypeScript 为需求状态、SSE 事件和接口数据提供静态类型检查。 |
| 前端框架 | Vue 3、Composition API、Vite | 符合现有技术基础，开发快，组件和类型支持成熟。 |
| 路由与状态 | Vue Router、Pinia | Router 管理工作台页面；Pinia 只保存当前会话和界面状态，避免与 IndexedDB 重复承担持久化职责。 |
| UI 与样式 | Element Plus、CSS Variables | 快速实现桌面工作台；颜色、间距和层级通过 CSS Token 统一，不额外引入大型样式框架。 |
| 本地数据 | IndexedDB、Dexie.js | 在浏览器保存项目、需求项、版本和 PRD；Dexie 负责事务、索引、数据库版本和迁移。 |
| Markdown | markdown-it | PRD 预览；默认关闭原始 HTML，降低 XSS 风险。 |
| 流程图 | Mermaid | 渲染和校验业务流程图，并保留可导出的 Mermaid 源码。 |
| 后端框架 | Spring Boot 4.1.x、Spring WebFlux | 提供 API、响应式 AI 调用和 SSE；全链路采用 WebFlux，避免同时混用 MVC。 |
| AI 接入 | Spring AI 2.0.x `ChatClient` | 统一接入 OpenAI 兼容模型，支持同步结构化结果和流式 Markdown 文本。 |
| 结构化校验 | Java record/DTO、Spring AI structured output、Jakarta Validation、业务校验 | 模型结果必须依次完成反序列化、字段约束和业务规则校验，校验通过后才进入正式状态。 |
| 流式协议 | SSE、Reactor `Flux`、浏览器 `fetch` + `AbortController` | 同一个 POST 请求既能提交当前状态，又能持续接收事件；支持停止生成和上游取消。 |
| 限流与额度 | Caffeine 本地缓存 + 原子计数 | 提供有容量上限、自动过期的单实例计数，避免手写清理逻辑造成内存增长；以后可在接口不变的情况下替换为 Redis。 |
| 后端测试 | JUnit 5、Spring Boot Test、WebTestClient | 覆盖确定性业务规则、接口校验、SSE 顺序、超时和取消。 |
| 前端测试 | Vitest、Vue Test Utils | 覆盖 Store、Dexie Repository、完整度计算和关键组件交互。 |
| 端到端测试 | Playwright | 只覆盖创建项目、问答保存、停止生成、恢复版本和导出 PRD 等核心闭环。 |
| 可观测性 | SLF4J + Logback、Spring Boot Actuator | 结构化记录请求 ID、任务 ID、耗时和错误类型；健康检查供本地与 Docker 使用。 |
| 构建交付 | Maven Wrapper、Node.js LTS、npm、Docker | 提交 `package-lock.json`；Maven 统一后端构建并调用前端构建，产出单个 JAR 和单容器镜像。 |

版本采用“主次版本锁定、补丁版本可升级”的策略，实际版本统一写在 `pom.xml`、`package.json` 和 lockfile 中，不在业务代码中分散维护。

## 3. 工程边界

### 前端负责

- 项目创建、重命名、复制、归档、回收站和恢复。
- 需求卡片、问题答案、冲突、版本、PRD 章节的 IndexedDB 持久化。
- 完整度的即时展示、Markdown/Mermaid 渲染和文件导出。
- SSE 事件去重、排序、断流提示以及 `AbortController` 取消请求。
- 用户 API Key 只保存在运行时内存，刷新页面后清除。
- 用户确认、拒绝、锁定、解锁、冲突解决和手动编辑采用前端确定性逻辑，并把最终状态持久化到 IndexedDB。

### 后端负责

- 模型配置测试和 OpenAI 兼容服务代理。
- 需求分析、架构推荐、流程图和 PRD 生成。
- 模型输出的 DTO、Schema、字段和业务规则校验。
- 请求超时、有限重试、取消传播、SSE 心跳和统一错误码。
- 系统 Key 的安全读取、单 IP 额度和进程级预算保护。
- AI 候选补丁的确定性合并、冲突检测和完整度计算；后端只返回最终有效状态，不保存项目。

### 后端不负责

- 不保存用户项目、回答、需求正文或 PRD。
- 不保存、缓存或记录用户 API Key。
- 不为 MVP 提供登录、多用户协作和云端同步。

## 4. 健壮性约束

1. **正式状态只接收完整结果**：结构化 AI 输出先完整聚合并校验，再逐条发送展示事件；不解析半截 JSON。
2. **状态修改保持确定性**：模型只能生成候选补丁，锁定、合并、冲突检测和完整度计算由普通代码完成。
3. **事件可幂等处理**：每次生成包含 `requestId`，每个事件包含递增 `eventId`；前端忽略旧请求和重复事件。
4. **本地写入必须可确认**：Dexie 写入成功后才显示“已保存”；相关对象使用同一事务提交，失败时回滚并提示。
5. **取消和超时贯穿全链路**：前端取消请求后，WebFlux 必须取消上游模型流；超时和重试次数集中配置，禁止无限重试。
6. **安全默认开启**：Markdown 禁止原始 HTML；上传限制为 `.md`/`.txt` 和 2 MB；日志对 Key、Authorization 和敏感正文做排除或脱敏。
7. **模型差异被隔离**：业务层依赖自定义 `ModelGateway` 接口，不直接依赖具体厂商客户端；供应商配置转换集中在适配层。
8. **失败不覆盖已有数据**：生成失败、断流或非法响应只标记当前任务失败，不能清空已确认需求或已保存版本。
9. **模型地址默认安全**：公网模型地址必须使用 HTTPS；只有本机回环地址允许 HTTP，其他私网地址必须由部署者显式加入白名单。
10. **系统 Key 默认关闭**：只有同时配置密钥和启用开关时才开放系统 Key 与免费额度，否则仅提供用户 Key。

## 5. 浏览器与测试环境

- 支持最新两个稳定版本的 Chrome、Edge 和 Firefox 桌面浏览器。
- Vitest 中使用隔离的 IndexedDB 测试实现，Playwright 以 Chromium 为必测目标并对 Firefox 执行核心冒烟流程。
- 自动化模型测试全部使用可控假网关；真实模型只通过用户主动执行的连接测试验证。

## 6. 建议的仓库结构

```text
Prompt2PRD/
├─ frontend/                 # Vue 3 应用
│  ├─ src/
│  │  ├─ api/               # HTTP、SSE 与事件类型
│  │  ├─ components/        # 可复用组件
│  │  ├─ db/                # Dexie schema、迁移和 Repository
│  │  ├─ features/          # project、analysis、architecture、prd
│  │  ├─ stores/            # 仅会话和界面状态
│  │  └─ views/             # 路由页面
│  └─ package.json
├─ backend/                  # Spring Boot 应用
│  ├─ src/main/java/.../
│  │  ├─ analysis/          # 需求分析与问题选择
│  │  ├─ architecture/      # 架构候选与确认数据
│  │  ├─ generation/        # 流程图和 PRD 生成
│  │  ├─ model/             # ModelGateway 与厂商适配
│  │  ├─ stream/            # SSE 协议和任务生命周期
│  │  ├─ quota/             # 限流与额度
│  │  └─ common/            # 错误码、日志、校验和配置
│  └─ pom.xml
├─ Dockerfile
├─ pom.xml                   # 聚合构建
└─ README.md
```

按业务能力分包，不提前拆成多个服务；只有同一能力内部再区分 controller、service、domain 和 adapter。

## 7. 暂不引入

| 技术 | 暂不使用的原因 | 何时再引入 |
|---|---|---|
| MySQL/PostgreSQL | 项目数据明确保存在用户本地，服务端数据库会增加部署和隐私成本。 | 需要账号、云同步或多人协作时。 |
| Redis | MVP 是单实例，本地额度缓存足够。 | 正式公开部署、多实例或需要跨重启保留额度时。 |
| Kafka/RabbitMQ | 当前任务是单请求流式生成，没有独立消息消费链路。 | 出现长时后台任务和可靠异步消费需求时。 |
| 微服务 | 模块规模和团队规模不足以抵消运维成本。 | 团队和流量扩大，且存在明确独立伸缩边界时。 |
| 向量数据库/RAG | MVP 使用结构化状态控制上下文，不依赖知识库检索。 | 需要跨项目知识复用或大型资料库检索时。 |
| WebSocket | 当前通信主要是单向服务端推送，SSE 更简单。 | 需要多人实时协作或高频双向消息时。 |
| Spring Security | MVP 没有账号体系；Key 安全由专门过滤与配置策略保证。 | 增加登录、角色和权限时。 |

## 8. 最终结论

Prompt2PRD 的 MVP 使用一套单体、同源、以本地数据为核心的技术栈：

**Vue 3 + TypeScript + Pinia + Dexie/IndexedDB + Element Plus + Spring Boot WebFlux + Spring AI + SSE + Maven + Docker。**

健壮性主要来自类型、Schema 校验、确定性状态合并、幂等事件、事务保存、超时取消和关键闭环测试，而不是增加基础设施数量。
