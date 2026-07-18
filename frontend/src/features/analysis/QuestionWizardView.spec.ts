import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import type { AnalysisState, AnalysisStateStore } from '@/db/repositories/analysisStateRepository'
import type { SubmitBatchResult } from '@/db/repositories/clarificationRepository'
import type { AnalysisAnswersRequestBody } from '@/api/analysisApi'
import QuestionWizardView from './QuestionWizardView.vue'

describe('QuestionWizardView', () => {
  it('persists answers before requesting and commits only the completed next state', async () => {
    const initial = state()
    const completed = { ...initial, questions: initial.questions.map(question => ({ ...question, status: 'ANSWERED' as const })) }
    const stateStore: AnalysisStateStore = { load: vi.fn(async () => initial), saveFinal: vi.fn(async () => completed) }
    const clarification = {
      submitBatch: vi.fn(async (_projectId, drafts): Promise<SubmitBatchResult> => ({
        answers: [{ id: '90000000-0000-4000-8000-000000000001', projectId: initial.project.id, questionId: drafts[0].questionId, selectedOptionIds: drafts[0].selectedOptionIds, customAnswer: drafts[0].customAnswer, note: drafts[0].note, skipped: drafts[0].skipped, createdAt: '2026-07-17T12:01:00.000Z', updatedAt: '2026-07-17T12:01:00.000Z' }],
        questions: completed.questions,
      })),
    }
    const client = { submitAnswers: vi.fn(async (_body: AnalysisAnswersRequestBody) => completed), cancel: vi.fn() }
    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore,
        clarification,
        architectureSelected: vi.fn(async () => null),
        client,
        modelSettings: {},
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()
    await wrapper.get('[data-testid="option-refund-window-:0"]').setValue(true)
    await wrapper.get('[data-testid="submit-batch"]').trigger('click')
    await flushPromises()

    expect(clarification.submitBatch).toHaveBeenCalledTimes(1)
    expect(client.submitAnswers).toHaveBeenCalledTimes(1)
    const body = client.submitAnswers.mock.calls[0]![0]
    expect(body.answers[0]).toMatchObject({ question: '退款期限是多少？', answer: '采用常见默认规则' })
    expect(stateStore.saveFinal).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('AI 已根据回答更新需求，当前没有新的追问。')
    expect(wrapper.text()).toContain('这一轮问题已回答完')
  })

  it('makes confirmed architecture explicit while requirement questions remain', async () => {
    const initial = state()
    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore: { load: vi.fn(async () => initial), saveFinal: vi.fn() },
        architectureSelected: vi.fn(async () => ({ id: '50000000-0000-4000-8000-000000000000' })),
        clarification: { submitBatch: vi.fn() },
        client: { submitAnswers: vi.fn(), cancel: vi.fn() },
        modelSettings: {},
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('AI 已读取你的初始需求')
    expect(wrapper.text()).toContain('最终 PRD 至少覆盖')
    expect(wrapper.text()).toContain('产品背景与目标')
    expect(wrapper.text()).toContain('接口需求')
    expect(wrapper.text()).toContain('假设、风险与待确认事项')
    expect(wrapper.text()).toContain('架构方案已确认，不需要再选择方案')
  })
})

function state(): AnalysisState {
  const projectId = '10000000-0000-4000-8000-000000000000'
  return {
    project: { id: projectId, name: '测试', originalPrompt: '创建测试需求工作台', uploadedFileName: null, uploadedFileContent: null, supplementalPrompt: null, language: 'zh-CN', stage: 'CLARIFYING', status: 'ACTIVE', completeness: 40, userRenamed: false, archivedAt: null, deletedAt: null, createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z' },
    requirements: [], answers: [], conflicts: [],
    questions: [{ id: '00000000-0000-4000-8000-000000000001', projectId, batchId: '20000000-0000-4000-8000-000000000000', text: '退款期限是多少？', reason: '影响退款规则', dimension: 'BUSINESS_RULES', targetField: 'refund.window', semanticKey: 'refund-window', inputType: 'TEXT', options: [], priority: 4, status: 'PENDING', createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z' }],
    completeness: { total: 40, dimensions: [], pendingCount: 1, hasCoreConflict: false },
  }
}
