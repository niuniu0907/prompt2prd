<script setup lang="ts">
import { computed, ref } from 'vue'

import type {
  ProjectListFilter,
  ProjectSummary,
} from '@/db/repositories/projectRepository'
import type { ProjectStage } from './types'

const props = withDefaults(
  defineProps<{
    summary: ProjectSummary
    view: ProjectListFilter
    busy?: boolean
  }>(),
  { busy: false },
)

const emit = defineEmits<{
  open: [projectId: string]
  rename: [projectId: string, name: string]
  copy: [projectId: string]
  archive: [projectId: string]
  trash: [projectId: string]
  restore: [projectId: string]
  permanentDelete: [projectId: string]
}>()

const stageLabels: Record<ProjectStage, string> = {
  CLARIFYING: '需求澄清',
  ARCHITECTURE: '架构确认',
  FLOWCHART: '流程设计',
  PRD: 'PRD 生成',
  COMPLETED: '已完成',
}

const isRenaming = ref(false)
const renameValue = ref(props.summary.project.name)
const stageLabel = computed(() => stageLabels[props.summary.project.stage])
const canOpen = computed(() => props.view !== 'DELETED' && !props.busy && !isRenaming.value)
const updatedLabel = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(props.summary.project.updatedAt)),
)

function startRename() {
  renameValue.value = props.summary.project.name
  isRenaming.value = true
}

function submitRename() {
  const name = renameValue.value.trim()
  if (!name) return
  emit('rename', props.summary.project.id, name)
  isRenaming.value = false
}

function requestPermanentDelete() {
  const confirmed = window.confirm(
    `永久删除“${props.summary.project.name}”？此操作无法撤销。`,
  )
  if (confirmed) emit('permanentDelete', props.summary.project.id)
}

function isCardControl(target: EventTarget | null) {
  return target instanceof Element && Boolean(
    target.closest('.project-menu, .rename-form, button, input, textarea, select, a'),
  )
}

function openProject(event: MouseEvent) {
  if (!canOpen.value || isCardControl(event.target)) return
  emit('open', props.summary.project.id)
}

function openProjectFromKeyboard(event: KeyboardEvent) {
  if (!canOpen.value || isCardControl(event.target)) return
  emit('open', props.summary.project.id)
}
</script>

<template>
  <article
    class="project-card"
    :class="{ 'project-card--openable': canOpen }"
    :aria-busy="busy"
    :data-project-id="summary.project.id"
    :role="canOpen ? 'link' : undefined"
    :tabindex="canOpen ? 0 : undefined"
    @click="openProject"
    @keydown.enter.prevent="openProjectFromKeyboard"
    @keydown.space.prevent="openProjectFromKeyboard"
  >
    <div class="project-card__topline">
      <span class="stage-badge">{{ stageLabel }}</span>
      <details class="project-menu">
        <summary :aria-label="`打开“${summary.project.name}”项目操作`">
          <span aria-hidden="true">•••</span>
        </summary>
        <div class="project-menu__panel">
          <button
            v-if="view === 'ACTIVE'"
            type="button"
            data-action="rename"
            :disabled="busy"
            @click="startRename"
          >
            重命名
          </button>
          <button
            v-if="view === 'ACTIVE'"
            type="button"
            data-action="copy"
            :disabled="busy"
            @click="emit('copy', summary.project.id)"
          >
            复制项目
          </button>
          <button
            v-if="view === 'ACTIVE'"
            type="button"
            data-action="archive"
            :disabled="busy"
            @click="emit('archive', summary.project.id)"
          >
            归档项目
          </button>
          <button
            v-if="view !== 'DELETED'"
            type="button"
            data-action="trash"
            :disabled="busy"
            @click="emit('trash', summary.project.id)"
          >
            移入回收站
          </button>
          <button
            v-if="view !== 'ACTIVE'"
            type="button"
            data-action="restore"
            :disabled="busy"
            @click="emit('restore', summary.project.id)"
          >
            恢复项目
          </button>
          <button
            v-if="view === 'DELETED'"
            class="project-menu__danger"
            type="button"
            data-action="permanent-delete"
            :disabled="busy"
            @click="requestPermanentDelete"
          >
            永久删除
          </button>
        </div>
      </details>
    </div>

    <form v-if="isRenaming" class="rename-form" @submit.prevent="submitRename">
      <label :for="`rename-${summary.project.id}`">项目名称</label>
      <div>
        <input
          :id="`rename-${summary.project.id}`"
          v-model="renameValue"
          maxlength="80"
          autocomplete="off"
          :disabled="busy"
        />
        <button type="submit" :disabled="busy || !renameValue.trim()">保存</button>
        <button type="button" :disabled="busy" @click="isRenaming = false">取消</button>
      </div>
    </form>
    <h3 v-else>{{ summary.project.name }}</h3>

    <div class="project-card__progress-row">
      <span>需求完整度</span>
      <strong>{{ summary.project.completeness }}%</strong>
    </div>
    <progress :value="summary.project.completeness" max="100">
      {{ summary.project.completeness }}%
    </progress>

    <footer>
      <span :class="['pending-count', { 'pending-count--clear': summary.pendingCount === 0 }]">
        {{ summary.pendingCount === 0 ? '暂无待确认' : `${summary.pendingCount} 项待确认` }}
      </span>
      <time :datetime="summary.project.updatedAt">更新于 {{ updatedLabel }}</time>
    </footer>
  </article>
