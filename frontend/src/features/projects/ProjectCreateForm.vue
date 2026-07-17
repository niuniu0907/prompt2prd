<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import {
  createTemporaryProjectName,
  type CreateProjectInput,
} from '@/features/projects/types'

export interface ConfirmedProjectFile {
  name: string
  content: string
  chunks: string[]
}

const props = withDefaults(
  defineProps<{
    busy?: boolean
    fileInput?: ConfirmedProjectFile | null
  }>(),
  {
    busy: false,
    fileInput: null,
  },
)

const emit = defineEmits<{ create: [input: CreateProjectInput] }>()

const prompt = ref('')
const errorMessage = ref('')

const normalizedPrompt = computed(() => prompt.value.trim())
const promptCharacterCount = computed(() => Array.from(normalizedPrompt.value).length)
const hasConfirmedFile = computed(
  () => Boolean(props.fileInput?.name.trim() && props.fileInput?.content.trim()),
)
const namingSource = computed(() => normalizedPrompt.value || props.fileInput?.content.trim() || '')
const temporaryName = computed(() =>
  namingSource.value ? createTemporaryProjectName(namingSource.value) : '等待输入产品想法',
)

watch(prompt, () => {
  errorMessage.value = ''
})

function submit() {
  if (props.busy) return
  if (!hasConfirmedFile.value && promptCharacterCount.value < 5) {
    errorMessage.value = '请至少输入 5 个字符的产品描述。'
    return
  }

  const file = hasConfirmedFile.value ? props.fileInput : null
  emit('create', {
    name: temporaryName.value,
    originalPrompt: normalizedPrompt.value,
    uploadedFileName: file?.name.trim() ?? null,
    uploadedFileContent: file?.content ?? null,
    supplementalPrompt: file && normalizedPrompt.value ? normalizedPrompt.value : null,
  })
}
</script>

<template>
  <form class="create-form" novalidate @submit.prevent="submit">
    <div class="create-form__heading">
      <span>产品想法</span>
      <span>{{ promptCharacterCount }} 个字符</span>
    </div>

    <label class="create-form__label" for="project-prompt">
      描述你想做什么
      <span v-if="!hasConfirmedFile">至少 5 个字符</span>
      <span v-else>可填写对文件内容的补充说明</span>
    </label>
    <textarea
      id="project-prompt"
      v-model="prompt"
      :disabled="busy"
      :aria-invalid="Boolean(errorMessage)"
      :aria-describedby="errorMessage ? 'project-prompt-error' : 'project-prompt-help'"
      rows="9"
      placeholder="例如：做一个帮助独立开发者梳理模糊需求，并生成可执行 PRD 的工具。"
    ></textarea>

    <p v-if="errorMessage" id="project-prompt-error" class="create-form__error" role="alert">
      {{ errorMessage }}
    </p>
    <p v-else id="project-prompt-help" class="create-form__help">
      先写清楚目标用户和主要用途即可，业务规则可以在后续问题中逐步补充。
    </p>

    <div v-if="hasConfirmedFile" class="confirmed-file" data-testid="confirmed-file">
      <div>
        <strong>{{ fileInput?.name }}</strong>
        <span>文件内容已确认，将与补充说明一起保存。</span>
      </div>
      <span>{{ Array.from(fileInput?.content ?? '').length }} 字符</span>
    </div>

    <div class="temporary-name">
      <span>临时项目名称</span>
      <strong data-testid="temporary-name">{{ temporaryName }}</strong>
      <p>首次分析成功后，只有你尚未手动改名时才会采用模型建议名称。</p>
    </div>

    <div class="create-form__actions">
      <p>项目会保存在当前浏览器的 IndexedDB 中。</p>
      <button class="button-primary" type="submit" :disabled="busy">
        {{ busy ? '正在创建…' : '开始分析' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.create-form {
  padding: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.create-form__heading,
.create-form__actions,
.confirmed-file,
.temporary-name {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.create-form__heading {
  margin-bottom: 20px;
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 720;
}

.create-form__heading span:last-child {
  color: var(--color-text-secondary);
  font-size: 11px;
  font-weight: 600;
}

.create-form__label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--color-text-primary);
  font-size: 12px;
  font-weight: 680;
}

.create-form__label span {
  color: var(--color-text-secondary);
  font-weight: 500;
}

textarea {
  width: 100%;
  min-height: 190px;
  padding: 15px 16px;
  resize: vertical;
  border: 1px solid var(--color-border-strong);
  border-radius: 12px;
  color: var(--color-text-primary);
  font-size: 14px;
  line-height: 1.7;
  background: var(--color-surface);
  transition: border-color 150ms ease, box-shadow 150ms ease;
}

textarea:focus {
  border-color: var(--color-accent);
  outline: none;
  box-shadow: 0 0 0 3px var(--color-accent-soft);
}

textarea[aria-invalid="true"] {
  border-color: #b96363;
}

textarea:disabled,
button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.create-form__help,
.create-form__error {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.6;
}

.create-form__help {
  color: var(--color-text-secondary);
}

.create-form__error {
  color: #944747;
}

.confirmed-file,
.temporary-name {
  margin-top: 20px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: 11px;
  background: var(--color-surface-muted);
}

.confirmed-file div,
.temporary-name {
  align-items: flex-start;
}

.confirmed-file div {
  display: grid;
  gap: 4px;
}

.confirmed-file strong,
.temporary-name strong {
  font-size: 12px;
}

.confirmed-file span,
.temporary-name span,
.temporary-name p {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.temporary-name {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
}

.temporary-name p {
  grid-column: 2;
  margin: 2px 0 0;
  line-height: 1.5;
}

.create-form__actions {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}

.create-form__actions p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 11px;
}

.create-form__actions .button-primary {
  min-width: 118px;
}
</style>
