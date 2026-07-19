<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from 'vue'
import { routeLocationKey, useRouter } from 'vue-router'
import { analysisStateRepository, type AnalysisState } from '@/db/repositories/analysisStateRepository'
import { requirementRepository, type ManualRequirementEdit } from '@/db/repositories/requirementRepository'
import { requirementInteractionRepository } from '@/db/repositories/requirementInteractionRepository'
import { versionRepository } from '@/db/repositories/versionRepository'
import type { RequirementItem, RequirementConflict, RequirementVersion } from './types'
import {
  isFormalRequirement,
  requirementToGroup,
  requirementGroupOrder,
  requirementGroupDefaultOpen,
} from './requirementDisplay'
import { useToast } from '@/composables/useToast'
import RequirementGroup from './RequirementGroup.vue'
import RequirementListItem from './RequirementListItem.vue'
import RequirementDetailDrawer from './RequirementDetailDrawer.vue'
import StatusFilter, { type StatusFilterValue } from './StatusFilter.vue'

const route = inject(routeLocationKey, null)
const router = useRouter()
const projectId = computed(() => String(route?.params.projectId ?? ''))
const { success: showSuccess, error: showError } = useToast()

const state = ref<AnalysisState | null>(null)
const versions = ref<RequirementVersion[]>([])
const versionsLoaded = ref(false)
const loading = ref(true)
const busy = ref(false)
const errorMessage = ref('')
const infoMessage = ref('')

const activeFilter = ref<StatusFilterValue>('ALL')
const drawerRequirement = ref<RequirementItem | null>(null)
const collapsedGroups = ref(new Set<string>())

const savingItems = ref(new Set<string>())

const formalRequirements = computed(() => state.value?.requirements.filter(isFormalRequirement) ?? [])

const statusFilterMap: Record<StatusFilterValue, string[]> = {
  ALL: ['UNANALYZED', 'INFERRED', 'PENDING', 'CONFIRMED', 'SKIPPED', 'NOT_APPLICABLE', 'CONFLICTED'],
  CONFIRMED: ['CONFIRMED'],
  PENDING: ['INFERRED', 'PENDING'],
  UNANALYZED: ['UNANALYZED'],
  CONFLICTED: ['CONFLICTED'],
}

const filteredRequirements = computed(() => {
  const allowed = statusFilterMap[activeFilter.value]
  return formalRequirements.value.filter(r => allowed.includes(r.status))
})

const groupedRequirements = computed(() => {
  const groups = new Map<string, RequirementItem[]>()
  for (const req of filteredRequirements.value) {
    const group = requirementToGroup(req.type)
    if (!groups.has(group)) groups.set(group, [])
    groups.get(group)!.push(req)
  }
  return Array.from(groups.entries())
    .sort(([a], [b]) => requirementGroupOrder(a) - requirementGroupOrder(b))
})

const openConflicts = computed(() => state.value?.conflicts.filter(c => c.status === 'OPEN') ?? [])
const openConflictCount = computed(() => openConflicts.value.length)
const blockingConflict = computed(() => openConflicts.value.some(c => c.core))

const confirmedCount = computed(() => formalRequirements.value.filter(r => r.status === 'CONFIRMED').length)
const pendingCount = computed(() => formalRequirements.value.filter(r => r.status === 'INFERRED' || r.status === 'PENDING').length)
const unanalyzedCount = computed(() => formalRequirements.value.filter(r => r.status === 'UNANALYZED').length)
const conflictedCount = computed(() => formalRequirements.value.filter(r => r.status === 'CONFLICTED').length)


const filterCounts = computed<Record<StatusFilterValue, number>>(() => ({
  ALL: formalRequirements.value.length,
  CONFIRMED: confirmedCount.value,
  PENDING: pendingCount.value,
  UNANALYZED: unanalyzedCount.value,
  CONFLICTED: conflictedCount.value,
}))

const drawerRelatedConflicts = computed(() => {
  if (!drawerRequirement.value) return []
  const reqId = drawerRequirement.value.id
  return openConflicts.value.filter(c => c.leftRequirementId === reqId || c.rightRequirementId === reqId)
})

const drawerVersions = computed(() => {
  if (!drawerRequirement.value) return []
  return versions.value.filter(v => {
    const reqs = v.snapshot.requirements
    return reqs.some(r => r.id === drawerRequirement.value!.id)
  })
})

