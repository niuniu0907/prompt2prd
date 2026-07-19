import 'fake-indexeddb/auto'
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
    const completed = sufficientCompletedRoundState()
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
        modelSettings: validModelSettings(),
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
    expect(wrapper.text()).toContain('关键信息已达到生成条件，可以生成PRD')
  })

  it('saves the current completed batch before navigating to PRD generation', async () => {
    const initial = sufficientPendingState()
    const completed = sufficientCompletedRoundState()
    const stateStore: AnalysisStateStore = { load: vi.fn(async () => initial), saveFinal: vi.fn(async () => completed) }
    const clarification = {
      submitBatch: vi.fn(async (_projectId, drafts): Promise<SubmitBatchResult> => ({
        answers: [{
          id: '90000000-0000-4000-8000-000000000001',
          projectId: initial.project.id,
          questionId: drafts[0].questionId,
          selectedOptionIds: drafts[0].selectedOptionIds,
          customAnswer: drafts[0].customAnswer,
          note: drafts[0].note,
          skipped: drafts[0].skipped,
          createdAt: '2026-07-17T12:01:00.000Z',
          updatedAt: '2026-07-17T12:01:00.000Z',
        }],
        questions: initial.questions.map(question => ({ ...question, status: 'ANSWERED' as const })),
      })),
    }
    const client = { submitAnswers: vi.fn(async (_body: AnalysisAnswersRequestBody) => completed), cancel: vi.fn() }
    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore,
        clarification,
        client,
        modelSettings: validModelSettings(),
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    await wrapper.get('[data-testid="option-refund-window-:0"]').setValue(true)
    await wrapper.get('[data-testid="generate-prd-now"]').trigger('click')
    await flushPromises()

    expect(clarification.submitBatch).toHaveBeenCalledTimes(1)
    expect(client.submitAnswers).toHaveBeenCalledTimes(1)
    const body = client.submitAnswers.mock.calls[0]![0]
    expect(body.answers[0]).toMatchObject({ question: '退款期限是多少？', answer: '采用常见默认规则' })
    expect(stateStore.saveFinal).toHaveBeenCalledWith(initial.project.id, completed)
  })

  it('automatically requests the next round when information is incomplete and no questions remain', async () => {
    const initial = completedRoundState()
    const nextRound = {
      ...initial,
      questions: [
        ...initial.questions,
        {
          id: '00000000-0000-4000-8000-000000000002',
          projectId: initial.project.id,
          batchId: '20000000-0000-4000-8000-000000000001',
          text: '是否需要角色权限？',
          reason: '影响 PRD 的权限边界',
          dimension: 'ROLES_PERMISSIONS',
          targetField: 'rolesScenarios.permission',
          semanticKey: 'permission',
          inputType: 'SINGLE_SELECT' as const,
          options: [
            { id: '70000000-0000-4000-8000-000000000001', label: '需要', impact: '补充权限矩阵', recommended: true },
            { id: '70000000-0000-4000-8000-000000000002', label: '不需要', impact: '保持简单', recommended: false },
          ],
          priority: 5,
          status: 'PENDING' as const,
          createdAt: '2026-07-17T12:02:00.000Z',
          updatedAt: '2026-07-17T12:02:00.000Z',
        },
      ],
    }
    const stateStore: AnalysisStateStore = { load: vi.fn(async () => initial), saveFinal: vi.fn(async () => nextRound) }
    const client = { submitAnswers: vi.fn(async (_body: AnalysisAnswersRequestBody) => nextRound), cancel: vi.fn() }

    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore,
        clarification: { submitBatch: vi.fn() },
        client,
        modelSettings: validModelSettings(),
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()
    await flushPromises()

    expect(client.submitAnswers).toHaveBeenCalledTimes(1)
    expect(stateStore.saveFinal).toHaveBeenCalledWith(initial.project.id, nextRound)
    expect(wrapper.text()).toContain('是否需要角色权限？')
    expect(wrapper.text()).not.toContain('信息还不够完整，可以先生成PRD草稿')
  })

  it('lets users answer the next saved batch while AI follow-up runs in the background', async () => {
    const originalScrollIntoView = Element.prototype.scrollIntoView
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    const initial = stateWithTwoPendingBatches()
    const answeredFirstBatch = {
      answers: [{
        id: '90000000-0000-4000-8000-000000000001',
        projectId: initial.project.id,
        questionId: initial.questions[0]!.id,
        selectedOptionIds: ['70000000-0000-4000-8000-000000000011'],
        customAnswer: null,
        note: null,
        skipped: false,
        createdAt: '2026-07-17T12:01:00.000Z',
        updatedAt: '2026-07-17T12:01:00.000Z',
      }],
      questions: [
        { ...initial.questions[0]!, status: 'ANSWERED' as const, updatedAt: '2026-07-17T12:01:00.000Z' },
      ],
    }
    const stateStore: AnalysisStateStore = { load: vi.fn(async () => initial), saveFinal: vi.fn() }
    const clarification = { submitBatch: vi.fn(async (): Promise<SubmitBatchResult> => answeredFirstBatch) }
    const client = { submitAnswers: vi.fn(() => new Promise<AnalysisState>(() => {})), cancel: vi.fn() }
    try {
      const wrapper = mount(QuestionWizardView, {
        props: {
          projectId: initial.project.id,
          stateStore,
          clarification,
          client,
          modelSettings: validModelSettings(),
        },
        global: { plugins: [createPinia()] },
      })
      await flushPromises()

      await wrapper.get('[data-testid="option-payment-11"]').setValue(true)
      await wrapper.get('[data-testid="submit-batch"]').trigger('click')
      await flushPromises()

      expect(client.submitAnswers).toHaveBeenCalledTimes(1)
      expect(stateStore.saveFinal).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain('本轮回答已保存，已切换到下一轮')
      expect(wrapper.text()).toContain('AI 正在后台整理上一轮回答')
      expect(wrapper.text()).toContain('是否需要通知提醒？')
      expect(scrollIntoView).toHaveBeenCalledWith({ block: 'start', behavior: 'smooth' })

      await wrapper.get('[data-testid="option-notification-22"]').setValue(true)
      expect(wrapper.get('[data-testid="submit-batch"]').attributes('disabled')).toBeUndefined()
    }
    finally {
      Element.prototype.scrollIntoView = originalScrollIntoView
    }
  })

  it('limits each clarification round to ten questions', async () => {
    const initial = stateWithLargeBatch()
    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore: { load: vi.fn(async () => initial), saveFinal: vi.fn() },
        clarification: { submitBatch: vi.fn() },
        client: { submitAnswers: vi.fn(), cancel: vi.fn() },
        modelSettings: validModelSettings(),
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.findAll('.question-card')).toHaveLength(10)
    expect(wrapper.text()).toContain('每轮最多 10 题')
  })

  it('edits the original prompt inline without returning to requirement input', async () => {
    const initial = state()
    const sourceRepository = {
      updateOriginalPrompt: vi.fn(async (_projectId: string, originalPrompt: string) => ({
        ...initial.project,
        originalPrompt,
        updatedAt: '2026-07-17T12:03:00.000Z',
      })),
    }
    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore: { load: vi.fn(async () => initial), saveFinal: vi.fn() },
        clarification: { submitBatch: vi.fn() },
        client: { submitAnswers: vi.fn(), cancel: vi.fn() },
        sourceRepository,
        modelSettings: validModelSettings(),
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    await wrapper.get('.wizard-view__link-button').trigger('click')
    await wrapper.get('[data-testid="original-prompt-editor"]').setValue('更新后的原始需求描述')
    await wrapper.get('.wizard-view__dialog').trigger('submit')
    await flushPromises()

    expect(sourceRepository.updateOriginalPrompt).toHaveBeenCalledWith(initial.project.id, '更新后的原始需求描述')
    expect(wrapper.text()).toContain('原始需求已更新')
    expect(wrapper.text()).toContain('更新后的原始需求描述')
    expect(wrapper.text()).not.toContain('需求输入')
  })

  it('shows the simplified PRD coverage checklist without interface or architecture requirements', async () => {
    const initial = state()
    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore: { load: vi.fn(async () => initial), saveFinal: vi.fn() },
        architectureSelected: vi.fn(async () => ({ id: '50000000-0000-4000-8000-000000000000' })),
        clarification: { submitBatch: vi.fn() },
        client: { submitAnswers: vi.fn(), cancel: vi.fn() },
        modelSettings: validModelSettings(),
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('AI 已读取你的初始需求')
    expect(wrapper.text()).toContain('最终 PRD 至少覆盖')
    expect(wrapper.text()).toContain('产品背景与目标')
    expect(wrapper.text()).toContain('页面清单')
    expect(wrapper.text()).not.toContain('接口需求')
    expect(wrapper.text()).toContain('假设、风险与待确认事项')
    expect(wrapper.text()).not.toContain('架构方案已确认')
  })

  it('collects supplemental ideas inline and keeps the user on the clarification page', async () => {
    const initial = sufficientCompletedRoundState()
    const nextRound = {
      ...initial,
      questions: [
        ...initial.questions,
        {
          id: '00000000-0000-4000-8000-000000000002',
          projectId: initial.project.id,
          batchId: '20000000-0000-4000-8000-000000000001',
          text: '是否需要角色权限？',
          reason: '影响 PRD 的权限边界',
          dimension: 'ROLES_PERMISSIONS',
          targetField: 'rolesScenarios.permission',
          semanticKey: 'permission',
          inputType: 'SINGLE_SELECT' as const,
          options: [
            { id: '70000000-0000-4000-8000-000000000001', label: '需要', impact: '补充权限矩阵', recommended: true },
            { id: '70000000-0000-4000-8000-000000000002', label: '不需要', impact: '保持简单', recommended: false },
          ],
          priority: 5,
          status: 'PENDING' as const,
          createdAt: '2026-07-17T12:02:00.000Z',
          updatedAt: '2026-07-17T12:02:00.000Z',
        },
      ],
    }
    const stateStore: AnalysisStateStore = { load: vi.fn(async () => initial), saveFinal: vi.fn(async () => nextRound) }
    const client = { submitAnswers: vi.fn(async (_body: AnalysisAnswersRequestBody) => nextRound), cancel: vi.fn() }
    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore,
        clarification: { submitBatch: vi.fn() },
        client,
        modelSettings: validModelSettings(),
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('关键信息已达到生成条件，可以生成PRD')
    expect(wrapper.text()).not.toContain('项目概览')
    await wrapper.get('.wizard-view__secondary').trigger('click')
    await wrapper.get('[data-testid="supplemental-idea"]').setValue('   ')
    expect(wrapper.get('[data-testid="submit-supplement"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="supplemental-idea"]').setValue('我还想补充后台审核和角色权限')
    await wrapper.get('[data-testid="submit-supplement"]').trigger('submit')
    await flushPromises()

    expect(client.submitAnswers).toHaveBeenCalledTimes(1)
    const body = client.submitAnswers.mock.calls[0]![0]
    expect(body.originalInput).toContain('创建测试需求工作台')
    expect(body.supplementalInput).toBe('我还想补充后台审核和角色权限')
    expect(body.answers).toHaveLength(1)
    expect(stateStore.saveFinal).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('AI 已根据补充想法生成下一轮追问')
    expect(wrapper.text()).toContain('是否需要角色权限？')
  })

  it('blocks answer submission before the backend returns HTTP 400 when model settings are incomplete', async () => {
    const initial = state()
    const stateStore: AnalysisStateStore = { load: vi.fn(async () => initial), saveFinal: vi.fn() }
    const clarification = { submitBatch: vi.fn() }
    const client = { submitAnswers: vi.fn(), cancel: vi.fn() }
    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore,
        clarification,
        client,
        modelSettings: { keySource: 'USER', apiKey: 'sk-test', model: '' },
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    await wrapper.get('[data-testid="option-refund-window-:0"]').setValue(true)
    await wrapper.get('[data-testid="submit-batch"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('模型名称未填写')
    expect(wrapper.text()).toContain('前往模型设置')
    expect(clarification.submitBatch).not.toHaveBeenCalled()
    expect(client.submitAnswers).not.toHaveBeenCalled()
  })

  it('lets users retry saved answers after the AI follow-up request fails', async () => {
    const initial = state()
    const answeredState = completedRoundState()
    const nextRound = {
      ...answeredState,
      questions: [
        ...answeredState.questions,
        {
          id: '00000000-0000-4000-8000-000000000002',
          projectId: initial.project.id,
          batchId: '20000000-0000-4000-8000-000000000001',
          text: '是否需要通知提醒？',
          reason: '影响页面和验收范围',
          dimension: 'FEATURES',
          targetField: 'featureScopePriorities.notification',
          semanticKey: 'notification',
          inputType: 'SINGLE_SELECT' as const,
          options: [
            { id: '70000000-0000-4000-8000-000000000011', label: '需要', impact: '补充通知功能', recommended: true },
            { id: '70000000-0000-4000-8000-000000000012', label: '不需要', impact: '缩小 MVP 范围', recommended: false },
          ],
          priority: 4,
          status: 'PENDING' as const,
          createdAt: '2026-07-17T12:02:00.000Z',
          updatedAt: '2026-07-17T12:02:00.000Z',
        },
      ],
    }
    const stateStore: AnalysisStateStore = { load: vi.fn(async () => initial), saveFinal: vi.fn(async (_id, value) => value as AnalysisState) }
    const clarification = {
      submitBatch: vi.fn(async (_projectId, drafts): Promise<SubmitBatchResult> => ({
        answers: [{
          id: '90000000-0000-4000-8000-000000000001',
          projectId: initial.project.id,
          questionId: drafts[0].questionId,
          selectedOptionIds: [],
          customAnswer: '采用常见默认规则',
          note: null,
          skipped: false,
          createdAt: '2026-07-17T12:01:00.000Z',
          updatedAt: '2026-07-17T12:01:00.000Z',
        }],
        questions: initial.questions.map(question => ({ ...question, status: 'ANSWERED' as const })),
      })),
    }
    const client = {
      submitAnswers: vi.fn()
        .mockRejectedValueOnce(new Error('请求参数无效，请检查模型设置和当前项目数据后重试。'))
        .mockResolvedValueOnce(nextRound),
      cancel: vi.fn(),
    }
    const wrapper = mount(QuestionWizardView, {
      props: {
        projectId: initial.project.id,
        stateStore,
        clarification,
        client,
        modelSettings: validModelSettings(),
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    await wrapper.get('[data-testid="option-refund-window-:0"]').setValue(true)
    await wrapper.get('[data-testid="submit-batch"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请求参数无效')
    expect(wrapper.text()).toContain('重新提交已保存回答')
    expect(wrapper.text()).toContain('还需要继续澄清关键需求')

    await wrapper.get('.wizard-view__error .button-primary').trigger('click')
    await flushPromises()

    expect(client.submitAnswers).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('AI 已重新处理保存的回答，并生成下一轮追问')
    expect(wrapper.text()).toContain('是否需要通知提醒？')
  })
})

function validModelSettings() {
  return { keySource: 'USER', apiKey: 'sk-test', provider: 'DEEPSEEK', model: 'deepseek-chat' }
}

function state(): AnalysisState {
  const projectId = '10000000-0000-4000-8000-000000000000'
  return {
    project: { id: projectId, name: '测试', originalPrompt: '创建测试需求工作台', uploadedFileName: null, uploadedFileContent: null, supplementalPrompt: null, language: 'zh-CN', stage: 'CLARIFYING', status: 'ACTIVE', completeness: 40, userRenamed: false, archivedAt: null, deletedAt: null, createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z' },
    requirements: [], answers: [], conflicts: [],
    questions: [{ id: '00000000-0000-4000-8000-000000000001', projectId, batchId: '20000000-0000-4000-8000-000000000000', text: '退款期限是多少？', reason: '影响退款规则', dimension: 'BUSINESS_RULES', targetField: 'refund.window', semanticKey: 'refund-window', inputType: 'TEXT', options: [], priority: 4, status: 'PENDING', createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z' }],
    completeness: { total: 40, dimensions: [], pendingCount: 1, hasCoreConflict: false },
  }
}

function completedRoundState(): AnalysisState {
  const base = state()
  return {
    ...base,
    requirements: [
      {
        id: '60000000-0000-4000-8000-000000000001',
        projectId: base.project.id,
        type: 'FEATURE',
        title: '需求澄清',
        content: '支持多轮需求澄清',
        status: 'CONFIRMED',
        sourceType: 'USER_ANSWER',
        sourceId: null,
        locked: false,
        metadata: {},
        createdAt: '2026-07-17T12:01:00.000Z',
        updatedAt: '2026-07-17T12:01:00.000Z',
      },
    ],
    questions: base.questions.map(question => ({ ...question, status: 'ANSWERED' as const })),
    answers: [
      {
        id: '90000000-0000-4000-8000-000000000001',
        projectId: base.project.id,
        questionId: base.questions[0]!.id,
        selectedOptionIds: [],
        customAnswer: '采用常见默认规则',
        note: null,
        skipped: false,
        createdAt: '2026-07-17T12:01:00.000Z',
        updatedAt: '2026-07-17T12:01:00.000Z',
      },
    ],
    completeness: { total: 60, dimensions: [], pendingCount: 0, hasCoreConflict: false },
  }
}

function sufficientPendingState(): AnalysisState {
  const base = state()
  return {
    ...base,
    project: { ...base.project, completeness: 85 },
    completeness: { total: 85, dimensions: [], pendingCount: 1, hasCoreConflict: false },
  }
}

function sufficientCompletedRoundState(): AnalysisState {
  const base = completedRoundState()
  return {
    ...base,
    project: { ...base.project, completeness: 85 },
    completeness: { total: 85, dimensions: [], pendingCount: 0, hasCoreConflict: false },
  }
}

function stateWithTwoPendingBatches(): AnalysisState {
  const base = state()
  return {
    ...base,
    questions: [
      {
        ...base.questions[0]!,
        text: '需要哪些支付方式？',
        semanticKey: 'payment',
        inputType: 'SINGLE_SELECT',
        options: [
          { id: '70000000-0000-4000-8000-000000000011', label: '微信支付', impact: '需要支付回调', recommended: true },
        ],
        priority: 5,
      },
      {
        ...base.questions[0]!,
        id: '00000000-0000-4000-8000-000000000002',
        batchId: '20000000-0000-4000-8000-000000000001',
        text: '是否需要通知提醒？',
        semanticKey: 'notification',
        targetField: 'featureScopePriorities.notification',
        inputType: 'SINGLE_SELECT',
        options: [
          { id: '70000000-0000-4000-8000-000000000022', label: '需要', impact: '补充消息触达范围', recommended: true },
        ],
        priority: 4,
      },
    ],
    completeness: { total: 40, dimensions: [], pendingCount: 2, hasCoreConflict: false },
  }
}

function stateWithLargeBatch(): AnalysisState {
  const base = state()
  return {
    ...base,
    questions: Array.from({ length: 12 }, (_, index) => ({
      ...base.questions[0]!,
      id: `00000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
      text: `第 ${index + 1} 个问题？`,
      semanticKey: `large-${index + 1}`,
      inputType: 'SINGLE_SELECT' as const,
      options: [
        {
          id: `70000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
          label: '采用推荐方案',
          impact: '继续推进',
          recommended: true,
        },
      ],
      priority: 12 - index,
    })),
    completeness: { total: 40, dimensions: [], pendingCount: 12, hasCoreConflict: false },
  }
}
