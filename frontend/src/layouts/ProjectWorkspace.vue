<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  analysisStateRepository,
  type AnalysisState,
  type AnalysisStateStore,
} from '@/db/repositories/analysisStateRepository'
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
const route = useRoute()
const router = useRouter()

function handleAppShellNavigate(view: ProjectListFilter) {
  void router.push({ name: 'project-home', query: { view } })
}

const project = ref<Project | null>(null)
const analysisState = ref<AnalysisState | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const currentModule = ref<ProjectModule>('overview')

const PROJECT_NAV_WIDTH_KEY = 'prompt2prd:layout:projectNavWidth'
const projectNavWidth = ref(readStoredWidth(PROJECT_NAV_WIDTH_KEY, 240, 168, 360))
const resizingProjectNav = ref(false)
const workspaceColumns = computed(() => `${projectNavWidth.value}px 7px minmax(0, 1fr)`)

let projectNavStartX = 0
let projectNavStartWidth = 0

const displayProject = computed(() => analysisState.value?.project ?? project.value)
const formalRequirements = computed(() => (analysisState.value?.requirements ?? []).filter(isFormalRequirement))
const pendingRequirements = computed(() => formalRequirements.value.filter(item => item.status === 'PENDING'))
const unanalyzedRequirements = computed(() => (analysisState.value?.requirements ?? [])
  .filter(item => item.status === 'UNANALYZED' || item.type === 'MISSING_INFORMATION'))
const pendingQuestions = computed(() => (analysisState.value?.questions ?? []).filter(item => item.status === 'PENDING'))
const openConflicts = computed(() => (analysisState.value?.conflicts ?? []).filter(item => item.status === 'OPEN'))
const coreConflictCount = computed(() => openConflicts.value.filter(item => item.core).length)
const totalProgress = computed(() => analysisState.value?.completeness.total ?? displayProject.value?.completeness ?? 0)
const hasAnalysisContent = computed(() => Boolean(analysisState.value)
  && (formalRequirements.value.length > 0
    || unanalyzedRequirements.value.length > 0
    || (analysisState.value?.questions.length ?? 0) > 0
    || (analysisState.value?.answers.length ?? 0) > 0
    || (analysisState.value?.conflicts.length ?? 0) > 0))
// 首次AI分析成功后即可生成PRD；完整度和冲突只做提示，不阻塞生成
const canGeneratePrd = computed(() => hasAnalysisContent.value)
const generateHint = computed(() => {
  if (!hasAnalysisContent.value) return '首次 AI 解析完成后会进入 AI 澄清。'
  const waiting = pendingRequirements.value.length + unanalyzedRequirements.value.length + pendingQuestions.value.length
  const parts: string[] = []
  if (coreConflictCount.value > 0) {
    parts.push(`仍有 ${coreConflictCount.value} 个核心冲突`)
  }
  if (totalProgress.value < 80) {
    parts.push(`当前完整度 ${totalProgress.value}%，仍有 ${unanalyzedRequirements.value.length} 项待分析`)
  }
  if (waiting > 0) {
    parts.push(`还有 ${waiting} 项待确认`)
  }
  if (parts.length > 0) {
    return `${parts.join('；')}。可以继续澄清，也可以先生成 PRD 草稿。`
  }
  return '关键信息已达到生成条件，可以生成PRD。你也可以继续补充细节。'
})

function resolveModuleFromRoute(): ProjectModule {
  const name = String(route.name ?? '')
  for (const mod of PROJECT_MODULES) {
    if (name === `project-${mod}`) return mod
  }
  return 'overview'
}

function syncFromRoute() {
  currentModule.value = resolveModuleFromRoute()
}

watch(() => route.name, syncFromRoute)

onMounted(async () => {
  console.log('[ProjectWorkspace] mounted')
  try {
    await refreshProjectContext()
    syncFromRoute()
    window.addEventListener('prompt2prd:analysis-state-saved', handleAnalysisStateSaved)
  } catch {
    errorMessage.value = '读取本地项目失败。'
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  console.log('[ProjectWorkspace] beforeUnmount')
  stopProjectNavResize()
  window.removeEventListener('prompt2prd:analysis-state-saved', handleAnalysisStateSaved)
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
  const loadedState = await stateStore.load(projectId)
  analysisState.value = loadedState ?? null
}

function handleAnalysisStateSaved(event: Event) {
  const detail = (event as CustomEvent<{ projectId?: string }>).detail
  if (detail?.projectId !== String(route.params.projectId)) return
  void refreshProjectContext()
}

function navigateTo(module: ProjectModule) {
  void router.push({
    name: `project-${module}`,
    params: { projectId: route.params.projectId },
  })
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
        :can-generate-prd="canGeneratePrd"
        :generate-hint="generateHint"
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
          <RouterView v-slot="{ Component, route }">
            <Transition name="canvas">
              <component :is="Component" :key="route.name" />
            </Transition>
          </RouterView>
        </section>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 100vh;
  min-width: 0;
  overflow: hidden;
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
  overflow: hidden;
}

.workspace__body-resizer {
  z-index: 4;
  height: 100%;
  min-height: 0;
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
  position: relative;
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

/* Canvas child route transitions — cross-fade, no blank frames */
.canvas-enter-active {
  transition: opacity var(--motion-fast) var(--ease-standard),
              transform var(--motion-fast) var(--ease-standard);
}
.canvas-leave-active {
  transition: opacity var(--motion-fast) var(--ease-standard),
              transform var(--motion-fast) var(--ease-standard);
  position: absolute;
}
.canvas-enter-from {
  opacity: 0;
  transform: translateY(4px);
}
.canvas-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

</style>
