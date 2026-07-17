<script setup lang="ts">
import { computed, ref } from 'vue'

import { appDatabase } from '@/db/appDatabase'
import {
  parseRequirementFile,
  processChunksSequentially,
  type ParsedRequirementFile,
} from './fileParser'
import {
  createUploadPrivacySetting,
  type RequirementChunkProcessor,
  type UploadPrivacySetting,
} from './requirementFileUpload'

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    privacySetting?: UploadPrivacySetting
    processChunk?: RequirementChunkProcessor
  }>(),
  {
    disabled: false,
    privacySetting: undefined,
    processChunk: undefined,
  },
)

const emit = defineEmits<{
  confirmed: [file: ParsedRequirementFile]
  cleared: []
}>()

const defaultPrivacySetting = createUploadPrivacySetting(appDatabase)

const input = ref<HTMLInputElement | null>(null)
const selectedFile = ref<ParsedRequirementFile | null>(null)
const pendingPrivacyFile = ref<ParsedRequirementFile | null>(null)
const errorMessage = ref('')
const reading = ref(false)
const processing = ref(false)
const confirmed = ref(false)
const processedChunks = ref(0)
const progressStatus = ref<'IDLE' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED'>('IDLE')
let controller: AbortController | null = null

const totalCharacters = computed(() => Array.from(selectedFile.value?.content ?? '').length)
const totalChunks = computed(() => selectedFile.value?.chunks.length ?? 0)
const controlsDisabled = computed(() => props.disabled || reading.value || processing.value)
const progressText = computed(() => {
  if (progressStatus.value === 'CANCELLED') {
    return `已取消，已处理 ${processedChunks.value} / ${totalChunks.value} 个片段`
  }
  if (progressStatus.value === 'COMPLETED') {
    return `已完成 ${processedChunks.value} / ${totalChunks.value} 个片段`
  }
  if (progressStatus.value === 'PROCESSING') {
    return `正在准备 ${processedChunks.value} / ${totalChunks.value} 个片段`
  }
  return `共 ${totalChunks.value} 个片段，尚未开始处理`
})

function currentPrivacySetting(): UploadPrivacySetting {
  return props.privacySetting ?? defaultPrivacySetting
}

function resetSelection(shouldEmit: boolean) {
  controller?.abort()
  controller = null
  selectedFile.value = null
  pendingPrivacyFile.value = null
  errorMessage.value = ''
  processedChunks.value = 0
  progressStatus.value = 'IDLE'
  confirmed.value = false
  if (input.value) input.value.value = ''
  if (shouldEmit) emit('cleared')
}

async function selectFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || controlsDisabled.value) return

  if (confirmed.value) emit('cleared')
  errorMessage.value = ''
  selectedFile.value = null
  pendingPrivacyFile.value = null
  confirmed.value = false
  processedChunks.value = 0
  progressStatus.value = 'IDLE'
  reading.value = true

  try {
    const parsedFile = await parseRequirementFile(file)
    if (await currentPrivacySetting().isAccepted()) {
      selectedFile.value = parsedFile
    } else {
      pendingPrivacyFile.value = parsedFile
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '文件读取失败，请重新选择 Markdown 或 TXT 文件。'
    if (input.value) input.value.value = ''
  } finally {
    reading.value = false
  }
}

async function acceptPrivacy() {
  if (!pendingPrivacyFile.value) return
  errorMessage.value = ''
  try {
    await currentPrivacySetting().accept()
    selectedFile.value = pendingPrivacyFile.value
    pendingPrivacyFile.value = null
  } catch {
    errorMessage.value = '隐私确认状态无法保存，请检查浏览器存储权限后重试。'
  }
}

function declinePrivacy() {
  pendingPrivacyFile.value = null
  if (input.value) input.value.value = ''
}

async function confirmFile() {
  const file = selectedFile.value
  if (!file || controlsDisabled.value || confirmed.value) return

  controller = new AbortController()
  const activeController = controller
  processing.value = true
  errorMessage.value = ''
  processedChunks.value = 0
  progressStatus.value = 'PROCESSING'

  try {
    await processChunksSequentially(
      file.chunks,
      async (chunk, index, total) => {
        await props.processChunk?.(chunk, index, total, activeController.signal)
        processedChunks.value = index + 1
      },
      activeController.signal,
    )
    progressStatus.value = 'COMPLETED'
    confirmed.value = true
    emit('confirmed', file)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      progressStatus.value = 'CANCELLED'
    } else {
      progressStatus.value = 'IDLE'
      errorMessage.value = '文件片段处理失败，原文件未确认。请重试。'
    }
  } finally {
    if (controller === activeController) controller = null
    processing.value = false
  }
}

function cancelProcessing() {
  controller?.abort()
}
</script>

