<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  projectRepository,
  type ProjectHomeRepository,
  type ProjectListFilter,
  type ProjectSummary,
} from '@/db/repositories/projectRepository'
import ProjectEmptyState from '@/features/projects/ProjectEmptyState.vue'
import ProjectList from '@/features/projects/ProjectList.vue'
import AppShell from '@/layouts/AppShell.vue'

const props = defineProps<{ repository?: ProjectHomeRepository }>()
const repository = props.repository ?? projectRepository
const router = useRouter()

const viewContent: Record<
  ProjectListFilter,
  { title: string; description: string; sectionTitle: string; sectionDescription: string }
> = {
  ACTIVE: {
    title: '全部项目',
    description: '把模糊想法逐步整理成可执行、可验收的产品文档。',
    sectionTitle: '最近项目',
    sectionDescription: '按最近更新时间查看当前需求分析项目。',
  },
  ARCHIVED: {
    title: '已归档',
    description: '暂时收起不常使用的项目，同时保留完整本地数据。',
    sectionTitle: '归档项目',
    sectionDescription: '可以恢复项目，或将不再需要的项目移入回收站。',
  },
  DELETED: {
    title: '回收站',
    description: '删除的项目仍保存在当前浏览器，永久删除后无法恢复。',
    sectionTitle: '已删除项目',
    sectionDescription: '恢复项目，或确认后永久删除单个项目。',
  },
}

const activeView = ref<ProjectListFilter>('ACTIVE')
const summaries = ref<ProjectSummary[]>([])
const loading = ref(true)
const errorMessage = ref('')
const busyProjectId = ref<string | null>(null)
const statusMessage = ref('')
let loadSequence = 0

const content = computed(() => viewContent[activeView.value])

