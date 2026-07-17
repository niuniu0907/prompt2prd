<script setup lang="ts">
import mermaid from 'mermaid'
import { computed, inject, nextTick, onMounted, reactive, ref } from 'vue'
import { routeLocationKey } from 'vue-router'
import { generateFlowcharts } from '@/api/flowchartApi'
import { analysisStateRepository, type AnalysisState } from '@/db/repositories/analysisStateRepository'
import { flowchartRepository } from '@/db/repositories/flowchartRepository'
import { useModelConfigStore } from '@/stores/modelConfigStore'
import { validateMermaid } from './mermaidValidator'
import type { FlowchartDiagramResult, FlowchartDraft, FlowchartRecord } from './types'

const route = inject(routeLocationKey, null)
const projectId = computed(() => String(route?.params.projectId ?? ''))
const modelStore = useModelConfigStore()
const analysisState = ref<AnalysisState | null>(null)
const diagrams = ref<FlowchartRecord[]>([])
const failures = ref<Array<{ key: string; title: string; message: string }>>([])
const missingInformation = ref<string[]>([])
const loading = ref(true)
const generatingKey = ref<string | null>(null)
const errorMessage = ref('')
const copiedKey = ref<string | null>(null)
const renderedSvg = reactive<Record<string, string>>({})

onMounted(async () => {
  mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'neutral' })
  try {
    const [state, saved] = await Promise.all([
      analysisStateRepository.load(projectId.value), flowchartRepository.listByProject(projectId.value),
    ])
    analysisState.value = state ?? null
    diagrams.value = saved
    await renderAll()
  } catch (error) { errorMessage.value = readable(error) }
  finally { loading.value = false }
})

async function generate(targetKey: string | null = null) {
  if (!analysisState.value) return
  generatingKey.value = targetKey ?? 'all'; errorMessage.value = ''; failures.value = []
  try {
    const response = await generateFlowcharts({
      state: backendState(analysisState.value), targetKey, modelSettings: modelSettings(),
    })
    missingInformation.value = response.missingInformation
    const results = [response.mainFlow, ...response.exceptionFlows].filter(
      (item): item is FlowchartDiagramResult => item !== null,
    )
    for (const result of results) await acceptResult(result)
    diagrams.value = await flowchartRepository.listByProject(projectId.value)
    await renderAll()
  } catch (error) { errorMessage.value = readable(error) }
  finally { generatingKey.value = null }
}

async function acceptResult(result: FlowchartDiagramResult) {
  if (result.status === 'FAILED') {
    failures.value.push({ key: result.key, title: result.title, message: result.errorCode ?? '单图生成失败。' })
    return
  }
  const validation = await validateMermaid(result.mermaid)
  if (!validation.valid) {
    failures.value.push({ key: result.key, title: result.title, message: validation.message ?? 'Mermaid 语法无效。' })
    return
  }
  const draft: FlowchartDraft = { key: result.key, type: result.type, title: result.title,
    mermaid: result.mermaid, sourceRequirementIds: result.sourceRequirementIds }
  const existing = await flowchartRepository.getByKey(projectId.value, draft.key)
  if (existing) {
    if (!window.confirm(`新版本“${draft.title}”已通过校验，是否替换当前流程图？`)) return
    await flowchartRepository.replaceAfterConfirmation(projectId.value, draft)
    return
  }
  const saved = await flowchartRepository.saveGenerated(projectId.value, [draft])
  for (const failure of saved.failures) failures.value.push({ key: failure.draft.key, title: failure.draft.title, message: failure.error })
}

async function renderAll() {
  await nextTick()
  for (const diagram of diagrams.value) {
    try {
      const id = `flowchart-${diagram.id.replace(/[^a-zA-Z0-9]/g, '')}`
      renderedSvg[diagram.key] = (await mermaid.render(id, diagram.mermaid)).svg
    } catch { renderedSvg[diagram.key] = '' }
  }
}

async function copySource(diagram: FlowchartRecord) {
  await navigator.clipboard.writeText(diagram.mermaid)
  copiedKey.value = diagram.key
}

function backendState(state: AnalysisState) {
  const { project, requirements, questions, answers, conflicts, completeness } = state
  return { project: { id: project.id, name: project.name, language: project.language,
    stage: project.stage, completeness: project.completeness }, requirements, questions, answers, conflicts, completeness }
}

function modelSettings() {
  const key = modelStore.requestKeyConfig
  return { keySource: key.keySource, provider: modelStore.provider, baseUrl: modelStore.baseUrl || null,
    model: modelStore.model, apiKey: key.keySource === 'USER' ? key.apiKey : null,
    parameters: { temperature: modelStore.temperature } }
}