const nextStep = computed(() => {
  if (formalRequirements.value.length === 0) {
    return { label: '回到需求输入', routeName: 'project-overview' as const }
  }
  return {
    label: state.value?.project.stage === 'PRD' || state.value?.project.stage === 'COMPLETED' ? '查看 PRD' : '生成 PRD',
    routeName: 'project-prd' as const,
  }
})

onMounted(load)

// Reload when project changes
watch(projectId, () => {
  versionsLoaded.value = false
  versions.value = []
  loading.value = true
  drawerRequirement.value = null
  collapsedGroups.value = new Set()
  load()
})

async function load() {
  try {
    const loaded = await analysisStateRepository.load(projectId.value)
    state.value = loaded ?? null
    if (drawerRequirement.value) {
      drawerRequirement.value = loaded?.requirements.find(r => r.id === drawerRequirement.value?.id) ?? null
    }
    // Initialize collapsed groups: collapse only AI group by default
    if (collapsedGroups.value.size === 0) {
      const next = new Set<string>()
      for (const [group] of groupedRequirements.value) {
        if (!requirementGroupDefaultOpen(group)) next.add(group)
      }
      collapsedGroups.value = next
    }
  } catch (error) {
    errorMessage.value = readable(error)
  } finally {
    loading.value = false
  }
}

// ── Optimistic actions ────────────────────────────────────────────

async function confirmItem(item: RequirementItem) {
  if (!state.value || savingItems.value.has(item.id)) return
  const prevState = state.value
  const idx = state.value.requirements.findIndex(r => r.id === item.id)
  if (idx === -1) return

  savingItems.value.add(item.id)
  errorMessage.value = ''

  // Optimistic update
  state.value = {
    ...state.value,
    requirements: state.value.requirements.map((r, i) =>
      i === idx
        ? { ...r, status: 'CONFIRMED' as const, confirmedAt: Date.now() }
        : r
    ),
  }

  try {
    if (item.type === 'ASSUMPTION') {
      await requirementInteractionRepository.decideAssumption(item.id, true)
    } else {
      await requirementInteractionRepository.confirmRequirement(item.id)
    }
    showSuccess('已确认')
  } catch (error) {
    // Rollback
    state.value = prevState
    showError('保存失败，已恢复原状态')
  } finally {
    savingItems.value.delete(item.id)
  }
}

async function rejectItem(itemId: string) {
  if (!state.value || savingItems.value.has(itemId)) return
  const idx = state.value.requirements.findIndex(r => r.id === itemId)
  if (idx === -1) return

  const item = state.value.requirements[idx]
  const prevState = state.value

  savingItems.value.add(itemId)
  errorMessage.value = ''

  // Optimistic update
  state.value = {
    ...state.value,
    requirements: state.value.requirements.map((r, i) =>
      i === idx ? { ...r, status: 'SKIPPED' as const } : r
    ),
  }

  try {
    if (item.type === 'ASSUMPTION') {
      await requirementInteractionRepository.decideAssumption(itemId, false)
    } else {
      await requirementInteractionRepository.rejectRequirement(itemId)
    }
    showSuccess('已排除')
  } catch (error) {
    state.value = prevState
    showError('保存失败，已恢复原状态')
  } finally {
    savingItems.value.delete(itemId)
  }
}

async function lockItem(id: string, locked: boolean) {
  if (!state.value || savingItems.value.has(id)) return
  const idx = state.value.requirements.findIndex(r => r.id === id)
  if (idx === -1) return

  const prevState = state.value

  savingItems.value.add(id)
  errorMessage.value = ''

  // Optimistic update
  state.value = {
    ...state.value,
    requirements: state.value.requirements.map((r, i) =>
      i === idx ? { ...r, locked } : r
    ),
  }

  try {
    await requirementInteractionRepository.setLocked(id, locked)
    showSuccess(locked ? '已锁定' : '已解锁')
  } catch (error) {
    state.value = prevState
    showError('保存失败，已恢复原状态')
  } finally {
    savingItems.value.delete(id)
  }
}

