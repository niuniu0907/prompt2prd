<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { routeLocationKey } from 'vue-router'
import { analysisStateRepository, type AnalysisState } from '@/db/repositories/analysisStateRepository'
import { requirementRepository, type ManualRequirementEdit } from '@/db/repositories/requirementRepository'
import { requirementInteractionRepository } from '@/db/repositories/requirementInteractionRepository'
import { versionRepository } from '@/db/repositories/versionRepository'
import type { RequirementItem, RequirementVersion } from './types'
import RequirementCard from './RequirementCard.vue'
import RequirementEditor from './RequirementEditor.vue'
import ConflictPanel from './ConflictPanel.vue'
import AssumptionPanel from './AssumptionPanel.vue'
import VersionHistory from '@/features/history/VersionHistory.vue'

const route = inject(routeLocationKey, null)
const projectId = computed(() => String(route?.params.projectId ?? ''))
const state = ref<AnalysisState | null>(null)
const versions = ref<RequirementVersion[]>([])
const selected = ref<RequirementItem | null>(null)
const busy = ref(false)
const loading = ref(true)
const errorMessage = ref('')
const blockingConflict = computed(() => state.value?.conflicts.some(item => item.core && item.status === 'OPEN') ?? false)
const assumptions = computed(() => state.value?.requirements.filter(item => item.type === 'ASSUMPTION' && item.metadata.decision !== 'REJECTED') ?? [])

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
function readable(error: unknown) { return error instanceof Error ? error.message : '本地操作失败，原状态已保留。' }
</script>

<template>
  <main class="requirements-view">
    <div v-if="loading" class="status">正在读取需求…</div>
    <template v-else-if="state">
      <header class="heading"><div><span>结构化需求</span><h1>需求卡片与版本</h1></div><p>{{ state.requirements.length }} 项 · 完整度 {{ state.completeness.total }}%</p></header>
      <div v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</div>
      <div v-if="blockingConflict" class="warning">存在未解决的核心冲突，项目暂时不能标记为完成。</div>
      <section class="layout">
        <div class="main-column">
          <RequirementEditor v-if="selected" :requirement="selected" :busy="busy" @save="save" @cancel="selected = null" />
          <div class="cards"><RequirementCard v-for="item in state.requirements" :key="item.id" :requirement="item" @edit="edit" @lock="lock" /></div>
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
.requirements-view{display:grid;gap:16px;max-width:1100px;margin:0 auto}.status{padding:48px;text-align:center;color:var(--color-text-secondary)}.heading{display:flex;align-items:end;justify-content:space-between}.heading span{color:var(--color-accent);font-size:10px;font-weight:750}.heading h1{margin:5px 0 0;font-size:22px}.heading p{color:var(--color-text-secondary);font-size:11px}.error,.warning{padding:11px 13px;border-radius:9px;font-size:10px}.error{color:#873f3f;background:#fff8f8}.warning{color:#765313;background:#fff9e8}.layout{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:16px}.main-column,.cards,aside{display:grid;align-content:start;gap:11px}.cards{grid-template-columns:repeat(2,minmax(0,1fr))}aside{padding-left:15px;border-left:1px solid var(--color-border)}
</style>
