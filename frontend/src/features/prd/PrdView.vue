<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { routeLocationKey } from 'vue-router'
import { analysisStateRepository, type AnalysisState } from '@/db/repositories/analysisStateRepository'
import { prdRepository } from '@/db/repositories/prdRepository'
import { useModelConfigStore } from '@/stores/modelConfigStore'
import { createPrdClient } from '@/api/prdApi'
import type { KnownStreamEvent } from '@/api/streamEvents'
import type { PrdSection } from './types'
import PrdSectionList from './PrdSectionList.vue'
import PrdEditor from './PrdEditor.vue'
import PrdPreview from './PrdPreview.vue'

const route = inject(routeLocationKey, null)
const projectId = computed(() => String(route?.params.projectId ?? ''))
const modelStore = useModelConfigStore()
const prdClient = createPrdClient()

const analysisState = ref<AnalysisState | null>(null)
const sections = ref<PrdSection[]>([])
const activeKey = ref<string | null>(null)
const mode = ref<'edit' | 'preview'>('edit')
const loading = ref(true)
const generating = ref(false)
const generatingKey = ref<string | null>(null)
const errorMessage = ref('')
const warnMessage = ref('')
const unsavedContent = ref<Record<string, string>>({})

onMounted(async () => {
  try {
    const [state] = await Promise.all([
      analysisStateRepository.load(projectId.value),
    ])
    analysisState.value = state ?? null
    sections.value = await prdRepository.initializeSections(projectId.value)
    if (sections.value.length > 0 && !activeKey.value) {
      activeKey.value = sections.value[0].sectionKey
    }
  } catch (error) {
    errorMessage.value = readable(error)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  prdClient.cancel()
})

const activeSection = computed(() =>
  sections.value.find(s => s.sectionKey === activeKey.value) ?? null,
)

const activeContent = computed(() => {
  if (!activeKey.value) return ''
  return unsavedContent.value[activeKey.value] ?? activeSection.value?.content ?? ''
})

function selectSection(key: string) {
  flushSave()
  activeKey.value = key
}

async function toggleLock(key: string) {
  try {
    const section = sections.value.find(s => s.sectionKey === key)
    if (!section) return
    const updated = await prdRepository.lockSection(projectId.value, key, !section.locked)
    const index = sections.value.findIndex(s => s.sectionKey === key)
    if (index >= 0) sections.value[index] = updated
  } catch (error) {
    errorMessage.value = readable(error)
  }
}

function onUpdate(content: string) {
  if (!activeKey.value) return
  unsavedContent.value[activeKey.value] = content
}

async function flushSave() {
  const key = activeKey.value
  if (!key || !(key in unsavedContent.value)) return
  try {
    const updated = await prdRepository.updateContent(
      projectId.value, key, unsavedContent.value[key],
    )
    const index = sections.value.findIndex(s => s.sectionKey === key)
    if (index >= 0) sections.value[index] = updated
    delete unsavedContent.value[key]
  } catch (error) {
    errorMessage.value = readable(error)
  }
}

async function saveCurrent() {
  const key = activeKey.value
  if (!key) return
  const content = unsavedContent.value[key] ?? activeSection.value?.content ?? ''
  try {
    const updated = await prdRepository.saveSection(projectId.value, key, content)
    const index = sections.value.findIndex(s => s.sectionKey === key)
    if (index >= 0) sections.value[index] = updated
    delete unsavedContent.value[key]
  } catch (error) {
    errorMessage.value = readable(error)
  }
}

async function generateAll() {
  if (!analysisState.value) return
  generating.value = true
  generatingKey.value = 'all'
  errorMessage.value = ''
  warnMessage.value = ''

  const contentBuffer: Record<string, string> = {}

  try {
    await prdClient.generateAll(
      {
        state: backendState(analysisState.value),
        missingInformation: [],
        modelSettings: modelSettings(),
      },
      {
        onEvent: (event: KnownStreamEvent) => handleStreamEvent(event, contentBuffer),
        onWarning: (msg: string) => { warnMessage.value = msg },
      },
    )
  } catch (error) {
    if (error instanceof Error && error.name === 'StalePrdError') return
    errorMessage.value = readable(error)
  } finally {
    generating.value = false
    generatingKey.value = null
  }
}

async function generateSection(key: string) {
  if (!analysisState.value) return
  generating.value = true
  generatingKey.value = key
  errorMessage.value = ''

  const contentBuffer: Record<string, string> = {}

  try {
    await prdClient.generateSection(
      key,
      {
        state: backendState(analysisState.value),
        missingInformation: [],
        modelSettings: modelSettings(),
      },
      {
        onEvent: (event: KnownStreamEvent) => handleStreamEvent(event, contentBuffer),
        onWarning: (msg: string) => { warnMessage.value = msg },
      },
    )
  } catch (error) {
    if (error instanceof Error && error.name === 'StalePrdError') return
    errorMessage.value = readable(error)
  } finally {
    generating.value = false
    generatingKey.value = null
  }
}

async function handleStreamEvent(
  event: KnownStreamEvent,
  buffer: Record<string, string>,
) {
  if (event.type === 'section_started') {
    const sectionId = String(event.data.sectionId)
    buffer[sectionId] = ''
    setSectionStatus(sectionId, 'GENERATING', null)
  } else if (event.type === 'section_delta') {
    const sectionId = String(event.data.sectionId)
    buffer[sectionId] = (buffer[sectionId] ?? '') + String(event.data.delta)
  } else if (event.type === 'section_completed') {
    const sectionId = String(event.data.sectionId)
    const content = buffer[sectionId] ?? ''
    delete buffer[sectionId]
    await saveGeneratedSection(sectionId, content)
  } else if (event.type === 'section_failed') {
    const sectionId = String(event.data.sectionId)
    delete buffer[sectionId]
    setSectionStatus(sectionId, 'FAILED', String(event.data.errorCode))
  }
}

async function saveGeneratedSection(sectionKey: string, content: string) {
  try {
    const updated = await prdRepository.saveGeneratedContent(
      projectId.value, sectionKey, content,
    )
    const index = sections.value.findIndex(s => s.sectionKey === sectionKey)
    if (index >= 0) sections.value[index] = updated
    delete unsavedContent.value[sectionKey]
  } catch {
    setSectionStatus(sectionKey, 'FAILED', 'SAVE_FAILED')
  }
}

function setSectionStatus(key: string, status: PrdSection['status'], errorCode: string | null) {
  const index = sections.value.findIndex(s => s.sectionKey === key)
  if (index < 0) return
  sections.value[index] = { ...sections.value[index], status, errorCode }
}

function backendState(state: AnalysisState) {
  const { project, requirements, questions, answers, conflicts, completeness } = state
  return {
    project: {
      id: project.id, name: project.name, language: project.language,
      stage: project.stage, completeness: project.completeness,
    },
    requirements, questions, answers, conflicts, completeness,
  }
}

function modelSettings() {
  const key = modelStore.requestKeyConfig
  return {
    keySource: key.keySource,
    provider: modelStore.provider,
    baseUrl: modelStore.baseUrl || null,
    model: modelStore.model,
    apiKey: key.keySource === 'USER' ? key.apiKey : null,
    parameters: { temperature: modelStore.temperature },
  }
}

function readable(error: unknown) {
  return error instanceof Error ? error.message : 'PRD 操作失败，已保存内容未改变。'
}
</script>

<template>
  <main class="prd-view" data-testid="prd-view">
    <header class="heading">
      <div>
        <span>PRD 文档</span>
        <h1>AI-ready PRD</h1>
        <p>17 个章节按序生成；编辑/预览切换，本地自动保存。</p>
      </div>
      <div class="heading-actions">
        <button
          type="button"
          class="button-primary"
          :disabled="loading || generating || !analysisState"
          data-testid="generate-all-btn"
          @click="generateAll"
        >
          {{ generatingKey === 'all' ? '正在生成…' : '生成全部 PRD' }}
        </button>
      </div>
    </header>

    <div v-if="loading" class="status">正在读取 PRD 数据…</div>

    <div v-if="errorMessage" class="error" role="alert" data-testid="prd-error">{{ errorMessage }}</div>
    <div v-if="warnMessage" class="warning" data-testid="prd-warning">{{ warnMessage }}</div>

    <div v-if="!loading" class="prd-layout">
      <aside class="prd-sidebar" data-testid="prd-sidebar">
        <PrdSectionList
          :sections="sections"
          :active-key="activeKey"
          :generating="generating"
          :generating-key="generatingKey"
          @select="selectSection"
          @toggle-lock="toggleLock"
        />
      </aside>

      <section class="prd-main" data-testid="prd-main">
        <div v-if="!activeSection" class="empty">
          <h2>选择章节</h2>
          <p>从左侧列表选择一个章节，开始编辑或预览。</p>
        </div>

        <template v-else>
          <div class="mode-bar">
            <button
              type="button"
              :class="{ active: mode === 'edit' }"
              data-testid="mode-edit"
              @click="mode = 'edit'"
            >
              编辑
            </button>
            <button
              type="button"
              :class="{ active: mode === 'preview' }"
              data-testid="mode-preview"
              @click="mode = 'preview'"
            >
              预览
            </button>
            <div class="mode-bar-spacer"></div>
            <button
              type="button"
              class="save-btn"
              :disabled="generating || activeSection.locked"
              data-testid="save-section-btn"
              @click="saveCurrent"
            >
              保存
            </button>
            <button
              v-if="activeSection.status !== 'DRAFT'"
              type="button"
              class="regen-btn"
              :disabled="generating || activeSection.locked"
              :data-testid="`regen-${activeSection.sectionKey}`"
              @click="generateSection(activeSection.sectionKey)"
            >
              {{ generatingKey === activeSection.sectionKey ? '生成中…' : '重新生成' }}
            </button>
          </div>

          <PrdEditor
            v-if="mode === 'edit'"
            :content="activeContent"
            :locked="activeSection.locked"
            :section-title="activeSection.title"
            @update="onUpdate"
            @save="flushSave"
          />

          <PrdPreview
            v-else
            :content="activeContent"
            :section-title="activeSection.title"
          />

          <div v-if="activeSection.status === 'FAILED'" class="failed-info" data-testid="section-failed-info">
            章节生成失败{{ activeSection.errorCode ? `（${activeSection.errorCode}）` : '' }}，可点击重新生成。
          </div>
        </template>
      </section>
    </div>
  </main>
</template>

<style scoped>
.prd-view {
  display: grid;
  gap: 16px;
  max-width: 1400px;
  margin: 0 auto;
}
.heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}
.heading span {
  color: var(--color-accent);
  font-size: 10px;
  font-weight: 750;
}
.heading h1 {
  margin: 4px 0;
  font-size: 22px;
}
.heading p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 11px;
}
.heading-actions {
  display: flex;
  gap: 8px;
}
.status, .empty {
  padding: 40px;
  text-align: center;
  color: var(--color-text-secondary);
}
.error, .warning, .failed-info {
  padding: 10px 12px;
  border-radius: 7px;
  font-size: 11px;
}
.error {
  background: #fff8f8;
  color: #873f3f;
}
.warning {
  background: #fff9e8;
  color: #765313;
}
.failed-info {
  background: #fff8f8;
  color: #873f3f;
  margin-top: 8px;
}
.prd-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  min-height: 400px;
}
.prd-sidebar {
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: var(--color-surface);
}
.prd-main {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: var(--color-surface);
  min-height: 350px;
}
.mode-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mode-bar button {
  padding: 4px 12px;
  border: 1px solid var(--color-border);
  border-radius: 5px;
  background: var(--color-surface);
  font-size: 11px;
  cursor: pointer;
}
.mode-bar button.active {
  background: var(--color-accent);
  color: #262b25;
  border-color: var(--color-accent);
}
.mode-bar-spacer {
  flex: 1;
}
.save-btn {
  font-weight: 600;
}
.regen-btn {
  color: #873f3f;
}
</style>
