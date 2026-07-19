<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { ClarificationQuestion } from '@/features/requirements/types'
import QuestionCard from './QuestionCard.vue'
import { emptyAnswer, type QuestionAnswerDraft } from './answerTypes'

const props = withDefaults(defineProps<{
  questions: ClarificationQuestion[]
  submitStatus?: string
  roundNo?: number
  progressMessage?: string
  timedOut?: boolean
}>(), { submitStatus: 'IDLE', roundNo: 1, progressMessage: '', timedOut: false })
const emit = defineEmits<{
  submit: [answers: QuestionAnswerDraft[]]
  cancel: []
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
const isIdle = computed(() => props.submitStatus === 'IDLE')
const isSaving = computed(() => props.submitStatus === 'SAVING')
const isAnalyzing = computed(() => props.submitStatus === 'ANALYZING')
const isGenerating = computed(() => props.submitStatus === 'GENERATING_NEXT_ROUND')
const canSubmit = computed(() => allAnswered.value && isIdle.value)
const canSkip = computed(() => isIdle.value)

function submit(skipAll = false) {
  if (!isIdle.value) return
  if (!props.questions.length) return
  if (skipAll) {
    emit('submit', props.questions.map(question => ({ ...emptyAnswer(question.id), skipped: true })))
    return
  }
  if (!allAnswered.value) return
  emit('submit', props.questions.map(question => ({ ...drafts[question.id]! })))
}
function adoptSuggestion() {
  if (!props.questions.length || !isIdle.value) return
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

const submitButtonLabel = computed(() => {
  if (isSaving.value) return '保存中…'
  if (isAnalyzing.value) return 'AI整理中…'
  if (isGenerating.value) return '正在生成下一轮…'
  return '提交并继续'
})
</script>

<template>
  <section class="question-batch">
    <header class="question-batch__heading">
      <div>
        <span>第 {{ roundNo }} 轮 · 已回答 {{ answeredCount }}/{{ questions.length }}</span>
        <h1>一次回答完本轮问题</h1>
      </div>
      <button data-testid="adopt-suggestion" type="button" class="question-batch__adopt" :disabled="!isIdle || !questions.length" @click="adoptSuggestion">采用AI建议</button>
    </header>
    <QuestionCard
      v-for="(question, index) in questions"
      :key="question.id"
      :question="question"
      :index="index"
      :model-value="drafts[question.id]!"
      :disabled="!isIdle"
      @update:model-value="setDraft(question.id, $event)"
    />
    <footer>
      <div class="question-batch__status">
        <span v-if="progressMessage && isAnalyzing" class="question-batch__progress">{{ progressMessage }}</span>
        <span v-if="timedOut && isAnalyzing" class="question-batch__timeout">AI整理时间较长，可以继续等待或取消后重试。</span>
      </div>
      <div class="question-batch__actions">
        <button v-if="timedOut && isAnalyzing" data-testid="cancel-analysis" type="button" class="question-batch__cancel" @click="emit('cancel')">取消</button>
        <button data-testid="skip-batch" type="button" class="question-batch__skip" :disabled="!canSkip" @click="submit(true)">跳过本轮</button>
        <button data-testid="submit-batch" class="button-primary" type="button" :disabled="!canSubmit" @click="submit(false)">{{ submitButtonLabel }}</button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.question-batch { display: grid; gap: 14px; max-width: 820px; margin: 0 auto; }
.question-batch__heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 4px; }
.question-batch__heading span { color: var(--color-accent); font-size: 10px; font-weight: 750; }
.question-batch__heading h1 { margin: 5px 0 0; font-size: 22px; }
.question-batch__adopt { min-height: 34px; padding: 0 12px; border-radius: 8px; color: var(--color-text-secondary); background: var(--color-surface); border: 1px solid var(--color-border); cursor: pointer; font-size: 12px; white-space: nowrap; }
.question-batch__adopt:disabled { color: var(--color-text-muted); cursor: not-allowed; }
.question-batch__actions { display: flex; justify-content: flex-end; gap: 12px; }
.question-batch__status { flex: 1; display: flex; align-items: center; gap: 12px; min-width: 0; }
.question-batch__progress { color: var(--color-text-secondary); font-size: 11px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.question-batch__timeout { color: #b85c1a; font-size: 11px; }
.question-batch__cancel { min-height: 34px; padding: 0 12px; border-radius: 8px; color: #873f3f; background: transparent; border: 1px solid #e2bcbc; cursor: pointer; font-size: 12px; }
footer { position: sticky; bottom: 0; display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding: 12px 0 18px; background: var(--color-background); }
.question-batch__actions button { min-height: 34px; padding: 0 12px; border-radius: 8px; cursor: pointer; }
.question-batch__skip { color: var(--color-text-secondary); background: transparent; border: 0; font-size: 12px; }
.question-batch__skip:disabled { color: var(--color-text-muted); cursor: not-allowed; }
</style>
