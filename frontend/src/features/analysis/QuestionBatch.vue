<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { ClarificationQuestion } from '@/features/requirements/types'
import QuestionCard from './QuestionCard.vue'
import { emptyAnswer, type QuestionAnswerDraft } from './answerTypes'

const props = withDefaults(defineProps<{ questions: ClarificationQuestion[]; busy?: boolean }>(), { busy: false })
const emit = defineEmits<{ submit: [answers: QuestionAnswerDraft[]] }>()
const drafts = reactive<Record<string, QuestionAnswerDraft>>({})

watch(() => props.questions, syncDrafts, { immediate: true, deep: true })
function syncDrafts() { for (const question of props.questions) drafts[question.id] ??= emptyAnswer(question.id) }
function setDraft(id: string, value: QuestionAnswerDraft) { drafts[id] = value }
function submit(skipAll = false) {
  if (props.busy) return
  emit('submit', props.questions.map(question => skipAll
    ? { ...emptyAnswer(question.id), skipped: true }
    : { ...drafts[question.id]! }))
}
</script>

<template>
  <section class="question-batch">
    <header class="question-batch__heading"><div><span>本轮共 {{ questions.length }} 题</span><h1>集中确认关键需求</h1></div><p>可以选择推荐项，也可以填写自己的答案。</p></header>
    <QuestionCard v-for="(question, index) in questions" :key="question.id" :question="question" :index="index" :model-value="drafts[question.id]!" :disabled="busy" @update:model-value="setDraft(question.id, $event)" />
    <footer><button data-testid="skip-batch" type="button" :disabled="busy" @click="submit(true)">跳过本轮</button><button data-testid="submit-batch" class="button-primary" type="button" :disabled="busy" @click="submit(false)">{{ busy ? '正在提交…' : '提交本轮回答' }}</button></footer>
  </section>
</template>

<style scoped>
.question-batch { display: grid; gap: 14px; max-width: 820px; margin: 0 auto; }
.question-batch__heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 4px; }
.question-batch__heading span { color: var(--color-accent); font-size: 10px; font-weight: 750; }
.question-batch__heading h1 { margin: 5px 0 0; font-size: 22px; }
.question-batch__heading p { color: var(--color-text-secondary); font-size: 11px; }
footer { display: flex; justify-content: flex-end; gap: 12px; padding: 8px 0; }
footer > button:first-child { padding: 0 12px; color: var(--color-text-secondary); background: transparent; cursor: pointer; }
</style>
