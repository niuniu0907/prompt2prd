<script setup lang="ts">
import type { RequirementItem } from './types'
defineProps<{ assumptions: RequirementItem[] }>()
defineEmits<{ decide: [requirementId: string, accepted: boolean] }>()
</script>
<template>
  <section class="assumptions"><article v-for="item in assumptions" :key="item.id"><span>AI 假设</span><strong>{{ item.title }}</strong><p>{{ item.content }}</p><footer><button data-testid="reject-assumption" type="button" @click="$emit('decide', item.id, false)">拒绝</button><button data-testid="accept-assumption" type="button" @click="$emit('decide', item.id, true)">确认假设</button></footer></article></section>
</template>
<style scoped>
.assumptions{display:grid;gap:10px}.assumptions article{padding:15px;border:1px solid var(--color-border);border-radius:11px;background:var(--color-surface)}span{color:var(--color-accent);font-size:9px}strong{display:block;margin-top:5px;font-size:12px}p{color:var(--color-text-secondary);font-size:11px}footer{display:flex;justify-content:flex-end;gap:8px}button{padding:7px 10px;border-radius:8px;cursor:pointer}button:last-child{background:var(--color-primary)}
</style>
