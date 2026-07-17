<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { routeLocationKey } from 'vue-router'
import { createAnalysisClient, type AnalysisAnswersRequestBody, type AnalysisCallbacks } from '@/api/analysisApi'
import { analysisStateRepository, type AnalysisState, type AnalysisStateStore } from '@/db/repositories/analysisStateRepository'
import { clarificationRepository, type ClarificationSubmitter } from '@/db/repositories/clarificationRepository'
import type { ClarificationAnswer, ClarificationQuestion } from '@/features/requirements/types'
import { useModelConfigStore } from '@/stores/modelConfigStore'
import QuestionBatch from './QuestionBatch.vue'
import type { QuestionAnswerDraft } from './answerTypes'

interface AnswerAnalysisRunner {
  submitAnswers(body: AnalysisAnswersRequestBody, callbacks?: AnalysisCallbacks): Promise<unknown>
  cancel(): void
}

const props = defineProps<{
  projectId?: string
  stateStore?: AnalysisStateStore
  clarification?: ClarificationSubmitter
  client?: AnswerAnalysisRunner
  modelSettings?: unknown
}>()
const route = inject(routeLocationKey, null)
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

const pendingQuestions = computed(() => state.value?.questions.filter(question => question.status === 'PENDING') ?? [])
const currentBatch = computed(() => {
  const first = [...pendingQuestions.value].sort((a, b) => b.priority - a.priority)[0]
  return first ? pendingQuestions.value.filter(question => question.batchId === first.batchId).slice(0, 10) : []
})

onMounted(async () => {
  try { state.value = await stateStore.load(projectId.value) ?? null }
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
    completedMessage.value = currentBatch.value.length ? '已生成下一轮问题' : '本轮问题已完成'
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
</script>

<template>
  <main class="wizard-view" data-testid="question-wizard-view">
    <div v-if="loading" class="wizard-view__status">正在恢复问题…</div>
    <div v-else-if="!state" class="wizard-view__status" role="alert">没有找到项目分析状态。</div>
    <template v-else>
      <div v-if="errorMessage" class="wizard-view__error" role="alert">{{ errorMessage }}</div>
      <div v-if="completedMessage" class="wizard-view__success">{{ completedMessage }}</div>
      <QuestionBatch v-if="currentBatch.length" :questions="currentBatch" :busy="busy" @submit="submit" />
      <section v-else class="wizard-view__empty"><h1>本轮问题已完成</h1><p>当前没有待回答问题，可以查看需求卡片或继续后续阶段。</p></section>
    </template>
  </main>
</template>

<style scoped>
.wizard-view { max-width: 900px; margin: 0 auto; }
.wizard-view__status,.wizard-view__empty { padding: 46px; color: var(--color-text-secondary); text-align: center; }
.wizard-view__empty h1 { color: var(--color-text-primary); }
.wizard-view__error,.wizard-view__success { margin-bottom: 14px; padding: 12px 14px; border-radius: 10px; font-size: 11px; }
.wizard-view__error { color: #873f3f; background: #fff8f8; }
.wizard-view__success { color: #246b58; background: #eefaf5; }
</style>
