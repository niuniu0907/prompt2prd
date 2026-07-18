import type { Page, Route } from '@playwright/test'

/** Build an SSE frame string from a data object. */
function sseFrame(data: unknown): string {
  return `data: ${JSON.stringify(data)}\n\n`
}

/** Helper to produce a deterministic UUID-like string from a seed. */
function fixedId(seed: string): string {
  const hex = Array.from(seed.padEnd(32, '0'))
    .map((ch) => ch.charCodeAt(0).toString(16).padStart(2, '0'))
    .join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-a${hex.slice(17, 20)}-${hex.slice(20, 32)}`
}

const REQUEST_ID = fixedId('analysis-request')
const DEFAULT_PROJECT_ID = '00000000-0000-0000-0000-000000000001'

/** Common timestamp for deterministic snapshots. */
const NOW = '2026-07-18T00:00:00.000Z'

/** Simulated analysis state returned as the finalState of generation_completed. */
function analysisFinalState() {
  return {
    project: {
      id: DEFAULT_PROJECT_ID,
      name: '测试项目',
      language: 'zh-CN',
      stage: 'CLARIFYING',
      completeness: 32,
    },
    requirements: sampleRequirements(),
    questions: sampleQuestions(),
    answers: [],
    conflicts: [],
    completeness: {
      total: 32,
      dimensions: [
        { dimension: 'PRODUCT_GOAL', applicable: true, score: 65, reasons: ['推断出产品目标'] },
        { dimension: 'ROLES', applicable: true, score: 40, reasons: ['需要明确用户角色'] },
        { dimension: 'FEATURES', applicable: true, score: 30, reasons: ['核心功能已推断'] },
        { dimension: 'BUSINESS_RULES', applicable: true, score: 10, reasons: ['待确认'] },
      ],
      pendingCount: 8,
      hasCoreConflict: false,
    },
  }
}

function sampleRequirements() {
  return [
    {
      id: fixedId('req-1'),
      projectId: DEFAULT_PROJECT_ID,
      type: 'PRODUCT_GOAL',
      title: '产品目标',
      content: '构建一个帮助独立开发者从模糊需求到可执行PRD的工具',
      status: 'CONFIRMED',
      sourceType: 'AI_INFERENCE',
      sourceId: null,
      locked: false,
      metadata: {},
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: fixedId('req-2'),
      projectId: DEFAULT_PROJECT_ID,
      type: 'ROLE',
      title: '独立开发者',
      content: '使用工具梳理需求并生成PRD的独立开发者',
      status: 'PENDING',
      sourceType: 'AI_INFERENCE',
      sourceId: null,
      locked: false,
      metadata: {},
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: fixedId('req-3'),
      projectId: DEFAULT_PROJECT_ID,
      type: 'FEATURE',
      title: '需求输入',
      content: '支持文字和文件两种需求输入方式',
      status: 'CONFIRMED',
      sourceType: 'INITIAL_INPUT',
      sourceId: null,
      locked: false,
      metadata: {},
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: fixedId('req-4'),
      projectId: DEFAULT_PROJECT_ID,
      type: 'FEATURE',
      title: 'AI 分析澄清',
      content: 'AI自动分析并生成澄清问题',
      status: 'INFERRED',
      sourceType: 'AI_INFERENCE',
      sourceId: null,
      locked: false,
      metadata: {},
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: fixedId('req-5'),
      projectId: DEFAULT_PROJECT_ID,
      type: 'MISSING_INFORMATION',
      title: '需要确认目标用户规模',
      content: '目标用户是个人开发者还是团队？',
      status: 'PENDING',
      sourceType: 'AI_INFERENCE',
      sourceId: null,
      locked: false,
      metadata: {},
      createdAt: NOW,
      updatedAt: NOW,
    },
  ]
}

function sampleQuestions() {
  const batchId = fixedId('batch-1')
  return [
    {
      id: fixedId('q-1'),
      projectId: DEFAULT_PROJECT_ID,
      batchId,
      text: '产品主要面向哪类用户？',
      reason: '用户角色影响功能设计和交互方式',
      dimension: 'ROLES',
      targetField: 'roles',
      semanticKey: 'target_user_type',
      inputType: 'SINGLE_SELECT',
      options: [
        { id: fixedId('opt-1a'), label: '独立开发者', impact: '功能聚焦个人效率', recommended: true },
        { id: fixedId('opt-1b'), label: '小型团队', impact: '需要协作功能', recommended: false },
        { id: fixedId('opt-1c'), label: '企业用户', impact: '需要权限和审计', recommended: false },
      ],
      priority: 0.95,
      status: 'PENDING',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: fixedId('q-2'),
      projectId: DEFAULT_PROJECT_ID,
      batchId,
      text: '是否需要支持文件上传功能？',
      reason: '文件上传触发隐私确认和安全处理流程',
      dimension: 'FEATURES',
      targetField: 'features.fileUpload',
      semanticKey: 'file_upload_requirement',
      inputType: 'CONFIRMATION',
      options: [
        { id: fixedId('opt-2a'), label: '需要', impact: '增加文件处理和隐私确认功能', recommended: true },
        { id: fixedId('opt-2b'), label: '不需要', impact: '简化实现', recommended: false },
      ],
      priority: 0.85,
      status: 'PENDING',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: fixedId('q-3'),
      projectId: DEFAULT_PROJECT_ID,
      batchId,
      text: '你倾向于使用哪些技术栈？',
      reason: '技术栈影响架构推荐',
      dimension: 'TECHNICAL_CONSTRAINT',
      targetField: 'knownTechnologies',
      semanticKey: 'tech_stack_preference',
      inputType: 'MULTI_SELECT',
      options: [
        { id: fixedId('opt-3a'), label: 'Vue 3', impact: '匹配现有技术栈', recommended: true },
        { id: fixedId('opt-3b'), label: 'React', impact: '需要额外学习', recommended: false },
        { id: fixedId('opt-3c'), label: 'Java / Spring Boot', impact: '后端Spring Boot', recommended: true },
        { id: fixedId('opt-3d'), label: 'Python / FastAPI', impact: '轻量快速', recommended: false },
      ],
      priority: 0.72,
      status: 'PENDING',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: fixedId('q-4'),
      projectId: DEFAULT_PROJECT_ID,
      batchId,
      text: '是否有特殊的业务规则需要遵守？',
      reason: '核心业务规则影响整体架构和数据处理',
      dimension: 'BUSINESS_RULES',
      targetField: 'businessRules',
      semanticKey: 'special_business_rules',
      inputType: 'TEXT',
      options: [],
      priority: 0.68,
      status: 'PENDING',
      createdAt: NOW,
      updatedAt: NOW,
    },
    {
      id: fixedId('q-5'),
      projectId: DEFAULT_PROJECT_ID,
      batchId,
      text: '预计用户规模是多少？',
      reason: '影响性能指标和架构决策',
      dimension: 'NON_FUNCTIONAL',
      targetField: 'userScale',
      semanticKey: 'expected_user_scale',
      inputType: 'SINGLE_SELECT',
      options: [
        { id: fixedId('opt-5a'), label: '个人使用', impact: '无性能压力', recommended: true },
        { id: fixedId('opt-5b'), label: '百人级', impact: '需要基本性能优化', recommended: false },
        { id: fixedId('opt-5c'), label: '千人级以上', impact: '需要高可用架构', recommended: false },
      ],
      priority: 0.60,
      status: 'PENDING',
      createdAt: NOW,
      updatedAt: NOW,
    },
  ]
}

/** Architecture candidates for mock response. */
function sampleArchitectureCandidates() {
  return [
    {
      id: fixedId('arch-1'),
      name: 'Vue 3 + Spring Boot 单体',
      stack: {
        frontend: 'Vue 3 + Vite + Element Plus',
        backend: 'Java 21 + Spring Boot 4 + WebFlux',
        storage: '浏览器 IndexedDB（本地优先）',
        auth: '无需认证（本地单用户）',
        ai: 'Spring AI 2.x 代理 OpenAI/DeepSeek',
        deployment: 'Docker 单容器',
      },
      responsibilities: [
        '前端 IndexedDB 持久化',
        '后端 AI 代理 + SSE 流式',
        'Maven 统一构建',
      ],
      advantages: [
        '技术栈与现有知识匹配',
        '单体部署简单',
        '开发速度快',
      ],
      disadvantages: [
        '不适合大规模协作',
        '单体扩展性有限',
      ],
      limitations: [
        '仅限单用户本地使用',
        '无后端数据库',
      ],
      unselectedReasons: [],
      scores: {
        LEARNING_COST: 95,
        DEVELOPMENT_SPEED: 90,
        DEPLOYMENT_SIMPLICITY: 88,
        RUNNING_COST: 85,
        MAINTAINABILITY: 82,
        SCALABILITY: 40,
        AI_SUPPORT: 90,
      },
      totalScore: 81,
      recommended: true,
    },
    {
      id: fixedId('arch-2'),
      name: 'Next.js 全栈',
      stack: {
        frontend: 'React + Next.js + Tailwind',
        backend: 'Next.js API Routes + Prisma',
        storage: 'PostgreSQL + Redis',
        auth: 'NextAuth.js',
        ai: 'Vercel AI SDK',
        deployment: 'Vercel + Railway',
      },
      responsibilities: [
        'Next.js 全栈处理',
        'Prisma ORM 数据层',
        'Vercel AI 流式',
      ],
      advantages: [
        '全栈 JavaScript 统一',
        'Vercel 部署便捷',
        '社区生态丰富',
      ],
      disadvantages: [
        '需要学习 React 生态',
        'PostgreSQL 运维成本',
        '不符合现有 Java 技能',
      ],
      limitations: [
        '冷启动可能延迟',
        'Vercel 定价随规模增长',
      ],
      unselectedReasons: ['技术栈不匹配现有技能', '引入数据库增加复杂度'],
      scores: {
        LEARNING_COST: 40,
        DEVELOPMENT_SPEED: 78,
        DEPLOYMENT_SIMPLICITY: 75,
        RUNNING_COST: 55,
        MAINTAINABILITY: 72,
        SCALABILITY: 85,
        AI_SUPPORT: 92,
      },
      totalScore: 67,
      recommended: false,
    },
    {
      id: fixedId('arch-3'),
      name: 'Python FastAPI + Vue 3',
      stack: {
        frontend: 'Vue 3 + Vite + Element Plus',
        backend: 'Python 3.12 + FastAPI',
        storage: 'SQLite + ChromaDB',
        auth: '无需认证',
        ai: 'LangChain + OpenAI',
        deployment: 'Docker Compose',
      },
      responsibilities: [
        'FastAPI 后端服务',
        'LangChain AI 编排',
        'SQLite 本地存储',
      ],
      advantages: [
        'Python AI 生态成熟',
        'FastAPI 开发快',
        '前端复用 Vue 技能',
      ],
      disadvantages: [
        'Python 类型安全弱于 Java',
        '多语言维护成本',
        'SQLite 并发限制',
      ],
      limitations: [
        'ChromaDB 向量检索需要额外内存',
        'Python 依赖管理复杂',
      ],
      unselectedReasons: ['多语言维护成本高', 'Java后端经验无法复用'],
      scores: {
        LEARNING_COST: 50,
        DEVELOPMENT_SPEED: 85,
        DEPLOYMENT_SIMPLICITY: 65,
        RUNNING_COST: 60,
        MAINTAINABILITY: 58,
        SCALABILITY: 55,
        AI_SUPPORT: 95,
      },
      totalScore: 66,
      recommended: false,
    },
  ]
}

/** Flowchart mock response. */
function sampleFlowchartResponse() {
  return {
    mainFlow: {
      key: 'main-flow',
      type: 'MAIN',
      title: '核心业务流程',
      mermaid: 'graph TD\n  A[用户输入需求] --> B[AI分析需求]\n  B --> C[生成澄清问题]\n  C --> D[用户回答问题]\n  D --> E[更新需求状态]\n  E --> F{完整度 >= 80%?}\n  F -->|是| G[进入架构阶段]\n  F -->|否| C',
      sourceRequirementIds: [fixedId('req-1'), fixedId('req-3')],
      status: 'GENERATED',
      errorCode: null,
    },
    exceptionFlows: [
      {
        key: 'exception-upload-failure',
        type: 'EXCEPTION',
        title: '文件上传失败处理',
        mermaid: 'graph TD\n  A[用户上传文件] --> B{文件校验}\n  B -->|通过| C[解析文件内容]\n  B -->|失败| D[显示错误提示]\n  D --> E[用户重新上传]',
        sourceRequirementIds: [fixedId('req-3')],
        status: 'GENERATED',
        errorCode: null,
      },
    ],
    missingInformation: [],
  }
}

// ---------------------------------------------------------------------------
// Route handler helpers
// ---------------------------------------------------------------------------

interface MockRoutesOptions {
  /**
   * Override the analysis SSE sequence.
   * Return an array of objects that will be sent as SSE frames.
   */
  analysisSequence?: unknown[]
  /**
   * Override the answer SSE sequence.
   */
  answerSequence?: unknown[]
  /**
   * Override the PRD SSE sequence.
   */
  prdSequence?: unknown[]
  /**
   * Override the PRD section SSE sequence.
   */
  prdSectionSequence?: unknown[]
  /**
   * Make the first analysis call fail.
   */
  failFirstAnalysis?: boolean
  /**
   * Simulate a slow response by adding delay.
   */
  slowMs?: number
}

/** Build a standard analysis SSE event sequence. */
function buildAnalysisSequence(): unknown[] {
  const ts = NOW
  return [
    { requestId: REQUEST_ID, eventId: 1, type: 'analysis_started', data: { phase: 'initial' }, timestamp: ts },
    { requestId: REQUEST_ID, eventId: 2, type: 'analysis_progress', data: { progress: 30, message: '正在提取关键信息' }, timestamp: ts },
    {
      requestId: REQUEST_ID, eventId: 3, type: 'requirement_patch',
      data: { path: 'requirements', operation: 'UPSERT', value: sampleRequirements()[0] },
      timestamp: ts,
    },
    {
      requestId: REQUEST_ID, eventId: 4, type: 'requirement_patch',
      data: { path: 'requirements', operation: 'UPSERT', value: sampleRequirements()[3] },
      timestamp: ts,
    },
    { requestId: REQUEST_ID, eventId: 5, type: 'analysis_progress', data: { progress: 60, message: '正在生成澄清问题' }, timestamp: ts },
    {
      requestId: REQUEST_ID, eventId: 6, type: 'question_created',
      data: { question: sampleQuestions()[0] },
      timestamp: ts,
    },
    {
      requestId: REQUEST_ID, eventId: 7, type: 'question_created',
      data: { question: sampleQuestions()[1] },
      timestamp: ts,
    },
    {
      requestId: REQUEST_ID, eventId: 8, type: 'question_created',
      data: { question: sampleQuestions()[2] },
      timestamp: ts,
    },
    {
      requestId: REQUEST_ID, eventId: 9, type: 'question_created',
      data: { question: sampleQuestions()[3] },
      timestamp: ts,
    },
    {
      requestId: REQUEST_ID, eventId: 10, type: 'question_created',
      data: { question: sampleQuestions()[4] },
      timestamp: ts,
    },
    { requestId: REQUEST_ID, eventId: 11, type: 'completeness_changed', data: { previous: 0, current: 32, missingInformation: [] }, timestamp: ts },
    { requestId: REQUEST_ID, eventId: 12, type: 'generation_completed', data: { nextStage: 'CLARIFYING', finalState: analysisFinalState() }, timestamp: ts },
  ]
}

/** Build an answer submission SSE event sequence. */
function buildAnswerSequence(): unknown[] {
  const ts = NOW
  const answerReqId = fixedId('answer-request')
  return [
    { requestId: answerReqId, eventId: 1, type: 'analysis_started', data: { phase: 'clarification' }, timestamp: ts },
    { requestId: answerReqId, eventId: 2, type: 'analysis_progress', data: { progress: 40, message: '正在分析你的回答' }, timestamp: ts },
    {
      requestId: answerReqId, eventId: 3, type: 'requirement_patch',
      data: {
        path: 'requirements', operation: 'UPSERT',
        value: {
          id: fixedId('req-2'),
          projectId: DEFAULT_PROJECT_ID,
          type: 'ROLE', title: '独立开发者', content: '使用工具梳理需求并生成PRD的独立开发者',
          status: 'CONFIRMED', sourceType: 'USER_ANSWER', sourceId: null, locked: false, metadata: {},
          createdAt: NOW, updatedAt: NOW,
        },
      },
      timestamp: ts,
    },
    {
      requestId: answerReqId, eventId: 4, type: 'completeness_changed',
      data: { previous: 32, current: 55, missingInformation: [] },
      timestamp: ts,
    },
    { requestId: answerReqId, eventId: 5, type: 'generation_completed', data: { nextStage: 'CLARIFYING', finalState: { ...analysisFinalState(), completeness: { ...analysisFinalState().completeness, total: 55 } } }, timestamp: ts },
  ]
}

/** Build a PRD generation SSE event sequence. */
function buildPrdSequence(): unknown[] {
  const ts = NOW
  const prdReqId = fixedId('prd-request')
  const sections = [
    { key: 'product-context', title: '产品背景、目标与非目标' },
    { key: 'roles-permissions', title: '用户角色与权限' },
    { key: 'features-priorities', title: '功能模块及优先级' },
  ]
  const events: unknown[] = [{ requestId: prdReqId, eventId: 1, type: 'analysis_started', data: { phase: 'prd' }, timestamp: ts }]

  let eventId = 2
  for (const section of sections) {
    events.push({ requestId: prdReqId, eventId: eventId++, type: 'section_started', data: { sectionId: section.key, title: section.title }, timestamp: ts })
    events.push({ requestId: prdReqId, eventId: eventId++, type: 'section_delta', data: { sectionId: section.key, delta: `## ${section.title}\n\n这是${section.title}的自动生成内容。\n\n基于当前已确认需求和架构信息生成。\n` }, timestamp: ts })
    events.push({ requestId: prdReqId, eventId: eventId++, type: 'section_completed', data: { sectionId: section.key, status: 'generated' }, timestamp: ts })
  }

  events.push({ requestId: prdReqId, eventId: eventId++, type: 'generation_completed', data: { nextStage: 'PRD', finalState: {} }, timestamp: ts })
  return events
}

