<script setup lang="ts">
import type { RequirementVersion } from '@/features/requirements/types'
defineProps<{ versions: RequirementVersion[]; busy?: boolean }>()
const emit = defineEmits<{ restore: [versionId: string] }>()
function restore(id: string) { if (window.confirm('恢复前会自动保存当前状态，确定继续吗？')) emit('restore', id) }
</script>
<template>
  <section class="history"><h2>版本历史</h2><p v-if="!versions.length">还没有版本记录。</p><article v-for="version in versions" :key="version.id"><div><strong>{{ version.summary }}</strong><small>{{ new Date(version.createdAt).toLocaleString('zh-CN') }}</small></div><button data-testid="restore-version" type="button" :disabled="busy" @click="restore(version.id)">恢复</button></article></section>
</template>
<style scoped>
.history{display:grid;gap:9px}.history h2{font-size:13px}.history>p{color:var(--color-text-muted);font-size:10px}.history article{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:11px;border:1px solid var(--color-border);border-radius:9px}.history article div{display:grid;gap:3px}.history strong{font-size:10px}.history small{color:var(--color-text-muted);font-size:9px}.history button{color:var(--color-accent);font-size:10px;background:transparent;cursor:pointer}
</style>
