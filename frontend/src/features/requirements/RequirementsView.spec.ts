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
  requirementInteractionRepository: {
    setLocked: vi.fn(),
    decideAssumption: vi.fn(),
    resolveConflict: vi.fn(),
    confirmRequirement: vi.fn(),
    rejectRequirement: vi.fn(),
  },
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

  it('guides users back to requirement input when no formal requirements exist', async () => {
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
    expect(wrapper.text()).toContain('回到需求输入')
    expect(wrapper.text()).not.toContain('Vue 3 + Spring Boot 单体')

    await wrapper.get('.empty-guide button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('project-overview')
  })

  it('displays requirements in a single-column grouped list', async () => {
    loadState.mockResolvedValue({
      project: { ...project, stage: 'CLARIFYING', completeness: 88 },
      requirements: [feature(project.id), businessRule(project.id)],
      questions: [],
      answers: [],
      conflicts: [],
      completeness: { total: 88, dimensions: [], pendingCount: 0, hasCoreConflict: false },
    } satisfies AnalysisState)
    const router = createAppRouter(createMemoryHistory())
    await router.push(`/projects/${project.id}/requirements`)
    await router.isReady()

    const wrapper = mount(RequirementsView, { global: { plugins: [router] } })
    await flushPromises()

    // Groups exist
    expect(wrapper.text()).toContain('功能需求')
    expect(wrapper.text()).toContain('业务规则')
    // Requirements are listed
    expect(wrapper.text()).toContain('订单支付')
    expect(wrapper.text()).toContain('退款规则')
  })

  it('filters requirements by status when filter pill is clicked', async () => {
    loadState.mockResolvedValue({
      project: { ...project, stage: 'CLARIFYING', completeness: 88 },
      requirements: [
        feature(project.id),
        { ...businessRule(project.id), status: 'CONFIRMED' as const },
      ],
      questions: [],
      answers: [],
      conflicts: [],
      completeness: { total: 88, dimensions: [], pendingCount: 0, hasCoreConflict: false },
    } satisfies AnalysisState)
    const router = createAppRouter(createMemoryHistory())
    await router.push(`/projects/${project.id}/requirements`)
    await router.isReady()

    const wrapper = mount(RequirementsView, { global: { plugins: [router] } })
    await flushPromises()

    // Both show initially
    expect(wrapper.text()).toContain('订单支付')
    expect(wrapper.text()).toContain('退款规则')

    // Click "已确认" filter
    const pills = wrapper.findAll('.status-filter__pill')
    const confirmedPill = pills.find(p => p.text().includes('已确认'))
    expect(confirmedPill).toBeTruthy()
    await confirmedPill!.trigger('click')
    await flushPromises()

    // Only confirmed shows
    expect(wrapper.text()).toContain('退款规则')
    expect(wrapper.text()).not.toContain('订单支付')
  })

  it('opens detail drawer on view button click', async () => {
    loadState.mockResolvedValue({
      project: { ...project, stage: 'CLARIFYING', completeness: 88 },
      requirements: [feature(project.id)],
      questions: [],
      answers: [],
      conflicts: [],
      completeness: { total: 88, dimensions: [], pendingCount: 0, hasCoreConflict: false },
    } satisfies AnalysisState)
    const router = createAppRouter(createMemoryHistory())
    await router.push(`/projects/${project.id}/requirements`)
    await router.isReady()

    const wrapper = mount(RequirementsView, { global: { plugins: [router] } })
    await flushPromises()

    // Click on requirement row (entire row now clickable)
    const row = wrapper.find('.requirement-list-item')
    expect(row.exists()).toBe(true)
    await row.trigger('click')
    await flushPromises()

    // Drawer should be open
    const drawer = wrapper.findComponent({ name: 'RequirementDetailDrawer' })
    expect(drawer.props('visible')).toBe(true)
  })

  it('shows conflict warning bar when open conflicts exist', async () => {
    loadState.mockResolvedValue({
      project: { ...project, stage: 'CLARIFYING', completeness: 88 },
      requirements: [feature(project.id)],
      questions: [],
      answers: [],
      conflicts: [{
        id: 'cf-1', projectId: project.id,
        leftRequirementId: feature(project.id).id,
        rightRequirementId: null,
        leftContent: '人工审核', rightContent: '自动审核',
        impact: '影响审核流程', core: true, status: 'OPEN', resolution: null,
        createdAt: project.createdAt, updatedAt: project.updatedAt, resolvedAt: null,
      }],
      completeness: { total: 88, dimensions: [], pendingCount: 0, hasCoreConflict: true },
    } satisfies AnalysisState)
    const router = createAppRouter(createMemoryHistory())
    await router.push(`/projects/${project.id}/requirements`)
    await router.isReady()

    const wrapper = mount(RequirementsView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('存在未解决的核心冲突')
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

function feature(projectId: string): RequirementItem {
  return {
    id: '60000000-0000-4000-8000-000000000000',
    projectId,
    type: 'FEATURE',
    title: '订单支付',
    content: '用户提交订单后完成在线支付',
    status: 'PENDING',
    sourceType: 'USER_ANSWER',
    sourceId: null,
    locked: false,
    metadata: {},
    createdAt: project.createdAt,
    updatedAt: project.updatedAt,
  }
}

function businessRule(projectId: string): RequirementItem {
  return {
    id: '70000000-0000-4000-8000-000000000000',
    projectId,
    type: 'BUSINESS_RULE',
    title: '退款规则',
    content: '用户可在7天内申请退款',
    status: 'PENDING',
    sourceType: 'AI_INFERENCE',
    sourceId: null,
    locked: false,
    metadata: {},
    createdAt: project.createdAt,
    updatedAt: project.updatedAt,
  }
}
