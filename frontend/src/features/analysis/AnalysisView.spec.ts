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
      props: { project, client, store, architectureSelected: vi.fn(async () => null), modelSettings: { keySource: 'SYSTEM', model: 'test-model' } },
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
      props: { project, client, store, architectureSelected: vi.fn(async () => null), modelSettings: {} },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('为宠物主人提供寄养服务')
    expect(wrapper.text()).toContain('寄养方支持哪些角色？')
    expect(client.analyze).not.toHaveBeenCalled()
  })

  it('keeps architecture candidates out of the requirements overview', async () => {
    const restored: AnalysisState = {
      ...finalState(),
      requirements: [
        { ...requirement, status: 'CONFIRMED' },
        architectureCandidate('55555555-5555-4555-8555-555555555555', 'Vue 3 + Spring Boot 单体', 'CONFIRMED'),
        architectureCandidate('66666666-6666-4666-8666-666666666666', 'Spring Boot 服务端渲染', 'PENDING'),
        architectureCandidate('77777777-7777-4777-8777-777777777777', 'Vue 3 + NestJS 全栈 TypeScript', 'PENDING'),
      ],
      questions: [],
      completeness: { total: 12, dimensions: [], pendingCount: 0, hasCoreConflict: false },
    }
    const store: AnalysisStateStore = {
      load: vi.fn(async () => restored),
      saveFinal: vi.fn(),
    }
    const client = { analyze: vi.fn(), submitAnswers: vi.fn(), cancel: vi.fn() }
    const wrapper = mount(AnalysisView, {
      props: { project, client, store, architectureSelected: vi.fn(async () => null), modelSettings: {} },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('已确认需求1')
    expect(wrapper.text()).toContain('待确认内容0')
    expect(wrapper.text()).not.toContain('Vue 3 + Spring Boot 单体')
    expect(wrapper.text()).not.toContain('ai: Spring AI')
    expect(client.analyze).not.toHaveBeenCalled()
  })

  it('does not send users back to architecture recommendations after a scheme is confirmed', async () => {
    const restored: AnalysisState = {
      ...finalState(),
      project: { ...project, stage: 'ARCHITECTURE', completeness: 82 },
      requirements: [{ ...requirement, status: 'CONFIRMED' }],
      questions: [],
      completeness: { total: 82, dimensions: [], pendingCount: 0, hasCoreConflict: false },
    }
    const store: AnalysisStateStore = {
      load: vi.fn(async () => restored),
      saveFinal: vi.fn(),
    }
    const client = { analyze: vi.fn(), submitAnswers: vi.fn(), cancel: vi.fn() }
    const wrapper = mount(AnalysisView, {
      props: {
        project,
        client,
        store,
        architectureSelected: vi.fn(async () => architectureCandidate('55555555-5555-4555-8555-555555555555', 'Vue 3 + Spring Boot 单体', 'CONFIRMED')),
        modelSettings: {},
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('生成业务流程图')
    expect(wrapper.text()).not.toContain('进入架构建议')
    expect(client.analyze).not.toHaveBeenCalled()
  })

  it('explains that clarification questions are unavailable when initial AI analysis fails', async () => {
    const client = {
      analyze: vi.fn(async () => {
        throw new Error('模型 API Key 验证失败，请检查 Key 是否正确或是否已过期。')
      }),
      submitAnswers: vi.fn(),
      cancel: vi.fn(),
    }
    const store: AnalysisStateStore = {
      load: vi.fn(async () => undefined),
      saveFinal: vi.fn(),
    }
    const wrapper = mount(AnalysisView, {
      props: { project, client, store, architectureSelected: vi.fn(async () => null), modelSettings: { keySource: 'USER', model: 'test-model', apiKey: 'bad-key' } },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('AI 还没有生成澄清问题')
    expect(wrapper.text()).toContain('模型 API Key 验证失败')
    expect(wrapper.text()).toContain('前往模型设置')
    expect(store.saveFinal).not.toHaveBeenCalled()
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

function architectureCandidate(id: string, title: string, status: RequirementItem['status']): RequirementItem {
  return {
    id,
    projectId: project.id,
    type: 'TECHNICAL_CONSTRAINT',
    title,
    content: 'ai: Spring AI\ndeployment: 单 JAR / Docker\nfrontend: Vue 3 + TypeScript',
    status,
    sourceType: status === 'CONFIRMED' ? 'USER_ANSWER' : 'AI_RECOMMENDATION',
    sourceId: null,
    locked: false,
    metadata: {
      kind: 'ARCHITECTURE_CANDIDATE',
      candidate: {
        id,
        name: title,
        stack: {
          ai: 'Spring AI',
          deployment: '单 JAR / Docker',
          frontend: 'Vue 3 + TypeScript',
        },
      },
    },
    createdAt: project.createdAt,
    updatedAt: project.updatedAt,
  }
}

function event(eventId: number, type: string, data: Record<string, unknown>) {
  return {
    known: true as const, requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', eventId,
    type: type as never, data, timestamp: '2026-07-17T12:00:00Z',
  }
}
