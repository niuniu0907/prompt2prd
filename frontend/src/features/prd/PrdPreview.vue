<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps<{
  content: string
  sectionTitle: string
}>()

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const rendered = computed(() => {
  if (!props.content.trim()) return '<p class="empty-preview">暂无内容</p>'
  return md.render(props.content)
})
</script>

<template>
  <div class="preview" :data-testid="'prd-preview'">
    <div class="preview-header">
      <h2>{{ sectionTitle }}</h2>
      <span class="preview-badge">预览</span>
    </div>
    <div
      class="preview-content"
      :data-testid="'prd-preview-content'"
      v-html="rendered"
    ></div>
  </div>
</template>

<style scoped>
.preview {
  display: grid;
  gap: 8px;
  height: 100%;
}
.preview-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.preview-header h2 {
  margin: 0;
  font-size: 15px;
}
.preview-badge {
  padding: 1px 7px;
  border-radius: 4px;
  background: var(--color-accent);
  color: #262b25;
  font-size: 10px;
  font-weight: 700;
}
.preview-content {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: var(--color-surface);
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.7;
}
.preview-content :deep(h1) { font-size: 18px; margin: 12px 0 6px; }
.preview-content :deep(h2) { font-size: 15px; margin: 10px 0 5px; }
.preview-content :deep(h3) { font-size: 13px; margin: 8px 0 4px; }
.preview-content :deep(p) { margin: 0 0 8px; }
.preview-content :deep(ul), .preview-content :deep(ol) { margin: 0 0 8px; padding-left: 18px; }
.preview-content :deep(code) {
  padding: 1px 4px;
  border-radius: 3px;
  background: var(--color-background);
  font-size: 11px;
}
.preview-content :deep(pre) {
  padding: 10px;
  border-radius: 5px;
  background: var(--color-background);
  overflow-x: auto;
}
.preview-content :deep(pre code) {
  padding: 0;
  background: none;
}
.preview-content :deep(blockquote) {
  margin: 0 0 8px;
  padding: 4px 12px;
  border-left: 3px solid var(--color-accent);
  color: var(--color-text-secondary);
}
.preview-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 0 0 8px;
}
.preview-content :deep(th), .preview-content :deep(td) {
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  text-align: left;
  font-size: 11px;
}
.preview-content :deep(th) {
  background: var(--color-background);
  font-weight: 700;
}
.empty-preview {
  color: var(--color-text-secondary);
  font-style: italic;
}
</style>
