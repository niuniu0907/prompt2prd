<script setup lang="ts">
import { computed } from 'vue'
import type { RequirementItem } from '@/features/requirements/types'

const props = defineProps<{ requirements: RequirementItem[] }>()
const goals = computed(() => props.requirements.filter(item => item.type === 'PRODUCT_GOAL'))
const roles = computed(() => props.requirements.filter(item => item.type === 'ROLE'))
const details = computed(() => props.requirements.filter(item => !['PRODUCT_GOAL', 'ROLE'].includes(item.type)))

const sourceLabels: Record<string, string> = {
  INITIAL_INPUT: '初始输入', UPLOADED_FILE: '上传文件', USER_ANSWER: '用户回答',
  AI_INFERENCE: 'AI 推断', AI_RECOMMENDATION: 'AI 建议', USER_EDIT: '用户编辑', VERSION_RESTORE: '版本恢复',
}
const statusLabels: Record<string, string> = {
  INFERRED: '推断', PENDING: '待确认', CONFIRMED: '已确认', CONFLICTED: '有冲突',
}
</script>

<template>
  <section class="summary">
    <div v-if="requirements.length === 0" class="summary__empty">需求卡片会随着分析逐步出现在这里。</div>
    <template v-else>
      <section v-if="goals.length" class="summary__group">
        <h2>产品目标</h2>
        <article v-for="item in goals" :key="item.id" class="requirement-card requirement-card--goal">
          <h3>{{ item.title }}</h3><p>{{ item.content }}</p>
          <footer><span>{{ sourceLabels[item.sourceType] }}</span><span>{{ statusLabels[item.status] }}</span></footer>
        </article>
      </section>
      <section v-if="roles.length" class="summary__group">
        <h2>角色</h2>
        <div class="summary__grid"><article v-for="item in roles" :key="item.id" class="requirement-card"><h3>{{ item.title }}</h3><p>{{ item.content }}</p><footer><span>{{ sourceLabels[item.sourceType] }}</span><span>{{ statusLabels[item.status] }}</span></footer></article></div>
      </section>
      <section v-if="details.length" class="summary__group">
        <h2>已识别需求</h2>
        <div class="summary__grid"><article v-for="item in details" :key="item.id" class="requirement-card"><h3>{{ item.title }}</h3><p>{{ item.content }}</p><footer><span>{{ sourceLabels[item.sourceType] }}</span><span>{{ statusLabels[item.status] }}</span></footer></article></div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.summary { display: grid; gap: 24px; }
.summary__empty { padding: 34px; border: 1px dashed var(--color-border-strong); border-radius: var(--radius-md); color: var(--color-text-muted); text-align: center; background: rgba(255,255,255,.45); }
.summary__group { display: grid; gap: 11px; }
.summary__group h2 { margin: 0; font-size: 13px; }
.summary__grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 11px; }
.requirement-card { padding: 16px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); }
.requirement-card--goal { border-left: 3px solid var(--color-accent); }
.requirement-card h3 { margin: 0; font-size: 12px; }
.requirement-card p { margin: 8px 0 14px; color: var(--color-text-secondary); font-size: 12px; line-height: 1.6; }
.requirement-card footer { display: flex; gap: 6px; }
.requirement-card footer span { padding: 3px 7px; border-radius: 999px; color: var(--color-text-secondary); font-size: 9px; background: var(--color-surface-muted); }
</style>
