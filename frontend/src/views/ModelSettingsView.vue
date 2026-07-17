<script setup lang="ts">
import { useRouter } from 'vue-router'

import type { ProjectListFilter } from '@/db/repositories/projectRepository'
import ModelSettingsPanel from '@/features/model/ModelSettingsPanel.vue'
import AppShell from '@/layouts/AppShell.vue'

const router = useRouter()

function navigateToProjects(_section: ProjectListFilter) {
  void router.push({ name: 'project-home' })
}
</script>

<template>
  <AppShell active-section="MODEL_SETTINGS" @navigate="navigateToProjects">
    <main class="model-settings-view">
      <ModelSettingsPanel />
    </main>
  </AppShell>
</template>

<style scoped>
.model-settings-view {
  min-height: 100vh;
  background: var(--color-background);
}
</style>
