<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { routeLocationKey } from 'vue-router'

import {
  createAnalysisClient,
  type AnalysisCallbacks,
  type AnalysisRequestBody,
} from '@/api/analysisApi'
import type { KnownStreamEvent } from '@/api/streamEvents'
import {
  analysisStateRepository,
  type AnalysisState,
  type AnalysisStateStore,
} from '@/db/repositories/analysisStateRepository'
import { projectRepository } from '@/db/repositories/projectRepository'
import type { Project } from '@/features/projects/types'
import type {
  ClarificationQuestion,
  CompletenessScore,
  RequirementItem,
} from '@/features/requirements/types'
import { useModelConfigStore } from '@/stores/modelConfigStore'
import AnalysisProgress from './AnalysisProgress.vue'
import RequirementSummary from './RequirementSummary.vue'

interface AnalysisRunner {
  analyze(body: AnalysisRequestBody, callbacks?: AnalysisCallbacks): Promise<unknown>
  cancel(): void
}

const props = defineProps<{
  project?: Project
  client?: AnalysisRunner
  store?: AnalysisStateStore
  modelSettings?: unknown
}>()

const route = inject(routeLocationKey, null)
const modelConfig = useModelConfigStore()
const client = props.client ?? createAnalysisClient()
const stateStore = props.store ?? analysisStateRepository
const currentProject = ref<Project | null>(props.project ?? null)
const requirements = ref<RequirementItem[]>([])
const questions = ref<ClarificationQuestion[]>([])
const completeness = ref<CompletenessScore>(emptyCompleteness())
const progress = ref(0)
const progressMessage = ref('准备分析项目输入')
const analyzing = ref(false)
const loading = ref(true)
const errorMessage = ref('')

const pendingQuestions = computed(() => questions.value.filter(item => item.status === 'PENDING'))

