import 'fake-indexeddb/auto'

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { ProjectLookupRepository } from '@/db/repositories/projectRepository'
import type { AnalysisState, AnalysisStateStore } from '@/db/repositories/analysisStateRepository'
import type { Project } from '@/features/projects/types'
import { PROJECT_MODULES } from '@/features/projects/types'
import ProjectWorkspace from '@/layouts/ProjectWorkspace.vue'
import { createAppRouter } from '@/router'

const project: Project = {
  id: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
  name: '宠物寄养平台',
  originalPrompt: '我要做一个宠物寄养平台',
  uploadedFileName: null,
  uploadedFileContent: null,
  supplementalPrompt: null,
  language: 'zh-CN',
  stage: 'CLARIFYING',
  status: 'ACTIVE',
  completeness: 45,
  userRenamed: false,
  archivedAt: null,
  deletedAt: null,
  createdAt: '2026-07-17T08:00:00.000Z',
  updatedAt: '2026-07-17T08:00:00.000Z',
}

function createRepository(overrides?: Partial<ProjectLookupRepository>): ProjectLookupRepository {
  return {
    getById: vi.fn(async (_id: string): Promise<Project | undefined> => project),
    ...overrides,
  }
}

async function mountWorkspace(
  repository: ProjectLookupRepository,
  initialRoute = `/projects/${project.id}/overview`,
  stateStore?: AnalysisStateStore,
  architectureSelected: (projectId: string) => Promise<unknown> = vi.fn(async () => null),
) {
  const history = createMemoryHistory()
  const router = createAppRouter(history)

  await router.push(initialRoute)
  await router.isReady()

  const wrapper = mount(ProjectWorkspace, {
    props: {
      repository,
      stateStore: stateStore ?? emptyStateStore(),
      architectureSelected,
    },
    global: {
      plugins: [router],
    },
  })
  await flushPromises()

  return { wrapper, router }
}

function architectureOnlyStateStore(): AnalysisStateStore {
  return {
    load: vi.fn(async (): Promise<AnalysisState> => ({
      project: { ...project, stage: 'ARCHITECTURE', completeness: 12 },
      requirements: [
        {
          id: '50000000-0000-4000-8000-000000000000',
          projectId: project.id,
          type: 'TECHNICAL_CONSTRAINT',
          title: 'Vue 3 + Spring Boot 单体',
          content: 'frontend: Vue 3 + TypeScript',
          status: 'CONFIRMED',
          sourceType: 'USER_ANSWER',
          sourceId: null,
          locked: false,
          metadata: { kind: 'ARCHITECTURE_CANDIDATE' },
          createdAt: '2026-07-17T08:00:00.000Z',
          updatedAt: '2026-07-17T08:00:00.000Z',
        },
      ],
      questions: [],
      answers: [],
      conflicts: [],
      completeness: { total: 12, dimensions: [], pendingCount: 0, hasCoreConflict: false },
    })),
    saveFinal: vi.fn(),
  }
}

function emptyStateStore(): AnalysisStateStore {
  return {
    load: vi.fn(async () => undefined),
    saveFinal: vi.fn(),
  }
}

