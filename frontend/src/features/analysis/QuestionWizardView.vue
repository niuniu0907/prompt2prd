<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { routeLocationKey, useRouter } from 'vue-router'
import { createAnalysisClient, type AnalysisAnswersRequestBody, type AnalysisCallbacks } from '@/api/analysisApi'
import { isModelSetupErrorMessage, validateAnalysisModelSettings } from '@/api/modelSettingsValidation'
import { analysisStateRepository, type AnalysisState, type AnalysisStateStore } from '@/db/repositories/analysisStateRepository'
import { clarificationRepository, type ClarificationSubmitter } from '@/db/repositories/clarificationRepository'
import type { ClarificationAnswer, ClarificationQuestion } from '@/features/requirements/types'
import { useModelConfigStore } from '@/stores/modelConfigStore'
import QuestionBatch from './QuestionBatch.vue'
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
}>()
const route = inject(routeLocationKey, null)
const router = useRouter()
const projectId = computed(() => props.projectId ?? String(route?.params.projectId ?? ''))
const stateStore = props.stateStore ?? analysisStateRepository
const clarification = props.clarification ?? clarificationRepository
const client = props.client ?? createAnalysisClient()
const modelConfig = useModelConfigStore()
const state = ref<AnalysisState | null>(null)
const busy = ref(false)
const loading = ref(true)
const errorMessage = ref('')
const completedMessage = ref('')
const supplementOpen = ref(false)
const supplementalIdea = ref('')

const pendingQuestions = computed(() => state.value?.questions.filter(question => question.status === 'PENDING') ?? [])
const currentBatch = computed(() => {
  const first = [...pendingQuestions.value].sort((a, b) => b.priority - a.priority)[0]
  return first ? pendingQuestions.value.filter(question => question.batchId === first.batchId).slice(0, 1) : []
})
const currentCoverageKeys = computed(() => activeCoverageKeys(currentBatch.value))
const originalPromptExcerpt = computed(() => {
  const text = state.value?.project.originalPrompt?.trim()
  if (!text) return ''
  return text.length > 120 ? `${text.slice(0, 120)}...` : text
})
const canSubmitSupplement = computed(() => Boolean(supplementalIdea.value.trim()) && !busy.value)
const canRetrySavedAnswers = computed(() => Boolean(state.value?.answers.length) && !busy.value)
const needsModelSetup = computed(() => isModelSetupErrorMessage(errorMessage.value))

onMounted(async () => {
  try {
    const loadedState = await stateStore.load(projectId.value)
    state.value = loadedState ?? null
  }
  catch (error) { errorMessage.value = readableError(error) }
  finally { loading.value = false }
})
onBeforeUnmount(() => client.cancel())

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
    const persisted = await clarification.submitBatch(projectId.value, drafts)
    const questionMap = new Map(state.value.questions.map(question => [question.id, question]))
    for (const question of persisted.questions) questionMap.set(question.id, question)
    const answerMap = new Map(state.value.answers.map(answer => [answer.questionId, answer]))
    for (const answer of persisted.answers) answerMap.set(answer.questionId, answer)
    const localState: AnalysisState = { ...state.value, questions: [...questionMap.values()], answers: [...answerMap.values()] }
    state.value = localState
    await requestFollowUp(localState, settings, undefined, 'AI 已根据回答更新需求，并生成下一轮追问。', 'AI 已根据回答更新需求，当前没有新的追问。')
  } catch (error) { errorMessage.value = readableError(error) }
  finally { busy.value = false }
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
    await requestFollowUp(state.value, settings, supplementalInput, 'AI 已根据补充想法生成下一轮追问。', 'AI 已根据补充想法更新需求，当前没有新的追问。')
    supplementalIdea.value = ''
    supplementOpen.value = false
  } catch (error) { errorMessage.value = readableError(error) }
  finally { busy.value = false }
}

async function retrySavedAnswers() {
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
    await requestFollowUp(state.value, settings, undefined, 'AI 已重新处理保存的回答，并生成下一轮追问。', 'AI 已重新处理保存的回答，当前没有新的追问。')
  } catch (error) { errorMessage.value = readableError(error) }
  finally { busy.value = false }
}

async function requestFollowUp(
  sourceState: AnalysisState,
  settings: unknown,
  supplementalInput: string | undefined,
  nextRoundMessage: string,
  noQuestionMessage: string,
) {
  const finalState = await client.submitAnswers({
    state: toServerState(sourceState),
    answers: toAnswerTurns(sourceState.questions, sourceState.answers),
    originalInput: originalProjectInput(sourceState),
    supplementalInput,
    missingInformation: sourceState.requirements.filter(item => item.type === 'MISSING_INFORMATION').map(item => item.content),
    modelSettings: settings,
  })
  state.value = await stateStore.saveFinal(projectId.value, finalState)
  notifyAnalysisStateSaved()
  completedMessage.value = currentBatch.value.length ? nextRoundMessage : noQuestionMessage
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
  void router.push({ name: 'project-requirements', params: { projectId: projectId.value } })
}

function goToPrd() {
  void router.push({ name: 'project-prd', params: { projectId: projectId.value } })
}

function goToModelSettings() {
  void router.push({ name: 'model-settings' })
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
      <section v-if="currentBatch.length" class="wizard-view__intro">
        <div>
          <span>AI 需求访谈</span>
          <h1>先回答当前最重要的问题</h1>
          <p>AI 已读取你的初始需求，并会一次只追问一个主要缺口。提交后，页面会停留在 AI 澄清并加载下一条问题。</p>
        </div>
        <p v-if="originalPromptExcerpt" class="wizard-view__source">初始想法：{{ originalPromptExcerpt }}</p>
        <div class="wizard-view__coverage" aria-label="PRD 覆盖清单">
          <b>最终 PRD 至少覆盖</b>
          <ul>
            <li
              v-for="area in prdCoverageAreas"
              :key="area.key"
              :class="{ 'is-active': currentCoverageKeys.has(area.key) }"
            >
              {{ area.label }}
            </li>
          </ul>
        </div>
      </section>
      <QuestionBatch v-if="currentBatch.length" :questions="currentBatch" :busy="busy" @submit="submit" @generate-prd="goToPrd" />
      <section v-else class="wizard-view__empty">
        <span>AI 需求访谈</span>
        <h1>关键信息已经足够，可以生成PRD</h1>
        <p>你也可以继续补充细节。生成 PRD 不代表项目结束，后续仍可回来回答问题或修改需求结果。</p>
        <div class="wizard-view__actions">
          <button type="button" class="button-primary" @click="supplementOpen = true">
            继续澄清
          </button>
          <button type="button" class="button-primary" @click="goToPrd">
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
</style>
