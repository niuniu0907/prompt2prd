<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { routeLocationKey, useRouter } from 'vue-router'
import { createAnalysisClient, type AnalysisAnswersRequestBody, type AnalysisCallbacks, type GenerateRoundRequestBody } from '@/api/analysisApi'
import { isModelSetupErrorMessage, validateAnalysisModelSettings } from '@/api/modelSettingsValidation'
import { analysisStateRepository, type AnalysisState, type AnalysisStateStore } from '@/db/repositories/analysisStateRepository'
import { clarificationRepository, type ClarificationSubmitter, type SubmitBatchResult } from '@/db/repositories/clarificationRepository'
import { clarificationRoundRepository } from '@/db/repositories/clarificationRoundRepository'
import { appDatabase } from '@/db/appDatabase'
import { toPlainData } from '@/db/toPlainData'
import {
  projectRepository,
  type ProjectSourceRepository,
} from '@/db/repositories/projectRepository'
import type { ClarificationAnswer, ClarificationQuestion, ClarificationRound } from '@/features/requirements/types'
import { useModelConfigStore } from '@/stores/modelConfigStore'
import { useAnalysisRoundStore } from '@/stores/analysisRoundStore'
import { useToast } from '@/composables/useToast'
import QuestionBatch from './QuestionBatch.vue'
import QuestionSkeleton from './QuestionSkeleton.vue'
import type { QuestionAnswerDraft } from './answerTypes'
import { activeCoverageKeys, prdCoverageAreas } from './prdCoverage'

interface AnswerAnalysisRunner {
  submitAnswers(body: AnalysisAnswersRequestBody, callbacks?: AnalysisCallbacks): Promise<unknown>
  generateRound(body: GenerateRoundRequestBody, signal?: AbortSignal): Promise<GenerateRoundResult>
  cancel(): void
}

interface GenerateRoundResult {
  success: boolean
  questions: unknown[]
  coverageCategories: string[]
  requestId: string
  errorCode?: string
  errorMessage?: string
}

type NextRoundOutcome =
  | { type: 'ACTIVATED' }
  | { type: 'COMPLETED' }
  | { type: 'FAILED'; message: string }

const props = defineProps<{
  projectId?: string
  stateStore?: AnalysisStateStore
  clarification?: ClarificationSubmitter
  architectureSelected?: (projectId: string) => Promise<unknown>
  client?: AnswerAnalysisRunner
  modelSettings?: unknown
  sourceRepository?: ProjectSourceRepository
}>()
const route = inject(routeLocationKey, null)
const router = useRouter() as ReturnType<typeof useRouter> | undefined
const projectId = computed(() => props.projectId ?? String(route?.params.projectId ?? ''))
const stateStore = props.stateStore ?? analysisStateRepository
const clarification = props.clarification ?? clarificationRepository
const sourceRepository = props.sourceRepository ?? projectRepository
const client = props.client ?? createAnalysisClient()
const modelConfig = useModelConfigStore()
const roundStore = useAnalysisRoundStore()
const { success: showSuccess, error: showError, info: showInfo } = useToast()
const state = ref<AnalysisState | null>(null)
/** IDLE → SAVING(保存中) → ANALYZING(AI整理中) → GENERATING_NEXT_ROUND(正在生成下一轮) → IDLE */
type SubmitStatus = 'IDLE' | 'SAVING' | 'ANALYZING' | 'GENERATING_NEXT_ROUND'
const submitStatus = ref<SubmitStatus>('IDLE')
const loading = ref(true)
const errorMessage = ref('')
const completedMessage = ref('')
const supplementOpen = ref(false)
const supplementalIdea = ref('')
const currentBatchAnchor = ref<HTMLElement | null>(null)
const sourceEditorOpen = ref(false)
const originalPromptDraft = ref('')
const sourceSaving = ref(false)
const sourceError = ref('')
const analysisProgressMessage = ref('')
const analysisStartTime = ref(0)
const analysisTimedOut = ref(false)
let analysisTimer: ReturnType<typeof setTimeout> | null = null
const ANALYSIS_TIMEOUT_MS = 120_000

