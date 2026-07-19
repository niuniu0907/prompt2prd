<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { routeLocationKey } from 'vue-router'
import { recommendArchitecture } from '@/api/architectureApi'
import { architectureRepository } from '@/db/repositories/architectureRepository'
import type { ArchitectureCandidate, ArchitectureConfirmedEvent, TechnicalConstraints } from './types'
import ArchitectureComparison from './ArchitectureComparison.vue'
import TechnicalConstraintsForm from './TechnicalConstraintsForm.vue'

const emit = defineEmits<{ architectureConfirmed: [event: ArchitectureConfirmedEvent] }>()
const route = inject(routeLocationKey, null)
const projectId = computed(() => String(route?.params.projectId ?? ''))
const candidates = ref<ArchitectureCandidate[]>([])
const pendingFields = ref<string[]>([])
const confirmedId = ref<string | null>(null)
const busy = ref(false)
const errorMessage = ref('')

onMounted(async () => {
  try {
    candidates.value = await architectureRepository.listCandidates(projectId.value)
    confirmedId.value = (await architectureRepository.selected(projectId.value))?.id ?? null
  } catch (error) { errorMessage.value = readable(error) }
})

async function generate(constraints: TechnicalConstraints) {
  busy.value = true; errorMessage.value = ''
  try {
    const response = await recommendArchitecture(constraints)
    await architectureRepository.saveCandidates(projectId.value, response.candidates)
    candidates.value = response.candidates
    pendingFields.value = response.pendingFields
    confirmedId.value = (await architectureRepository.selected(projectId.value))?.id ?? null
  } catch (error) { errorMessage.value = readable(error) }
  finally { busy.value = false }
}

async function confirm(candidate: ArchitectureCandidate, manual: boolean) {
  busy.value = true; errorMessage.value = ''
  try {
    const result = await architectureRepository.confirm(projectId.value, candidate, manual)
    candidates.value = await architectureRepository.listCandidates(projectId.value)
    confirmedId.value = candidate.id
    emit('architectureConfirmed', result.event)
    window.dispatchEvent(new CustomEvent('architecture_confirmed', { detail: result.event }))
  } catch (error) { errorMessage.value = readable(error) }
  finally { busy.value = false }
}

function readable(error: unknown) { return error instanceof Error ? error.message : '架构操作失败，原状态已保留。' }
</script>

<template>
  <main class="architecture-view">
    <div v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</div>
    <TechnicalConstraintsForm :project-id="projectId" :busy="busy" @submit="generate" />
    <ArchitectureComparison v-if="candidates.length" :candidates="candidates" :pending-fields="pendingFields" :confirmed-id="confirmedId" :busy="busy" @confirm="confirm" />
  </main>
</template>

<style scoped>.architecture-view{display:grid;gap:28px;max-width:1200px;margin:0 auto}.error{padding:11px 13px;border-radius:9px;background:#fff8f8;color:#873f3f;font-size:11px}</style>