async function saveEdit(edit: ManualRequirementEdit) {
  if (!drawerRequirement.value || savingItems.value.has(drawerRequirement.value.id)) return
  const reqId = drawerRequirement.value.id
  const idx = state.value?.requirements.findIndex(r => r.id === reqId)
  if (idx === undefined || idx === -1) return

  const prevState = state.value!
  savingItems.value.add(reqId)
  errorMessage.value = ''

  // Optimistic update
  state.value = {
    ...state.value!,
    requirements: state.value!.requirements.map((r, i) =>
      i === idx
        ? {
            ...r,
            title: edit.title,
            content: edit.content,
            sourceType: 'USER_EDIT' as const,
            updatedAt: new Date().toISOString(),
          }
        : r
    ),
  }

  try {
    await requirementRepository.commitManualEdit(reqId, edit)
    // Refresh drawer requirement
    drawerRequirement.value = state.value?.requirements.find(r => r.id === reqId) ?? null
    showSuccess('已修改')
  } catch (error) {
    state.value = prevState
    showError('保存失败，已恢复原状态')
  } finally {
    savingItems.value.delete(reqId)
  }
}

async function resolveConflict(conflictId: string, resolution: string) {
  if (!state.value || savingItems.value.has(conflictId)) return
  const prevState = state.value

  savingItems.value.add(conflictId)
  errorMessage.value = ''

  // Optimistic update
  state.value = {
    ...state.value,
    conflicts: state.value.conflicts.map(c =>
      c.id === conflictId ? { ...c, status: 'RESOLVED' as const } : c
    ),
  }

  try {
    await requirementInteractionRepository.resolveConflict(conflictId, resolution)
    showSuccess('已解决')
  } catch (error) {
    state.value = prevState
    showError('保存失败，已恢复原状态')
  } finally {
    savingItems.value.delete(conflictId)
  }
}

// ── Drawer & navigation ───────────────────────────────────────────

async function openDrawer(item: RequirementItem) {
  drawerRequirement.value = item
  if (!versionsLoaded.value) {
    try {
      versions.value = await versionRepository.listByProject(projectId.value)
      versionsLoaded.value = true
    } catch {
      // Version history is non-critical; silently swallow
    }
  }
}

function closeDrawer() { drawerRequirement.value = null }

function generateAcceptance(item: RequirementItem) {
  errorMessage.value = ''
  infoMessage.value = '验收标准会进入 PRD 文档生成；如需先补充细节，可以在需求详情中编辑。'
}

function handleDrawerConfirm(reqId: string) {
  const item = state.value?.requirements.find(r => r.id === reqId)
  if (item) confirmItem(item)
}

function handleDrawerSaveEdit(edit: ManualRequirementEdit) {
  saveEdit(edit)
}

function toggleGroup(label: string) {
  const next = new Set(collapsedGroups.value)
  if (next.has(label)) next.delete(label)
  else next.add(label)
  collapsedGroups.value = next
}

function goToNextStep() {
  void router.push({ name: nextStep.value.routeName, params: { projectId: projectId.value } })
}

function readable(error: unknown) {
  return error instanceof Error ? error.message : '本地操作失败，原状态已保留。'
}
</script>

