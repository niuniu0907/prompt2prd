<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { routeLocationKey, useRouter } from 'vue-router'
import { analysisStateRepository, type AnalysisState } from '@/db/repositories/analysisStateRepository'
import { requirementRepository, type ManualRequirementEdit } from '@/db/repositories/requirementRepository'
import { requirementInteractionRepository } from '@/db/repositories/requirementInteractionRepository'
import { versionRepository } from '@/db/repositories/versionRepository'
import type { RequirementItem, RequirementVersion } from './types'
import { isFormalRequirement } from './requirementDisplay'
import RequirementCard from './RequirementCard.vue'
import RequirementEditor from './RequirementEditor.vue'
import ConflictPanel from './ConflictPanel.vue'
import AssumptionPanel from './AssumptionPanel.vue'
import VersionHistory from '@/features/history/VersionHistory.vue'

const route = inject(routeLocationKey, null)
const router = useRouter()
const projectId = computed(() => String(route?.params.projectId ?? ''))
const state = ref<AnalysisState | null>(null)
const versions = ref<RequirementVersion[]>([])
const selected = ref<RequirementItem | null>(null)
const busy = ref(false)
const loading = ref(true)
const errorMessage = ref('')
const blockingConflict = computed(() => state.value?.conflicts.some(item => item.core && item.status === 'OPEN') ?? false)
const assumptions = computed(() => state.value?.requirements.filter(item => item.type === 'ASSUMPTION' && item.metadata.decision !== 'REJECTED') ?? [])
const formalRequirements = computed(() => state.value?.requirements.filter(isFormalRequirement) ?? [])
const confirmedCount = computed(() => formalRequirements.value.filter(item => item.status === 'CONFIRMED').length)
const pendingCount = computed(() => formalRequirements.value.filter(item => item.status === 'PENDING').length)
const openConflictCount = computed(() => state.value?.conflicts.filter(item => item.status === 'OPEN').length ?? 0)
const nextStep = computed<{ title: string; detail: string; routeName: string | null; action: string }>(() => {
  if (formalRequirements.value.length === 0) {
    return {
      title: '补充产品需求',
      detail: '当前只有架构或历史记录，没有可确认的产品需求。先回到需求概览检查分析状态，再补充或重新分析需求。',
      routeName: 'project-overview',
      action: '回到需求概览',
    }
  }
  if (pendingCount.value) {
    return {
      title: `确认 ${pendingCount.value} 条待确认需求`,
      detail: '先确认或编辑待确认需求，确认后完整度才会继续提高。',
      routeName: null,
      action: '处理本页需求',
    }
  }
  if (openConflictCount.value) {
    return {
      title: `处理 ${openConflictCount.value} 个冲突`,
      detail: '先在右侧冲突面板选择保留哪一侧内容，核心冲突未解决前不能生成 PRD。',
      routeName: null,
      action: '处理右侧冲突',
    }
  }
  if ((state.value?.completeness.total ?? 0) < 80) {
    return {
      title: '继续补充需求',
      detail: '需求已确认但完整度还不足，回到需求概览查看缺口并继续澄清。',
      routeName: 'project-overview',
      action: '查看需求缺口',
    }
  }
  return {
    title: state.value?.project.stage === 'ARCHITECTURE' ? '生成业务流程图' : '进入架构建议',
    detail: state.value?.project.stage === 'ARCHITECTURE'
      ? '需求和架构已经具备基础，可以继续生成业务流程图。'
      : '需求已经确认，可以继续选择并确认技术架构。',
    routeName: state.value?.project.stage === 'ARCHITECTURE' ? 'project-flowchart' : 'project-architecture',
    action: state.value?.project.stage === 'ARCHITECTURE' ? '进入流程图' : '进入架构建议',
  }
})

onMounted(load)
async function load() {
  try {
    const [loaded, history] = await Promise.all([analysisStateRepository.load(projectId.value), versionRepository.listByProject(projectId.value)])
    state.value = loaded ?? null; versions.value = history
    if (selected.value) selected.value = loaded?.requirements.find(item => item.id === selected.value?.id) ?? null
  } catch (error) { errorMessage.value = readable(error) }
  finally { loading.value = false }
}
async function execute(action: () => Promise<unknown>) { busy.value = true; errorMessage.value = ''; try { await action(); await load() } catch (error) { errorMessage.value = readable(error) } finally { busy.value = false } }
function edit(item: RequirementItem) { selected.value = item }
function save(edit: ManualRequirementEdit) { if (selected.value) void execute(() => requirementRepository.commitManualEdit(selected.value!.id, edit)) }
function lock(id: string, value: boolean) { void execute(() => requirementInteractionRepository.setLocked(id, value)) }
function decide(id: string, accepted: boolean) { void execute(() => requirementInteractionRepository.decideAssumption(id, accepted)) }
function resolve(id: string, resolution: string) { void execute(() => requirementInteractionRepository.resolveConflict(id, resolution)) }
function restore(id: string) { void execute(() => versionRepository.restore(projectId.value, id)) }
function goToNextStep() {
  if (!nextStep.value.routeName) return
  void router.push({ name: nextStep.value.routeName, params: { projectId: projectId.value } })
}
function readable(error: unknown) { return error instanceof Error ? error.message : '本地操作失败，原状态已保留。' }
</script>

