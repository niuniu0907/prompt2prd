<script setup lang="ts">
import type { RequirementItem } from './types'
import { requirementSummary } from './requirementDisplay'

const props = defineProps<{ requirement: RequirementItem }>()
defineEmits<{
  view: [requirement: RequirementItem]
  confirm: [requirement: RequirementItem]
}>()

const typeLabels: Record<string, string> = {
  PRODUCT_GOAL: '产品目标',
  ROLE: '用户角色',
  FEATURE: '功能需求',
  USER_STORY: '用户故事',
  BUSINESS_RULE: '业务规则',
  EXCEPTION_SCENARIO: '异常场景',
  TECHNICAL_CONSTRAINT: '技术约束',
  DATA_MODEL: '数据模型',
  ACCEPTANCE_CRITERION: '验收标准',
  PAGE: '页面需求',
  API: '接口需求',
  IMPLEMENTATION_PHASE: '实施阶段',
  CODING_AGENT_CONSTRAINT: 'AI 编程约束',
  NON_FUNCTIONAL_REQUIREMENT: '非功能需求',
  ASSUMPTION: 'AI 假设',
  RISK_OPEN_ITEM: '风险与待确认项',
  MISSING_INFORMATION: '待补充信息',
}

const statusLabels: Record<string, string> = {
  UNANALYZED: '未分析',
  INFERRED: '待确认',
  PENDING: '待确认',
  CONFIRMED: '已确认',
  SKIPPED: '已跳过',
  NOT_APPLICABLE: '不适用',
  CONFLICTED: '存在冲突',
}

const sourceLabels: Record<string, string> = {
  INITIAL_INPUT: '用户输入',
  UPLOADED_FILE: '导入文档',
  USER_ANSWER: '用户回答',
  AI_INFERENCE: 'AI 推断',
  AI_RECOMMENDATION: 'AI 建议',
  USER_EDIT: '用户编辑',
  VERSION_RESTORE: '版本恢复',
}

const statusClass = (status: string) => {
  if (status === 'CONFIRMED') return 'status--confirmed'
  if (status === 'CONFLICTED') return 'status--conflicted'
  if (status === 'SKIPPED' || status === 'NOT_APPLICABLE') return 'status--muted'
  return 'status--pending'
}

const summary = () => requirementSummary(props.requirement.content)
const canConfirm = () => !props.requirement.locked && props.requirement.status !== 'CONFIRMED' && props.requirement.status !== 'SKIPPED'
</script>
<template>
  <article class="requirement-list-item">
    <div class="requirement-list-item__main">
      <div class="requirement-list-item__meta">
        <span class="requirement-list-item__type">{{ typeLabels[requirement.type] ?? requirement.type }}</span>
        <span class="requirement-list-item__status" :class="statusClass(requirement.status)">
          {{ statusLabels[requirement.status] ?? requirement.status }}
        </span>
      </div>
      <h4 class="requirement-list-item__title">{{ requirement.title }}</h4>
      <p class="requirement-list-item__summary">{{ summary() }}</p>
    </div>
    <div class="requirement-list-item__actions">
      <span class="requirement-list-item__source">{{ sourceLabels[requirement.sourceType] }}</span>
      <div class="requirement-list-item__buttons">
        <button type="button" class="btn-text" @click="$emit('view', requirement)">查看</button>
        <button
          v-if="canConfirm()"
          type="button"
          class="btn-secondary"
          @click="$emit('confirm', requirement)"
        >
          确认
        </button>
      </div>
    </div>
  </article>
</template>
<style scoped>
.requirement-list-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  background: var(--color-surface);
  transition: background 120ms ease;
}
.requirement-list-item:hover {
  background: var(--color-surface-muted);
}
.requirement-list-item__main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 4px;
}
.requirement-list-item__meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.requirement-list-item__type {
  font-size: 10px;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.requirement-list-item__status {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 999px;
}
.status--confirmed {
  color: #246b58;
  background: #eefaf5;
}
.status--pending {
  color: #765313;
  background: #fff9e8;
}
.status--conflicted {
  color: #873f3f;
  background: #fff8f8;
}
.status--muted {
  color: var(--color-text-muted);
  background: var(--color-surface-muted);
}
.requirement-list-item__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.requirement-list-item__summary {
  margin: 0;
  font-size: 11px;
  color: var(--color-text-secondary);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.requirement-list-item__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}
.requirement-list-item__source {
  font-size: 10px;
  color: var(--color-text-muted);
}
.requirement-list-item__buttons {
  display: flex;
  gap: 6px;
}
.btn-text {
  padding: 3px 7px;
  font-size: 11px;
  color: var(--color-accent);
  background: transparent;
  border: none;
  cursor: pointer;
  border-radius: 5px;
}
.btn-text:hover {
  background: var(--color-accent-soft);
}
.btn-secondary {
  padding: 3px 7px;
  font-size: 11px;
  color: var(--color-accent);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: 5px;
  cursor: pointer;
}
.btn-secondary:hover {
  background: var(--color-accent-soft);
  border-color: var(--color-accent);
}
</style>
