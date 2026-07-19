<script setup lang="ts">
import { computed } from 'vue'
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

const isCustomText = computed(() => props.question.inputType === 'CUSTOM_TEXT')

const displayOptions = computed(() => {
  if (isCustomText.value) return []
  if (props.question.options.length) {
    return props.question.options.map(option => ({ ...option, fallback: false }))
  }
  return fallbackOptions(props.question).map((option, index) => ({
    id: `${props.question.id}:fallback:${index}`,
    label: option.label,
    impact: option.impact,
    recommended: option.recommended,
    fallback: true,
  }))
})

function fallbackOptions(question: ClarificationQuestion) {
  const text = question.text
  if (/分类|品牌|标签|属性|颜色|规格/.test(text)) {
    return [
      { label: '基础分类即可', impact: '实现最简单，适合先做 MVP', recommended: false },
      { label: '分类 + 品牌 + 标签', impact: '便于搜索筛选，开发复杂度中等', recommended: true },
      { label: '完整属性体系（颜色/规格等）', impact: '筛选能力最完整，但数据模型更复杂', recommended: false },
      { label: '暂不确定，先按推荐方案', impact: '先推进后续分析，保留之后调整空间', recommended: false },
    ]
  }
  if (/是否|需要|要不要|有没有|能否|是否要/.test(text)) {
    return [
      { label: '需要', impact: '功能更完整，但实现和测试成本更高', recommended: true },
      { label: '不需要', impact: '范围更小，适合先快速完成核心闭环', recommended: false },
      { label: '暂不确定，先按推荐方案', impact: '先继续分析，后续仍可修改', recommended: false },
    ]
  }
  if (/期限|时间|多久|多少|几/.test(text)) {
    return [
      { label: '采用常见默认规则', impact: '便于快速推进，后续可在需求确认中细化', recommended: true },
      { label: '从简处理', impact: '减少规则分支，适合 MVP', recommended: false },
      { label: '暂不确定，先按推荐方案', impact: '先保留为可调整决策', recommended: false },
    ]
  }
  return [
    { label: '采用推荐方案', impact: '按当前产品类型选择常见做法', recommended: true },
    { label: '采用简化方案', impact: '降低开发成本，后续可扩展', recommended: false },
    { label: '暂不确定，后续补充', impact: '不会阻塞当前填写，但仍需最终确认', recommended: false },
  ]
}

function toggleOption(id: string, checked: boolean) {
  const fallback = displayOptions.value.find(option => option.id === id && option.fallback)
  if (fallback) {
    update({ selectedOptionIds: [], customAnswer: checked ? fallback.label : null })
    return
  }
  if (props.question.inputType === 'SINGLE_SELECT'
    || props.question.inputType === 'SINGLE_SELECT_CUSTOM'
    || props.question.inputType === 'CONFIRMATION') {
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
    <p v-if="!isCustomText && !question.options.length" class="answer-form__legacy" role="status">
      这是旧版本生成的无选项问题，已临时转换为可选答案；重新分析后会生成正式选项。
    </p>
    <div class="answer-form__options">
      <label v-for="option in displayOptions" :key="option.id" class="answer-option">
        <input
          :data-testid="`option-${question.semanticKey}-${option.id.slice(-2)}`"
          :type="(question.inputType === 'MULTI_SELECT' || question.inputType === 'MULTI_SELECT_CUSTOM') && !option.fallback ? 'checkbox' : 'radio'"
          :name="question.id"
          :checked="option.fallback ? modelValue.customAnswer === option.label : modelValue.selectedOptionIds.includes(option.id)"
          :disabled="disabled || modelValue.skipped"
          @change="toggleOption(option.id, ($event.target as HTMLInputElement).checked)"
        >
        <span><strong>{{ option.label }} <i v-if="option.recommended">推荐</i></strong><small>影响：{{ option.impact }}</small></span>
      </label>
    </div>

    <input
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
.answer-form__legacy { margin: 0; padding: 9px 11px; border: 1px solid #ead9a6; border-radius: 8px; color: #765313; background: #fff9e8; font-size: 10px; line-height: 1.5; }
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