/** Build a single PRD section generation SSE sequence. */
function buildPrdSectionSequence(sectionKey: string): unknown[] {
  const ts = NOW
  const reqId = fixedId(`prd-section-${sectionKey}`)
  return [
    { requestId: reqId, eventId: 1, type: 'section_started', data: { sectionId: sectionKey, title: sectionKey }, timestamp: ts },
    { requestId: reqId, eventId: 2, type: 'section_delta', data: { sectionId: sectionKey, delta: `## ${sectionKey}\n\n重新生成的内容。\n` }, timestamp: ts },
    { requestId: reqId, eventId: 3, type: 'section_completed', data: { sectionId: sectionKey, status: 'generated' }, timestamp: ts },
    { requestId: reqId, eventId: 4, type: 'generation_completed', data: { nextStage: 'PRD', finalState: {} }, timestamp: ts },
  ]
}

/**
 * Register all mock API route handlers on a Playwright Page.
 * Call this before navigating to any page.
 */
export async function setupMockRoutes(page: Page, options: MockRoutesOptions = {}): Promise<void> {
  // Remove all existing routes first to avoid conflicts
  await page.unrouteAll({ behavior: 'ignoreErrors' })

  // ----- POST /api/analysis (SSE) -----
  await page.route('**/api/analysis', async (route: Route) => {
    if (options.failFirstAnalysis) {
      await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: '模拟的分析服务错误。', errorCode: 'INTERNAL_ERROR' }) })
      return
    }
    await serveSse(route, projectScopedSequence(route, options.analysisSequence ?? buildAnalysisSequence()), options.slowMs)
  })

  // ----- POST /api/analysis/answers (SSE) -----
  await page.route('**/api/analysis/answers', async (route: Route) => {
    await serveSse(route, projectScopedSequence(route, options.answerSequence ?? buildAnswerSequence()), options.slowMs)
  })

  // ----- POST /api/architecture/recommend -----
  await page.route('**/api/architecture/recommend', async (route: Route) => {
    if (options.slowMs) await sleep(options.slowMs)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        candidates: sampleArchitectureCandidates(),
        pendingFields: [],
      }),
    })
  })

  // ----- POST /api/generation/flowchart -----
  await page.route('**/api/generation/flowchart', async (route: Route) => {
    if (options.slowMs) await sleep(options.slowMs)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(sampleFlowchartResponse()),
    })
  })

  // ----- POST /api/generation/prd (SSE for full generation) -----
  await page.route('**/api/generation/prd', async (route: Route) => {
    // The URL may end with /api/generation/prd or /api/generation/prd/sections/xxx
    // This route handles the base /prd path; the sections path is handled separately below
    if (route.request().url().includes('/sections/')) {
      // This is a section-specific request — delegate to the sections handler
      return route.fallback()
    }
    await serveSse(route, options.prdSequence ?? buildPrdSequence(), options.slowMs)
  })

  // ----- POST /api/generation/prd/sections/:sectionId (SSE) -----
  await page.route(/\/api\/generation\/prd\/sections\//, async (route: Route) => {
    const url = route.request().url()
    const match = url.match(/\/sections\/([^/?]+)/)
    const sectionKey = match ? decodeURIComponent(match[1]) : 'unknown'
    const sequence = options.prdSectionSequence ?? buildPrdSectionSequence(sectionKey)
    await serveSse(route, sequence, options.slowMs)
  })

  // ----- POST /api/generation/prd/validate -----
  await page.route('**/api/generation/prd/validate', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ valid: true, errors: [], warnings: ['部分章节为草稿状态'] }),
    })
  })

  // ----- POST /api/generation/prd/analyze-changes -----
  await page.route('**/api/generation/prd/analyze-changes', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        syncedChanges: [],
        pendingChanges: [],
        conflictWarnings: [],
        hasChanges: false,
      }),
    })
  })

  // ----- POST /api/model-config/test -----
  await page.route('**/api/model-config/test', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ connected: true, modelName: 'gpt-4o', latencyMs: 120 }),
    })
  })

  // ----- GET /api/model-config/capabilities -----
  await page.route('**/api/model-config/capabilities', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        systemKeyAvailable: false,
        providers: ['OPENAI', 'DEEPSEEK', 'QWEN', 'CUSTOM'],
      }),
    })
  })

  // ----- GET /api/quota -----
  await page.route('**/api/quota', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        dailyAnalysisUsed: 0,
        dailyAnalysisLimit: 3,
        dailyPrdUsed: 0,
        dailyPrdLimit: 1,
        globalDailyUsed: 0,
        globalDailyLimit: 50,
      }),
    })
  })
}

