<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ProjectModule } from '@/features/projects/types'
import { OPTIONAL_PROJECT_MODULES, PRIMARY_PROJECT_MODULES } from '@/features/projects/types'

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

const labels: Record<ProjectModule, string> = {
  overview: '需求输入',
  questions: 'AI澄清',
  requirements: '需求结果',
  prd: 'PRD文档',
  flowchart: '流程图',
  architecture: '架构建议',
}

const primaryNavItems: NavItem[] = PRIMARY_PROJECT_MODULES.map(module => ({
  module,
  label: labels[module],
}))

const optionalNavItems: NavItem[] = OPTIONAL_PROJECT_MODULES.map(module => ({
  module,
  label: labels[module],
}))

const moreToolsExpanded = ref(false)
const activeOptionalTool = computed(() => OPTIONAL_PROJECT_MODULES.includes(
  props.currentModule as (typeof OPTIONAL_PROJECT_MODULES)[number],
))
const showOptionalTools = computed(() => moreToolsExpanded.value || activeOptionalTool.value)

function toggleMoreTools() {
  moreToolsExpanded.value = !moreToolsExpanded.value
}

function itemClasses(item: NavItem) {
  return [
    'project-nav__item',
    { 'project-nav__item--active': props.currentModule === item.module },
  ]
}
</script>

<template>
  <nav class="project-nav" aria-label="项目模块导航">
    <p class="project-nav__label">主流程</p>
    <ul class="project-nav__list">
      <li v-for="item in primaryNavItems" :key="item.module">
        <button
          :class="itemClasses(item)"
          type="button"
          :data-module="item.module"
          :aria-current="currentModule === item.module ? 'page' : undefined"
          @click="emit('navigate', item.module)"
        >
          <span class="project-nav__item-label">{{ item.label }}</span>
        </button>
      </li>
    </ul>

    <div class="project-nav__more">
      <button
        class="project-nav__more-toggle"
        type="button"
        data-testid="more-tools-toggle"
        :aria-expanded="showOptionalTools"
        @click="toggleMoreTools"
      >
        <span>更多工具</span>
        <span class="project-nav__more-icon" aria-hidden="true">{{ showOptionalTools ? '⌃' : '⌄' }}</span>
      </button>

      <ul v-if="showOptionalTools" class="project-nav__list project-nav__list--optional">
        <li v-for="item in optionalNavItems" :key="item.module">
          <button
            :class="itemClasses(item)"
            type="button"
            :data-module="item.module"
            :aria-current="currentModule === item.module ? 'page' : undefined"
            @click="emit('navigate', item.module)"
          >
            <span class="project-nav__item-label">{{ item.label }}</span>
          </button>
        </li>
      </ul>
    </div>
  </nav>
</template>

<style scoped>
.project-nav {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  padding: 16px 10px;
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
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

.project-nav__more {
  display: grid;
  gap: 5px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.project-nav__more-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  padding: 0 10px;
  border-radius: 7px;
  color: var(--color-text-secondary);
  background: transparent;
  font-size: 12px;
  font-weight: 640;
  cursor: pointer;
}

.project-nav__more-toggle:hover {
  background: var(--color-surface-muted);
  color: var(--color-text-primary);
}

.project-nav__more-icon {
  color: var(--color-text-muted);
  font-size: 12px;
}

.project-nav__list--optional {
  padding-left: 8px;
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
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
