<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { routeLocationKey, useRouter } from 'vue-router'
import { createAnalysisClient, type AnalysisAnswersRequestBody, type AnalysisCallbacks } from '@/api/analysisApi'
import { analysisStateRepository, type AnalysisState, type AnalysisStateStore } from '@/db/repositories/analysisStateRepository'
import { architectureRepository } from '@/db/repositories/architectureRepository'
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
const architectureSelected = props.architectureSelected ?? ((id: string) => architectureRepository.selected(id))
const client = props.client ?? createAnalysisClient()
const modelConfig = useModelConfigStore()
const state = ref<AnalysisState | null>(null)
const architectureConfirmed = ref(false)
const busy = ref(false)
const loading = ref(true)
const errorMessage = ref('')
const completedMessage = ref('')

const pendingQuestions = computed(() => state.value?.questions.filter(question => question.status === 'PENDING') ?? [])
const currentBatch = computed(() => {
  const first = [...pendingQuestions.value].sort((a, b) => b.priority - a.priority)[0]
  return first ? pendingQuestions.value.filter(question => question.batchId === first.batchId).slice(0, 10) : []
})
const currentCoverageKeys = computed(() => activeCoverageKeys(currentBatch.value))
const originalPromptExcerpt = computed(() => {
  const text = state.value?.project.originalPrompt?.trim()
  if (!text) return ''
  return text.length > 120 ? `${text.slice(0, 120)}...` : text
})

onMounted(async () => {
  try {
    const [loadedState, selectedArchitecture] = await Promise.all([
      stateStore.load(projectId.value),
      architectureSelected(projectId.value),
    ])
    state.value = loadedState ?? null
    architectureConfirmed.value = Boolean(selectedArchitecture)
  }
  catch (error) { errorMessage.value = readableError(error) }
  finally { loading.value = false }
})
onBeforeUnmount(() => client.cancel())

async function submit(drafts: QuestionAnswerDraft[]) {
  if (!state.value || busy.value) return
  busy.value = true; errorMessage.value = ''; completedMessage.value = ''
  try {
    const persisted = await clarification.submitBatch(projectId.value, drafts)
    const questionMap = new Map(state.value.questions.map(question => [question.id, question]))
    for (const question of persisted.questions) questionMap.set(question.id, question)
    const answerMap = new Map(state.value.answers.map(answer => [answer.questionId, answer]))
    for (const answer of persisted.answers) answerMap.set(answer.questionId, answer)
    const localState: AnalysisState = { ...state.value, questions: [...questionMap.values()], answers: [...answerMap.values()] }
    state.value = localState
    const finalState = await client.submitAnswers({
      state: toServerState(localState),
      answers: toAnswerTurns(persisted.questions, persisted.answers),
      missingInformation: localState.requirements.filter(item => item.type === 'MISSING_INFORMATION').map(item => item.content),
      modelSettings: props.modelSettings ?? requestModelSettings(),
    })
    state.value = await stateStore.saveFinal(projectId.value, finalState)
    completedMessage.value = currentBatch.value.length
      ? 'AI 已根据回答更新需求，并生成下一轮追问。'
      : 'AI 已根据回答更新需求，当前没有新的追问。'
  } catch (error) { errorMessage.value = readableError(error) }
  finally { busy.value = false }
}

function toAnswerTurns(questions: ClarificationQuestion[], answers: ClarificationAnswer[]) {
  const byId = new Map(questions.map(question => [question.id, question]))
  return answers.map(answer => {
    const question = byId.get(answer.questionId)!
    const labels = question.options.filter(option => answer.selectedOptionIds.includes(option.id)).map(option => option.label)
    const text = [labels.join('、'), answer.customAnswer, answer.note].filter(Boolean).join('；') || '跳过'
    return { questionId: question.id, batchId: question.batchId, question: question.text, answer: text, answeredAt: answer.updatedAt }
  })
}

function toServerState(value: AnalysisState) {
  const { id, name, language, stage, completeness } = value.project
  return { ...value, project: { id, name, language, stage, completeness } }
}
function requestModelSettings() { return { ...modelConfig.requestKeyConfig, provider: modelConfig.provider, baseUrl: modelConfig.baseUrl || undefined, model: modelConfig.model, parameters: { temperature: modelConfig.temperature } } }
function readableError(error: unknown) { return error instanceof Error ? error.message : '提交失败，已保留本地回答。' }

function goToRequirements() {
  void router.push({ name: 'project-requirements', params: { projectId: projectId.value } })
}

function goToOverview() {
  void router.push({ name: 'project-overview', params: { projectId: projectId.value } })
}
</script>

<template>
  <main class="wizard-view" data-testid="question-wizard-view">
    <div v-if="loading" class="wizard-view__status">正在恢复问题…</div>
    <div v-else-if="!state" class="wizard-view__status" role="alert">没有找到项目分析状态。</div>
    <template v-else>
      <div v-if="errorMessage" class="wizard-view__error" role="alert">{{ errorMessage }}</div>
      <div v-if="completedMessage" class="wizard-view__success">{{ completedMessage }}</div>
      <section v-if="currentBatch.length" class="wizard-view__intro">
        <div>
          <span>AI 需求访谈</span>
          <h1>回答这些问题，PRD 会逐步变完整</h1>
          <p>AI 已读取你的初始需求，并把不明确的地方拆成这一轮问题。提交后，AI 会结合回答继续追问或整理成需求卡片。</p>
        </div>
        <p v-if="originalPromptExcerpt" class="wizard-view__source">初始想法：{{ originalPromptExcerpt }}</p>
        <p v-if="architectureConfirmed" class="wizard-view__notice" role="status">
          架构方案已确认，不需要再选择方案；这里继续补齐 PRD 所需的业务细节。
        </p>
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
      <QuestionBatch v-if="currentBatch.length" :questions="currentBatch" :busy="busy" @submit="submit" />
      <section v-else class="wizard-view__empty">
        <span>AI 需求访谈</span>
        <h1>这一轮问题已回答完</h1>
        <p>AI 已把回答沉淀到需求卡片。下一步先确认这些需求；如果还想补充想法，可以让 AI 继续追问。</p>
        <div class="wizard-view__actions">
          <button type="button" class="button-primary" @click="goToRequirements">
            查看已整理需求
          </button>
          <button type="button" class="wizard-view__secondary" @click="goToOverview">
            补充想法让 AI 继续追问
          </button>
        </div>
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
.wizard-view__error,.wizard-view__success { margin-bottom: 14px; padding: 12px 14px; border-radius: 10px; font-size: 11px; }
.wizard-view__error { color: #873f3f; background: #fff8f8; }
.wizard-view__success { color: #246b58; background: #eefaf5; }
.wizard-view__notice { padding: 10px 12px; border: 1px solid #c8e9e4; border-radius: 10px; color: #28635b; background: #f4fbfa; font-size: 11px; }
</style>
