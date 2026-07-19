<script setup lang="ts">
import type { ClarificationQuestion } from '@/features/requirements/types'
import AnswerForm from './AnswerForm.vue'
import type { QuestionAnswerDraft } from './answerTypes'

defineProps<{ question: ClarificationQuestion; modelValue: QuestionAnswerDraft; disabled?: boolean; index: number }>()
defineEmits<{ 'update:modelValue': [value: QuestionAnswerDraft] }>()
</script>

<template>
  <article class="question-card">
    <header>
      <span>问题 {{ index + 1 }}</span>
      <h2>{{ question.text }}</h2>
      <details v-if="question.reason" class="question-card__reason">
        <summary>为什么问这个？</summary>
        <p>{{ question.reason }}</p>
      </details>
    </header>
    <AnswerForm :question="question" :model-value="modelValue" :disabled="disabled" @update:model-value="$emit('update:modelValue', $event)" />
  </article>
</template>

<style scoped>
.question-card { display: grid; gap: 16px; padding: 19px; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface); }
header span { color: var(--color-accent); font-size: 9px; font-weight: 750; }
h2 { margin: 5px 0; font-size: 15px; }
.question-card__reason { margin-top: 6px; }
.question-card__reason summary { color: var(--color-text-secondary); font-size: 11px; cursor: pointer; }
.question-card__reason p { margin: 6px 0 0; color: var(--color-text-secondary); font-size: 11px; }
</style>