// Round-driven: use store state instead of batch-based grouping
const currentRoundQuestions = computed(() => roundStore.currentRoundQuestions)
const currentCoverageKeys = computed(() => activeCoverageKeys(currentRoundQuestions.value))
const currentRoundKey = computed(() =>
  `${roundStore.currentRoundNo}:${currentRoundQuestions.value.map(q => q.id).join('|')}`
)
const originalPromptExcerpt = computed(() => {
  const text = state.value?.project.originalPrompt?.trim()
  if (!text) return ''
  return text.length > 120 ? `${text.slice(0, 120)}...` : text
})
const isIdle = computed(() => submitStatus.value === 'IDLE')
const canSubmitSupplement = computed(() => Boolean(supplementalIdea.value.trim()) && isIdle.value)
const canRetrySavedAnswers = computed(() => Boolean(state.value?.answers.length) && isIdle.value)
const needsModelSetup = computed(() => isModelSetupErrorMessage(errorMessage.value))
const hasGenerationError = computed(() => roundStore.generationError !== null)
// 首次AI分析成功后即可生成PRD；完整度和冲突只做提示，不阻塞生成
const canGeneratePrd = computed(() => {
  const s = state.value
  return s !== null && (s.requirements.length > 0 || s.questions.length > 0)
})
const needsMoreClarification = computed(() => Boolean(state.value) && !canGeneratePrd.value)
const completionTitle = computed(() => {
  if (canGeneratePrd.value) return '关键信息已达到生成条件，可以生成PRD'
  return '还需要继续澄清关键需求'
})
const completionDescription = computed(() => {
  const total = state.value?.completeness.total ?? 0
  const hasConflict = state.value?.completeness.hasCoreConflict ?? false
  if (canGeneratePrd.value) {
    const warnings: string[] = []
    if (total < 80) warnings.push(`当前完整度 ${total}%，仍有信息待补齐`)
    if (hasConflict) warnings.push('仍有核心冲突待处理')
    const suffix = warnings.length > 0 ? `（${warnings.join('；')}）` : ''
    return `可以继续澄清，也可以先生成 PRD 草稿。${suffix}`
  }
  return '系统会继续生成下一轮问题来补齐关键需求。'
})
const generatingNextRound = computed(() => roundStore.isLoadingNextRound && submitStatus.value !== 'ANALYZING')

onMounted(async () => {
  try {
    const loadedState = await stateStore.load(projectId.value)
    state.value = loadedState ?? null
    if (loadedState) {
      // Recover round state from IndexedDB
      await roundStore.recover(projectId.value)
      // If no round data yet (legacy project), initialize Round 1 from existing questions
      if (roundStore.allQuestions.size === 0 && loadedState.questions.length > 0) {
        roundStore.setCurrentRoundQuestions(1, loadedState.questions)
      }
      // If current round is empty but state has pending questions, re-seed from state
      if (roundStore.currentRoundQuestions.length === 0 && loadedState.questions.length > 0) {
        roundStore.setCurrentRoundQuestions(1, loadedState.questions)
      }

      // Auto-recover: if the current round is complete and a READY next
      // round exists (e.g. generation succeeded in a previous session but
      // activateNextRound failed due to DataCloneError), activate it now.
      const currentCompleted =
        roundStore.currentRoundQuestions.length > 0 &&
        roundStore.currentRoundQuestions.every(
          question =>
            question.status === 'ANSWERED' ||
            question.status === 'SKIPPED',
        )
      if (roundStore.hasReadyNextRound && currentCompleted) {
        await roundStore.activateNextRound(projectId.value)
      }
    }
  }
  catch (error) { errorMessage.value = readableError(error) }
  finally {
    loading.value = false
  }
})
onBeforeUnmount(() => { stopAnalysisTimer(); client.cancel() })

watch(currentRoundKey, async (value, oldValue) => {
  if (!value || !oldValue || value === oldValue) return
  await nextTick()
  currentBatchAnchor.value?.scrollIntoView?.({ block: 'start', behavior: 'smooth' })
}, { flush: 'post' })

function startAnalysisTimer() {
  stopAnalysisTimer()
  analysisTimedOut.value = false
  analysisTimer = setTimeout(() => { analysisTimedOut.value = true }, ANALYSIS_TIMEOUT_MS)
}
function stopAnalysisTimer() {
  if (analysisTimer !== null) { clearTimeout(analysisTimer); analysisTimer = null }
}
function cancelAnalysis() {
  stopAnalysisTimer()
  client.cancel()
  submitStatus.value = 'IDLE'
  analysisProgressMessage.value = ''
  errorMessage.value = '已取消本次提交，可以重新回答或提交。'
}

