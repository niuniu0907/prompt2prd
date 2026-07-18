<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref } from 'vue'
import { routerKey } from 'vue-router'

import type { ProjectListFilter } from '@/db/repositories/projectRepository'

export type AppSection = ProjectListFilter | 'MODEL_SETTINGS'

withDefaults(defineProps<{ activeSection?: AppSection }>(), {
  activeSection: 'ACTIVE',
})

defineEmits<{ navigate: [section: ProjectListFilter] }>()

const router = inject(routerKey, null)
const SIDEBAR_WIDTH_KEY = 'prompt2prd:layout:appSidebarWidth'
const sidebarWidth = ref(readStoredWidth(SIDEBAR_WIDTH_KEY, 244, 196, 380))
const resizingSidebar = ref(false)
const shellColumns = computed(() => `${sidebarWidth.value}px 7px minmax(0, 1fr)`)

let startX = 0
let startWidth = 0

function openModelSettings() {
  void router?.push({ name: 'model-settings' })
}

function readStoredWidth(key: string, fallback: number, min: number, max: number) {
  const raw = window.localStorage.getItem(key)
  const parsed = raw ? Number.parseInt(raw, 10) : Number.NaN
  return clamp(Number.isFinite(parsed) ? parsed : fallback, min, max)
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, Math.round(value)))
}

function startSidebarResize(event: MouseEvent) {
  resizingSidebar.value = true
  startX = event.clientX
  startWidth = sidebarWidth.value
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('mousemove', resizeSidebar)
  window.addEventListener('mouseup', stopSidebarResize)
}

function resizeSidebar(event: MouseEvent) {
  if (!resizingSidebar.value) return
  sidebarWidth.value = clamp(startWidth + event.clientX - startX, 196, 380)
}

function stopSidebarResize() {
  if (!resizingSidebar.value) return
  resizingSidebar.value = false
  window.localStorage.setItem(SIDEBAR_WIDTH_KEY, String(sidebarWidth.value))
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('mousemove', resizeSidebar)
  window.removeEventListener('mouseup', stopSidebarResize)
}

onBeforeUnmount(stopSidebarResize)
</script>

<template>
  <div class="app-shell" :style="{ gridTemplateColumns: shellColumns }">
    <aside class="app-sidebar">
      <div class="brand" aria-label="Prompt2PRD">
        <span class="brand__mark" aria-hidden="true">
          <span class="brand__mark-dot"></span>
        </span>
        <span class="brand__name">Prompt<span>2</span>PRD</span>
      </div>

      <div class="sidebar-section">
        <p class="sidebar-section__label">工作区</p>
        <nav class="sidebar-nav" aria-label="全局导航">
          <button
            :class="['sidebar-nav__item', { 'sidebar-nav__item--active': activeSection === 'ACTIVE' }]"
            type="button"
            data-navigation="ACTIVE"
            :aria-current="activeSection === 'ACTIVE' ? 'page' : undefined"
            @click="$emit('navigate', 'ACTIVE')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v11a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5z" />
              <path d="M8 8h8M8 12h8M8 16h5" />
            </svg>
            <span>全部项目</span>
          </button>
          <button
            :class="['sidebar-nav__item', { 'sidebar-nav__item--active': activeSection === 'ARCHIVED' }]"
            type="button"
            data-navigation="ARCHIVED"
            :aria-current="activeSection === 'ARCHIVED' ? 'page' : undefined"
            @click="$emit('navigate', 'ARCHIVED')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 7.5h16M6 7.5v10A2.5 2.5 0 0 0 8.5 20h7a2.5 2.5 0 0 0 2.5-2.5v-10M9 4h6l1 3.5H8z" />
              <path d="M9 12h6" />
            </svg>
            <span>已归档</span>
          </button>
          <button
            :class="['sidebar-nav__item', { 'sidebar-nav__item--active': activeSection === 'DELETED' }]"
            type="button"
            data-navigation="DELETED"
            :aria-current="activeSection === 'DELETED' ? 'page' : undefined"
            @click="$emit('navigate', 'DELETED')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" />
            </svg>
            <span>回收站</span>
          </button>
          <button
            :class="['sidebar-nav__item', { 'sidebar-nav__item--active': activeSection === 'MODEL_SETTINGS' }]"
            type="button"
            data-navigation="MODEL_SETTINGS"
            :aria-current="activeSection === 'MODEL_SETTINGS' ? 'page' : undefined"
            @click="openModelSettings"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="3" />
              <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.86 2.86-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1 .6 1.7 1.7 0 0 0-.4 1.1V21H9.55v-.09A1.7 1.7 0 0 0 8.5 19.4a1.7 1.7 0 0 0-1.88.34l-.06.06-2.86-2.86.06-.06A1.7 1.7 0 0 0 4.1 15a1.7 1.7 0 0 0-.6-1 1.7 1.7 0 0 0-1.1-.4H2.3V9.55h.1A1.7 1.7 0 0 0 4.1 8.5a1.7 1.7 0 0 0-.34-1.88l-.06-.06L6.56 3.7l.06.06A1.7 1.7 0 0 0 8.5 4.1a1.7 1.7 0 0 0 1-.6 1.7 1.7 0 0 0 .4-1.1V2.3h4.05v.1A1.7 1.7 0 0 0 15 4.1a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.86 2.86-.06.06A1.7 1.7 0 0 0 19.4 8.5a1.7 1.7 0 0 0 .6 1 1.7 1.7 0 0 0 1.1.4h.1v4.05h-.1A1.7 1.7 0 0 0 19.4 15Z" />
            </svg>
            <span>模型设置</span>
          </button>
        </nav>
      </div>

      <div class="sidebar-status">
        <span class="sidebar-status__indicator" aria-hidden="true"></span>
        <div>
          <strong>本地工作区</strong>
          <span>数据保存在当前浏览器</span>
        </div>
      </div>
    </aside>

    <div
      class="app-shell__resizer"
      :class="{ 'app-shell__resizer--active': resizingSidebar }"
      role="separator"
      aria-label="调整全局导航宽度"
      aria-orientation="vertical"
      data-testid="app-sidebar-resizer"
      @mousedown.prevent="startSidebarResize"
    ></div>

    <section class="app-shell__content">
      <slot />
    </section>
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  min-height: 100vh;
  background: var(--color-background);
}