/**
 * Serve an SSE stream of events through a Playwright route.
 */
async function serveSse(route: Route, events: unknown[], delayMs?: number): Promise<void> {
  const body = events.map((event) => sseFrame(event)).join('')

  if (delayMs) await sleep(delayMs)

  await route.fulfill({
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    },
    body,
  })
}

interface ProjectScope {
  id: string
  name?: string
}

function projectScopedSequence(route: Route, events: unknown[]): unknown[] {
  return rewriteProjectRefs(events, projectScopeFromRequest(route)) as unknown[]
}

function projectScopeFromRequest(route: Route): ProjectScope {
  try {
    const body = JSON.parse(route.request().postData() ?? '{}') as {
      state?: { project?: { id?: unknown, name?: unknown } }
    }
    return {
      id: typeof body.state?.project?.id === 'string' ? body.state.project.id : DEFAULT_PROJECT_ID,
      name: typeof body.state?.project?.name === 'string' ? body.state.project.name : undefined,
    }
  } catch {
    return { id: DEFAULT_PROJECT_ID }
  }
}

function rewriteProjectRefs(value: unknown, scope: ProjectScope): unknown {
  if (Array.isArray(value)) return value.map(item => rewriteProjectRefs(item, scope))
  if (!value || typeof value !== 'object') return value

  const output: Record<string, unknown> = {}
  for (const [key, item] of Object.entries(value)) {
    if (key === 'projectId' && typeof item === 'string') {
      output[key] = scope.id
    } else if (key === 'project' && item && typeof item === 'object' && 'id' in item) {
      output[key] = {
        ...(rewriteProjectRefs(item, scope) as Record<string, unknown>),
        id: scope.id,
        ...(scope.name ? { name: scope.name } : {}),
      }
    } else {
      output[key] = rewriteProjectRefs(item, scope)
    }
  }
  return output
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// Re-export sample data for use in assertions
export { fixedId, NOW, REQUEST_ID, sampleRequirements, sampleQuestions, sampleArchitectureCandidates, sampleFlowchartResponse, buildAnalysisSequence, buildAnswerSequence, buildPrdSequence }