async function loadProjects() {
  const sequence = ++loadSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await repository.listSummaries(activeView.value)
    if (sequence === loadSequence) summaries.value = result
  } catch {
    if (sequence === loadSequence) errorMessage.value = '读取本地项目失败'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function runOperation(
  projectId: string,
  successMessage: string,
  operation: () => Promise<unknown>,
) {
  busyProjectId.value = projectId
  errorMessage.value = ''
  statusMessage.value = ''
  try {
    await operation()
    statusMessage.value = successMessage
    await loadProjects()
  } catch {
    errorMessage.value = '本地项目操作失败'
  } finally {
    busyProjectId.value = null
  }
}

function renameProject(projectId: string, name: string) {
  return runOperation(projectId, '项目名称已保存', () => repository.rename(projectId, name))
}

function copyProject(projectId: string) {
  return runOperation(projectId, '项目副本已创建', () => repository.copy(projectId))
}

function archiveProject(projectId: string) {
  return runOperation(projectId, '项目已归档', () => repository.archive(projectId))
}

function trashProject(projectId: string) {
  return runOperation(projectId, '项目已移入回收站', () => repository.moveToTrash(projectId))
}

function restoreProject(projectId: string) {
  return runOperation(projectId, '项目已恢复', () => repository.restore(projectId))
}

function permanentlyDeleteProject(projectId: string) {
  return runOperation(projectId, '项目已永久删除', () => repository.permanentlyDelete(projectId))
}

function createProject() {
  void router.push({ name: 'new-project' })
}

watch(activeView, () => {
  summaries.value = []
  statusMessage.value = ''
  void loadProjects()
})

onMounted(() => {
  void loadProjects()
})
</script>

<template>
  <AppShell :active-section="activeView" @navigate="activeView = $event">
    <div class="project-home" data-testid="project-home">
      <header class="project-home__header">
        <div>
          <p class="project-home__eyebrow">项目工作区</p>
          <h1>{{ content.title }}</h1>
          <p>{{ content.description }}</p>
        </div>
        <button
          v-if="activeView === 'ACTIVE'"
          class="button-primary"
          type="button"
          data-testid="new-project-button"
          @click="createProject"
        >
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="M10 4v12M4 10h12" />
          </svg>
          新建项目
        </button>
      </header>

      <main class="project-home__main">
        <div class="section-heading">
          <div>
            <h2>{{ content.sectionTitle }}</h2>
            <p>{{ content.sectionDescription }}</p>
          </div>
          <span class="project-count">{{ summaries.length }} 个项目</span>
        </div>

        <div v-if="errorMessage" class="load-error" role="alert">
          <div>
            <strong>{{ errorMessage }}</strong>
            <span>项目数据没有被修改，请检查浏览器存储权限后重试。</span>
          </div>
          <button type="button" @click="loadProjects">重新加载</button>
        </div>

        <div v-if="loading && summaries.length === 0" class="project-loading" aria-label="正在读取本地项目">
          <span></span>
          <span></span>
          <span></span>
        </div>

        <ProjectList
          v-else-if="summaries.length > 0"
          :projects="summaries"
          :view="activeView"
          :busy-project-id="busyProjectId"
          @rename="renameProject"
          @copy="copyProject"
          @archive="archiveProject"
          @trash="trashProject"
          @restore="restoreProject"
          @permanent-delete="permanentlyDeleteProject"
        />

        <ProjectEmptyState v-else-if="!errorMessage" :view="activeView" @create="createProject" />

        <p class="operation-status" aria-live="polite">{{ statusMessage }}</p>
        <div class="local-notice">
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="M6.5 8V6.5a3.5 3.5 0 1 1 7 0V8M5 8h10v8H5z" />
          </svg>
          <span>项目数据仅保存在当前浏览器中，清除浏览器数据后将无法恢复。</span>
        </div>
      </main>
    </div>
  </AppShell>
</template>

<style scoped>
.project-home {
  min-height: 100vh;
  background: var(--color-background);
}

.project-home__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 128px;
  padding: 24px 40px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.project-home__eyebrow {
  margin: 0 0 7px;
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.project-home__header h1 {
  margin: 0;
  font-size: 25px;
  font-weight: 720;
  letter-spacing: -0.025em;
}

.project-home__header p:last-child {
  margin: 7px 0 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.button-primary svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-width: 1.8;
}

.project-home__main {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 40px 48px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 18px;
}

.section-heading h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.section-heading p {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.project-count {
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-secondary);
  font-size: 11px;
  font-weight: 650;
  background: rgba(255, 255, 255, 0.65);
}

.project-loading {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.project-loading span {
  min-height: 236px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  background-image: linear-gradient(100deg, transparent 20%, rgba(239, 243, 244, 0.85) 50%, transparent 80%);
  background-size: 220% 100%;
  animation: loading-slide 1.4s ease-in-out infinite;
}

.local-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  margin-top: 28px;
  padding: 13px 16px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  color: var(--color-text-secondary);
  font-size: 11px;
  background: rgba(255, 255, 255, 0.62);
}

.load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  border: 1px solid #e8c6c6;
  border-radius: 12px;
  color: #7f3535;
  background: #fff8f8;
}

.load-error div {
  display: grid;
  gap: 4px;
}

.load-error strong {
  font-size: 13px;
}

.load-error span {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.load-error button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #dfb7b7;
  border-radius: 8px;
  color: #7f3535;
  background: var(--color-surface);
  cursor: pointer;
}

.operation-status {
  min-height: 18px;
  margin: 14px 0 0;
  color: #407665;
  font-size: 11px;
  text-align: right;
}

@keyframes loading-slide {
  from {
    background-position: 120% 0;
  }
  to {
    background-position: -120% 0;
  }
}

.local-notice svg {
  width: 15px;
  height: 15px;
  fill: none;
  stroke: var(--color-accent);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
}

@media (max-width: 1080px) {
  .project-home__header,
  .project-home__main {
    padding-inline: 28px;
  }

  .project-loading {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
