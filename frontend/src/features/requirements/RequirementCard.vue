<script setup lang="ts">
import type { RequirementItem } from './types'
defineProps<{ requirement: RequirementItem }>()
defineEmits<{ lock: [requirementId: string, locked: boolean]; edit: [requirement: RequirementItem] }>()
const source: Record<string,string> = { INITIAL_INPUT:'初始输入',UPLOADED_FILE:'上传文件',USER_ANSWER:'用户回答',AI_INFERENCE:'AI 推断',AI_RECOMMENDATION:'AI 建议',USER_EDIT:'用户编辑',VERSION_RESTORE:'版本恢复' }
</script>
<template>
  <article class="requirement-card"><header><span>{{ requirement.type }}</span><b>{{ requirement.status }}</b></header><h2>{{ requirement.title }}</h2><p>{{ requirement.content }}</p><footer><small>{{ source[requirement.sourceType] }}</small><div><button type="button" :disabled="requirement.locked" @click="$emit('edit', requirement)">编辑</button><button data-testid="toggle-lock" type="button" :disabled="!requirement.locked && requirement.status !== 'CONFIRMED'" @click="$emit('lock', requirement.id, !requirement.locked)">{{ requirement.locked ? '解锁' : '锁定' }}</button></div></footer></article>
</template>
<style scoped>
.requirement-card{padding:16px;border:1px solid var(--color-border);border-radius:12px;background:var(--color-surface)}header,footer{display:flex;align-items:center;justify-content:space-between;gap:10px}header span,header b,small{font-size:9px}header b{color:var(--color-accent)}h2{margin:10px 0 5px;font-size:13px}p{color:var(--color-text-secondary);font-size:11px;line-height:1.6}footer button{margin-left:7px;padding:5px 8px;border-radius:7px;cursor:pointer}
</style>
