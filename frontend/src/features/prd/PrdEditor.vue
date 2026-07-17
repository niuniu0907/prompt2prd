<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  content: string
  locked: boolean
  sectionTitle: string
}>()

const emit = defineEmits<{
  update: [content: string]
  save: []
}>()

const editContent = ref(props.content)
let saveTimer: ReturnType<typeof setTimeout> | undefined

watch(() => props.content, (value) => {
  editContent.value = value
})

function onInput() {
  emit('update', editContent.value)
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => emit('save'), 2000)
}

const charCount = computed(() => editContent.value.length)
</script>

<template>
  <div class="editor" :data-testid="'prd-editor'">
    <div class="editor-header">
      <h2>{{ sectionTitle }}</h2>
      <span class="char-count">{{ charCount }} 字符</span>
    </div>
    <textarea
      v-model="editContent"
      class="editor-textarea"
      :disabled="locked"
      :aria-label="`编辑 ${sectionTitle}`"
      :data-testid="'prd-editor-textarea'"
      @input="onInput"
    ></textarea>
    <div v-if="locked" class="locked-notice" role="alert">
      🔒 此章节已锁定，解锁后才能编辑。
    </div>
  </div>
</template>

<style scoped>
.editor {
  display: grid;
  gap: 8px;
  height: 100%;
}
.editor-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.editor-header h2 {
  margin: 0;
  font-size: 15px;
}
.char-count {
  font-size: 10px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.editor-textarea {
  width: 100%;
  min-height: 300px;
  flex: 1;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  font-size: 12px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  line-height: 1.6;
  resize: vertical;
  tab-size: 2;
}
.editor-textarea:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.locked-notice {
  padding: 6px 10px;
  border-radius: 6px;
  background: #fff8e1;
  color: #765313;
  font-size: 11px;
}
</style>
