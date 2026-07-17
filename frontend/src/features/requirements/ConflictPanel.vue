<script setup lang="ts">
import type { RequirementConflict } from './types'
defineProps<{ conflicts: RequirementConflict[] }>()
defineEmits<{ resolve: [conflictId: string, resolution: string] }>()
</script>
<template>
  <section class="conflicts"><article v-for="conflict in conflicts.filter(item => item.status === 'OPEN')" :key="conflict.id"><header><strong>{{ conflict.core ? '核心冲突' : '普通冲突' }}</strong><span>{{ conflict.impact }}</span></header><div class="sides"><p>{{ conflict.leftContent }}</p><b>或</b><p>{{ conflict.rightContent }}</p></div><button data-testid="resolve-conflict" type="button" @click="$emit('resolve', conflict.id, '保留左侧内容')">保留左侧内容并解决</button></article></section>
</template>
<style scoped>
.conflicts { display:grid; gap:10px }.conflicts article{padding:15px;border:1px solid #e2bcbc;border-radius:11px;background:#fffafa}.conflicts header{display:flex;justify-content:space-between;gap:10px}.conflicts header span{color:var(--color-text-secondary);font-size:10px}.sides{display:grid;grid-template-columns:1fr auto 1fr;align-items:center;gap:8px}.sides p{padding:9px;background:var(--color-surface);font-size:11px}.sides b{color:var(--color-text-muted);font-size:9px}.conflicts button{color:var(--color-accent);font-size:10px;background:transparent;cursor:pointer}
</style>
