<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { routeLocationKey, useRouter } from 'vue-router'
import { createAnalysisClient, type AnalysisAnswersRequestBody, type AnalysisCallbacks, type GenerateRoundRequestBody } from '@/api/analysisApi'
import { isModelSetupErrorMessage, validateAnalysisModelSettings } from '@/api/modelSettingsValidation'
import { analysisStateRepository, type AnalysisState, type AnalysisStateStore } from '@/db/repositories/analysisStateRepository'
import { clarificationRepository, type ClarificationSubmitter, type SubmitBatchResult } from '@/db/repositories/clarificationRepository'
import { clarificationRoundRepository } from '@/db/repositories/clarificationRoundRepository'
import { appDatabase } from '@/db/appDatabase'
import {
  projectRepository,
  type ProjectSourceRepository,
} from '@/db/repositories/projectRepository'
import type { ClarificationAnswer, ClarificationQuestion, ClarificationRound } from '@/features/requirements/types'
import { useModelConfigStore } from '@/stores/modelConfigStore'
import { useAnalysisRoundStore } from '@/stores/analysisRoundStore'
import QuestionBatch from './QuestionBatch.vue'
import QuestionSkeleton from './QuestionSkeleton.vue'
import type { QuestionAnswerDraft } from './answerTypes'
import { activeCoverageKeys, prdCoverageAreas } from './prdCoverage'

interface AnswerAnalysisRunner {
  submitAnswers(body: AnalysisAnswersRequestBody, callbacks?: AnalysisCallbacks): Promise<unknown>
  cancel(): void
}

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
const state = ref<AnalysisState | null>(null)
const busy = ref(false)
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
const canSubmitSupplement = computed(() => Boolean(supplementalIdea.value.trim()) && !busy.value)
const canRetrySavedAnswers = computed(() => Boolean(state.value?.answers.length) && !busy.value)
const needsModelSetup = computed(() => isModelSetupErrorMessage(errorMessage.value))
const hasGenerationError = computed(() => roundStore.generationError !== null)
// 首次AI分析成功后即可生成PRD；完整度和冲突只做提示，不阻塞生成
const canGeneratePrd = computed(() => Boolean(state.value)
  && (state.value.requirements.length > 0 || state.value.questions.length > 0))
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
const followUpStatusMessage = computed(() =>
  roundStore.isLoadingNextRound
    ? 'AI 正在后台生成下一轮问题，生成完成后会直接显示在当前页面。'
    : ''
)

// Auto-trigger pre-generation when round 1 is loaded and no next round exists
watch(
  [loading, currentRoundKey, busy, needsMoreClarification],
  () => { void triggerPreGenerationIfNeeded() },
  { flush: 'post' },
)

async function triggerPreGenerationIfNeeded() {
  if (loading.value || busy.value) return
  if (errorMessage.value) return
  if (!state.value || currentRoundQuestions.value.length === 0) return
  if (roundStore.hasReadyNextRound) return
  if (roundStore.generatingRoundNo !== null) return
  if (!needsMoreClarification.value && roundStore.currentRoundNo > 1) return
  await triggerPreGeneration()
}

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
    }
  }
  catch (error) { errorMessage.value = readableError(error) }
  finally {
    loading.value = false
    await nextTick()
    void triggerPreGenerationIfNeeded()
  }
})
onBeforeUnmount(() => client.cancel())

watch(currentRoundKey, async (value, oldValue) => {
  if (!value || !oldValue || value === oldValue) return
  await nextTick()
  currentBatchAnchor.value?.scrollIntoView?.({ block: 'start', behavior: 'smooth' })
}, { flush: 'post' })

async function submit(drafts: QuestionAnswerDraft[]) {
  if (!state.value || busy.value) return
  const settings = props.modelSettings ?? requestModelSettings()
  const validation = validateAnalysisModelSettings(settings)
  if (validation) {
    errorMessage.value = validation
    completedMessage.value = ''
    return
  }
  busy.value = true; errorMessage.value = ''; completedMessage.value = ''
  try {
    // 1. Persist answers to IndexedDB immediately
    const persisted = await clarification.submitBatch(projectId.value, drafts)
    const localState = mergePersistedAnswers(state.value, persisted)
    state.value = localState
    completedMessage.value = '回答已保存'

    // 2. Send answers to backend and wait for processing result
    const finalState = await client.submitAnswers({
      state: toServerState(localState),
      answers: toAnswerTurns(localState.questions, localState.answers),
      originalInput: originalProjectInput(localState),
      missingInformation: localState.requirements.filter(item => item.type === 'MISSING_INFORMATION').map(item => item.content),
      modelSettings: settings,
    })

    // 3. Save the returned state before generating next round
    const savedState = await stateStore.saveFinal(projectId.value, finalState)
    state.value = savedState
    notifyAnalysisStateSaved()

    // 4. Mark downstream rounds as stale, then trigger pre-generation with updated state
    await roundStore.markDownstreamStale(roundStore.currentRoundNo)

    if (roundStore.hasReadyNextRound) {
      roundStore.activateNextRound()
      completedMessage.value = '已切换到下一轮；AI 正在后台整理回答并准备后续问题。'
      busy.value = false
      void triggerPreGeneration()
    } else {
      completedMessage.value = '回答已保存，正在生成下一轮问题...'
      busy.value = false
      void triggerPreGeneration()
    }
  } catch (error) {
    errorMessage.value = readableError(error)
    busy.value = false
  }
}

