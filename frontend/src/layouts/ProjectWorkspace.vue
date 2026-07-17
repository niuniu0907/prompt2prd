<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  projectRepository,
  type ProjectLookupRepository,
} from '@/db/repositories/projectRepository'
import type { Project } from '@/features/projects/types'
import { PROJECT_MODULES, type ProjectModule } from '@/features/projects/types'
import ProjectHeader from '@/features/projects/ProjectHeader.vue'
import ProjectNavigation from '@/features/projects/ProjectNavigation.vue'
import AppShell from '@/layouts/AppShell.vue'

const props = defineProps<{
  repository?: ProjectLookupRepository
  modelName?: string
  saveStatus?: string
}>()

const emit = defineEmits<{
  generatePrd: []
}>()

const repository = props.repository ?? projectRepository
const route = useRoute()
const router = useRouter()

const project = ref<Project | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const currentModule = ref<ProjectModule>('overview')

const modulesWithPanelCollapsed: ProjectModule[] = ['flowchart', 'prd']
const rightPanelCollapsed = ref(false)

function resolveModuleFromRoute(): ProjectModule {
  const name = String(route.name ?? '')
  for (const mod of PROJECT_MODULES) {
    if (name === `project-${mod}`) return mod
  }
  return 'overview'
}

function syncFromRoute() {
  const mod = resolveModuleFromRoute()
  currentModule.value = mod
  rightPanelCollapsed.value = modulesWithPanelCollapsed.includes(mod)
}

watch(() => route.name, syncFromRoute)

onMounted(async () => {
  try {
    const found = await repository.getById(String(route.params.projectId))
    if (!found) {
      errorMessage.value = '没有找到这个本地项目。'
      return
    }
    project.value = found
    syncFromRoute()
  } catch {
    errorMessage.value = '读取本地项目失败。'
  } finally {
    loading.value = false
  }
})

function navigateTo(module: ProjectModule) {
  void router.push({
    name: `project-${module}`,
    params: { projectId: route.params.projectId },
  })
}

function toggleRightPanel() {
  rightPanelCollapsed.value = !rightPanelCollapsed.value
}

function goHome() {
  void router.push({ name: 'project-home' })
}
</script>

<template>
  <AppShell>
    <div v-if="loading" class="workspace" data-testid="project-workspace">
      <section class="workspace__status" aria-label="正在读取项目">正在读取项目…</section>
    </div>

    <div
      v-else-if="errorMessage"
      class="workspace"
      data-testid="project-workspace"
    >
      <section class="workspace__status workspace__status--error" role="alert">
        <h1>无法打开项目</h1>
        <p>{{ errorMessage }}</p>
        <button type="button" class="button-primary" @click="goHome">返回项目列表</button>
      </section>
    </div>

    <div v-else-if="project" class="workspace" data-testid="project-workspace">
      <ProjectHeader
        :project-name="project.name"
        :completeness="project.completeness"
        :stage="project.stage"
        :model-name="modelName"
        :save-status="saveStatus"
        @generate-prd="emit('generatePrd')"
      />

      <div class="workspace__body">
        <ProjectNavigation
          :current-module="currentModule"
          :project-id="project.id"
          :completeness="project.completeness"
          @navigate="navigateTo"
        />

        <section class="workspace__canvas" data-testid="workspace-canvas">
          <router-view />
        </section>

        <aside
          :class="[
            'workspace__panel',
            { 'workspace__panel--collapsed': rightPanelCollapsed },
          ]"
          data-testid="workspace-right-panel"
          :aria-expanded="!rightPanelCollapsed"
        >
          <div class="workspace__panel-inner">
            <button
              class="workspace__panel-toggle"
              type="button"
              :aria-label="rightPanelCollapsed ? '展开辅助面板' : '收起辅助面板'"
              data-testid="panel-toggle"
              @click="toggleRightPanel"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true" class="workspace__panel-toggle-icon">
                <path
                  v-if="rightPanelCollapsed"
                  d="M15 18l-6-6 6-6"
                />
                <path
                  v-else
                  d="M9 18l6-6-6-6"
                />
              </svg>
            </button>

            <div v-if="!rightPanelCollapsed" class="workspace__panel-content">
              <slot name="panel">
                <p class="workspace__panel-placeholder">
                  辅助面板 — 分析进度、假设、冲突和变更将在此展示。
                </p>
              </slot>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--color-background);
}

.workspace__status {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 60vh;
  color: var(--color-text-secondary);
}

.workspace__status--error {
  gap: 8px;
}

.workspace__status--error h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 20px;
}

.workspace__status--error p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.workspace__status--error .button-primary {
  margin-top: 12px;
}

.workspace__body {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  flex: 1;
  min-height: 0;
}

.workspace__canvas {
  min-width: 0;
  padding: 24px 28px;
  overflow-y: auto;
}

.workspace__panel {
  width: 300px;
  border-left: 1px solid var(--color-border);
  background: var(--color-surface);
  transition: width 180ms ease;
  flex-shrink: 0;
}

.workspace__panel--collapsed {
  width: 44px;
}

.workspace__panel-inner {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.workspace__panel-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  min-height: 44px;
  padding: 0;
  color: var(--color-text-secondary);
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
  border-bottom: 1px solid var(--color-border);
}

.workspace__panel-toggle:hover {
  color: var(--color-text-primary);
  background: var(--color-surface-muted);
}

.workspace__panel-toggle-icon {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.workspace__panel-content {
  padding: 16px;
  overflow-y: auto;
  flex: 1;
}

.workspace__panel-placeholder {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