async function submit(drafts: QuestionAnswerDraft[]) {
  if (!state.value || submitStatus.value !== 'IDLE') return

  // If generation previously failed but answers are already saved, retry
  // generation only — do NOT re-save the same answers or call submitAnswers again.
  if (hasGenerationError.value) {
    const settings = props.modelSettings ?? requestModelSettings()
    const validation = validateAnalysisModelSettings(settings)
    if (validation) {
      errorMessage.value = validation
      completedMessage.value = ''
      return
    }
    await retryFailedGeneration()
    return
  }

  const settings = props.modelSettings ?? requestModelSettings()
  const validation = validateAnalysisModelSettings(settings)
  if (validation) {
    errorMessage.value = validation
    completedMessage.value = ''
    return
  }
  submitStatus.value = 'SAVING'; errorMessage.value = ''; completedMessage.value = '保存中…'
  analysisProgressMessage.value = ''; analysisTimedOut.value = false
  showInfo('保存中…')
  try {
    // 1. Persist answers to IndexedDB immediately
    const persisted = await clarification.submitBatch(projectId.value, drafts)
    const localState = mergePersistedAnswers(state.value, persisted)
    state.value = localState

    // Sync the round store so the UI reflects the updated question statuses
    // (ANSWERED / SKIPPED) even if the backend round or pre-generation fails.
    if (persisted.questions.length > 0) {
      roundStore.setCurrentRoundQuestions(roundStore.currentRoundNo, persisted.questions)
    }

    // 2. Send ONLY current batch answers to backend with SSE progress callbacks
    completedMessage.value = '回答已保存，AI正在整理'
    submitStatus.value = 'ANALYZING'
    showInfo('AI整理中…')
    analysisStartTime.value = Date.now()
    startAnalysisTimer()

    let finalState: unknown
    try {
      finalState = await client.submitAnswers({
        state: toServerState(localState),
        answers: toAnswerTurns(persisted.questions, persisted.answers),
        originalInput: originalProjectInput(localState),
        missingInformation: localState.requirements.filter(item => item.type === 'MISSING_INFORMATION').map(item => item.content),
        modelSettings: settings,
      }, {
        onEvent: (event) => {
          if (event.type === 'analysis_progress') {
            analysisProgressMessage.value = String(event.data.message)
          }
        },
      })
    } catch (analysisError) {
      stopAnalysisTimer()
      completedMessage.value = ''
      errorMessage.value = `回答已保存，但AI整理失败：${readableError(analysisError)}`
      submitStatus.value = 'IDLE'
      return
    }

    stopAnalysisTimer()

    // 3. Save the returned state before generating next round
    const savedState = await stateStore.saveFinal(projectId.value, finalState)
    state.value = savedState
    await repersistFutureRoundQuestions()
    notifyAnalysisStateSaved()

    // 4. If the next round is already READY activate it directly;
    //    otherwise mark downstream rounds as stale and trigger generation.
    const nextRoundNo = roundStore.currentRoundNo + 1
    if (roundStore.readyNextRoundNo === nextRoundNo) {
      await roundStore.activateNextRound(projectId.value)
      completedMessage.value = ''
      submitStatus.value = 'IDLE'
      return
    }

    await roundStore.markDownstreamStale(roundStore.currentRoundNo)

    completedMessage.value = ''
    submitStatus.value = 'GENERATING_NEXT_ROUND'
    showInfo('正在生成下一轮')
    const outcome = await triggerPreGeneration()

    // Handle generation outcome explicitly — no fire-and-forget
    switch (outcome.type) {
      case 'ACTIVATED':
        // Round activated — UI transitions automatically via store reactivity
        completedMessage.value = ''
        submitStatus.value = 'IDLE'
        break
      case 'COMPLETED':
        completedMessage.value = '本轮已完成，没有新的追问，可以查看需求结果或生成PRD。'
        submitStatus.value = 'IDLE'
        break
      case 'FAILED':
        completedMessage.value = ''
        errorMessage.value = `回答已保存，但下一轮生成失败：${outcome.message}`
        submitStatus.value = 'IDLE'
        break
    }
  } catch (error) {
    stopAnalysisTimer()
    completedMessage.value = ''
    errorMessage.value = readableError(error)
    submitStatus.value = 'IDLE'
  }
}

/**
 * Triggers pre-generation of the next round (N+1).
 *
 * Returns an explicit outcome so callers never fire-and-forget.
 */
