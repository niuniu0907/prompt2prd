<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  analysisStateRepository,
  type AnalysisState,
  type AnalysisStateStore,
} from '@/db/repositories/analysisStateRepository'
import { architectureRepository } from '@/db/repositories/architectureRepository'
import {
  projectRepository,
  type ProjectListFilter,
  type ProjectLookupRepository,
} from '@/db/repositories/projectRepository'
import type { Project } from '@/features/projects/types'
import { PROJECT_MODULES, type ProjectModule } from '@/features/projects/types'
import { isFormalRequirement } from '@/features/requirements/requirementDisplay'
import ProjectHeader from '@/features/projects/ProjectHeader.vue'
import ProjectNavigation from '@/features/projects/ProjectNavigation.vue'
import AppShell from '@/layouts/AppShell.vue'

const props = defineProps<{
  repository?: ProjectLookupRepository
  stateStore?: AnalysisStateStore
  architectureSelected?: (projectId: string) => Promise<unknown>
  modelName?: string
  saveStatus?: string
}>()

const emit = defineEmits<{
  generatePrd: []
}>()

const repository = props.repository ?? projectRepository
const stateStore = props.stateStore ?? analysisStateRepository
const architectureSelected = props.architectureSelected ?? ((projectId: string) => architectureRepository.selected(projectId))
const route = useRoute()
const router = useRouter()

function handleAppShellNavigate(view: ProjectListFilter) {
  void router.push({ name: 'project-home', query: { view } })
}

const project = ref<Project | null>(null)
const analysisState = ref<AnalysisState | null>(null)
const architectureConfirmed = ref(false)
const loading = ref(true)
const errorMessage = ref('')

const currentModule = ref<ProjectModule>('overview')

const rightPanelCollapsed = ref(true)
const PROJECT_NAV_WIDTH_KEY = 'prompt2prd:layout:projectNavWidth'
const projectNavWidth = ref(readStoredWidth(PROJECT_NAV_WIDTH_KEY, 240, 168, 360))
const resizingProjectNav = ref(false)
const workspaceColumns = computed(() => `${projectNavWidth.value}px 7px minmax(0, 1fr)`)

let projectNavStartX = 0
let projectNavStartWidth = 0

const displayProject = computed(() => analysisState.value?.project ?? project.value)
const formalRequirements = computed(() => (analysisState.value?.requirements ?? []).filter(isFormalRequirement))
const hasFormalRequirements = computed(() => formalRequirements.value.length > 0)
const pendingRequirements = computed(() => formalRequirements.value.filter(item => item.status === 'PENDING'))
const confirmedRequirements = computed(() => formalRequirements.value.filter(item => item.status === 'CONFIRMED'))
const pendingQuestions = computed(() => (analysisState.value?.questions ?? []).filter(item => item.status === 'PENDING'))
const openConflicts = computed(() => (analysisState.value?.conflicts ?? []).filter(item => item.status === 'OPEN'))
const coreConflictCount = computed(() => openConflicts.value.filter(item => item.core).length)
const totalProgress = computed(() => analysisState.value?.completeness.total ?? displayProject.value?.completeness ?? 0)
const prdStarted = computed(() => displayProject.value?.stage === 'PRD' || displayProject.value?.stage === 'COMPLETED')
const progressItems = computed<Array<{ label: string; value: string; tone: 'done' | 'pending' | 'blocked' }>>(() => [
  {
    label: '需求澄清',
    value: !hasFormalRequirements.value ? '待补充' : pendingQuestions.value.length === 0 ? '已完成' : '进行中',
    tone: hasFormalRequirements.value && pendingQuestions.value.length === 0 ? 'done' : 'pending',
  },
  {
    label: '需求确认',
    value: hasFormalRequirements.value ? `${confirmedRequirements.value.length}/${formalRequirements.value.length}` : '无需求',
    tone: pendingRequirements.value.length === 0 && hasFormalRequirements.value ? 'done' : 'pending',
  },
  {
    label: '架构选择',
    value: architectureConfirmed.value ? '已完成' : '未完成',
    tone: architectureConfirmed.value ? 'done' : 'pending',
  },
  {
    label: 'PRD生成',
    value: prdStarted.value ? '已开始' : '未开始',
    tone: prdStarted.value ? 'done' : 'pending',
  },
])
const canGeneratePrd = computed(() =>
  totalProgress.value >= 80
  && confirmedRequirements.value.length > 0
  && pendingQuestions.value.length === 0
  && pendingRequirements.value.length === 0
  && architectureConfirmed.value
  && coreConflictCount.value === 0,
)
const generateDisabledReason = computed(() => {
  if (confirmedRequirements.value.length === 0) return '还没有已确认需求，先补充并确认需求后才能生成PRD'
  if (pendingRequirements.value.length > 0) return `还需确认${pendingRequirements.value.length}条需求后才能生成PRD`
  if (pendingQuestions.value.length > 0) return `还需回答${pendingQuestions.value.length}个澄清问题后才能生成PRD`
  if (!architectureConfirmed.value) return '还需确认架构方案后才能生成PRD'
  if (coreConflictCount.value > 0) return `还需解决${coreConflictCount.value}个核心冲突后才能生成PRD`
  if (totalProgress.value < 80) return `总体进度达到80%后才能生成PRD，当前为${Math.round(totalProgress.value)}%`
  return '可以生成PRD'
})
const panelAttentionCount = computed(() =>
  pendingRequirements.value.length + pendingQuestions.value.length + openConflicts.value.length)
