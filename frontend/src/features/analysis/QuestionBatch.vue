<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { ClarificationQuestion } from '@/features/requirements/types'
import QuestionCard from './QuestionCard.vue'
import { emptyAnswer, type QuestionAnswerDraft } from './answerTypes'

const props = withDefaults(defineProps<{
  questions: ClarificationQuestion[]
  busy?: boolean
  canGeneratePrd?: boolean
  roundNo?: number
}>(), { busy: false, canGeneratePrd: false, roundNo: 1 })
const emit = defineEmits<{
  submit: [answers: QuestionAnswerDraft[]]
  generatePrd: [answers: QuestionAnswerDraft[]]
}>()
const drafts = reactive<Record<string, QuestionAnswerDraft>>({})

watch(() => props.questions, syncDrafts, { immediate: true, deep: true })
function syncDrafts() { for (const question of props.questions) drafts[question.id] ??= emptyAnswer(question.id) }
function setDraft(id: string, value: QuestionAnswerDraft) { drafts[id] = value }
function answered(draft: QuestionAnswerDraft | undefined) {
  return Boolean(draft?.skipped || draft?.selectedOptionIds.length || draft?.customAnswer?.trim())
}
const answeredCount = computed(() => props.questions.filter(question => answered(drafts[question.id])).length)
const allAnswered = computed(() => props.questions.length > 0 && answeredCount.value === props.questions.length)
const canSubmit = computed(() => allAnswered.value && !props.busy)
const canGeneratePrd = computed(() => props.canGeneratePrd && !props.busy && (answeredCount.value === 0 || allAnswered.value))
function submit(skipAll = false) {
  if (props.busy) return
  if (!props.questions.length) return
  if (skipAll) {
    emit('submit', props.questions.map(question => ({ ...emptyAnswer(question.id), skipped: true })))
    return
  }
  if (!allAnswered.value) return
  emit('submit', props.questions.map(question => ({ ...drafts[question.id]! })))
}
function generatePrd() {
  if (!canGeneratePrd.value) return
  const answers = allAnswered.value
    ? props.questions.map(question => ({ ...drafts[question.id]! }))
    : []
  emit('generatePrd', answers)
}
function adoptSuggestion() {
  if (!props.questions.length || props.busy) return
  for (const question of props.questions) {
    const selected = question.options.filter(option => option.recommended)
    const fallback = selected.length ? selected : question.options.slice(0, 1)
    const draft = drafts[question.id] ?? emptyAnswer(question.id)
    drafts[question.id] = {
      ...draft,
      selectedOptionIds: fallback.map(option => option.id),
      customAnswer: fallback.length ? draft.customAnswer : '采用 AI 推荐方案',
      skipped: false,
    }
  }
}
</script>

<template>
  <section class="question-batch">
    <header class="question-batch__heading">
      <div>
        <span>第 {{ roundNo }} 轮 · 已回答 {{ answeredCount }}/{{ questions.length }}</span>
        <h1>一次回答完本轮问题</h1>
      </div>
      <p>可以选择选项、填写补充、采用 AI 建议或跳过本轮。每轮 8-10 题。</p>
    </header>
    <QuestionCard
      v-for="(question, index) in questions"
      :key="question.id"
      :question="question"
      :index="index"
      :model-value="drafts[question.id]!"
      :disabled="busy"
      @update:model-value="setDraft(question.id, $event)"
    />
    <footer>
      <p v-if="answeredCount > 0 && !allAnswered">请回答或跳过本轮全部问题，再提交给 AI 继续追问。</p>
      <p v-else-if="!allAnswered">请回答或跳过本轮全部问题，未提交前 AI 建议不会变成已确认需求。</p>
      <p v-else>本轮问题已准备好，将一次提交给 AI 整理。</p>
      <div class="question-batch__actions">
        <button data-testid="skip-batch" type="button" :disabled="busy" @click="submit(true)">跳过本轮</button>
        <button data-testid="adopt-suggestion" type="button" :disabled="busy || !questions.length" @click="adoptSuggestion">采用AI建议</button>
        <button data-testid="submit-batch" class="button-primary" type="button" :disabled="!canSubmit" @click="submit(false)">{{ busy ? '正在提交…' : '提交并继续' }}</button>
        <button v-if="props.canGeneratePrd" data-testid="generate-prd-now" type="button" :disabled="!canGeneratePrd" @click="generatePrd">
          {{ allAnswered ? '提交并生成PRD' : '生成PRD' }}
        </button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.question-batch { display: grid; gap: 14px; max-width: 820px; margin: 0 auto; }
.question-batch__heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 4px; }
.question-batch__heading span { color: var(--color-accent); font-size: 10px; font-weight: 750; }
.question-batch__heading h1 { margin: 5px 0 0; font-size: 22px; }
.question-batch__heading p { color: var(--color-text-secondary); font-size: 11px; }
.question-batch__actions { display: flex; justify-content: flex-end; gap: 12px; }
footer { position: sticky; bottom: 0; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0 18px; background: var(--color-background); }
footer p { margin: 0; color: var(--color-text-secondary); font-size: 11px; }
.question-batch__actions button { min-height: 34px; padding: 0 12px; border-radius: 8px; color: var(--color-text-secondary); background: var(--color-surface); border: 1px solid var(--color-border); cursor: pointer; }
.question-batch__actions button:disabled { color: var(--color-text-muted); cursor: not-allowed; }
</style>
