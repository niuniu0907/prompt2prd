<script setup lang="ts">
import { computed } from 'vue'
import type { ProjectStage } from '@/features/projects/types'

const props = withDefaults(
  defineProps<{
    projectName: string
    completeness?: number
    stage?: ProjectStage
    modelName?: string
    saveStatus?: string
  }>(),
  {
    completeness: 0,
    stage: 'CLARIFYING',
    modelName: undefined,
    saveStatus: undefined,
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
</script>

<template>
  <header class="project-header">
    <div class="project-header__left">
      <h1 class="project-header__name">{{ projectName }}</h1>
      <div class="project-header__meta">
        <span class="project-header__stage">{{ stageLabel }}</span>
        <span class="project-header__completeness" data-testid="header-completeness">
          {{ completenessPercent }}
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
        @click="emit('generatePrd')"
      >
        生成 PRD
      </button>
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
}

.project-header__stage,
.project-header__completeness,
.project-header__model {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 9px;
  border-radius: 6px;
}

.project-header__stage {
  color: var(--color-on-accent);
  background: var(--color-accent);
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
</style>