const panelHasContent = computed(() => panelAttentionCount.value > 0)

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
  rightPanelCollapsed.value = true
}

watch(() => route.name, syncFromRoute)

onMounted(async () => {
  try {
    await refreshProjectContext()
    syncFromRoute()
    window.addEventListener('architecture_confirmed', refreshAfterWorkspaceEvent)
  } catch {
    errorMessage.value = '读取本地项目失败。'
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  stopProjectNavResize()
  window.removeEventListener('architecture_confirmed', refreshAfterWorkspaceEvent)
})

function readStoredWidth(key: string, fallback: number, min: number, max: number) {
  const raw = window.localStorage.getItem(key)
  const parsed = raw ? Number.parseInt(raw, 10) : Number.NaN
  return clamp(Number.isFinite(parsed) ? parsed : fallback, min, max)
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, Math.round(value)))
}

function startProjectNavResize(event: MouseEvent) {
  resizingProjectNav.value = true
  projectNavStartX = event.clientX
  projectNavStartWidth = projectNavWidth.value
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('mousemove', resizeProjectNav)
  window.addEventListener('mouseup', stopProjectNavResize)
}

function resizeProjectNav(event: MouseEvent) {
  if (!resizingProjectNav.value) return
  projectNavWidth.value = clamp(projectNavStartWidth + event.clientX - projectNavStartX, 168, 360)
}

function stopProjectNavResize() {
  if (!resizingProjectNav.value) return
  resizingProjectNav.value = false
  window.localStorage.setItem(PROJECT_NAV_WIDTH_KEY, String(projectNavWidth.value))
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('mousemove', resizeProjectNav)
  window.removeEventListener('mouseup', stopProjectNavResize)
}

async function refreshProjectContext() {
  const projectId = String(route.params.projectId)
  const found = await repository.getById(projectId)
  if (!found) {
    errorMessage.value = '没有找到这个本地项目。'
    return
  }
  project.value = found
  const [loadedState, selectedArchitecture] = await Promise.all([
    stateStore.load(projectId),
    architectureSelected(projectId),
  ])
  analysisState.value = loadedState ?? null
  architectureConfirmed.value = Boolean(selectedArchitecture)
}

function refreshAfterWorkspaceEvent() {
  void refreshProjectContext()
}

function navigateTo(module: ProjectModule) {
  void router.push({
    name: `project-${module}`,
    params: { projectId: route.params.projectId },
  })
}

function toggleRightPanel() {
  rightPanelCollapsed.value = !rightPanelCollapsed.value
}

function handleGeneratePrd() {
  if (!canGeneratePrd.value) return
  void router.push({
    name: 'project-prd',
    params: { projectId: route.params.projectId },
  })
  emit('generatePrd')
}

function goHome() {
  void router.push({ name: 'project-home' })
}
</script>

