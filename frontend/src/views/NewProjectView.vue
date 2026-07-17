<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  projectRepository,
  type ProjectCreateRepository,
} from '@/db/repositories/projectRepository'
import ProjectCreateForm from '@/features/projects/ProjectCreateForm.vue'
import RequirementFileUpload from '@/features/projects/RequirementFileUpload.vue'
import type {
  RequirementChunkProcessor,
  UploadPrivacySetting,
} from '@/features/projects/requirementFileUpload'
import type { ParsedRequirementFile } from '@/features/projects/fileParser'
import type { CreateProjectInput } from '@/features/projects/types'
import AppShell from '@/layouts/AppShell.vue'

const props = defineProps<{
  repository?: ProjectCreateRepository
  privacySetting?: UploadPrivacySetting
  processChunk?: RequirementChunkProcessor
}>()
const repository = props.repository ?? projectRepository
const router = useRouter()

const busy = ref(false)
const errorMessage = ref('')
const confirmedFile = ref<ParsedRequirementFile | null>(null)

async function createProject(input: CreateProjectInput) {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    const project = await repository.create(input)
    await router.push({ name: 'project-start', params: { projectId: project.id } })
  } catch {
    errorMessage.value = '项目创建失败，本地数据没有保存。请检查浏览器存储权限后重试。'
  } finally {
    busy.value = false
  }
}

function goHome() {
  void router.push({ name: 'project-home' })
}
</script>

<template>
  <AppShell>
    <main class="new-project" data-testid="new-project-view">
      <header class="new-project__header">
        <button class="back-button" type="button" @click="goHome">
          <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m12.5 5-5 5 5 5" /></svg>
          返回项目列表
        </button>
        <p>新建需求项目</p>
        <h1>先说说你想做什么</h1>
        <span>不用一次写完整。输入一个清晰的起点，后续分析会逐步补齐角色、流程和业务规则。</span>
      </header>

      <section class="new-project__content">
        <div v-if="errorMessage" class="create-error" role="alert">
          <strong>项目创建失败</strong>
          <span>{{ errorMessage }}</span>
        </div>
        <RequirementFileUpload
          :disabled="busy"
          :privacy-setting="privacySetting"
          :process-chunk="processChunk"
          @confirmed="confirmedFile = $event"
          @cleared="confirmedFile = null"
        />
        <ProjectCreateForm :busy="busy" :file-input="confirmedFile" @create="createProject" />

        <aside class="next-steps" aria-label="创建后流程">
          <span>创建后</span>
          <ol>
            <li><strong>保存项目</strong><small>原始输入先写入当前浏览器</small></li>
            <li><strong>进入分析</strong><small>后续步骤将接入真实 AI 分析流</small></li>
            <li><strong>逐步确认</strong><small>通过问题完善需求并提高完整度</small></li>
          </ol>
        </aside>
      </section>
    </main>
  </AppShell>
</template>

<style scoped>
.new-project {
  min-height: 100vh;
  padding: 42px 48px 64px;
  background: var(--color-background);
}

.new-project__header,
.new-project__content {
  max-width: 860px;
  margin: 0 auto;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 30px;
  padding: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  background: transparent;
  cursor: pointer;
}

.back-button svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.new-project__header > p {
  margin: 0 0 8px;
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.new-project__header h1 {
  margin: 0;
  font-size: 30px;
  letter-spacing: -0.035em;
}

.new-project__header > span {
  display: block;
  max-width: 650px;
  margin-top: 10px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.new-project__content {
  margin-top: 28px;
}

.create-error {
  display: grid;
  gap: 4px;
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px solid #e2bcbc;
  border-radius: 11px;
  color: #873f3f;
  font-size: 12px;
  background: #fff8f8;
}

.create-error span {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.next-steps {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 18px;
  margin-top: 18px;
  padding: 18px 22px;
  border: 1px solid var(--color-border);
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.68);
}

.next-steps > span {
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 700;
}

.next-steps ol {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: steps;
}

.next-steps li {
  display: grid;
  gap: 4px;
  counter-increment: steps;
}

.next-steps strong {
  font-size: 12px;
}

.next-steps strong::before {
  margin-right: 6px;
  color: var(--color-accent);
  content: counter(steps) ".";
}

.next-steps small {
  color: var(--color-text-secondary);
  font-size: 10px;
  line-height: 1.5;
}
</style>