async function triggerPreGeneration(): Promise<NextRoundOutcome> {
  if (!state.value) {
    return { type: 'FAILED', message: '项目状态丢失，请刷新页面后重试' }
  }
  const nextRoundNo = roundStore.currentRoundNo + 1

  // If the next round is already READY (e.g. generation succeeded but
  // activateNextRound failed due to DataCloneError), activate it directly
  // rather than re-generating — otherwise shouldStartGeneration returns
  // false and the page is stuck on the current round forever.
  if (roundStore.readyNextRoundNo === nextRoundNo) {
    await roundStore.activateNextRound(projectId.value)
    completedMessage.value = ''
    submitStatus.value = 'IDLE'
    return { type: 'ACTIVATED' }
  }

  const requestId = crypto.randomUUID()

  if (!roundStore.shouldStartGeneration(nextRoundNo, requestId)) {
    return { type: 'FAILED', message: '已有相同的生成请求在处理中' }
  }

  roundStore.startGeneration(nextRoundNo, requestId)
  const settings = props.modelSettings ?? requestModelSettings()

  try {
    const currentQuestions = roundStore.currentRoundQuestions
    const body: GenerateRoundRequestBody = {
      state: toServerState(state.value),
      targetRoundNo: nextRoundNo,
      coveredAreas: [...roundStore.coveredAreas],
      currentVisibleQuestions: currentQuestions.map(q => ({
        text: q.text,
        targetField: q.targetField,
        semanticKey: q.semanticKey,
      })),
      missingInformation: state.value.requirements
        .filter(item => item.type === 'MISSING_INFORMATION')
        .map(item => item.content),
      modelSettings: settings,
    }

    const result = await client.generateRound(body)

    if (result.success && result.questions.length > 0) {
      const questions = result.questions as ClarificationQuestion[]
      const now = new Date().toISOString()
      const round: ClarificationRound = {
        id: crypto.randomUUID(),
        projectId: projectId.value,
        roundNo: nextRoundNo,
        requestId: result.requestId,
        contextVersion: String(roundStore.contextVersion),
        questionIds: questions.map(q => q.id),
        coverageCategories: result.coverageCategories,
        status: 'READY',
        createdAt: now,
        generatedAt: now,
      }

      // Atomic IndexedDB write: round + questions
      await appDatabase.transaction(
        'rw',
        [
          appDatabase.clarification_round,
          appDatabase.clarification_question,
        ],
        async () => {
          await appDatabase.clarification_round.put(round)
          await appDatabase.clarification_question.bulkAdd(questions)
        },
      )

      // Update Pinia state AFTER successful persistence, then persist the
      // updated state so readyNextRoundNo / coveredAreas / contextVersion
      // are durable.
      roundStore.completeGeneration(nextRoundNo, questions, requestId)
      await roundStore.persist(projectId.value)

      // Auto-activate the freshly generated round so the user sees it immediately
      await roundStore.activateNextRound(projectId.value)
      return { type: 'ACTIVATED' }
    }

    if (result.success) {
      // AI determined no more clarification is needed: mark as complete
      // so the UI transitions to the completion / generate-PRD state.
      roundStore.completeGeneration(nextRoundNo, [], requestId)
      await roundStore.persist(projectId.value)
      await roundStore.activateNextRound(projectId.value)
      return { type: 'COMPLETED' }
    }

    // result.success === false — backend returned a controlled error
    const failureMessage = result.errorMessage || '下一轮问题生成失败'
    roundStore.markGenerationFailed(nextRoundNo, failureMessage)
    return { type: 'FAILED', message: failureMessage }
  } catch (error) {
    const message = error instanceof Error ? error.message : '下一轮问题生成失败'
    roundStore.markGenerationFailed(nextRoundNo, message)
    console.warn('Round pre-generation failed for round', nextRoundNo, error)
    return { type: 'FAILED', message }
  }
}

async function submitSupplement() {
  if (!state.value || submitStatus.value !== 'IDLE') return
  const supplementalInput = supplementalIdea.value.trim()
  if (!supplementalInput) {
    errorMessage.value = '补充想法不能为空。'
    return
  }
  const settings = props.modelSettings ?? requestModelSettings()
  const validation = validateAnalysisModelSettings(settings)
  if (validation) {
    errorMessage.value = validation
    completedMessage.value = ''
    return
  }
  submitStatus.value = 'ANALYZING'; errorMessage.value = ''; completedMessage.value = ''
  analysisProgressMessage.value = ''; analysisTimedOut.value = false
  startAnalysisTimer()
  try {
    const finalState = await client.submitAnswers({
      state: toServerState(state.value),
      answers: [], // supplemental has no batch answers — state carries all history
      originalInput: originalProjectInput(state.value),
      supplementalInput,
      missingInformation: state.value.requirements.filter(item => item.type === 'MISSING_INFORMATION').map(item => item.content),
      modelSettings: settings,
    }, {
      onEvent: (event) => {
        if (event.type === 'analysis_progress') {
          analysisProgressMessage.value = String(event.data.message)
        }
      },
    })

    stopAnalysisTimer()
    const savedState = await stateStore.saveFinal(projectId.value, finalState)
    state.value = savedState
    await repersistFutureRoundQuestions()
    notifyAnalysisStateSaved()

    supplementalIdea.value = ''
    supplementOpen.value = false

    // If the next round is already READY, activate directly.
    const nextRoundNo2 = roundStore.currentRoundNo + 1
    if (roundStore.readyNextRoundNo === nextRoundNo2) {
      await roundStore.activateNextRound(projectId.value)
      completedMessage.value = ''
      submitStatus.value = 'IDLE'
      return
    }

    await roundStore.markDownstreamStale(roundStore.currentRoundNo)
    completedMessage.value = ''
    submitStatus.value = 'GENERATING_NEXT_ROUND'
    const outcome = await triggerPreGeneration()

    switch (outcome.type) {
      case 'ACTIVATED':
        completedMessage.value = ''
        submitStatus.value = 'IDLE'
        break
      case 'COMPLETED':
        completedMessage.value = '本轮已完成，没有新的追问，可以查看需求结果或生成PRD。'
        submitStatus.value = 'IDLE'
        break
      case 'FAILED':
        completedMessage.value = ''
        errorMessage.value = `AI已根据补充想法更新需求，但下一轮生成失败：${outcome.message}`
        submitStatus.value = 'IDLE'
        break
    }
  } catch (error) {
    stopAnalysisTimer()
    completedMessage.value = ''
    errorMessage.value = readableError(error)
    submitStatus.value = 'IDLE'
  }
}