function pendingStateStore(): AnalysisStateStore {
  return {
    load: vi.fn(async (): Promise<AnalysisState> => ({
      project,
      requirements: [
        {
          id: '20000000-0000-4000-8000-000000000001',
          projectId: project.id,
          type: 'FEATURE',
          title: '在线支付',
          content: '支持订单在线支付',
          status: 'PENDING',
          sourceType: 'AI_RECOMMENDATION',
          sourceId: null,
          locked: false,
          metadata: {},
          createdAt: '2026-07-17T08:00:00.000Z',
          updatedAt: '2026-07-17T08:00:00.000Z',
        },
      ],
      questions: [
        {
          id: '30000000-0000-4000-8000-000000000001',
          projectId: project.id,
          batchId: '30000000-0000-4000-8000-000000000002',
          text: '是否需要支付？',
          reason: '影响订单闭环',
          dimension: 'BUSINESS_RULES',
          targetField: 'payment',
          semanticKey: 'payment',
          inputType: 'CONFIRMATION',
          options: [],
          priority: 5,
          status: 'PENDING',
          createdAt: '2026-07-17T08:00:00.000Z',
          updatedAt: '2026-07-17T08:00:00.000Z',
        },
      ],
      answers: [],
      conflicts: [
        {
          id: '40000000-0000-4000-8000-000000000001',
          projectId: project.id,
          leftRequirementId: null,
          rightRequirementId: null,
          leftContent: '只支持线下支付',
          rightContent: '支持在线支付',
          impact: '支付链路冲突',
          core: false,
          status: 'OPEN',
          resolution: null,
          createdAt: '2026-07-17T08:00:00.000Z',
          updatedAt: '2026-07-17T08:00:00.000Z',
          resolvedAt: null,
        },
      ],
      completeness: { total: 45, dimensions: [], pendingCount: 2, hasCoreConflict: false },
    })),
    saveFinal: vi.fn(),
  }
}

