import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'

import type {
  ProjectListFilter,
  ProjectHomeRepository,
  ProjectSummary,
} from '@/db/repositories/projectRepository'
import ProjectHomeView from './ProjectHomeView.vue'
import { createAppRouter } from '@/router'

function summary(
  id: string,
  name: string,
  status: ProjectListFilter,
  pendingCount = 0,
): ProjectSummary {
  return {
    project: {
      id,
      name,
      originalPrompt: `${name}的原始需求`,
      uploadedFileName: null,
      uploadedFileContent: null,
      supplementalPrompt: null,
      language: 'zh-CN',
      stage: 'CLARIFYING',
      status,
      completeness: 42,
      userRenamed: false,
      archivedAt: status === 'ARCHIVED' ? '2026-07-17T08:20:00.000Z' : null,
      deletedAt: status === 'DELETED' ? '2026-07-17T08:20:00.000Z' : null,
      createdAt: '2026-07-17T08:00:00.000Z',
      updatedAt: '2026-07-17T08:20:00.000Z',
    },
    pendingCount,
  }
}

function createRepository(data: Partial<Record<ProjectListFilter, ProjectSummary[]>> = {}) {
  return {
    listSummaries: vi.fn(async (filter: ProjectListFilter = 'ACTIVE') => data[filter] ?? []),
    rename: vi.fn(async () => undefined),
    copy: vi.fn(async () => undefined),
    archive: vi.fn(async () => undefined),
    moveToTrash: vi.fn(async () => undefined),
    restore: vi.fn(async () => undefined),
    permanentlyDelete: vi.fn(async () => undefined),
  }
}

function mountView(repository: ReturnType<typeof createRepository>) {
  const router = createAppRouter(createMemoryHistory())
  return mount(ProjectHomeView, {
    props: { repository },
    global: { plugins: [router] },
  })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ProjectHomeView', () => {
  it('routes the empty-state creation entry to the new-project page', async () => {
    const repository = createRepository()
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()
    const wrapper = mount(ProjectHomeView, {
      props: { repository },
      global: { plugins: [router] },
    })
    await flushPromises()

    const createButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('创建第一个项目'))
    expect(createButton).toBeDefined()
    await createButton!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('new-project')
  })

  it('shows view-specific empty states without sample content', async () => {
    const repository = createRepository()
    const wrapper = mountView(repository)
    await flushPromises()

    expect(wrapper.text()).toContain('还没有项目')
    expect(wrapper.text()).not.toContain('宠物寄养')
    expect(wrapper.get('[data-testid="new-project-button"]').text()).toContain('新建项目')

    await wrapper.get('[data-navigation="ARCHIVED"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有归档项目')
    expect(wrapper.find('[data-testid="new-project-button"]').exists()).toBe(false)

    await wrapper.get('[data-navigation="DELETED"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('回收站为空')
    expect(repository.listSummaries).toHaveBeenCalledWith('DELETED')
  })

  it('filters real summaries and renders project-card metadata', async () => {
    const active = summary('76deeeab-70cf-41af-92a2-24ff466ca1b1', '活动项目', 'ACTIVE', 3)
    const archived = summary('ce617340-3b0f-43e6-8e2b-524871c828ab', '归档项目', 'ARCHIVED')
    const deleted = summary('df423353-4358-48c6-ae0a-82104962e51d', '删除项目', 'DELETED')
    const wrapper = mountView(
      createRepository({ ACTIVE: [active], ARCHIVED: [archived], DELETED: [deleted] }),
    )
    await flushPromises()

    expect(wrapper.text()).toContain('活动项目')
    expect(wrapper.text()).toContain('42%')
    expect(wrapper.text()).toContain('3 项待确认')

    await wrapper.get('[data-navigation="ARCHIVED"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('归档项目')
    expect(wrapper.text()).not.toContain('活动项目')

    await wrapper.get('[data-navigation="DELETED"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('删除项目')
    expect(wrapper.text()).not.toContain('归档项目')
  })

  it('opens an active project card in the overview workspace', async () => {
    const active = summary('76deeeab-70cf-41af-92a2-24ff466ca1b1', '可进入项目', 'ACTIVE')
    const repository = createRepository({ ACTIVE: [active] })
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()
    const wrapper = mount(ProjectHomeView, {
      props: { repository },
      global: { plugins: [router] },
    })
    await flushPromises()

    await wrapper.get('h3').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('project-overview')
    expect(router.currentRoute.value.params.projectId).toBe(active.project.id)
  })

  it('routes card operations through the Repository and reloads the current view', async () => {
    const active = summary('76deeeab-70cf-41af-92a2-24ff466ca1b1', '可操作项目', 'ACTIVE')
    const deleted = summary('df423353-4358-48c6-ae0a-82104962e51d', '待删除项目', 'DELETED')
    const repository = createRepository({ ACTIVE: [active], DELETED: [deleted] })
    const wrapper = mountView(repository)
    await flushPromises()

    await wrapper.get('[data-action="rename"]').trigger('click')
    await wrapper.get('input').setValue('已重命名')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(repository.rename).toHaveBeenCalledWith(active.project.id, '已重命名')

    await wrapper.get('[data-action="copy"]').trigger('click')
    await flushPromises()
    expect(repository.copy).toHaveBeenCalledWith(active.project.id)

    await wrapper.get('[data-action="archive"]').trigger('click')
    await flushPromises()
    expect(repository.archive).toHaveBeenCalledWith(active.project.id)

    await wrapper.get('[data-action="trash"]').trigger('click')
    await flushPromises()
    expect(repository.moveToTrash).toHaveBeenCalledWith(active.project.id)

    await wrapper.get('[data-navigation="DELETED"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-action="restore"]').trigger('click')
    await flushPromises()
    expect(repository.restore).toHaveBeenCalledWith(deleted.project.id)

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await wrapper.get('[data-action="permanent-delete"]').trigger('click')
    await flushPromises()
    expect(repository.permanentlyDelete).toHaveBeenCalledWith(deleted.project.id)
  })

  it('shows an explicit load failure instead of an empty state', async () => {
    const repository = createRepository()
    repository.listSummaries.mockRejectedValueOnce(new Error('IndexedDB unavailable'))
    const wrapper = mountView(repository)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('读取本地项目失败')
    expect(wrapper.text()).not.toContain('还没有项目')
  })

  it('keeps the current list visible when a project operation fails', async () => {
    const active = summary('76deeeab-70cf-41af-92a2-24ff466ca1b1', '保留显示的项目', 'ACTIVE')
    const repository = createRepository({ ACTIVE: [active] })
    repository.archive.mockRejectedValueOnce(new Error('write failed'))
    const wrapper = mountView(repository)
    await flushPromises()

    await wrapper.get('[data-action="archive"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('本地项目操作失败')
    expect(wrapper.text()).toContain('保留显示的项目')
  })
})