async function retrySavedAnswers() {
  if (!state.value || submitStatus.value !== 'IDLE') return
  const settings = props.modelSettings ?? requestModelSettings()
  const validation = validateAnalysisModelSettings(settings)
  if (validation) {
    errorMessage.value = validation
    return
  }
  submitStatus.value = 'ANALYZING'; errorMessage.value = ''; completedMessage.value = ''
  analysisProgressMessage.value = ''; analysisTimedOut.value = false
  startAnalysisTimer()
  try {
    // retry sends ALL saved answers — error recovery needs full history context
    const finalState = await client.submitAnswers({
      state: toServerState(state.value),
      answers: toAnswerTurns(state.value.questions, state.value.answers),
      originalInput: originalProjectInput(state.value),
      missingInformation: state.value.requirements.filter(item => item.type === 'MISSING_INFORMATION').map(item => item.content),
      modelSettings: settings,
    }, {
      onEvent: (event) => {
        if (event.type === 'analysis_progress') {
          analysisProgressMessage.value = String(event.data.message)
        }
      },
    })
    stopAnalysisTimer()
    const savedState = await stateStore.saveFinal(projectId.value, finalState)
    state.value = savedState
    await repersistFutureRoundQuestions()
    notifyAnalysisStateSaved()
    // If the next round is already READY, activate directly.
    const nextRoundNo3 = roundStore.currentRoundNo + 1
    if (roundStore.readyNextRoundNo === nextRoundNo3) {
      await roundStore.activateNextRound(projectId.value)
      completedMessage.value = ''
      submitStatus.value = 'IDLE'
      return
    }

    await roundStore.markDownstreamStale(roundStore.currentRoundNo)

    completedMessage.value = ''
    submitStatus.value = 'GENERATING_NEXT_ROUND'
    const outcome = await triggerPreGeneration()

    switch (outcome.type) {
      case 'ACTIVATED':
        completedMessage.value = ''
        submitStatus.value = 'IDLE'
        break
      case 'COMPLETED':
        completedMessage.value = '本轮已完成，没有新的追问，可以查看需求结果或生成PRD。'
        submitStatus.value = 'IDLE'
        break
      case 'FAILED':
        completedMessage.value = ''
        errorMessage.value = `回答已保存，但下一轮生成失败：${outcome.message}`
        submitStatus.value = 'IDLE'
        break
    }
  } catch (error) {
    stopAnalysisTimer()
    completedMessage.value = ''
    errorMessage.value = readableError(error)
    submitStatus.value = 'IDLE'
  }
}

async function retryFailedGeneration() {
  if (!state.value) {
    errorMessage.value = '项目状态丢失，请刷新页面后重试'
    return
  }
  // Clear the error and any stale generation state before retrying.
  roundStore.generationError = null
  if (roundStore.generatingRoundNo !== null) {
    roundStore.markGenerationFailed(roundStore.generatingRoundNo)
    roundStore.generationError = null
  }

  completedMessage.value = ''
  errorMessage.value = ''
  submitStatus.value = 'GENERATING_NEXT_ROUND'
  showInfo('正在重新生成下一轮…')

  const outcome = await triggerPreGeneration()

  switch (outcome.type) {
    case 'ACTIVATED':
      completedMessage.value = ''
      submitStatus.value = 'IDLE'
      break
    case 'COMPLETED':
      completedMessage.value = '本轮已完成，没有新的追问，可以查看需求结果或生成PRD。'
      submitStatus.value = 'IDLE'
      break
    case 'FAILED':
      completedMessage.value = ''
      errorMessage.value = `下一轮生成失败：${outcome.message}`
      submitStatus.value = 'IDLE'
      break
  }
}

function openOriginalEditor() {
  if (!state.value) return
  originalPromptDraft.value = state.value.project.originalPrompt
  sourceError.value = ''
  sourceEditorOpen.value = true
}

async function saveOriginalPrompt() {
  if (!state.value || sourceSaving.value) return
  if (Array.from(originalPromptDraft.value.trim()).length < 5) {
    sourceError.value = '原始需求至少需要 5 个字符。'
    return
  }
  sourceSaving.value = true
  sourceError.value = ''
  try {
    const updatedProject = await sourceRepository.updateOriginalPrompt(projectId.value, originalPromptDraft.value)
    state.value = { ...state.value, project: { ...state.value.project, ...updatedProject } }
    notifyAnalysisStateSaved()
    sourceEditorOpen.value = false
    completedMessage.value = '原始需求已更新。后续追问会使用新的原始需求。'
  } catch (error) {
    sourceError.value = readableError(error)
  } finally {
    sourceSaving.value = false
  }
}