<template>
  <main class="requirements-view">
    <div v-if="loading" class="status">正在读取需求…</div>
    <template v-else-if="state">
      <header class="heading"><div><span>结构化需求</span><h1>需求卡片与版本</h1></div><p>{{ formalRequirements.length }} 项 · 完整度 {{ state.completeness.total }}%</p></header>
      <section class="requirement-status" aria-label="需求确认状态">
        <article><span>已确认</span><strong>{{ confirmedCount }}</strong></article>
        <article><span>待确认</span><strong>{{ pendingCount }}</strong></article>
        <article :class="{ blocked: openConflictCount > 0 }"><span>冲突</span><strong>{{ openConflictCount }}</strong></article>
        <article class="next-card">
          <span>下一步</span>
          <strong>{{ nextStep.title }}</strong>
          <button v-if="nextStep.routeName" type="button" class="button-primary" @click="goToNextStep">
            {{ nextStep.action }}
          </button>
        </article>
      </section>
      <div v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</div>
      <div v-if="blockingConflict" class="warning">存在未解决的核心冲突，项目暂时不能标记为完成。</div>
      <section v-if="formalRequirements.length === 0" class="empty-guide" aria-label="没有可确认需求">
        <div>
          <span>当前无法继续生成 PRD</span>
          <h2>还没有可确认的产品需求</h2>
          <p>{{ nextStep.detail }}</p>
        </div>
        <button type="button" class="button-primary" @click="goToNextStep">{{ nextStep.action }}</button>
      </section>
      <section class="layout">
        <div class="main-column">
          <RequirementEditor v-if="selected" :requirement="selected" :busy="busy" @save="save" @cancel="selected = null" />
          <div v-if="formalRequirements.length" class="cards"><RequirementCard v-for="item in formalRequirements" :key="item.id" :requirement="item" @edit="edit" @lock="lock" /></div>
        </div>
        <aside>
          <ConflictPanel :conflicts="state.conflicts" @resolve="resolve" />
          <AssumptionPanel :assumptions="assumptions" @decide="decide" />
          <VersionHistory :versions="versions" :busy="busy" @restore="restore" />
        </aside>
      </section>
    </template>
    <div v-else class="status">没有找到项目需求状态。</div>
  </main>
</template>

<style scoped>
.requirements-view{display:grid;gap:16px;max-width:1100px;margin:0 auto}.status{padding:48px;text-align:center;color:var(--color-text-secondary)}.heading{display:flex;align-items:end;justify-content:space-between}.heading span{color:var(--color-accent);font-size:10px;font-weight:750}.heading h1{margin:5px 0 0;font-size:22px}.heading p{color:var(--color-text-secondary);font-size:11px}.requirement-status{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.requirement-status article{display:grid;gap:7px;min-height:96px;padding:12px 13px;border:1px solid var(--color-border);border-radius:9px;background:var(--color-surface)}.requirement-status span{color:var(--color-text-secondary);font-size:11px}.requirement-status strong{color:var(--color-text-primary);font-size:15px}.requirement-status .blocked{border-color:#e2bcbc;background:#fff8f8}.requirement-status .blocked strong{color:#873f3f}.next-card button{align-self:end;justify-self:start;min-height:30px;padding:0 10px;border-radius:7px;font-size:11px}.error,.warning{padding:11px 13px;border-radius:9px;font-size:10px}.error{color:#873f3f;background:#fff8f8}.warning{color:#765313;background:#fff9e8}.empty-guide{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:20px 22px;border:1px solid #ead9a6;border-radius:12px;background:#fff9e8}.empty-guide span{color:#765313;font-size:11px;font-weight:750}.empty-guide h2{margin:5px 0 6px;font-size:18px}.empty-guide p{margin:0;color:var(--color-text-secondary);font-size:12px;line-height:1.6}.empty-guide button{flex-shrink:0;min-height:38px;padding:0 15px;border-radius:8px;font-size:12px}.layout{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:16px}.main-column,.cards,aside{display:grid;align-content:start;gap:11px}.cards{grid-template-columns:repeat(2,minmax(0,1fr))}aside{padding-left:15px;border-left:1px solid var(--color-border)}
</style>
