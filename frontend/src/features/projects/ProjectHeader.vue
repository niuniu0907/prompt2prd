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
    canGeneratePrd?: boolean
    generateHint?: string
  }>(),
  {
    completeness: 0,
    stage: 'CLARIFYING',
    modelName: undefined,
    saveStatus: undefined,
    canGeneratePrd: false,
    generateHint: '首次 AI 解析完成后会进入 AI 澄清。',
  },
)

const emit = defineEmits<{
  generatePrd: []
}>()

const showSaveStatus = computed(() => Boolean(props.saveStatus))
</script>

<template>
  <header class="project-header">
    <div class="project-header__left">
      <h1 class="project-header__name">{{ projectName }}</h1>
      <div class="project-header__meta">
        <span class="project-header__completeness">
          需求完整度：<b>{{ completeness }}%</b>
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
        :title="canGeneratePrd ? '进入 PRD 页面生成当前版本文档' : generateHint"
        @click="emit('generatePrd')"
      >
        生成 PRD
      </button>
      <span
        class="project-header__generate-hint"
        data-testid="header-generate-hint"
      >
        {{ generateHint }}
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

.project-header__model,
.project-header__completeness {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 9px;
  border-radius: 6px;
}

.project-header__completeness {
  color: var(--color-text-primary);
  background: #effbd4;
}

.project-header__completeness b {
  font-weight: 760;
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