function mergePersistedAnswers(source: AnalysisState, persisted: SubmitBatchResult): AnalysisState {
  const questionMap = new Map(source.questions.map(question => [question.id, question]))
  for (const question of persisted.questions) questionMap.set(question.id, question)
  const answerMap = new Map(source.answers.map(answer => [answer.questionId, answer]))
  for (const answer of persisted.answers) answerMap.set(answer.questionId, answer)
  return { ...source, questions: [...questionMap.values()], answers: [...answerMap.values()] }
}

function toAnswerTurns(questions: ClarificationQuestion[], answers: ClarificationAnswer[]) {
  const byId = new Map(questions.map(question => [question.id, question]))
  return answers.flatMap(answer => {
    const question = byId.get(answer.questionId)
    if (!question) return []
    const labels = question.options.filter(option => answer.selectedOptionIds.includes(option.id)).map(option => option.label)
    const text = [labels.join('、'), answer.customAnswer, answer.note].filter(Boolean).join('；') || '跳过'
    return [{ questionId: question.id, batchId: question.batchId, question: question.text, answer: text, answeredAt: answer.updatedAt }]
  })
}

function toServerState(value: AnalysisState) {
  const { id, name, language, stage, completeness } = value.project
  return { ...value, project: { id, name, language, stage, completeness } }
}
function originalProjectInput(value: AnalysisState) {
  return [value.project.originalPrompt, value.project.uploadedFileContent, value.project.supplementalPrompt]
    .filter((item): item is string => Boolean(item?.trim()))
    .join('\n\n')
}
function requestModelSettings() { return { ...modelConfig.requestKeyConfig, provider: modelConfig.provider, baseUrl: modelConfig.baseUrl || undefined, model: modelConfig.model, parameters: { temperature: modelConfig.temperature } } }
function readableError(error: unknown) { return error instanceof Error ? error.message : '提交失败，已保留本地回答。' }
/** Re-persist pre-generated (future) round questions that saveFinal's wholesale
 *  delete removed from the IndexedDB clarification_question store. */
async function repersistFutureRoundQuestions() {
  const future: ClarificationQuestion[] = []
  for (const [roundNo, questions] of roundStore.allQuestions) {
    if (roundNo > roundStore.currentRoundNo) {
      future.push(...questions)
    }
  }
  if (future.length > 0) {
    await appDatabase.clarification_question.bulkPut(
      toPlainData(future),
    )
  }
}
function notifyAnalysisStateSaved() {
  window.dispatchEvent(new CustomEvent('prompt2prd:analysis-state-saved', { detail: { projectId: projectId.value } }))
}

function goToRequirements() {
  void router?.push({ name: 'project-requirements', params: { projectId: projectId.value } })
}

function goToPrd() {
  void router?.push({ name: 'project-prd', params: { projectId: projectId.value } })
}

async function generatePrdFromCurrentAnswers(drafts: QuestionAnswerDraft[]) {
  if (!canGeneratePrd.value) {
    errorMessage.value = '当前信息还不够完整，请先继续回答下一轮问题。'
    completedMessage.value = ''
    return
  }
  if (!drafts.length) {
    goToPrd()
    return
  }
  if (!state.value || submitStatus.value !== 'IDLE') return
  const settings = props.modelSettings ?? requestModelSettings()
  const validation = validateAnalysisModelSettings(settings)
  if (validation) {
    errorMessage.value = validation
    completedMessage.value = ''
    return
  }
  submitStatus.value = 'SAVING'; errorMessage.value = ''; completedMessage.value = ''
  try {
    const persisted = await clarification.submitBatch(projectId.value, drafts)
    const localState = mergePersistedAnswers(state.value, persisted)
    state.value = localState
    try {
      await client.submitAnswers({
        state: toServerState(localState),
        answers: toAnswerTurns(persisted.questions, persisted.answers),
        originalInput: originalProjectInput(localState),
        missingInformation: localState.requirements.filter(item => item.type === 'MISSING_INFORMATION').map(item => item.content),
        modelSettings: settings,
      })
    } catch (error) {
      errorMessage.value = `本轮回答已保存，但 AI 整理失败：${readableError(error)}`
    }
    goToPrd()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    submitStatus.value = 'IDLE'
  }
}

function goToModelSettings() {
  void router?.push({ name: 'model-settings' })
}
</script>

