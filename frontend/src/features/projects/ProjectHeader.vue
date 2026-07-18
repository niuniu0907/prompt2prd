<script setup lang="ts">
import { computed } from 'vue'
import type { ProjectStage } from '@/features/projects/types'

export interface ProjectProgressItem {
  label: string
  value: string
  tone?: 'done' | 'pending' | 'blocked'
}

const props = withDefaults(
  defineProps<{
    projectName: string
    completeness?: number
    stage?: ProjectStage
    modelName?: string
    saveStatus?: string
    progressItems?: ProjectProgressItem[]
    canGeneratePrd?: boolean
    generateDisabledReason?: string
  }>(),
  {
    completeness: 0,
    stage: 'CLARIFYING',
    modelName: undefined,
    saveStatus: undefined,
    progressItems: () => [],
    canGeneratePrd: false,
    generateDisabledReason: '需求、架构和冲突处理完成后才能生成 PRD',
  },
)

const emit = defineEmits<{
  generatePrd: []
}>()

const stageLabel = computed(() => {
  const map: Record<ProjectStage, string> = {
    CLARIFYING: '需求澄清',
    ARCHITECTURE: '架构建议',
    FLOWCHART: '流程图',
    PRD: 'PRD 生成',
    COMPLETED: '已完成',
  }
  return map[props.stage] ?? props.stage
})

const completenessPercent = computed(() => `${Math.round(props.completeness)}%`)

const showSaveStatus = computed(() => Boolean(props.saveStatus))

const progressItems = computed<ProjectProgressItem[]>(() => props.progressItems.length
  ? props.progressItems
  : [
      { label: '需求澄清', value: stageLabel.value, tone: props.stage === 'CLARIFYING' ? 'pending' : 'done' },
      { label: '总体进度', value: completenessPercent.value, tone: props.completeness >= 80 ? 'done' : 'pending' },
    ])
</script>

<template>
  <header class="project-header">
    <div class="project-header__left">
      <h1 class="project-header__name">{{ projectName }}</h1>
      <div class="project-header__meta">
        <span
          v-for="item in progressItems"
          :key="item.label"
          class="project-header__progress"
          :class="`project-header__progress--${item.tone ?? 'pending'}`"
        >
          <span>{{ item.label }}</span>
          <b>{{ item.value }}</b>
        </span>
        <span class="project-header__completeness" data-testid="header-completeness">
          总体进度：{{ completenessPercent }}
        </span>
        <span v-if="modelName" class="project-header__model">{{ modelName }}</span>
      </div>
    </div>

    <div class="project-header__right">
      <span
        v-if="showSaveStatus"
        class="project-header__save-status"
        data-testid="header-save-status"
      >
        {{ saveStatus }}
      </span>

      <button
        class="button-primary project-header__generate"
        type="button"
        data-testid="header-generate-prd"
        :disabled="!canGeneratePrd"
        :title="canGeneratePrd ? '进入 PRD 页面生成文档' : generateDisabledReason"
        @click="emit('generatePrd')"
      >
        生成 PRD
      </button>
      <span
        v-if="!canGeneratePrd"
        class="project-header__generate-hint"
        data-testid="header-generate-hint"
      >
        {{ generateDisabledReason }}
      </span>
    </div>
  </header>
</template>

<style scoped>
.project-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 56px;
  padding: 0 24px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.project-header__left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.project-header__name {
  margin: 0;
  font-size: 15px;
  font-weight: 680;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

.project-header__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.project-header__stage,
.project-header__completeness,
.project-header__model,
.project-header__progress {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 9px;
  border-radius: 6px;
}

.project-header__progress {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  color: var(--color-text-secondary);
  background: var(--color-surface-muted);
}

.project-header__progress span {
  color: var(--color-text-muted);
}

.project-header__progress b {
  color: var(--color-text-primary);
  font-weight: 720;
}

.project-header__progress--done {
  background: #eefaf5;
}

.project-header__progress--done b {
  color: #246b58;
}

.project-header__progress--blocked {
  background: #fff8f8;
}

.project-header__progress--blocked b {
  color: #873f3f;
}

.project-header__completeness {
  color: var(--color-text-primary);
  background: var(--color-primary);
}

.project-header__model {
  color: var(--color-text-secondary);
  background: var(--color-surface-muted);
}

.project-header__right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 360px;
}

.project-header__save-status {
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 540;
}

.project-header__generate {
  min-height: 36px;
  font-size: 13px;
  padding: 0 16px;
  border-radius: 9px;
}

.project-header__generate:disabled {
  color: var(--color-text-muted);
  background: var(--color-surface-muted);
  cursor: not-allowed;
  box-shadow: none;
}

.project-header__generate-hint {
  flex-basis: 100%;
  color: var(--color-text-secondary);
  font-size: 11px;
  text-align: right;
}
</style>
