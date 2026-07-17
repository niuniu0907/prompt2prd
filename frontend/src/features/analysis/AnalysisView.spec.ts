import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'

import type { AnalysisCallbacks, AnalysisRequestBody } from '@/api/analysisApi'
import type { AnalysisState, AnalysisStateStore } from '@/db/repositories/analysisStateRepository'
import type { Project } from '@/features/projects/types'
import type { ClarificationQuestion, RequirementItem } from '@/features/requirements/types'
import AnalysisView from './AnalysisView.vue'

const project: Project = {
  id: '11111111-1111-4111-8111-111111111111',
  name: '宠物寄养平台',
  originalPrompt: '我要做一个宠物寄养平台',
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
  createdAt: '2026-07-17T12:00:00.000Z',
  updatedAt: '2026-07-17T12:00:00.000Z',
}

describe('AnalysisView', () => {
  it('renders streamed progress, requirement cards, and questions before committing final state', async () => {
    let callbacks!: AnalysisCallbacks
    let finish!: (state: AnalysisState) => void
    const completed = new Promise<AnalysisState>(resolve => { finish = resolve })
    const client = {
      analyze: vi.fn((_body: AnalysisRequestBody, next: AnalysisCallbacks) => {
        callbacks = next
        return completed
      }),
      submitAnswers: vi.fn(),
      cancel: vi.fn(),
    }
    const store: AnalysisStateStore = {
      load: vi.fn(async () => undefined),
      saveFinal: vi.fn(async (_id, state) => state),
    }
    const wrapper = mount(AnalysisView, {
      props: { project, client, store, modelSettings: { configured: true } },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    callbacks.onEvent?.(event(1, 'analysis_progress', { progress: 35, message: '识别核心目标' }))
    callbacks.onEvent?.(event(2, 'requirement_patch', {
      path: '/requirements/0', operation: 'add', value: requirement,
    }))
    callbacks.onEvent?.(event(3, 'question_created', { question }))
    await flushPromises()

    expect(wrapper.text()).toContain('35%')
    expect(wrapper.text()).toContain('为宠物主人提供寄养服务')
    expect(wrapper.text()).toContain('寄养方支持哪些角色？')
    expect(store.saveFinal).not.toHaveBeenCalled()

    finish(finalState())
    await flushPromises()
    expect(store.saveFinal).toHaveBeenCalledTimes(1)
  })

  it('restores the last valid state without starting a new initial analysis', async () => {
    const restored = finalState()
    const store: AnalysisStateStore = {
      load: vi.fn(async () => restored),
      saveFinal: vi.fn(),
    }
    const client = { analyze: vi.fn(), submitAnswers: vi.fn(), cancel: vi.fn() }
    const wrapper = mount(AnalysisView, {
      props: { project, client, store, modelSettings: {} },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('为宠物主人提供寄养服务')
    expect(wrapper.text()).toContain('寄养方支持哪些角色？')
    expect(client.analyze).not.toHaveBeenCalled()
  })
})

const requirement: RequirementItem = {
  id: '22222222-2222-4222-8222-222222222222', projectId: project.id,
  type: 'PRODUCT_GOAL', title: '产品目标', content: '为宠物主人提供寄养服务',
  status: 'INFERRED', sourceType: 'AI_INFERENCE', sourceId: null, locked: false,
  metadata: {}, createdAt: project.createdAt, updatedAt: project.updatedAt,
}

const question: ClarificationQuestion = {
  id: '33333333-3333-4333-8333-333333333333', projectId: project.id,
  batchId: '44444444-4444-4444-8444-444444444444', text: '寄养方支持哪些角色？',
  reason: '角色影响权限', dimension: 'ROLES_PERMISSIONS', targetField: 'roles',
  semanticKey: 'roles', inputType: 'TEXT', options: [], priority: 4,
  status: 'PENDING', createdAt: project.createdAt, updatedAt: project.updatedAt,
}

function finalState(): AnalysisState {
  return {
    project: { ...project, completeness: 40 }, requirements: [{ ...requirement }],
    questions: [{ ...question }], answers: [], conflicts: [],
    completeness: { total: 40, dimensions: [], pendingCount: 1, hasCoreConflict: false },
  }
}

function event(eventId: number, type: string, data: Record<string, unknown>) {
  return {
    known: true as const, requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', eventId,
    type: type as never, data, timestamp: '2026-07-17T12:00:00Z',
  }
}
