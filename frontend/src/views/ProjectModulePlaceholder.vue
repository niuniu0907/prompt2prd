<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const moduleLabels: Record<string, string> = {
  'project-overview': '需求概览',
  'project-questions': '问题向导',
  'project-requirements': '需求卡片',
  'project-architecture': '架构建议',
  'project-flowchart': '流程图',
  'project-prd': 'PRD',
}

const label = computed(() => moduleLabels[String(route.name ?? '')] ?? '未知模块')
</script>

<template>
  <div class="module-placeholder" data-testid="module-placeholder">
    <h2>{{ label }}</h2>
    <p>此模块将在后续步骤中实现。</p>
  </div>
</template>

<style scoped>
.module-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  color: var(--color-text-secondary);
}

.module-placeholder h2 {
  margin: 0 0 8px;
  color: var(--color-text-primary);
  font-size: 18px;
  font-weight: 640;
}

.module-placeholder p {
  margin: 0;
  font-size: 13px;
}
</style>
