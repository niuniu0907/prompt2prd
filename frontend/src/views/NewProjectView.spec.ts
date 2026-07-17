import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import type { ProjectCreateRepository } from '@/db/repositories/projectRepository'
import type { UploadPrivacySetting } from '@/features/projects/requirementFileUpload'
import type { Project } from '@/features/projects/types'
import { createAppRouter } from '@/router'
import NewProjectView from './NewProjectView.vue'

const project: Project = {
  id: '76deeeab-70cf-41af-92a2-24ff466ca1b1',
  name: '本地需求分析工具',
  originalPrompt: '建立一个本地需求分析工具',
  uploadedFileName: null,
  uploadedFileContent: null,
  supplementalPrompt: null,
  language: 'zh-CN',
  stage: 'CLARIFYING',
  status: 'ACTIVE',
  completeness: 0,
  userRenamed: false,
  archivedAt: null,
  deletedAt: null,
  createdAt: '2026-07-17T08:00:00.000Z',
  updatedAt: '2026-07-17T08:00:00.000Z',
}

function createRepository(): ProjectCreateRepository {
  return {
    create: vi.fn(async () => project),
    applySuggestedName: vi.fn(async () => project),
  }
}

const acceptedPrivacySetting: UploadPrivacySetting = {
  isAccepted: vi.fn(async () => true),
  accept: vi.fn(async () => undefined),
}

async function mountView(repository: ProjectCreateRepository) {
  const router = createAppRouter(createMemoryHistory())
  await router.push('/projects/new')
  await router.isReady()
  const wrapper = mount(NewProjectView, {
    props: { repository, privacySetting: acceptedPrivacySetting },
    global: { plugins: [router] },
  })
  return { wrapper, router }
}

async function selectFile(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], file: File) {
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
  await flushPromises()
}

describe('NewProjectView', () => {
  it('persists valid input before entering the project route', async () => {
    const repository = createRepository()
    const { wrapper, router } = await mountView(repository)

    await wrapper.get('textarea').setValue('建立一个本地需求分析工具')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(repository.create).toHaveBeenCalledWith(
      expect.objectContaining({
        name: '建立一个本地需求分析工具',
        originalPrompt: '建立一个本地需求分析工具',
      }),
    )
    expect(router.currentRoute.value.name).toBe('project-start')
    expect(router.currentRoute.value.params.projectId).toBe(project.id)
  })

  it('keeps the form visible and does not navigate when persistence fails', async () => {
    const repository = createRepository()
    vi.mocked(repository.create).mockRejectedValueOnce(new Error('IndexedDB unavailable'))
    const { wrapper, router } = await mountView(repository)

    await wrapper.get('textarea').setValue('建立一个会失败的本地项目')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('项目创建失败')
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(router.currentRoute.value.name).toBe('new-project')
  })

  it('creates a project from the full confirmed file when supplemental text is blank', async () => {
    const repository = createRepository()
    const { wrapper, router } = await mountView(repository)
    const source = '# 本地优先需求\n\n使用 Markdown 文件创建项目。'

    await selectFile(wrapper, new File([source], 'requirements.md'))
    await wrapper.get('[data-testid="confirm-file"]').trigger('click')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(repository.create).toHaveBeenCalledWith(
      expect.objectContaining({
        uploadedFileName: 'requirements.md',
        uploadedFileContent: source,
        originalPrompt: '',
        supplementalPrompt: null,
      }),
    )
    expect(router.currentRoute.value.name).toBe('project-start')
  })

  it('restores the text minimum after a confirmed file is cleared', async () => {
    const repository = createRepository()
    const { wrapper } = await mountView(repository)

    await selectFile(wrapper, new File(['有效文件需求'], 'requirements.txt'))
    await wrapper.get('[data-testid="confirm-file"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="confirmed-file"]').exists()).toBe(true)

    await wrapper.get('[data-testid="clear-file"]').trigger('click')
    await wrapper.get('textarea:not([readonly])').setValue('四个字')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toContain('至少输入 5 个字符')
    expect(repository.create).not.toHaveBeenCalled()
  })
})