.app-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 22px 16px 18px;
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
}

.app-shell__resizer {
  position: sticky;
  top: 0;
  z-index: 8;
  height: 100vh;
  cursor: col-resize;
  background: transparent;
}

.app-shell__resizer::before {
  display: block;
  width: 1px;
  height: 100%;
  margin: 0 auto;
  background: var(--color-border);
  content: "";
}

.app-shell__resizer:hover::before,
.app-shell__resizer--active::before {
  width: 3px;
  background: var(--color-accent);
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 40px;
  padding: 0 8px;
}

.brand__mark {
  position: relative;
  display: grid;
  width: 31px;
  height: 31px;
  border-radius: 9px;
  place-items: center;
  background: var(--color-text-primary);
  box-shadow: 0 4px 12px rgba(38, 43, 37, 0.14);
}

.brand__mark::before {
  width: 14px;
  height: 10px;
  border: 2px solid var(--color-primary);
  border-right-width: 5px;
  border-radius: 3px;
  content: "";
}

.brand__mark-dot {
  position: absolute;
  right: -3px;
  bottom: -3px;
  width: 10px;
  height: 10px;
  border: 2px solid var(--color-surface);
  border-radius: 50%;
  background: var(--color-accent);
}

.brand__name {
  font-size: 18px;
  font-weight: 720;
  letter-spacing: -0.025em;
}

.brand__name span {
  color: var(--color-accent);
}

.sidebar-section {
  margin-top: 34px;
}

.sidebar-section__label {
  margin: 0 10px 9px;
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.sidebar-nav {
  display: grid;
  gap: 5px;
}

.sidebar-nav__item {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  min-height: 44px;
  padding: 0 12px;
  border-radius: 10px;
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 560;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.sidebar-nav__item svg {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.sidebar-nav__item--active {
  color: var(--color-text-primary);
  background: var(--color-primary);
  box-shadow: inset 0 0 0 1px rgba(79, 101, 27, 0.09);
}

.sidebar-status {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: auto;
  padding: 13px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface-muted);
}

.sidebar-status__indicator {
  width: 8px;
  height: 8px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--color-accent);
  box-shadow: 0 0 0 3px var(--color-accent-soft);
}

.sidebar-status div {
  display: grid;
  gap: 3px;
}

.sidebar-status strong {
  font-size: 12px;
  font-weight: 680;
}

.sidebar-status span:last-child {
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 1.45;
}

.app-shell__content {
  min-width: 0;
}

@media (max-width: 1080px) {
  .app-sidebar {
    padding-inline: 12px;
  }
}
</style>