<template>
  <main class="wizard-view" data-testid="question-wizard-view">
    <div v-if="loading" class="wizard-view__status">正在恢复问题…</div>
    <div v-else-if="!state" class="wizard-view__status" role="alert">没有找到项目分析状态。</div>
    <template v-else>
      <div v-if="errorMessage" class="wizard-view__error" role="alert">
        <span>{{ errorMessage }}</span>
        <button v-if="canRetrySavedAnswers" type="button" class="button-primary" :disabled="!isIdle" @click="retrySavedAnswers">
          {{ !isIdle ? '正在重新提交…' : '重新提交已保存回答' }}
        </button>
        <button v-if="needsModelSetup" type="button" class="button-primary" @click="goToModelSettings">前往模型设置</button>
      </div>
      <div v-if="generatingNextRound" class="wizard-view__notice" role="status">正在生成下一轮问题…</div>
      <div v-else-if="completedMessage" class="wizard-view__success">{{ completedMessage }}</div>
      <div v-if="hasGenerationError" class="wizard-view__error" role="alert">
        <span>{{ roundStore.generationError }}</span>
        <button type="button" class="button-primary" @click="retryFailedGeneration">重新生成</button>
      </div>
      <!-- Round header -->
      <section v-if="currentRoundQuestions.length" ref="currentBatchAnchor" class="wizard-view__intro">
        <div class="wizard-view__intro-top">
          <div>
            <span>AI 需求访谈</span>
            <h1>第 {{ roundStore.currentRoundNo }} 轮 · 共 {{ currentRoundQuestions.length }} 题</h1>
          </div>
          <button type="button" class="wizard-view__link-button" @click="openOriginalEditor">编辑原始需求</button>
        </div>
        <p v-if="originalPromptExcerpt" class="wizard-view__source">初始想法：{{ originalPromptExcerpt }}</p>
        <div v-if="roundStore.currentRoundInfo.coverageCategories.length" class="wizard-view__coverage" aria-label="本轮覆盖分类">
          <b>本轮主要覆盖</b>
          <ul>
            <li v-for="cat in roundStore.currentRoundInfo.coverageCategories" :key="cat" class="is-active">
              {{ cat }}
            </li>
          </ul>
        </div>
      </section>
      <!-- Questions -->
      <QuestionBatch
        v-if="currentRoundQuestions.length"
        :questions="currentRoundQuestions"
        :submit-status="submitStatus"
        :round-no="roundStore.currentRoundNo"
        :progress-message="analysisProgressMessage"
        :timed-out="analysisTimedOut"
        :generation-retry="hasGenerationError"
        @submit="submit"
        @cancel="cancelAnalysis"
      />
      <!-- Skeleton loading -->
      <QuestionSkeleton v-else-if="roundStore.isLoadingNextRound && submitStatus !== 'ANALYZING'" :count="8" />
      <!-- Empty / complete state -->
      <section v-else class="wizard-view__empty">
        <span>AI 需求访谈</span>
        <h1>{{ completionTitle }}</h1>
        <p>{{ completionDescription }}</p>
        <div class="wizard-view__actions">
          <button v-if="canGeneratePrd" type="button" class="button-primary" @click="goToPrd">
            生成PRD
          </button>
          <button type="button" class="wizard-view__secondary" @click="supplementOpen = true">
            补充新想法
          </button>
          <button type="button" class="wizard-view__secondary" @click="goToRequirements">
            查看需求结果
          </button>
        </div>
        <form v-if="supplementOpen" class="wizard-view__supplement" @submit.prevent="submitSupplement">
          <label for="supplemental-idea">补充想法</label>
          <textarea
            id="supplemental-idea"
            v-model="supplementalIdea"
            rows="5"
            :disabled="!isIdle"
            placeholder="例如：我还想补充退款、角色权限、页面状态或异常场景。"
            data-testid="supplemental-idea"
          ></textarea>
          <p v-if="!isIdle">AI 正在生成下一轮问题…</p>
          <div>
            <button type="button" class="wizard-view__secondary" :disabled="!isIdle" @click="supplementOpen = false">取消</button>
            <button type="submit" class="button-primary" :disabled="!canSubmitSupplement" data-testid="submit-supplement">
              {{ !isIdle ? '正在生成…' : '提交补充想法' }}
            </button>
          </div>
        </form>
      </section>
      <div v-if="sourceEditorOpen" class="wizard-view__modal" role="dialog" aria-modal="true" aria-label="编辑原始需求">
        <form class="wizard-view__dialog" @submit.prevent="saveOriginalPrompt">
          <header>
            <div>
              <span>原始需求</span>
              <h2>编辑原始需求</h2>
            </div>
            <button type="button" :disabled="sourceSaving" @click="sourceEditorOpen = false">关闭</button>
          </header>
          <textarea
            v-model="originalPromptDraft"
            rows="8"
            :disabled="sourceSaving"
            data-testid="original-prompt-editor"
          ></textarea>
          <p v-if="state.project.uploadedFileName" class="wizard-view__dialog-note">
            已上传文档“{{ state.project.uploadedFileName }}”会继续作为分析材料保留。
          </p>
          <p v-if="sourceError" class="wizard-view__dialog-error" role="alert">{{ sourceError }}</p>
          <footer>
            <button type="button" class="wizard-view__secondary" :disabled="sourceSaving" @click="sourceEditorOpen = false">取消</button>
            <button type="submit" class="button-primary" :disabled="sourceSaving" data-testid="save-original-prompt">
              {{ sourceSaving ? '保存中…' : '保存原始需求' }}
            </button>
          </footer>
        </form>
      </div>
    </template>
  </main>
