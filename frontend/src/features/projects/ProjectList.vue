<script setup lang="ts">
import type {
  ProjectListFilter,
  ProjectSummary,
} from '@/db/repositories/projectRepository'
import ProjectCard from './ProjectCard.vue'

defineProps<{
  projects: ProjectSummary[]
  view: ProjectListFilter
  busyProjectId: string | null
}>()

const emit = defineEmits<{
  rename: [projectId: string, name: string]
  copy: [projectId: string]
  archive: [projectId: string]
  trash: [projectId: string]
  restore: [projectId: string]
  permanentDelete: [projectId: string]
}>()
</script>

<template>
  <section class="project-list" aria-label="项目列表">
    <ProjectCard
      v-for="summary in projects"
      :key="summary.project.id"
      :summary="summary"
      :view="view"
      :busy="busyProjectId === summary.project.id"
      @rename="(id, name) => emit('rename', id, name)"
      @copy="(id) => emit('copy', id)"
      @archive="(id) => emit('archive', id)"
      @trash="(id) => emit('trash', id)"
      @restore="(id) => emit('restore', id)"
      @permanent-delete="(id) => emit('permanentDelete', id)"
    />
  </section>
</template>

<style scoped>
.project-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 1180px) {
  .project-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