function readable(error: unknown) { return error instanceof Error ? error.message : '流程图操作失败，已保存内容未改变。' }
</script>

<template>
  <main class="flowchart-view">
    <header class="heading"><div><span>业务流程</span><h1>主流程与异常流程</h1><p>只使用已确认需求生成；每张图独立校验和保存。</p></div><button class="button-primary" type="button" :disabled="loading || Boolean(generatingKey) || !analysisState" @click="generate(null)">{{ generatingKey === 'all' ? '正在生成…' : '生成全部流程图' }}</button></header>
    <div v-if="loading" class="status">正在读取流程图…</div>
    <div v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</div>
    <section v-if="missingInformation.length" class="missing" aria-label="待补充信息"><h2>待补充信息</h2><ul><li v-for="item in missingInformation" :key="item">{{ item }}</li></ul></section>
    <section v-if="failures.length" class="failures" aria-label="生成失败的流程图"><article v-for="failure in failures" :key="`${failure.key}-${failure.message}`"><strong>{{ failure.title }}</strong><span>{{ failure.message }}</span><button type="button" :disabled="Boolean(generatingKey)" @click="generate(failure.key)">单图重试</button></article></section>
    <section v-if="!loading && !diagrams.length" class="empty"><h2>还没有已保存流程图</h2><p>确认核心流程后生成主流程；确认异常事实后才会生成异常流程。</p></section>
    <section class="diagrams">
      <article v-for="diagram in diagrams" :key="diagram.id" class="diagram" :data-testid="`flowchart-${diagram.key}`">
        <header><div><span>{{ diagram.type === 'MAIN' ? '主流程' : '异常流程' }}</span><h2>{{ diagram.title }}</h2></div><div class="actions"><button type="button" @click="copySource(diagram)">{{ copiedKey === diagram.key ? '已复制' : '复制源码' }}</button><button type="button" :disabled="Boolean(generatingKey)" @click="generate(diagram.key)">{{ generatingKey === diagram.key ? '生成中…' : '重新生成' }}</button></div></header>
        <div v-if="renderedSvg[diagram.key]" class="diagram__canvas" v-html="renderedSvg[diagram.key]"></div><div v-else class="diagram__render-error">流程图渲染失败，可复制源码检查。</div>
        <details><summary>查看 Mermaid 源码</summary><pre>{{ diagram.mermaid }}</pre></details>
      </article>
    </section>
  </main>
</template>

<style scoped>
.flowchart-view{display:grid;gap:16px;max-width:1200px;margin:0 auto}.heading{display:flex;align-items:end;justify-content:space-between;gap:20px}.heading span,.diagram header span{color:var(--color-accent);font-size:10px;font-weight:750}.heading h1{margin:4px 0;font-size:22px}.heading p{margin:0;color:var(--color-text-secondary);font-size:11px}.status,.empty{padding:40px;text-align:center;color:var(--color-text-secondary)}.error,.missing,.failures{padding:12px;border-radius:9px;font-size:11px}.error{background:#fff8f8;color:#873f3f}.missing{border:1px solid #ead9a6;background:#fff9e8;color:#765313}.missing h2{margin:0;font-size:12px}.missing ul{margin:6px 0 0}.failures{display:grid;gap:7px;background:#fff8f8}.failures article{display:flex;align-items:center;gap:9px}.failures span{flex:1;color:#873f3f}.diagrams{display:grid;gap:15px}.diagram{padding:16px;border:1px solid var(--color-border);border-radius:11px;background:var(--color-surface);box-shadow:var(--shadow-card)}.diagram>header{display:flex;justify-content:space-between;gap:14px}.diagram h2{margin:4px 0 0;font-size:15px}.actions{display:flex;gap:7px}.actions button,.failures button{padding:7px 10px;border:1px solid var(--color-border);border-radius:6px;background:var(--color-surface);font-size:10px}.diagram__canvas{min-height:240px;margin-top:14px;padding:18px;overflow:auto;border-radius:8px;background:var(--color-background)}.diagram__canvas :deep(svg){display:block;max-width:none;margin:auto}.diagram__render-error{margin-top:14px;padding:30px;text-align:center;color:#873f3f;background:#fff8f8}details{margin-top:10px;color:var(--color-text-secondary);font-size:10px}pre{padding:10px;overflow:auto;background:var(--color-background);color:var(--color-text-primary)}
</style>
