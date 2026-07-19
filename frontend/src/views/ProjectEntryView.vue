<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { analysisStateRepository } from '@/db/repositories/analysisStateRepository'

const route = useRoute()
const router = useRouter()
const errorMessage = ref('')

function hasAnalysisContent(state: unknown): boolean {
  if (!state || typeof state !== 'object') return false
  const s = state as Record<string, unknown>
  return (Array.isArray(s.requirements) && s.requirements.length > 0)
    || (Array.isArray(s.questions) && s.questions.length > 0)
    || (Array.isArray(s.answers) && s.answers.length > 0)
    || (Array.isArray(s.conflicts) && s.conflicts.length > 0)
}

onMounted(async () => {
  try {
    const projectId = String(route.params.projectId)
    const state = await analysisStateRepository.load(projectId)
    // 有分析内容 → 直接进澄清页；没有 → 走首次分析
    const targetName = hasAnalysisContent(state) ? 'project-questions' : 'project-overview'
    await router.replace({ name: targetName, params: { projectId } })
  } catch {
    errorMessage.value = '读取本地项目失败。'
  }
})
</script>

<template>
  <main v-if="errorMessage" class="entry-error" role="alert">
    <h1>无法打开项目</h1>
    <p>{{ errorMessage }}</p>
  </main>
  <main v-else class="entry-loading" aria-label="正在读取项目状态">正在读取项目…</main>
</template>

<style scoped>
.entry-loading, .entry-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  color: var(--color-text-secondary);
}
.entry-error h1 { margin: 0 0 8px; color: var(--color-text-primary); font-size: 20px; }
</style>
