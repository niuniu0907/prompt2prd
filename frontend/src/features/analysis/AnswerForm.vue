<script setup lang="ts">
import type { ClarificationQuestion } from '@/features/requirements/types'
import type { QuestionAnswerDraft } from './answerTypes'

const props = defineProps<{
  question: ClarificationQuestion
  modelValue: QuestionAnswerDraft
  disabled?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: QuestionAnswerDraft]
}>()

function update(changes: Partial<QuestionAnswerDraft>) {
  emit('update:modelValue', { ...props.modelValue, ...changes, skipped: changes.skipped ?? false })
}

function toggleOption(id: string, checked: boolean) {
  if (props.question.inputType === 'SINGLE_SELECT' || props.question.inputType === 'CONFIRMATION') {
    update({ selectedOptionIds: checked ? [id] : [] })
    return
  }
  const selected = new Set(props.modelValue.selectedOptionIds)
  if (checked) selected.add(id); else selected.delete(id)
  update({ selectedOptionIds: [...selected] })
}
</script>

<template>
  <div class="answer-form">
    <div v-if="question.options.length" class="answer-form__options">
      <label v-for="option in question.options" :key="option.id" class="answer-option">
        <input
          :data-testid="`option-${question.semanticKey}-${option.id.slice(-2)}`"
          :type="question.inputType === 'MULTI_SELECT' ? 'checkbox' : 'radio'"
          :name="question.id"
          :checked="modelValue.selectedOptionIds.includes(option.id)"
          :disabled="disabled || modelValue.skipped"
          @change="toggleOption(option.id, ($event.target as HTMLInputElement).checked)"
        >
        <span><strong>{{ option.label }} <i v-if="option.recommended">推荐</i></strong><small>影响：{{ option.impact }}</small></span>
      </label>
    </div>

    <textarea
      v-if="question.inputType === 'TEXT'"
      :data-testid="`text-${question.semanticKey}`"
      :value="modelValue.customAnswer ?? ''"
      :disabled="disabled || modelValue.skipped"
      placeholder="输入你的回答"
      @input="update({ customAnswer: ($event.target as HTMLTextAreaElement).value || null })"
    />
    <input
      v-else
      :data-testid="`custom-${question.semanticKey}`"
      :value="modelValue.customAnswer ?? ''"
      :disabled="disabled || modelValue.skipped"
      placeholder="自定义答案（可选）"
      @input="update({ customAnswer: ($event.target as HTMLInputElement).value || null })"
    >
    <input
      :data-testid="`note-${question.semanticKey}`"
      :value="modelValue.note ?? ''"
      :disabled="disabled || modelValue.skipped"
      placeholder="补充说明（可选）"
      @input="update({ note: ($event.target as HTMLInputElement).value || null })"
    >
    <button :data-testid="`skip-${question.semanticKey}`" type="button" :disabled="disabled" @click="update({ selectedOptionIds: [], customAnswer: null, note: null, skipped: !modelValue.skipped })">
      {{ modelValue.skipped ? '已跳过（点击恢复）' : '跳过此题' }}
    </button>
  </div>
</template>

<style scoped>
.answer-form { display: grid; gap: 10px; }
.answer-form__options { display: grid; gap: 8px; }
.answer-option { display: flex; gap: 9px; padding: 10px; border: 1px solid var(--color-border); border-radius: 9px; cursor: pointer; }
.answer-option span { display: grid; gap: 3px; }
.answer-option strong { font-size: 12px; }
.answer-option i { color: var(--color-accent); font-size: 9px; font-style: normal; }
.answer-option small { color: var(--color-text-secondary); font-size: 10px; }
input[type='text'], input:not([type]), textarea { width: 100%; padding: 10px 11px; border: 1px solid var(--color-border); border-radius: 9px; background: var(--color-surface); }
textarea { min-height: 72px; resize: vertical; }
button { justify-self: start; padding: 0; color: var(--color-text-secondary); font-size: 10px; background: transparent; cursor: pointer; }
</style>