<template>
  <AppShell :active-section="undefined" @navigate="handleAppShellNavigate">
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

    <div v-else-if="displayProject" class="workspace" data-testid="project-workspace">
      <ProjectHeader
        :project-name="displayProject.name"
        :completeness="totalProgress"
        :stage="displayProject.stage"
        :model-name="modelName"
        :save-status="saveStatus"
        :progress-items="progressItems"
        :can-generate-prd="canGeneratePrd"
        :generate-disabled-reason="generateDisabledReason"
        @generate-prd="handleGeneratePrd"
      />

      <div class="workspace__body" :style="{ gridTemplateColumns: workspaceColumns }">
        <ProjectNavigation
          :current-module="currentModule"
          :project-id="displayProject.id"
          :completeness="totalProgress"
          @navigate="navigateTo"
        />

        <div
          class="workspace__body-resizer"
          :class="{ 'workspace__body-resizer--active': resizingProjectNav }"
          role="separator"
          aria-label="调整项目模块宽度"
          aria-orientation="vertical"
          data-testid="project-nav-resizer"
          @mousedown.prevent="startProjectNavResize"
        ></div>

        <section
          class="workspace__canvas"
          :class="{ 'workspace__canvas--wide': currentModule === 'prd' || currentModule === 'flowchart' }"
          data-testid="workspace-canvas"
        >
          <router-view />
        </section>

        <aside
          v-if="panelHasContent || !rightPanelCollapsed"
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
              <span v-if="rightPanelCollapsed && panelAttentionCount" class="workspace__panel-dot">
                {{ panelAttentionCount }}
              </span>
            </button>

            <div v-if="!rightPanelCollapsed" class="workspace__panel-content">
              <slot name="panel">
                <header class="workspace__panel-header">
                  <span>辅助面板</span>
                  <h2>待处理事项</h2>
                </header>
                <div class="workspace__panel-list">
                  <p v-if="pendingRequirements.length">待确认需求：{{ pendingRequirements.length }} 条</p>
                  <p v-if="pendingQuestions.length">待回答问题：{{ pendingQuestions.length }} 个</p>
                  <p v-if="openConflicts.length">开放冲突：{{ openConflicts.length }} 个</p>
                </div>
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
  position: relative;
  display: grid;
  flex: 1;
  min-height: 0;
}

.workspace__body-resizer {
  position: sticky;
  top: 0;
  z-index: 4;
  height: calc(100vh - 58px);
  min-height: 100%;
  cursor: col-resize;
  background: transparent;
}

.workspace__body-resizer::before {
  display: block;
  width: 1px;
  height: 100%;
  margin: 0 auto;
  background: var(--color-border);
  content: "";
}

.workspace__body-resizer:hover::before,
.workspace__body-resizer--active::before {
  width: 3px;
  background: var(--color-accent);
}

.workspace__canvas {
  min-width: 0;
  padding: 24px 28px;
  overflow-y: auto;
  display: flex;
  justify-content: center;
}

.workspace__canvas > :deep(*) {
  width: min(100%, 1100px);
}

.workspace__canvas--wide > :deep(*) {
  width: min(100%, 1400px);
}

.workspace__panel {
  position: absolute;
  z-index: 5;
  top: 0;
  right: 0;
  width: 320px;
  height: 100%;
  border-left: 1px solid var(--color-border);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
  transition: width 180ms ease, opacity 180ms ease;
}

.workspace__panel--collapsed {
  width: 0;
  border-left: 0;
  background: transparent;
  box-shadow: none;
}

.workspace__panel-inner {
  position: relative;
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
  background: var(--color-surface);
  cursor: pointer;
  border: 1px solid var(--color-border);
  border-right: 0;
  border-radius: 10px 0 0 10px;
  position: absolute;
  top: 16px;
  left: -44px;
  box-shadow: var(--shadow-card);
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

.workspace__panel-dot {
  position: absolute;
  top: -6px;
  right: 4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  color: #873f3f;
  background: #fff8f8;
  border: 1px solid #e2bcbc;
  font-size: 10px;
  font-weight: 750;
  line-height: 16px;
}

.workspace__panel-content {
  padding: 18px;
  overflow-y: auto;
  flex: 1;
}

.workspace__panel-header span {
  color: var(--color-accent);
  font-size: 10px;
  font-weight: 750;
}

.workspace__panel-header h2 {
  margin: 4px 0 12px;
  font-size: 16px;
}

.workspace__panel-list {
  display: grid;
  gap: 8px;
}

.workspace__panel-list p {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-text-secondary);
  font-size: 12px;
  background: var(--color-background);
}
</style>
