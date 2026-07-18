<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { ClarificationQuestion } from '@/features/requirements/types'
import QuestionCard from './QuestionCard.vue'
import { emptyAnswer, type QuestionAnswerDraft } from './answerTypes'

const props = withDefaults(defineProps<{ questions: ClarificationQuestion[]; busy?: boolean }>(), { busy: false })
const emit = defineEmits<{
  submit: [answers: QuestionAnswerDraft[]]
  generatePrd: []
}>()
const drafts = reactive<Record<string, QuestionAnswerDraft>>({})

watch(() => props.questions, syncDrafts, { immediate: true, deep: true })
function syncDrafts() { for (const question of props.questions) drafts[question.id] ??= emptyAnswer(question.id) }
function setDraft(id: string, value: QuestionAnswerDraft) { drafts[id] = value }
function answered(draft: QuestionAnswerDraft | undefined) {
  return Boolean(draft?.skipped || draft?.selectedOptionIds.length || draft?.customAnswer?.trim() || draft?.note?.trim())
}
const currentQuestion = computed(() => props.questions[0] ?? null)
const answeredCount = computed(() => currentQuestion.value && answered(drafts[currentQuestion.value.id]) ? 1 : 0)
const canSubmit = computed(() => answeredCount.value > 0 && !props.busy)
const recommendedOptions = computed(() => currentQuestion.value?.options.filter(option => option.recommended) ?? [])
function submit(skipAll = false) {
  if (props.busy) return
  if (!skipAll && !answeredCount.value) return
  const question = currentQuestion.value
  if (!question) return
  if (skipAll) {
    emit('submit', [{ ...emptyAnswer(question.id), skipped: true }])
    return
  }
  const draft = drafts[question.id]
  emit('submit', answered(draft) ? [{ ...draft! }] : [])
}
function adoptSuggestion() {
  const question = currentQuestion.value
  if (!question || props.busy) return
  const selected = recommendedOptions.value.length ? recommendedOptions.value : question.options.slice(0, 1)
  const draft = drafts[question.id] ?? emptyAnswer(question.id)
  drafts[question.id] = {
    ...draft,
    selectedOptionIds: selected.map(option => option.id),
    customAnswer: selected.length ? draft.customAnswer : '采用 AI 推荐方案',
    skipped: false,
  }
}
</script>

<template>
  <section class="question-batch">
    <header class="question-batch__heading">
      <div>
        <span>当前问题 1/{{ questions.length }}</span>
        <h1>先确认这一条最重要的问题</h1>
      </div>
      <p>可以选择选项、填写补充、采用 AI 建议、跳过，或先生成当前 PRD 草稿。</p>
    </header>
    <QuestionCard
      v-if="currentQuestion"
      :key="currentQuestion.id"
      :question="currentQuestion"
      :index="0"
      :model-value="drafts[currentQuestion.id]!"
      :disabled="busy"
      @update:model-value="setDraft(currentQuestion.id, $event)"
    />
    <footer>
      <p v-if="!answeredCount">未确认前，AI 建议只作为建议，不会变成已确认需求。</p>
      <div>
        <button data-testid="skip-batch" type="button" :disabled="busy" @click="submit(true)">跳过</button>
        <button data-testid="adopt-suggestion" type="button" :disabled="busy || !currentQuestion" @click="adoptSuggestion">采用AI建议</button>
        <button data-testid="submit-batch" class="button-primary" type="button" :disabled="!canSubmit" @click="submit(false)">{{ busy ? 'AI 正在整理…' : '提交并继续' }}</button>
        <button data-testid="generate-prd-now" type="button" :disabled="busy" @click="emit('generatePrd')">暂时生成PRD</button>
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
footer { position: sticky; bottom: 0; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0 18px; background: var(--color-background); }
footer p { margin: 0; color: var(--color-text-secondary); font-size: 11px; }
footer div { display: flex; justify-content: flex-end; gap: 12px; }
footer button { min-height: 34px; padding: 0 12px; border-radius: 8px; color: var(--color-text-secondary); background: var(--color-surface); border: 1px solid var(--color-border); cursor: pointer; }
footer button:disabled { color: var(--color-text-muted); cursor: not-allowed; }
</style>
