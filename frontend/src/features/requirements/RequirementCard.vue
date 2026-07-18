<script setup lang="ts">
import { computed } from 'vue'
import type { RequirementItem } from './types'
import { structuredRequirementFields } from './requirementDisplay'

const props = defineProps<{ requirement: RequirementItem }>()
defineEmits<{ lock: [requirementId: string, locked: boolean]; edit: [requirement: RequirementItem] }>()

const source: Record<string, string> = {
  INITIAL_INPUT: '初始输入',
  UPLOADED_FILE: '上传文件',
  USER_ANSWER: '用户回答',
  AI_INFERENCE: 'AI 推断',
  AI_RECOMMENDATION: 'AI 建议',
  USER_EDIT: '用户编辑',
  VERSION_RESTORE: '版本恢复',
}
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
  INFERRED: '推断',
  PENDING: '待确认',
  CONFIRMED: '已确认',
  CONFLICTED: '有冲突',
}
const structuredFields = computed(() => structuredRequirementFields(props.requirement))
</script>
<template>
  <article class="requirement-card">
    <header>
      <span>{{ typeLabels[requirement.type] ?? requirement.type }}</span>
      <b>{{ statusLabels[requirement.status] ?? requirement.status }}</b>
    </header>
    <h2>{{ requirement.title }}</h2>
    <dl v-if="structuredFields.length" class="requirement-fields">
      <template v-for="field in structuredFields" :key="field.label">
        <dt>{{ field.label }}</dt>
        <dd>{{ field.value }}</dd>
      </template>
    </dl>
    <p v-else>{{ requirement.content }}</p>
    <footer>
      <small>{{ source[requirement.sourceType] }}</small>
      <div>
        <button type="button" :disabled="requirement.locked" @click="$emit('edit', requirement)">编辑</button>
        <button
          data-testid="toggle-lock"
          type="button"
          :disabled="!requirement.locked && requirement.status !== 'CONFIRMED'"
          @click="$emit('lock', requirement.id, !requirement.locked)"
        >
          {{ requirement.locked ? '解锁' : '锁定' }}
        </button>
      </div>
    </footer>
  </article>
</template>
<style scoped>
.requirement-card{display:grid;gap:10px;padding:16px;border:1px solid var(--color-border);border-radius:12px;background:var(--color-surface)}header,footer{display:flex;align-items:center;justify-content:space-between;gap:10px}header span,header b,small{font-size:10px}header span{color:var(--color-text-secondary)}header b{color:var(--color-accent)}h2{margin:0;font-size:14px}p{margin:0;color:var(--color-text-secondary);font-size:12px;line-height:1.6}.requirement-fields{display:grid;grid-template-columns:88px minmax(0,1fr);gap:7px 12px;margin:0}.requirement-fields dt{color:var(--color-text-secondary);font-size:11px}.requirement-fields dd{min-width:0;margin:0;color:var(--color-text-primary);font-size:12px;line-height:1.45;overflow-wrap:anywhere}footer button{margin-left:7px;padding:5px 8px;border-radius:7px;cursor:pointer}
</style>