/** Triggers background pre-generation of the next round (N+1). */
async function triggerPreGeneration() {
  if (!state.value) return
  const nextRoundNo = roundStore.currentRoundNo + 1
  const requestId = crypto.randomUUID()

  if (!roundStore.shouldStartGeneration(nextRoundNo, requestId)) {
    return
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
      roundStore.completeGeneration(nextRoundNo, questions, requestId)

      // Persist round to IndexedDB
      const round: ClarificationRound = {
        id: crypto.randomUUID(),
        projectId: projectId.value,
        roundNo: nextRoundNo,
        requestId: result.requestId,
        contextVersion: String(roundStore.contextVersion),
        questionIds: questions.map(q => q.id),
        coverageCategories: result.coverageCategories,
        status: 'READY',
        createdAt: new Date().toISOString(),
        generatedAt: new Date().toISOString(),
      }
      await clarificationRoundRepository.saveRound(round)

      // Save generated questions to clarification_question table for recovery
      await appDatabase.clarification_question.bulkAdd(questions)

      // Persist round store state (round metadata, not questions)
      await roundStore.persist(projectId.value)
    }
  } catch (error) {
    roundStore.markGenerationFailed(nextRoundNo,
      error instanceof Error ? error.message : 'Generation failed')
    console.warn('Background round pre-generation failed for round', nextRoundNo, error)
    // Don't show to user — this is background work
  }
}

async function submitSupplement() {
  if (!state.value || busy.value) return
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
  busy.value = true; errorMessage.value = ''; completedMessage.value = ''
  try {
    const finalState = await client.submitAnswers({
      state: toServerState(state.value),
      answers: toAnswerTurns(state.value.questions, state.value.answers),
      originalInput: originalProjectInput(state.value),
      supplementalInput,
      missingInformation: state.value.requirements.filter(item => item.type === 'MISSING_INFORMATION').map(item => item.content),
      modelSettings: settings,
    })

    // Save the returned state before generating new round
    const savedState = await stateStore.saveFinal(projectId.value, finalState)
    state.value = savedState
    notifyAnalysisStateSaved()

    supplementalIdea.value = ''
    supplementOpen.value = false
    completedMessage.value = 'AI 已根据补充想法更新需求。'

    // Mark downstream stale then trigger new round generation with updated context
    await roundStore.markDownstreamStale(roundStore.currentRoundNo)
    void triggerPreGeneration()
  } catch (error) { errorMessage.value = readableError(error) }
  finally { busy.value = false }
}

async function retryFailedGeneration() {
  roundStore.markGenerationFailed(roundStore.generatingRoundNo ?? 0)
  await triggerPreGeneration()
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
  if (!state.value || busy.value) return
  const settings = props.modelSettings ?? requestModelSettings()
  const validation = validateAnalysisModelSettings(settings)
  if (validation) {
    errorMessage.value = validation
    completedMessage.value = ''
    return
  }
  busy.value = true; errorMessage.value = ''; completedMessage.value = ''
  try {
    const persisted = await clarification.submitBatch(projectId.value, drafts)
    const localState = mergePersistedAnswers(state.value, persisted)
    state.value = localState
    try {
      await requestFollowUp(
        localState,
        settings,
        undefined,
        'AI 已根据回答更新需求，正在进入 PRD。',
        'AI 已根据回答更新需求，正在进入 PRD。',
      )
    } catch (error) {
      errorMessage.value = `本轮回答已保存，但 AI 整理失败：${readableError(error)}`
    }
    goToPrd()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    busy.value = false
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
        <button v-if="canRetrySavedAnswers" type="button" class="button-primary" :disabled="busy" @click="retrySavedAnswers">
          {{ busy ? '正在重新提交…' : '重新提交已保存回答' }}
        </button>
        <button v-if="needsModelSetup" type="button" class="button-primary" @click="goToModelSettings">前往模型设置</button>
      </div>
      <div v-if="completedMessage" class="wizard-view__success">{{ completedMessage }}</div>
      <div v-if="roundStore.isLoadingNextRound && !busy" class="wizard-view__notice" role="status">{{ followUpStatusMessage }}</div>
      <div v-if="hasGenerationError" class="wizard-view__error" role="alert">
        <span>{{ roundStore.generationError }}</span>
        <button type="button" class="button-primary" @click="retryFailedGeneration">重新生成</button>
      </div>
      <!-- Round header -->
      <section v-if="currentRoundQuestions.length" ref="currentBatchAnchor" class="wizard-view__intro">
        <div>
          <span>AI 需求访谈</span>
          <h1>第 {{ roundStore.currentRoundNo }} 轮 · 共 {{ currentRoundQuestions.length }} 题</h1>
          <p>AI 已读取你的初始需求，并把当前最值得确认的问题放在同一轮。每轮 8-10 题，提交后页面会切到下一轮。</p>
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
        :busy="busy"
        :can-generate-prd="canGeneratePrd"
        :round-no="roundStore.currentRoundNo"
        @submit="submit"
        @generate-prd="generatePrdFromCurrentAnswers"
      />
      <!-- Skeleton loading -->
      <QuestionSkeleton v-else-if="roundStore.isLoadingNextRound && !busy" :count="8" />
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
            :disabled="busy"
            placeholder="例如：我还想补充退款、角色权限、页面状态或异常场景。"
            data-testid="supplemental-idea"
          ></textarea>
          <p v-if="busy">AI 正在生成下一轮问题…</p>
          <div>
            <button type="button" class="wizard-view__secondary" :disabled="busy" @click="supplementOpen = false">取消</button>
            <button type="submit" class="button-primary" :disabled="!canSubmitSupplement" data-testid="submit-supplement">
              {{ busy ? '正在生成…' : '提交补充想法' }}
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
