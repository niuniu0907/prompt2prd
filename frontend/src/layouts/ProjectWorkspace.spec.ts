import 'fake-indexeddb/auto'

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import type { ProjectLookupRepository } from '@/db/repositories/projectRepository'
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
) {
  const history = createMemoryHistory()
  const router = createAppRouter(history)

  await router.push(initialRoute)
  await router.isReady()

  const wrapper = mount(ProjectWorkspace, {
    props: { repository },
    global: {
      plugins: [router],
    },
  })
  await flushPromises()

  return { wrapper, router }
}

describe('ProjectWorkspace', () => {
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
    expect(wrapper.get('[data-testid="header-completeness"]').text()).toBe('45%')
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

  it('has a collapsible right panel', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository)

    const panel = wrapper.get('[data-testid="workspace-right-panel"]')
    expect(panel.attributes('aria-expanded')).toBe('true')

    await wrapper.get('[data-testid="panel-toggle"]').trigger('click')
    await flushPromises()

    expect(panel.attributes('aria-expanded')).toBe('false')
    expect(panel.classes()).toContain('workspace__panel--collapsed')
  })

  it('toggles the right panel back open after collapsing', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository)

    const panel = wrapper.get('[data-testid="workspace-right-panel"]')
    const toggle = wrapper.get('[data-testid="panel-toggle"]')

    await toggle.trigger('click')
    await flushPromises()
    expect(panel.attributes('aria-expanded')).toBe('false')

    await toggle.trigger('click')
    await flushPromises()
    expect(panel.attributes('aria-expanded')).toBe('true')
  })

  it('defaults the right panel to collapsed on flowchart page', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(
      repository,
      `/projects/${project.id}/flowchart`,
    )

    const panel = wrapper.get('[data-testid="workspace-right-panel"]')
    expect(panel.attributes('aria-expanded')).toBe('false')
    expect(panel.classes()).toContain('workspace__panel--collapsed')
  })

  it('defaults the right panel to collapsed on PRD page', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(
      repository,
      `/projects/${project.id}/prd`,
    )

    const panel = wrapper.get('[data-testid="workspace-right-panel"]')
    expect(panel.attributes('aria-expanded')).toBe('false')
    expect(panel.classes()).toContain('workspace__panel--collapsed')
  })

  it('defaults the right panel to expanded on non-flowchart/PRD pages', async () => {
    const repository = createRepository()

    for (const mod of PROJECT_MODULES) {
      if (mod === 'flowchart' || mod === 'prd') continue

      const { wrapper } = await mountWorkspace(
        repository,
        `/projects/${project.id}/${mod}`,
      )

      const panel = wrapper.get('[data-testid="workspace-right-panel"]')
      expect(panel.attributes('aria-expanded')).toBe('true')
    }
  })

  it('emits generatePrd when the header button is clicked', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository)

    await wrapper.get('[data-testid="header-generate-prd"]').trigger('click')
    expect(wrapper.emitted('generatePrd')).toHaveLength(1)
  })

  it('shows a placeholder message in the right panel', async () => {
    const repository = createRepository()
    const { wrapper } = await mountWorkspace(repository)

    expect(wrapper.text()).toContain('辅助面板')
  })
})
