<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  projectRepository,
  type ProjectLookupRepository,
} from '@/db/repositories/projectRepository'
import type { Project } from '@/features/projects/types'
import AppShell from '@/layouts/AppShell.vue'

const props = defineProps<{ repository?: ProjectLookupRepository }>()
const repository = props.repository ?? projectRepository
const route = useRoute()
const router = useRouter()

const project = ref<Project | null>(null)
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    const found = await repository.getById(String(route.params.projectId))
    if (!found) {
      errorMessage.value = '没有找到这个本地项目。'
      return
    }
    project.value = found
  } catch {
    errorMessage.value = '读取本地项目失败。'
  } finally {
    loading.value = false
  }
})

function goHome() {
  void router.push({ name: 'project-home' })
}
</script>

<template>
  <AppShell>
    <main class="project-start" data-testid="project-start-view">
      <button type="button" @click="goHome">← 返回项目列表</button>
      <section v-if="loading" aria-label="正在读取项目">正在读取项目…</section>
      <section v-else-if="errorMessage" class="project-start__panel" role="alert">
        <h1>无法打开项目</h1>
        <p>{{ errorMessage }}</p>
      </section>
      <section v-else-if="project" class="project-start__panel">
        <span>项目已保存</span>
        <h1>{{ project.name }}</h1>
        <p>原始需求已安全写入当前浏览器。真实 AI 分析流将在后续分析步骤中接入。</p>
        <dl>
          <div><dt>当前阶段</dt><dd>需求澄清</dd></div>
          <div><dt>完整度</dt><dd>{{ project.completeness }}%</dd></div>
          <div><dt>保存位置</dt><dd>浏览器 IndexedDB</dd></div>
        </dl>
      </section>
    </main>
  </AppShell>
</template>

<style scoped>
.project-start {
  min-height: 100vh;
  padding: 42px 48px;
  background: var(--color-background);
}

.project-start > button {
  padding: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  background: transparent;
  cursor: pointer;
}

.project-start__panel {
  max-width: 760px;
  margin: 36px auto 0;
  padding: 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.project-start__panel > span {
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 740;
}

.project-start__panel h1 {
  margin: 10px 0;
  font-size: 28px;
}

.project-start__panel > p {
  color: var(--color-text-secondary);
  line-height: 1.7;
}

dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 28px 0 0;
}

dl div {
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface-muted);
}

dt {
  color: var(--color-text-secondary);
  font-size: 10px;
}

dd {
  margin: 6px 0 0;
  font-size: 12px;
  font-weight: 680;
}
</style>
