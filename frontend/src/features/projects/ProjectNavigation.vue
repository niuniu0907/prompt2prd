<script setup lang="ts">
import { computed } from 'vue'
import type { ProjectModule } from '@/features/projects/types'

const props = defineProps<{
  currentModule: ProjectModule
  projectId: string
  completeness?: number
}>()

const emit = defineEmits<{
  navigate: [module: ProjectModule]
}>()

interface NavItem {
  module: ProjectModule
  label: string
}

const navItems: NavItem[] = [
  { module: 'overview', label: '需求概览' },
  { module: 'questions', label: '问题向导' },
  { module: 'requirements', label: '需求卡片' },
  { module: 'architecture', label: '架构建议' },
  { module: 'flowchart', label: '流程图' },
  { module: 'prd', label: 'PRD' },
]

const moduleCompleteness = computed(() => props.completeness ?? 0)
</script>

<template>
  <nav class="project-nav" aria-label="项目模块导航">
    <p class="project-nav__label">项目模块</p>
    <ul class="project-nav__list">
      <li v-for="item in navItems" :key="item.module">
        <button
          :class="[
            'project-nav__item',
            { 'project-nav__item--active': currentModule === item.module },
          ]"
          type="button"
          :data-module="item.module"
          :aria-current="currentModule === item.module ? 'page' : undefined"
          @click="emit('navigate', item.module)"
        >
          <span class="project-nav__item-label">{{ item.label }}</span>
        </button>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.project-nav {
  display: flex;
  flex-direction: column;
  padding: 16px 10px;
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
  min-width: 168px;
}

.project-nav__label {
  margin: 0 8px 10px;
  color: var(--color-text-muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.project-nav__list {
  display: grid;
  gap: 3px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.project-nav__item {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 38px;
  padding: 0 10px;
  border-radius: 8px;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 540;
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition: background-color 120ms ease, color 120ms ease;
}

.project-nav__item:hover {
  background: var(--color-surface-muted);
  color: var(--color-text-primary);
}

.project-nav__item--active {
  color: var(--color-text-primary);
  background: var(--color-primary);
  font-weight: 640;
  box-shadow: inset 0 0 0 1px rgba(79, 101, 27, 0.09);
}

.project-nav__item-label {
  flex: 1;
}
</style>
