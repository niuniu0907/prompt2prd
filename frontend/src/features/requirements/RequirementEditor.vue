<script setup lang="ts">
import { ref, watch } from 'vue'
import type { RequirementItem } from './types'
import type { ManualRequirementEdit } from '@/db/repositories/requirementRepository'
const props = defineProps<{ requirement: RequirementItem; busy?: boolean }>()
const emit = defineEmits<{ save: [edit: ManualRequirementEdit]; cancel: [] }>()
const title = ref(props.requirement.title); const content = ref(props.requirement.content)
watch(() => props.requirement, value => { title.value = value.title; content.value = value.content })
function save() { if (!props.requirement.locked && (title.value !== props.requirement.title || content.value !== props.requirement.content)) emit('save', { title: title.value, content: content.value, affectedArtifacts: ['FLOWCHART', 'PRD'] }) }
</script>
<template>
  <form class="editor" @submit.prevent="save"><p v-if="requirement.locked">请先解锁后再编辑。</p><input data-testid="requirement-title" v-model="title" :disabled="requirement.locked || busy" @blur="save"><textarea data-testid="requirement-content" v-model="content" :disabled="requirement.locked || busy" @blur="save"/><footer><button type="button" @click="$emit('cancel')">关闭</button><button class="button-primary" type="submit" :disabled="requirement.locked || busy">保存</button></footer></form>
</template>
<style scoped>
.editor{display:grid;gap:10px;padding:16px;border:1px solid var(--color-border);border-radius:12px;background:var(--color-surface)}input,textarea{padding:10px;border:1px solid var(--color-border);border-radius:9px}textarea{min-height:110px;resize:vertical}.editor p{margin:0;color:#873f3f;font-size:10px}footer{display:flex;justify-content:flex-end;gap:8px}footer button:first-child{padding:0 10px;background:transparent;cursor:pointer}
</style>