<template>
  <main class="requirements-view">
    <div v-if="loading" class="status">正在读取需求…</div>
    <template v-else-if="state">
      <!-- Header -->
      <header class="heading">
        <div>
          <span>需求结果</span>
          <h1>结构化需求结果</h1>
        </div>
        <div class="heading__right">
          <p>共{{ formalRequirements.length }}项</p>
          <button
            v-if="nextStep.routeName"
            type="button"
            class="button-primary"
            @click="goToNextStep"
          >
            {{ nextStep.label }}
          </button>
        </div>
      </header>

      <!-- Messages -->
      <div v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</div>
      <div v-if="infoMessage" class="info" role="status">{{ infoMessage }}</div>
      <div v-if="blockingConflict" class="warning">
        存在未解决的核心冲突，生成 PRD 时会标记为待处理。
        <button type="button" class="warning__link" @click="activeFilter = 'CONFLICTED'">查看冲突</button>
      </div>

      <!-- Empty guide -->
      <section v-if="formalRequirements.length === 0" class="empty-guide" aria-label="没有可确认需求">
        <div>
          <span>暂无结构化需求</span>
          <h2>还没有可确认的产品需求</h2>
          <p>当前还没有可展示的结构化需求。先回到需求输入页检查分析状态，再补充或重新分析需求。</p>
        </div>
        <button type="button" class="button-primary" @click="goToNextStep">回到需求输入</button>
      </section>

      <!-- Filter -->
      <StatusFilter
        v-if="formalRequirements.length > 0"
        v-model:active-filter="activeFilter"
        :counts="filterCounts"
      />

      <!-- Grouped list with TransitionGroup -->
      <TransitionGroup
        v-if="filteredRequirements.length"
        name="req-list"
        tag="section"
        class="requirement-list"
      >
        <RequirementGroup
          v-for="[groupLabel, items] in groupedRequirements"
          :key="groupLabel"
          :label="groupLabel"
          :count="items.length"
          :collapsed="collapsedGroups.has(groupLabel)"
          @toggle="toggleGroup(groupLabel)"
        >
          <TransitionGroup name="req-item" tag="div">
            <RequirementListItem
              v-for="item in items"
              :key="item.id"
              :requirement="item"
              :saving="savingItems.has(item.id)"
              @view="openDrawer"
              @confirm="confirmItem"
            />
          </TransitionGroup>
        </RequirementGroup>
      </TransitionGroup>
      <p v-else-if="formalRequirements.length > 0" class="no-results">
        当前筛选条件下没有需求。
      </p>

      <!-- Detail drawer -->
      <RequirementDetailDrawer
        :requirement="drawerRequirement"
        :visible="drawerRequirement !== null"
        :busy="busy"
        :versions="drawerVersions"
        :related-conflicts="drawerRelatedConflicts"
        @close="closeDrawer"
        @confirm="handleDrawerConfirm"
        @reject="rejectItem"
        @lock="lockItem"
        @save-edit="handleDrawerSaveEdit"
        @generate-acceptance="generateAcceptance"
        @resolve-conflict="resolveConflict"
      />
    </template>
    <div v-else class="status">没有找到项目需求状态。</div>
  </main>
</template>

<style scoped>
.requirements-view {
  display: grid;
  gap: 16px;
  max-width: 1060px;
  margin: 0 auto;
}

.status {
  padding: 48px;
  text-align: center;
  color: var(--color-text-secondary);
}

/* Heading */
.heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
}
.heading span {
  color: var(--color-accent);
  font-size: 10px;
  font-weight: 750;
}
.heading h1 {
  margin: 5px 0 0;
  font-size: 22px;
}
.heading__right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.heading__right p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 11px;
}
.heading__right .button-primary {
  min-height: 32px;
  padding: 0 14px;
  font-size: 12px;
  border-radius: var(--radius-sm);
}

/* Messages */
.error, .warning, .info {
  padding: 11px 13px;
  border-radius: 9px;
  font-size: 11px;
}
.error {
  color: #873f3f;
  background: #fff8f8;
}
.warning {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #765313;
  background: #fff9e8;
}
.warning__link {
  padding: 0;
  font-size: 11px;
  color: var(--color-accent);
  background: transparent;
  border: none;
  cursor: pointer;
  text-decoration: underline;
}
.info {
  color: #246b58;
  background: #eefaf5;
}

/* Empty guide */
.empty-guide {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 22px;
  border: 1px solid #ead9a6;
  border-radius: 12px;
  background: #fff9e8;
}
.empty-guide span {
  color: #765313;
  font-size: 11px;
  font-weight: 750;
}
.empty-guide h2 {
  margin: 5px 0 6px;
  font-size: 18px;
}
.empty-guide p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.empty-guide button {
  flex-shrink: 0;
  min-height: 38px;
  padding: 0 15px;
  border-radius: 8px;
  font-size: 12px;
}

/* Requirement list */
.requirement-list {
  display: grid;
  gap: 12px;
}

.no-results {
  text-align: center;
  padding: 36px;
  color: var(--color-text-muted);
  font-size: 13px;
}

/* Group-level TransitionGroup */
.req-list-enter-active,
.req-list-leave-active,
.req-list-move {
  transition: opacity var(--motion-base) var(--ease-standard),
              transform var(--motion-base) var(--ease-standard);
}
.req-list-enter-from {
  opacity: 0;
  transform: translateY(-6px);
}
.req-list-leave-to {
  opacity: 0;
}
.req-list-leave-active {
  position: absolute;
}

/* Item-level TransitionGroup */
.req-item-enter-active,
.req-item-leave-active,
.req-item-move {
  transition: opacity var(--motion-base) var(--ease-standard),
              transform var(--motion-base) var(--ease-standard);
}
.req-item-enter-from {
  opacity: 0;
  transform: translateY(-6px);
}
.req-item-leave-to {
  opacity: 0;
}
.req-item-leave-active {
  position: absolute;
}
</style>