describe('ProjectWorkspace', () => {
  afterEach(() => {
    window.localStorage.removeItem('prompt2prd:layout:projectNavWidth')
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  })

  it('shows a loading state while fetching the project', () => {
    const repository = createRepository({
      getById: vi.fn(
        (_id: string): Promise<Project | undefined> => new Promise(() => {}),
      ),
    })

    const history = createMemoryHistory()
    const router = createAppRouter(history)
    void router.push(`/projects/${project.id}/overview`)

    const wrapper = mount(ProjectWorkspace, {
      props: { repository },
      global: { plugins: [router] },
    })

    expect(wrapper.text()).toContain('正在读取项目')
  })

  it('shows an error message when the project is not found', async () => {
    const repository = createRepository({
      getById: vi.fn(async (_id: string): Promise<Project | undefined> => undefined),
    })

    const { wrapper } = await mountWorkspace(repository)

    expect(wrapper.text()).toContain('无法打开项目')
    expect(wrapper.text()).toContain('没有找到这个本地项目')
  })

  it('shows an error message when the database read fails', async () => {
    const repository = createRepository({
      getById: vi.fn(async (_id: string): Promise<Project | undefined> => {
        throw new Error('DB unavailable')
      }),
    })

    const { wrapper } = await mountWorkspace(repository)

    expect(wrapper.text()).toContain('无法打开项目')
    expect(wrapper.text()).toContain('读取本地项目失败')
  })

  it('renders the workspace layout when the project loads', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository)

    wrapper.get('[data-testid="project-workspace"]')
    expect(wrapper.text()).toContain('宠物寄养平台')
    expect(wrapper.get('[data-testid="header-completeness"]').text()).toContain('45%')
  })

  it('lets users resize the project module column and keeps the width locally', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository)
    const resizer = wrapper.get('[data-testid="project-nav-resizer"]').element

    resizer.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 240 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 300 }))
    window.dispatchEvent(new MouseEvent('mouseup'))
    await flushPromises()

    expect((wrapper.get('.workspace__body').element as HTMLElement).style.gridTemplateColumns).toContain('300px')
    expect(window.localStorage.getItem('prompt2prd:layout:projectNavWidth')).toBe('300')
  })

  it('displays all secondary navigation items', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository)

    const navLabels = ['需求概览', '问题向导', '需求卡片', '架构建议', '流程图', 'PRD']
    for (const label of navLabels) {
      expect(wrapper.text()).toContain(label)
    }
  })

  it('has only one active module at a time', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository)

    const activeButtons = wrapper.findAll('[aria-current="page"]')
    const projectNavActive = activeButtons.filter((b) =>
      PROJECT_MODULES.some((m) => b.attributes('data-module') === m),
    )
    expect(projectNavActive).toHaveLength(1)
  })

  it('switches modules without leaving the workspace', async () => {
    const repository = createRepository()
    const { wrapper, router } = await mountWorkspace(repository)

    for (const mod of PROJECT_MODULES) {
      await wrapper.get(`[data-module="${mod}"]`).trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.name).toBe(`project-${mod}`)
      wrapper.get('[data-testid="project-workspace"]')
      expect(wrapper.text()).toContain('宠物寄养平台')
    }
  })

  it('preserves the project ID when switching modules', async () => {
    const repository = createRepository()
    const { wrapper, router } = await mountWorkspace(repository)

    await wrapper.get('[data-module="questions"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.params.projectId).toBe(project.id)

    await wrapper.get('[data-module="architecture"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.params.projectId).toBe(project.id)
  })

  it('shows a collapsed right panel only when there are pending items', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository, `/projects/${project.id}/overview`, pendingStateStore())

    const panel = wrapper.get('[data-testid="workspace-right-panel"]')
    expect(panel.attributes('aria-expanded')).toBe('false')

    await wrapper.get('[data-testid="panel-toggle"]').trigger('click')
    await flushPromises()

    expect(panel.attributes('aria-expanded')).toBe('true')
    expect(wrapper.text()).toContain('待确认需求：1 条')
  })

  it('toggles the right panel back open after collapsing', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository, `/projects/${project.id}/overview`, pendingStateStore())

    const panel = wrapper.get('[data-testid="workspace-right-panel"]')
    const toggle = wrapper.get('[data-testid="panel-toggle"]')

    await toggle.trigger('click')
    await flushPromises()
    expect(panel.attributes('aria-expanded')).toBe('true')

    await toggle.trigger('click')
    await flushPromises()
    expect(panel.attributes('aria-expanded')).toBe('false')
  })

  it('keeps the right panel collapsed by default on flowchart page', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(
      repository,
      `/projects/${project.id}/flowchart`,
      pendingStateStore(),
    )

    const panel = wrapper.get('[data-testid="workspace-right-panel"]')
    expect(panel.attributes('aria-expanded')).toBe('false')
    expect(panel.classes()).toContain('workspace__panel--collapsed')
  })

  it('keeps the right panel collapsed by default on PRD page', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(
      repository,
      `/projects/${project.id}/prd`,
      pendingStateStore(),
    )

    const panel = wrapper.get('[data-testid="workspace-right-panel"]')
    expect(panel.attributes('aria-expanded')).toBe('false')
    expect(panel.classes()).toContain('workspace__panel--collapsed')
  })

  it('does not render the right panel when there is no auxiliary content', async () => {
    const repository = createRepository()

    for (const mod of PROJECT_MODULES) {
      if (mod === 'flowchart' || mod === 'prd') continue

      const { wrapper } = await mountWorkspace(
        repository,
        `/projects/${project.id}/${mod}`,
        emptyStateStore(),
      )

      expect(wrapper.find('[data-testid="workspace-right-panel"]').exists()).toBe(false)
    }
  })

  it('does not emit generatePrd when prerequisites are missing', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository)

    await wrapper.get('[data-testid="header-generate-prd"]').trigger('click')
    expect(wrapper.emitted('generatePrd')).toBeUndefined()
    expect(wrapper.text()).toContain('还没有已确认需求，先补充并确认需求后才能生成PRD')
  })

  it('prioritizes missing confirmed requirements over the 80 percent PRD threshold', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(
      repository,
      `/projects/${project.id}/requirements`,
      architectureOnlyStateStore(),
      vi.fn(async () => ({ id: '50000000-0000-4000-8000-000000000000' })),
    )

    expect(wrapper.text()).toContain('需求澄清待补充')
    expect(wrapper.text()).toContain('需求确认无需求')
    expect(wrapper.get('[data-testid="header-generate-hint"]').text()).toBe('还没有已确认需求，先补充并确认需求后才能生成PRD')
  })

  it('shows actionable content in the right panel', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository, `/projects/${project.id}/overview`, pendingStateStore())
    await wrapper.get('[data-testid="panel-toggle"]').trigger('click')

    expect(wrapper.text()).toContain('待处理事项')
    expect(wrapper.text()).not.toContain('分析进度、假设、冲突和变更将在此展示')
  })
})