<template>
  <section class="file-upload" aria-labelledby="requirement-file-title">
    <div class="file-upload__heading">
      <div>
        <span>可选</span>
        <h2 id="requirement-file-title">上传已有需求文档</h2>
      </div>
      <label class="file-picker" :class="{ 'file-picker--disabled': controlsDisabled }">
        {{ selectedFile ? '更换文件' : reading ? '正在读取…' : '选择文件' }}
        <input
          ref="input"
          type="file"
          accept=".md,.txt,text/markdown,text/plain"
          :disabled="controlsDisabled"
          @change="selectFile"
        />
      </label>
    </div>

    <p class="file-upload__help">支持 UTF-8 编码的 .md、.txt 文件，最大 2 MB。</p>
    <p data-testid="privacy-reminder" class="privacy-reminder">
      文件内容确认后会发送给你选择的大模型服务商，请勿上传密码、密钥等敏感信息。
    </p>

    <p v-if="errorMessage" class="file-upload__error" role="alert">{{ errorMessage }}</p>

    <div v-if="pendingPrivacyFile" class="privacy-dialog" role="dialog" aria-modal="true">
      <strong>发送文件前请确认</strong>
      <p>文件内容将发送给用户选择的大模型服务商进行分析，请勿上传密码、密钥等敏感信息。</p>
      <div>
        <button type="button" class="button-secondary" @click="declinePrivacy">取消上传</button>
        <button
          type="button"
          class="button-primary"
          data-testid="accept-privacy"
          @click="acceptPrivacy"
        >
          我已了解并继续
        </button>
      </div>
    </div>

    <div v-if="selectedFile" class="file-preview" data-testid="file-preview">
      <div class="file-preview__meta">
        <div>
          <strong>{{ selectedFile.name }}</strong>
          <span>{{ totalCharacters }} 个字符 · {{ totalChunks }} 个片段</span>
        </div>
        <button
          type="button"
          class="text-button"
          data-testid="clear-file"
          :disabled="controlsDisabled"
          @click="resetSelection(true)"
        >
          移除
        </button>
      </div>

      <label for="requirement-file-preview">文本预览</label>
      <textarea
        id="requirement-file-preview"
        :value="selectedFile.content"
        readonly
        rows="10"
      ></textarea>

      <div class="file-preview__footer">
        <span data-testid="chunk-progress" aria-live="polite">{{ progressText }}</span>
        <button
          v-if="processing"
          type="button"
          class="button-secondary"
          data-testid="cancel-processing"
          @click="cancelProcessing"
        >
          取消处理
        </button>
        <button
          v-else
          type="button"
          class="button-primary"
          data-testid="confirm-file"
          :disabled="props.disabled || confirmed"
          @click="confirmFile"
        >
          {{ confirmed ? '文件已确认' : '确认使用此文件' }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.file-upload {
  margin-bottom: 18px;
  padding: 22px 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.file-upload__heading,
.file-preview__meta,
.file-preview__footer,
.privacy-dialog > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.file-upload__heading > div {
  display: flex;
  align-items: baseline;
  gap: 9px;
}

.file-upload__heading span {
  color: var(--color-accent);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.file-upload h2 {
  margin: 0;
  font-size: 15px;
}

.file-picker {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid var(--color-border-strong);
  border-radius: 9px;
  color: var(--color-text-primary);
  font-size: 12px;
  font-weight: 650;
  background: var(--color-surface-muted);
  cursor: pointer;
}

.file-picker input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
}

.file-picker--disabled,
button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.file-upload__help,
.privacy-reminder,
.file-upload__error {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.6;
}

.file-upload__help {
  color: var(--color-text-secondary);
}

.privacy-reminder {
  padding: 9px 11px;
  border-left: 3px solid var(--color-accent);
  color: var(--color-text-secondary);
  background: var(--color-accent-soft);
}

.file-upload__error {
  color: #944747;
}

.privacy-dialog,
.file-preview {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--color-border-strong);
  border-radius: 11px;
  background: var(--color-surface-muted);
}

.privacy-dialog strong,
.file-preview strong {
  font-size: 13px;
}

.privacy-dialog p {
  margin: 8px 0 14px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.65;
}

.button-secondary,
.text-button {
  border: 0;
  color: var(--color-text-secondary);
  background: transparent;
  cursor: pointer;
}

.button-secondary {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--color-border-strong);
  border-radius: 9px;
  background: var(--color-surface);
}

.file-preview__meta > div {
  display: grid;
  gap: 3px;
}

.file-preview__meta span,
.file-preview__footer > span {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.file-preview > label {
  display: block;
  margin: 14px 0 7px;
  color: var(--color-text-primary);
  font-size: 11px;
  font-weight: 650;
}

.file-preview textarea {
  width: 100%;
  min-height: 170px;
  padding: 12px 13px;
  resize: vertical;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  color: var(--color-text-primary);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 11px;
  line-height: 1.65;
  background: var(--color-surface);
}

.file-preview__footer {
  margin-top: 13px;
}
</style>