</template>

<style scoped>
.wizard-view { max-width: 900px; margin: 0 auto; }
.wizard-view__status,.wizard-view__empty { padding: 46px; color: var(--color-text-secondary); text-align: center; }
.wizard-view__intro { display: grid; gap: 12px; margin-bottom: 18px; }
.wizard-view__intro-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.wizard-view__intro span,.wizard-view__empty span { color: var(--color-accent); font-size: 10px; font-weight: 750; }
.wizard-view__intro h1 { margin: 5px 0 0; font-size: 26px; line-height: 1.25; }
.wizard-view__intro p { margin: 7px 0 0; color: var(--color-text-secondary); font-size: 12px; line-height: 1.7; }
.wizard-view__link-button { margin-top: 8px; padding: 0; border: 0; color: var(--color-accent); background: transparent; font-size: 12px; font-weight: 700; cursor: pointer; }
.wizard-view__source { padding: 12px 14px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); }
.wizard-view__coverage { display: grid; gap: 9px; padding: 12px 14px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); }
.wizard-view__coverage b { font-size: 12px; }
.wizard-view__coverage ul { display: flex; flex-wrap: wrap; gap: 7px; margin: 0; padding: 0; list-style: none; }
.wizard-view__coverage li { padding: 5px 8px; border: 1px solid var(--color-border); border-radius: 999px; color: var(--color-text-secondary); font-size: 11px; }
.wizard-view__coverage li.is-active { border-color: #a9d94b; color: var(--color-text-primary); background: #effbd4; font-weight: 700; }
.wizard-view__empty h1 { color: var(--color-text-primary); }
.wizard-view__empty p { margin: 8px 0 0; }
.wizard-view__actions { display: flex; justify-content: center; gap: 10px; margin-top: 18px; }
.wizard-view__actions button { min-height: 38px; padding: 0 15px; border-radius: 8px; font-size: 12px; }
.wizard-view__secondary { border: 1px solid var(--color-border); color: var(--color-text-primary); background: var(--color-surface); cursor: pointer; }
.wizard-view__secondary:disabled { color: var(--color-text-muted); cursor: not-allowed; }
.wizard-view__error,.wizard-view__success { margin-bottom: 14px; padding: 12px 14px; border-radius: 10px; font-size: 11px; }
.wizard-view__error { display: grid; gap: 8px; color: #873f3f; background: #fff8f8; }
.wizard-view__error .button-primary { justify-self: start; }
.wizard-view__success { color: #246b58; background: #eefaf5; }
.wizard-view__notice { padding: 10px 12px; border: 1px solid #c8e9e4; border-radius: 10px; color: #28635b; background: #f4fbfa; font-size: 11px; }
.wizard-view__supplement { display: grid; gap: 10px; max-width: 640px; margin: 18px auto 0; padding: 14px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); text-align: left; }
.wizard-view__supplement label { color: var(--color-text-primary); font-size: 12px; font-weight: 750; }
.wizard-view__supplement textarea { width: 100%; resize: vertical; border: 1px solid var(--color-border); border-radius: 8px; padding: 10px 12px; color: var(--color-text-primary); font: inherit; font-size: 12px; line-height: 1.6; }
.wizard-view__supplement textarea:disabled { color: var(--color-text-muted); background: var(--color-background); }
.wizard-view__supplement p { margin: 0; color: var(--color-text-secondary); font-size: 11px; }
.wizard-view__supplement div { display: flex; justify-content: flex-end; gap: 10px; }
.wizard-view__supplement button { min-height: 36px; padding: 0 13px; border-radius: 8px; font-size: 12px; }
.wizard-view__modal { position: fixed; z-index: 20; inset: 0; display: grid; place-items: center; padding: 24px; background: rgba(38, 43, 37, .32); }
.wizard-view__dialog { display: grid; gap: 12px; width: min(680px, 100%); padding: 18px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); box-shadow: var(--shadow-card); }
.wizard-view__dialog header,.wizard-view__dialog footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.wizard-view__dialog header span { color: var(--color-accent); font-size: 10px; font-weight: 750; }
.wizard-view__dialog h2 { margin: 4px 0 0; font-size: 18px; }
.wizard-view__dialog header button { border: 0; color: var(--color-text-secondary); background: transparent; cursor: pointer; }
.wizard-view__dialog textarea { width: 100%; resize: vertical; border: 1px solid var(--color-border); border-radius: 8px; padding: 10px 12px; color: var(--color-text-primary); font: inherit; font-size: 12px; line-height: 1.7; }
.wizard-view__dialog-note { margin: 0; color: var(--color-text-secondary); font-size: 11px; }
.wizard-view__dialog-error { margin: 0; color: #873f3f; font-size: 11px; }
.wizard-view__dialog footer button { min-height: 36px; padding: 0 13px; border-radius: 8px; font-size: 12px; }
</style>
