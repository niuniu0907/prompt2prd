<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'

import {
  ModelConfigApiError,
  modelConfigClient,
  type ModelConfigClient,
  type ModelConnectionInput,
} from '@/api/modelConfigApi'
import {
  useModelConfigStore,
  type ModelKeyError,
  type ModelProvider,
} from '@/stores/modelConfigStore'

const props = defineProps<{ client?: ModelConfigClient }>()
const client = props.client ?? modelConfigClient
const store = useModelConfigStore()
const {
  selectedKeySource,
  userApiKey,
  systemKeyAvailable,
  provider,
  baseUrl,
  model,
  temperature,
} = storeToRefs(store)
const loadingCapabilities = ref(true)
const testing = ref(false)
const capabilityError = ref('')
const resultMessage = ref('')
const errorMessage = ref('')

const providers: Array<{ value: ModelProvider; label: string; endpoint: string }> = [
  { value: 'OPENAI', label: 'OpenAI', endpoint: 'https://api.openai.com/v1' },
  { value: 'DEEPSEEK', label: 'DeepSeek', endpoint: 'https://api.deepseek.com/v1' },
  { value: 'QWEN', label: '通义千问', endpoint: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { value: 'CUSTOM', label: '自定义兼容服务', endpoint: '' },
]

const selectedProvider = computed(() => providers.find((item) => item.value === provider.value)!)
const canTest = computed(() => {
  if (!model.value.trim()) return false
  if (provider.value === 'CUSTOM' && !baseUrl.value.trim()) return false
  if (selectedKeySource.value === 'USER' && !userApiKey.value.trim()) return false
  return !testing.value && !loadingCapabilities.value
})

onMounted(async () => {
  try {
    const capabilities = await client.getCapabilities()
    store.setSystemKeyAvailable(capabilities.systemKeyAvailable)
  } catch {
    capabilityError.value = '无法读取服务端模型配置，系统 Key 模式保持关闭。'
    store.setSystemKeyAvailable(false)
  } finally {
    loadingCapabilities.value = false
  }
})

async function testConnection() {
  if (!canTest.value) return
  testing.value = true
  resultMessage.value = ''
  errorMessage.value = ''

  const input: ModelConnectionInput = {
    keySource: selectedKeySource.value,
    provider: provider.value,
    model: model.value.trim(),
    parameters: { temperature: Number(temperature.value) },
  }
  if (provider.value === 'CUSTOM') input.baseUrl = baseUrl.value.trim()
  if (selectedKeySource.value === 'USER') input.apiKey = userApiKey.value

  try {
    const result = await client.testConnection(input)
    resultMessage.value = `连接成功：${result.model}，耗时 ${result.latencyMs} ms`
  } catch (error) {
    const category = error instanceof ModelConfigApiError ? error.code : 'INTERNAL_ERROR'
    const mapped = mapError(category)
    store.recordKeyFailure(mapped.storeError)
    errorMessage.value = mapped.message
  } finally {
    testing.value = false
  }
}

function mapError(code: string): { storeError: ModelKeyError; message: string } {
  switch (code) {
    case 'UNAUTHORIZED':
      return { storeError: 'AUTHENTICATION_FAILED', message: '鉴权失败，请检查当前选择的 API Key。' }
    case 'MODEL_NOT_FOUND':
      return { storeError: 'MODEL_NOT_FOUND', message: '模型不存在或当前账号无权访问。' }
    case 'RATE_LIMIT_EXCEEDED':
      return { storeError: 'RATE_LIMITED', message: '请求受到限流，请稍后重试。' }
    case 'SERVICE_UNAVAILABLE':
      return { storeError: 'UNREACHABLE', message: '服务地址不可达，请检查地址和网络。' }
    case 'FORMAT_INCOMPATIBLE':
      return { storeError: 'INCOMPATIBLE_RESPONSE', message: '响应格式不兼容，请更换模型或服务。' }
    case 'REQUEST_TIMEOUT':
      return { storeError: 'TIMEOUT', message: '连接测试超时，请稍后重试。' }
    case 'BAD_REQUEST':
      return { storeError: 'INVALID_CONFIGURATION', message: '模型配置无效，请检查必填项和服务地址。' }
    default:
      return { storeError: 'INTERNAL', message: '连接测试失败，请稍后重试。' }
  }
}
</script>

<template>
  <section class="model-settings" data-testid="model-settings-panel">
    <header class="model-settings__header">
      <div>
        <p>模型连接</p>
        <h1>模型设置</h1>
        <span>选择本次分析使用的 OpenAI 兼容服务与 Key 来源。</span>
      </div>
      <span class="runtime-badge">仅运行时</span>
    </header>

    <p v-if="capabilityError" class="notice notice--warning" role="alert">{{ capabilityError }}</p>

    <div class="settings-card">
      <fieldset>
        <legend>API Key 来源</legend>
        <label class="choice-card">
          <input
            v-model="selectedKeySource"
            type="radio"
            value="USER"
            data-key-source="USER"
            @change="store.selectKeySource('USER')"
          />
          <span><strong>用户 Key</strong><small>只用于当前浏览器运行时和本次请求</small></span>
        </label>
        <label v-if="systemKeyAvailable" class="choice-card">
          <input
            v-model="selectedKeySource"
            type="radio"
            value="SYSTEM"
            data-key-source="SYSTEM"
            @change="store.selectKeySource('SYSTEM')"
          />
          <span><strong>系统 Key</strong><small>由部署者在服务端环境变量中提供</small></span>
        </label>
      </fieldset>

      <label v-if="selectedKeySource === 'USER'" class="field">
        <span>API Key</span>
        <input
          :value="userApiKey"
          type="password"
          autocomplete="off"
          placeholder="输入当前请求使用的 Key"
          data-testid="api-key-input"
          @input="store.setUserApiKey(($event.target as HTMLInputElement).value)"
        />
      </label>

      <div class="field-grid">
        <label class="field">
          <span>模型服务</span>
          <select v-model="provider" data-testid="provider-select">
            <option v-for="item in providers" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>模型名称</span>
          <input v-model="model" data-testid="model-input" placeholder="例如模型服务提供的模型 ID" />
        </label>
      </div>

      <label v-if="provider === 'CUSTOM'" class="field">
        <span>兼容服务地址</span>
        <input
          v-model="baseUrl"
          data-testid="base-url-input"
          placeholder="https://example.com/v1"
        />
        <small>公网必须使用 HTTPS；仅 localhost、127.0.0.1 和 ::1 可使用 HTTP。</small>
      </label>
      <div v-else class="preset-endpoint">
        <span>预设服务地址</span>
        <code>{{ selectedProvider.endpoint }}</code>
      </div>

      <label class="field field--narrow">
        <span>Temperature</span>
        <input
          v-model.number="temperature"
          type="number"
          min="0"
          max="2"
          step="0.1"
          data-testid="temperature-input"
        />
      </label>

      <p class="privacy-note">用户 Key 不会写入项目数据、浏览器存储或服务端日志，刷新页面后自动清除。</p>

      <div class="settings-actions">
        <button
          class="button-primary"
          type="button"
          :disabled="!canTest"
          data-testid="test-connection"
          @click="testConnection"
        >
          {{ testing ? '正在测试…' : '测试连接' }}
        </button>
        <p v-if="resultMessage" class="test-result test-result--success" role="status">{{ resultMessage }}</p>
        <p v-if="errorMessage" class="test-result test-result--error" role="alert">{{ errorMessage }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.model-settings {
  max-width: 900px;
  margin: 0 auto;
  padding: 36px 42px 56px;
}

.model-settings__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.model-settings__header p {
  margin: 0 0 6px;
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.model-settings__header h1 {
  margin: 0;
  font-size: 28px;
}

.model-settings__header span:not(.runtime-badge) {
  display: block;
  margin-top: 8px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.runtime-badge {
  padding: 7px 10px;
  border-radius: 999px;
  color: var(--color-on-accent);
  font-size: 11px;
  font-weight: 700;
  background: var(--color-accent-soft);
}

.settings-card {
  display: grid;
  gap: 22px;
  padding: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

fieldset {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  border: 0;
}

legend,
.field > span,
.preset-endpoint > span {
  margin-bottom: 9px;
  color: var(--color-text-primary);
  font-size: 12px;
  font-weight: 680;
}

legend {
  grid-column: 1 / -1;
}

.choice-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 11px;
  cursor: pointer;
}

.choice-card:has(input:checked) {
  border-color: #a9cf43;
  background: #f8fce9;
}

.choice-card span {
  display: grid;
  gap: 4px;
}

.choice-card strong {
  font-size: 13px;
}

.choice-card small,
.field small {
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 1.45;
}

.field,
.preset-endpoint {
  display: grid;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.field input,
.field select {
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 9px;
  color: var(--color-text-primary);
  background: var(--color-surface);
}

.field small {
  margin-top: 7px;
}

.field--narrow {
  max-width: 190px;
}

.preset-endpoint {
  padding: 13px 14px;
  border-radius: 10px;
  background: var(--color-surface-muted);
}

.preset-endpoint code {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.privacy-note {
  margin: 0;
  padding: 12px 14px;
  border-left: 3px solid var(--color-accent);
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 1.55;
  background: var(--color-accent-soft);
}

.settings-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.button-primary:disabled {
  color: var(--color-text-muted);
  background: #e5e9da;
  cursor: not-allowed;
  transform: none;
}

.test-result {
  margin: 0;
  font-size: 12px;
}

.test-result--success {
  color: #326c59;
}

.test-result--error,
.notice--warning {
  color: #8b4141;
}

.notice {
  padding: 12px 14px;
  border: 1px solid #ead0b8;
  border-radius: 10px;
  background: #fffaf3;
}
</style>
