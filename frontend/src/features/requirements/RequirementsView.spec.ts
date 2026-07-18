import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppRouter } from '@/router'
import type { AnalysisState } from '@/db/repositories/analysisStateRepository'
import type { Project } from '@/features/projects/types'
import type { RequirementItem } from './types'
import RequirementsView from './RequirementsView.vue'

const mocks = vi.hoisted(() => ({
  loadState: vi.fn(),
  listVersions: vi.fn(),
}))
const { loadState, listVersions } = mocks

vi.mock('@/db/repositories/analysisStateRepository', () => ({
  analysisStateRepository: { load: mocks.loadState },
}))
vi.mock('@/db/repositories/versionRepository', () => ({
  versionRepository: { listByProject: mocks.listVersions, restore: vi.fn() },
}))
vi.mock('@/db/repositories/requirementRepository', () => ({
  requirementRepository: { commitManualEdit: vi.fn() },
}))
vi.mock('@/db/repositories/requirementInteractionRepository', () => ({
  requirementInteractionRepository: { setLocked: vi.fn(), decideAssumption: vi.fn(), resolveConflict: vi.fn() },
}))

const project: Project = {
  id: '11111111-1111-4111-8111-111111111111',
  name: '美妆产品网站',
  originalPrompt: '我要做一个美妆产品网站',
  uploadedFileName: null,
  uploadedFileContent: null,
  supplementalPrompt: null,
  language: 'zh-CN',
  stage: 'ARCHITECTURE',
  status: 'ACTIVE',
  completeness: 12,
  userRenamed: false,
  archivedAt: null,
  deletedAt: null,
  createdAt: '2026-07-18T05:00:00.000Z',
  updatedAt: '2026-07-18T05:00:00.000Z',
}

describe('RequirementsView', () => {
  beforeEach(() => {
    loadState.mockReset()
    listVersions.mockReset()
    listVersions.mockResolvedValue([])
  })

  it('guides users back to the overview when no formal requirements exist', async () => {
    loadState.mockResolvedValue({
      project,
      requirements: [architectureCandidate(project.id)],
      questions: [],
      answers: [],
      conflicts: [],
      completeness: { total: 12, dimensions: [], pendingCount: 0, hasCoreConflict: false },
    } satisfies AnalysisState)
    const router = createAppRouter(createMemoryHistory())
    await router.push(`/projects/${project.id}/requirements`)
    await router.isReady()

    const wrapper = mount(RequirementsView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('还没有可确认的产品需求')
    expect(wrapper.text()).toContain('回到需求概览')
    expect(wrapper.text()).not.toContain('Vue 3 + Spring Boot 单体')

    await wrapper.get('.empty-guide button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('project-overview')
  })
})

function architectureCandidate(projectId: string): RequirementItem {
  return {
    id: '50000000-0000-4000-8000-000000000000',
    projectId,
    type: 'TECHNICAL_CONSTRAINT',
    title: 'Vue 3 + Spring Boot 单体',
    content: 'frontend: Vue 3 + TypeScript',
    status: 'CONFIRMED',
    sourceType: 'USER_ANSWER',
    sourceId: null,
    locked: false,
    metadata: {
      kind: 'ARCHITECTURE_CANDIDATE',
      candidate: { id: '50000000-0000-4000-8000-000000000000', name: 'Vue 3 + Spring Boot 单体', stack: { frontend: 'Vue 3 + TypeScript' } },
    },
    createdAt: project.createdAt,
    updatedAt: project.updatedAt,
  }
}
