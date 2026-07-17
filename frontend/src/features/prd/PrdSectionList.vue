<script setup lang="ts">
import type { PrdSection } from './types'

const props = defineProps<{
  sections: PrdSection[]
  activeKey: string | null
  generating: boolean
  generatingKey: string | null
}>()

const emit = defineEmits<{
  select: [key: string]
  toggleLock: [key: string]
}>()

function statusLabel(status: string) {
  if (status === 'COMPLETED') return '已完成'
  if (status === 'GENERATING') return '生成中'
  if (status === 'FAILED') return '失败'
  return '草稿'
}

function statusClass(status: string) {
  if (status === 'COMPLETED') return 'status--completed'
  if (status === 'GENERATING') return 'status--generating'
  if (status === 'FAILED') return 'status--failed'
  return 'status--draft'
}
</script>

<template>
  <nav class="section-list" aria-label="PRD 章节">
    <div
      v-for="section in sections"
      :key="section.id"
      class="section-item"
      :class="{
        'section-item--active': activeKey === section.sectionKey,
        'section-item--locked': section.locked,
      }"
      :data-testid="`prd-section-${section.sectionKey}`"
      @click="emit('select', section.sectionKey)"
    >
      <div class="section-order">{{ section.order }}</div>
      <div class="section-info">
        <span class="section-title">{{ section.title }}</span>
        <span class="section-status" :class="statusClass(section.status)">
          {{ statusLabel(section.status) }}
        </span>
      </div>
      <button
        v-if="section.status === 'COMPLETED'"
        type="button"
        class="lock-toggle"
        :aria-label="section.locked ? '解锁' : '锁定'"
        :data-testid="`lock-${section.sectionKey}`"
        @click.stop="emit('toggleLock', section.sectionKey)"
      >
        {{ section.locked ? '🔒' : '🔓' }}
      </button>
    </div>
  </nav>
</template>

<style scoped>
.section-list {
  display: grid;
  gap: 2px;
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}
.section-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 7px;
  cursor: pointer;
  transition: background 0.15s;
}
.section-item:hover {
  background: var(--color-background);
}
.section-item--active {
  background: var(--color-background);
  font-weight: 600;
}
.section-item--locked {
  opacity: 0.75;
}
.section-order {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  background: var(--color-background);
  font-size: 10px;
  font-weight: 700;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.section-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.section-title {
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.section-status {
  font-size: 9px;
  font-weight: 600;
}
.status--draft { color: var(--color-text-secondary); }
.status--generating { color: #b8860b; }
.status--completed { color: #2e7d32; }
.status--failed { color: #c62828; }
.lock-toggle {
  padding: 2px 4px;
  border: none;
  background: none;
  font-size: 12px;
  cursor: pointer;
  flex-shrink: 0;
}
</style>