</template>

<style scoped>
.project-card {
  position: relative;
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: 0 1px 2px rgba(25, 39, 42, 0.04);
  transition: border-color 150ms ease, box-shadow 150ms ease, transform 150ms ease;
}

.project-card:hover,
.project-card:focus-within {
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-card);
  transform: translateY(-1px);
}

.project-card--openable {
  cursor: pointer;
}

.project-card--openable:focus-visible {
  outline: 3px solid rgba(36, 157, 165, 0.32);
  outline-offset: 3px;
}

.project-card[aria-busy="true"] {
  opacity: 0.68;
}

.project-card__topline,
.project-card__progress-row,
.project-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stage-badge {
  padding: 5px 8px;
  border: 1px solid #cfe1e3;
  border-radius: 999px;
  color: #226f75;
  font-size: 11px;
  font-weight: 680;
  background: var(--color-accent-soft);
}

.project-menu {
  position: relative;
}

.project-menu summary {
  display: grid;
  width: 32px;
  height: 30px;
  border-radius: 8px;
  color: var(--color-text-secondary);
  font-size: 12px;
  letter-spacing: 1px;
  list-style: none;
  cursor: pointer;
  place-items: center;
}

.project-menu summary::-webkit-details-marker {
  display: none;
}

.project-menu summary:hover,
.project-menu[open] summary {
  color: var(--color-text-primary);
  background: var(--color-surface-muted);
}

.project-menu__panel {
  position: absolute;
  z-index: 3;
  top: 34px;
  right: 0;
  display: grid;
  min-width: 154px;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: 11px;
  background: var(--color-surface);
  box-shadow: 0 12px 30px rgba(25, 39, 42, 0.14);
}

.project-menu__panel button {
  min-height: 34px;
  padding: 0 10px;
  border-radius: 7px;
  color: var(--color-text-secondary);
  font-size: 12px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.project-menu__panel button:hover:not(:disabled) {
  color: var(--color-text-primary);
  background: var(--color-surface-muted);
}

.project-menu__panel .project-menu__danger {
  color: #a13f3f;
}

.project-menu__panel button:disabled {
  color: var(--color-text-muted);
  cursor: wait;
}

.project-card h3 {
  min-height: 48px;
  margin: 19px 0 24px;
  overflow: hidden;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.015em;
  line-height: 1.4;
  text-overflow: ellipsis;
}

.project-card__progress-row {
  margin-bottom: 8px;
  color: var(--color-text-secondary);
  font-size: 11px;
}

.project-card__progress-row strong {
  color: var(--color-text-primary);
  font-size: 12px;
}

progress {
  width: 100%;
  height: 7px;
  border: 0;
  border-radius: 999px;
  overflow: hidden;
  background: #e5eaeb;
}

progress::-webkit-progress-bar {
  background: #e5eaeb;
}

progress::-webkit-progress-value {
  border-radius: 999px;
  background: var(--color-accent);
}

progress::-moz-progress-bar {
  border-radius: 999px;
  background: var(--color-accent);
}

.project-card footer {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #edf0f1;
  color: var(--color-text-secondary);
  font-size: 10px;
}

.pending-count {
  color: #8b6417;
  font-weight: 680;
}

.pending-count--clear {
  color: #407665;
}

.rename-form {
  min-height: 91px;
  margin-top: 12px;
}

.rename-form label {
  display: block;
  margin-bottom: 6px;
  color: var(--color-text-secondary);
  font-size: 11px;
}

.rename-form div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.rename-form input {
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  color: var(--color-text-primary);
  background: var(--color-surface);
}

.rename-form button {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 7px;
  color: var(--color-text-secondary);
  font-size: 11px;
  background: var(--color-surface-muted);
  cursor: pointer;
}

.rename-form button[type="submit"] {
  color: var(--color-text-primary);
  background: var(--color-primary);
}
</style>
