import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routeLocationKey } from 'vue-router'
import type { FlowchartGenerationResult, FlowchartRecord } from './types'

const mocks = vi.hoisted(() => ({
  loadState: vi.fn(),
  listByProject: vi.fn(),
  getByKey: vi.fn(),
  saveGenerated: vi.fn(),
  replaceAfterConfirmation: vi.fn(),
  generateFlowcharts: vi.fn(),
  writeText: vi.fn(),
}))

vi.mock('mermaid', () => ({ default: {
  initialize: vi.fn(),
  parse: vi.fn(async () => ({ diagramType: 'flowchart-v2' })),
  render: vi.fn(async () => ({ svg: '<svg data-testid="rendered-svg"></svg>' })),
} }))
vi.mock('@/db/repositories/analysisStateRepository', () => ({
  analysisStateRepository: { load: mocks.loadState },
}))
vi.mock('@/db/repositories/flowchartRepository', () => ({
  flowchartRepository: {
    listByProject: mocks.listByProject, getByKey: mocks.getByKey,
    saveGenerated: mocks.saveGenerated, replaceAfterConfirmation: mocks.replaceAfterConfirmation,
  },
}))
vi.mock('@/api/flowchartApi', () => ({ generateFlowcharts: mocks.generateFlowcharts }))

import FlowchartView from './FlowchartView.vue'
import { useModelConfigStore } from '@/stores/modelConfigStore'

const projectId = '123e4567-e89b-42d3-a456-426614174000'
const sourceRequirementId = '123e4567-e89b-42d3-a456-426614174001'
const record: FlowchartRecord = {
  id: '123e4567-e89b-42d3-a456-426614174010', projectId, key: 'main', type: 'MAIN',
  title: '主流程', mermaid: 'flowchart TD\nA-->B', status: 'VALID', sourceRequirementIds: [],
  createdAt: '2026-07-17T00:00:00.000Z', updatedAt: '2026-07-17T00:00:00.000Z',
}

function state() {
  return {
    project: { id: projectId, name: '流程图测试', originalPrompt: '测试流程图页面', uploadedFileName: null,
      uploadedFileContent: null, supplementalPrompt: null, language: 'zh-CN', stage: 'FLOWCHART', status: 'ACTIVE',
      completeness: 80, userRenamed: false, archivedAt: null, deletedAt: null,
      createdAt: '2026-07-17T00:00:00.000Z', updatedAt: '2026-07-17T00:00:00.000Z' },
    requirements: [], questions: [], answers: [], conflicts: [],
    completeness: { total: 80, dimensions: [], pendingCount: 0, hasCoreConflict: false },
  }
}

function result(): FlowchartGenerationResult {
  return {
    mainFlow: { key: 'main', type: 'MAIN', title: '主流程', mermaid: 'flowchart TD\nA-->C',
      sourceRequirementIds: [], status: 'GENERATED', errorCode: null },
    exceptionFlows: [{ key: 'exception-a', type: 'EXCEPTION', title: '支付失败', mermaid: '',
      sourceRequirementIds: [sourceRequirementId], status: 'FAILED', errorCode: 'MODEL_DIAGRAM_FAILED' }],
    missingInformation: [],
  }
}

async function mounted() {
  const wrapper = mount(FlowchartView, {
    global: { provide: { [routeLocationKey as symbol]: { params: { projectId } } } },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  const store = useModelConfigStore()
  store.provider = 'CUSTOM'; store.baseUrl = 'http://localhost:11434'; store.model = 'fixture'; store.setUserApiKey('user-key')
  mocks.loadState.mockResolvedValue(state())
  mocks.listByProject.mockResolvedValue([])
  mocks.getByKey.mockResolvedValue(undefined)
  mocks.saveGenerated.mockResolvedValue({ saved: [record], failures: [] })
  mocks.replaceAfterConfirmation.mockResolvedValue(record)
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: mocks.writeText } })
})

describe('FlowchartView', () => {
  it('keeps valid diagrams when a sibling generation fails', async () => {
    mocks.generateFlowcharts.mockResolvedValue(result())
    mocks.listByProject.mockResolvedValueOnce([]).mockResolvedValueOnce([record])
    const wrapper = await mounted()
    await wrapper.find('.button-primary').trigger('click')
    await flushPromises()

    expect(mocks.saveGenerated).toHaveBeenCalledTimes(1)
    expect(mocks.saveGenerated.mock.calls[0]?.[1]?.[0]).toMatchObject({ key: 'main' })
    expect(wrapper.text()).toContain('MODEL_DIAGRAM_FAILED')
    expect(wrapper.find('[data-testid="flowchart-main"]').exists()).toBe(true)
  })

  it('copies the exact Mermaid source', async () => {
    mocks.listByProject.mockResolvedValue([record])
    const wrapper = await mounted()
    await wrapper.find('.actions button').trigger('click')
    expect(mocks.writeText).toHaveBeenCalledWith(record.mermaid)
    expect(wrapper.text()).toContain('已复制')
  })

  it('keeps the old diagram when the user rejects a validated replacement', async () => {
    mocks.listByProject.mockResolvedValue([record])
    mocks.getByKey.mockResolvedValue(record)
    mocks.generateFlowcharts.mockResolvedValue(result())
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = await mounted()
    await wrapper.findAll('.actions button')[1]!.trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalled()
    expect(mocks.replaceAfterConfirmation).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('A-->B')
    confirm.mockRestore()
  })

  it('replaces only after validation and explicit confirmation', async () => {
    mocks.listByProject.mockResolvedValue([record])
    mocks.getByKey.mockResolvedValue(record)
    mocks.generateFlowcharts.mockResolvedValue(result())
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = await mounted()
    await wrapper.findAll('.actions button')[1]!.trigger('click')
    await flushPromises()

    expect(mocks.replaceAfterConfirmation).toHaveBeenCalledWith(projectId, expect.objectContaining({ mermaid: 'flowchart TD\nA-->C' }))
    confirm.mockRestore()
  })
})