onMounted(async () => {
  try {
    const project = currentProject.value ?? await projectRepository.getById(String(route?.params.projectId ?? ''))
    if (!project) {
      errorMessage.value = '没有找到这个本地项目。'
      return
    }
    currentProject.value = project
    const restored = await stateStore.load(project.id)
    if (restored) applyState(restored)
    loading.value = false
    if (!restored || !hasAnalysisContent(restored)) await startAnalysis()
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => client.cancel())

function validateModelSettings(settings: unknown): string | null {
  const s = settings as Record<string, unknown> | null | undefined
  if (!s) return '模型配置未设置，请先在「模型设置」页面配置 AI 服务。'
  if (!String(s.model ?? '').trim()) return '模型名称未填写，请先在「模型设置」页面选择模型。'
  if (s.keySource === 'USER' && !String(s.apiKey ?? '').trim()) return 'API Key 未填写，请先在「模型设置」页面输入 Key。'
  if (s.keySource !== 'SYSTEM' && s.keySource !== 'USER') return '模型 Key 来源未选择，请先在「模型设置」页面配置。'
  return null
}

async function startAnalysis() {
  const project = currentProject.value
  if (!project || analyzing.value) return

  const settings = props.modelSettings ?? requestModelSettings()
  const validation = validateModelSettings(settings)
  if (validation) {
    errorMessage.value = validation
    return
  }

  const input = analysisInput(project)
  if (!input.trim()) {
    errorMessage.value = '项目输入内容为空，请返回编辑项目并填写需求描述。'
    return
  }

  analyzing.value = true
  errorMessage.value = ''
  progress.value = Math.max(progress.value, 5)
  progressMessage.value = '正在连接分析服务'
  try {
    const finalState = await client.analyze({
      state: serverState(project),
      input,
      missingInformation: [],
      modelSettings: settings,
    }, { onEvent: handleEvent, onWarning: message => console.warn(message) })
    const saved = await stateStore.saveFinal(project.id, finalState)
    applyState(saved)
    progress.value = 100
    progressMessage.value = '初始分析已完成并保存'
  } catch (error) {
    errorMessage.value = readableError(error)
    progressMessage.value = '分析暂时中断，已保留上次有效状态'
  } finally {
    analyzing.value = false
  }
}

function handleEvent(event: KnownStreamEvent) {
  if (event.type === 'analysis_started') {
    progress.value = Math.max(progress.value, 10)
    progressMessage.value = '开始理解项目目标'
  } else if (event.type === 'analysis_progress') {
    progress.value = Number(event.data.progress)
    progressMessage.value = String(event.data.message)
  } else if (event.type === 'requirement_patch') {
    const item = event.data.value as RequirementItem
    if (item?.id) upsert(requirements.value, item)
  } else if (event.type === 'question_created') {
    const question = event.data.question as ClarificationQuestion
    if (question?.id) upsert(questions.value, question)
  } else if (event.type === 'completeness_changed') {
    const current = Number(event.data.current)
    if (Number.isFinite(current)) completeness.value = { ...completeness.value, total: current }
  }
}

function applyState(state: AnalysisState) {
  currentProject.value = state.project
  requirements.value = [...state.requirements]
  questions.value = [...state.questions]
  completeness.value = state.completeness
  progress.value = state.completeness.total
  progressMessage.value = hasAnalysisContent(state) ? '已恢复最近一次有效分析' : '准备分析项目输入'
}

function serverState(project: Project) {
  return {
    project: {
      id: project.id,
      name: project.name,
      language: project.language,
      stage: project.stage,
      completeness: completeness.value.total,
    },
    requirements: requirements.value,
    questions: questions.value,
    answers: [],
    conflicts: [],
    completeness: completeness.value,
  }
}

function requestModelSettings() {
  return {
    ...modelConfig.requestKeyConfig,
    provider: modelConfig.provider,
    baseUrl: modelConfig.baseUrl || undefined,
    model: modelConfig.model,
    parameters: { temperature: modelConfig.temperature },
  }
}

function analysisInput(project: Project): string {
  return [project.originalPrompt, project.uploadedFileContent, project.supplementalPrompt]
    .filter((value): value is string => Boolean(value?.trim()))
    .join('\n\n')
}

function hasAnalysisContent(state: AnalysisState) {
  return state.requirements.length > 0 || state.questions.length > 0
    || state.answers.length > 0 || state.conflicts.length > 0
}

function upsert<T extends { id: string }>(items: T[], value: T) {
  const index = items.findIndex(item => item.id === value.id)
  if (index >= 0) items.splice(index, 1, value)
  else items.push(value)
}

function emptyCompleteness(): CompletenessScore {
  return { total: 0, dimensions: [], pendingCount: 0, hasCoreConflict: false }
}

function readableError(error: unknown) {
  return error instanceof Error ? error.message : '分析失败，请检查模型设置后重试。'
}
</script>

<template>
  <main class="analysis-view" data-testid="analysis-view">
    <section v-if="loading" class="analysis-view__loading">正在恢复本地分析状态…</section>
    <template v-else>
      <header class="analysis-view__header">
        <div><span>需求概览</span><h1>从想法到可确认的需求</h1></div>
        <button v-if="errorMessage" type="button" class="button-primary" :disabled="analyzing" @click="startAnalysis">重新分析</button>
      </header>

      <AnalysisProgress :progress="progress" :message="progressMessage" :active="analyzing" />

      <div v-if="errorMessage" class="analysis-view__error" role="alert">
        <strong>本次分析未完成</strong><span>{{ errorMessage }}</span>
      </div>

      <RequirementSummary :requirements="requirements" />

      <section v-if="pendingQuestions.length" class="question-preview">
        <header><div><span>第一轮澄清</span><h2>还需要你确认 {{ pendingQuestions.length }} 个问题</h2></div><small>下一步将在问题向导中集中回答</small></header>
        <article v-for="question in pendingQuestions" :key="question.id">
          <div><strong>{{ question.text }}</strong><p>{{ question.reason }}</p></div>
          <footer><span>AI 提问</span><span>待回答</span></footer>
        </article>
      </section>
    </template>
  </main>
</template>

<style scoped>
.analysis-view { display: grid; gap: 20px; max-width: 980px; margin: 0 auto; }
.analysis-view__loading { padding: 48px; color: var(--color-text-secondary); text-align: center; }
.analysis-view__header { display: flex; align-items: end; justify-content: space-between; gap: 20px; }
.analysis-view__header span,.question-preview header span { color: var(--color-accent); font-size: 10px; font-weight: 750; letter-spacing: .08em; }
.analysis-view__header h1 { margin: 5px 0 0; font-size: 22px; }
.analysis-view__error { display: grid; gap: 4px; padding: 13px 15px; border: 1px solid #e2bcbc; border-radius: 11px; color: #873f3f; background: #fff8f8; }
.analysis-view__error span { color: var(--color-text-secondary); font-size: 11px; }
.question-preview { display: grid; gap: 10px; padding-top: 4px; }
.question-preview > header { display: flex; align-items: end; justify-content: space-between; gap: 20px; }
.question-preview h2 { margin: 4px 0 0; font-size: 14px; }
.question-preview small { color: var(--color-text-muted); font-size: 10px; }
.question-preview article { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 14px 16px; border: 1px solid var(--color-border); border-radius: 11px; background: var(--color-surface); }
.question-preview article strong { font-size: 12px; }
.question-preview article p { margin: 5px 0 0; color: var(--color-text-secondary); font-size: 10px; }
.question-preview footer { display: flex; gap: 5px; flex-shrink: 0; }
.question-preview footer span { padding: 3px 7px; border-radius: 999px; color: var(--color-text-secondary); font-size: 9px; background: var(--color-surface-muted); }
</style>
