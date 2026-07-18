import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '@/router'
import type { PrdSection } from './types'

const mocks = vi.hoisted(() => ({
  loadState: vi.fn(),
  initializeSections: vi.fn(),
  updateContent: vi.fn(),
  saveSection: vi.fn(),
  lockSection: vi.fn(),
  saveGeneratedContent: vi.fn(),
  generateAll: vi.fn(),
  generateSection: vi.fn(),
  cancelPrd: vi.fn(),
}))

vi.mock('@/db/repositories/analysisStateRepository', () => ({
  analysisStateRepository: { load: mocks.loadState },
}))
vi.mock('@/db/repositories/prdRepository', () => ({
  prdRepository: {
    initializeSections: mocks.initializeSections,
    updateContent: mocks.updateContent,
    saveSection: mocks.saveSection,
    lockSection: mocks.lockSection,
    saveGeneratedContent: mocks.saveGeneratedContent,
  },
}))
vi.mock('@/api/prdApi', () => ({
  createPrdClient: () => ({
    generateAll: mocks.generateAll,
    generateSection: mocks.generateSection,
    cancel: mocks.cancelPrd,
  }),
}))

import PrdView from './PrdView.vue'
import { useModelConfigStore } from '@/stores/modelConfigStore'

const projectId = '123e4567-e89b-42d3-a456-426614174000'

function state() {
  return {
    project: { id: projectId, name: 'PRD 测试', originalPrompt: '测试', uploadedFileName: null,
      uploadedFileContent: null, supplementalPrompt: null, language: 'zh-CN', stage: 'PRD', status: 'ACTIVE',
      completeness: 85, userRenamed: false, archivedAt: null, deletedAt: null,
      createdAt: '2026-07-17T00:00:00.000Z', updatedAt: '2026-07-17T00:00:00.000Z' },
    requirements: [], questions: [], answers: [], conflicts: [],
    completeness: { total: 85, dimensions: [], pendingCount: 0, hasCoreConflict: false },
  }
}

function sections(): PrdSection[] {
  return [
    { id: 'a'.repeat(32), projectId, sectionKey: 'product-background-goals', title: '产品背景与目标',
      content: '# 说明', order: 1, status: 'COMPLETED', locked: false, errorCode: null,
      createdAt: '2026-07-17T00:00:00.000Z', updatedAt: '2026-07-17T00:00:00.000Z' },
    { id: 'b'.repeat(32), projectId, sectionKey: 'target-users-scenarios', title: '目标用户与使用场景',
      content: '', order: 2, status: 'DRAFT', locked: false, errorCode: null,
      createdAt: '2026-07-17T00:00:00.000Z', updatedAt: '2026-07-17T00:00:00.000Z' },
    { id: 'c'.repeat(32), projectId, sectionKey: 'acceptance-criteria', title: '验收标准',
      content: '', order: 9, status: 'FAILED', locked: false, errorCode: 'TIMEOUT',
      createdAt: '2026-07-17T00:00:00.000Z', updatedAt: '2026-07-17T00:00:00.000Z' },
  ]
}

async function mounted() {
  const router = createAppRouter(createMemoryHistory())
  await router.push(`/projects/${projectId}/prd`)
  await router.isReady()
  const wrapper = mount(PrdView, {
    global: { plugins: [router] },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  const store = useModelConfigStore()
  store.provider = 'CUSTOM'; store.baseUrl = 'http://localhost:11434'
  store.model = 'fixture'; store.setUserApiKey('user-key')
  mocks.loadState.mockResolvedValue(state())
  mocks.initializeSections.mockResolvedValue(sections())
  mocks.updateContent.mockImplementation((_pid: string, _key: string, content: string) =>
    Promise.resolve({ ...sections().find(s => s.sectionKey === _key)!, content }))
  mocks.saveSection.mockImplementation((_pid: string, _key: string, content: string) =>
    Promise.resolve({ ...sections().find(s => s.sectionKey === _key)!, content, status: 'COMPLETED' }))
  mocks.lockSection.mockImplementation((_pid: string, key: string, locked: boolean) =>
    Promise.resolve({ ...sections().find(s => s.sectionKey === key)!, locked }))
  mocks.saveGeneratedContent.mockImplementation((_pid: string, key: string, content: string) =>
    Promise.resolve({ ...sections().find(s => s.sectionKey === key)!, content, status: 'COMPLETED' }))
  mocks.generateAll.mockResolvedValue({ mode: 'FINAL', missingItems: [] })
  mocks.generateSection.mockResolvedValue({ mode: 'FINAL', missingItems: [] })
})

describe('PrdView', () => {
  it('renders layout with sidebar and main area', async () => {
    const wrapper = await mounted()
    expect(wrapper.find('[data-testid="prd-view"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="prd-sidebar"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="prd-main"]').exists()).toBe(true)
  })

  it('shows section list items', async () => {
    const wrapper = await mounted()
    const items = wrapper.findAll('[data-testid^="prd-section-"]')
    expect(items.length).toBeGreaterThanOrEqual(2)
  })

  it('shows generate all button', async () => {
    const wrapper = await mounted()
    const btn = wrapper.find('[data-testid="generate-all-btn"]')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toContain('生成全部 PRD')
  })

  it('does not request PRD generation when model settings are incomplete', async () => {
    const store = useModelConfigStore()
    store.model = ''
    const wrapper = await mounted()

    await wrapper.find('[data-testid="generate-all-btn"]').trigger('click')
    await flushPromises()

    expect(mocks.generateAll).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="prd-error"]').text()).toContain('模型名称未填写')
  })

  it('shows edit and preview mode toggle', async () => {
    const wrapper = await mounted()
    expect(wrapper.find('[data-testid="mode-edit"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mode-preview"]').exists()).toBe(true)
  })

  it('displays section failed info', async () => {
    const wrapper = await mounted()
    // Select the failed section
    const failedItem = wrapper.find('[data-testid="prd-section-acceptance-criteria"]')
    await failedItem.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="section-failed-info"]').exists()).toBe(true)
  })

  it('shows loading state initially', async () => {
    mocks.initializeSections.mockReturnValueOnce(new Promise(() => {}))
    const router = createAppRouter(createMemoryHistory())
    await router.push(`/projects/${projectId}/prd`)
    await router.isReady()
    const wrapper = mount(PrdView, {
      global: { plugins: [router] },
    })
    expect(wrapper.text()).toContain('正在读取')
  })

  it('displays error message on load failure', async () => {
    mocks.loadState.mockRejectedValueOnce(new Error('数据库读取失败'))
    const wrapper = await mounted()
    expect(wrapper.find('[data-testid="prd-error"]').exists()).toBe(true)
  })
})
