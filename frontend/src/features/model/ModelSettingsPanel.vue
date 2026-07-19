<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'

import {
  ModelConfigApiError,
  modelConfigClient,
  type AvailableModel,
  type ModelConfigClient,
  type ModelConnectionInput,
  type ModelListInput,
} from '@/api/modelConfigApi'
import {
  presetBaseUrl,
  providerLabels,
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
  rememberApiKey,
  systemKeyAvailable,
  provider,
  baseUrl,
  model,
  temperature,
  connected,
} = storeToRefs(store)
const loadingCapabilities = ref(true)
const loadingModels = ref(false)
const testing = ref(false)
const capabilityError = ref('')
const modelListError = ref('')
let modelListGeneration = 0
let modelListDebounceTimer: ReturnType<typeof setTimeout> | undefined
const resultMessage = ref('')
const errorMessage = ref('')
const availableModels = ref<AvailableModel[]>([])
const manualModelAllowed = ref(false)

const providers: Array<{ value: ModelProvider; label: string }> = [
  { value: 'DEEPSEEK', label: 'DeepSeek' },
  { value: 'OPENAI', label: 'OpenAI' },
  { value: 'CUSTOM', label: '其他 OpenAI 兼容服务' },
]

const hasUsableCredential = computed(() => {
  if (selectedKeySource.value === 'SYSTEM') return systemKeyAvailable.value
  return Boolean(userApiKey.value.trim())
})

const canFetchModels = computed(() => {
  if (!hasUsableCredential.value) return false
  if (provider.value === 'CUSTOM' && !baseUrl.value.trim()) return false
  return !loadingCapabilities.value
})

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
    await store.loadRememberedApiKey()
  } catch {
    capabilityError.value = '无法读取服务端模型配置，系统 Key 模式保持关闭。'
    store.setSystemKeyAvailable(false)
  } finally {
    loadingCapabilities.value = false
    await refreshModels()
  }
})

watch(
  () => [selectedKeySource.value, userApiKey.value, provider.value, baseUrl.value],
  () => {
    resultMessage.value = ''
    errorMessage.value = ''
    scheduleRefreshModels()
  },
)

function scheduleRefreshModels() {
  if (modelListDebounceTimer !== undefined) clearTimeout(modelListDebounceTimer)
  modelListDebounceTimer = setTimeout(() => {
    modelListDebounceTimer = undefined
    void refreshModels()
  }, 400)
}

function selectProvider(value: string) {
  store.setProvider(value as ModelProvider)
}

function selectModel(value: string) {
  store.setModel(value)
}

async function setRemember(value: boolean) {
  try {
    await store.setRememberApiKey(value)
  } catch {
    errorMessage.value = 'Key 记住设置保存失败，请检查浏览器本地数据权限。'
  }
}

async function refreshModels() {
  if (!canFetchModels.value) {
    availableModels.value = []
    manualModelAllowed.value = false
    modelListError.value = ''
    return
  }
  const generation = ++modelListGeneration
  loadingModels.value = true
  modelListError.value = ''
  manualModelAllowed.value = false
  try {
    const result = await client.listModels(listModelsInput())
    if (generation !== modelListGeneration) return // stale response
    availableModels.value = result.models
    if (result.models.length === 0) {
      manualModelAllowed.value = true
      modelListError.value = '服务返回的模型列表为空，可在高级设置中手动填写模型 ID。'
      return
    }
    if (!result.models.some((item) => item.id === model.value)) {
      store.setModel(result.models[0]!.id)
    }
  } catch (error) {
    availableModels.value = []
    manualModelAllowed.value = true
    const category = error instanceof ModelConfigApiError ? error.code : 'INTERNAL_ERROR'
    modelListError.value = mapModelListError(category)
  } finally {
    loadingModels.value = false
  }
}

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
    store.markConnected()
    resultMessage.value = `已连接：${friendlyModelName(result.model)}，配置已保存。`
  } catch (error) {
    const category = error instanceof ModelConfigApiError ? error.code : 'INTERNAL_ERROR'
    const mapped = mapError(category)
    store.recordKeyFailure(mapped.storeError)
    errorMessage.value = mapped.message
  } finally {
    testing.value = false
  }
}

function listModelsInput(): ModelListInput {
  const input: ModelListInput = {
    keySource: selectedKeySource.value,
    provider: provider.value,
  }
  if (provider.value === 'CUSTOM') input.baseUrl = baseUrl.value.trim()
  if (selectedKeySource.value === 'USER') input.apiKey = userApiKey.value
  return input
}

function friendlyModelName(modelId: string): string {
  return availableModels.value.find((item) => item.id === modelId)?.displayName ?? modelId
}

function mapModelListError(code: string): string {
  switch (code) {
    case 'UNAUTHORIZED':
      return '无法获取模型列表：鉴权失败。修正 Key 后会重新获取；暂时可在高级设置中手动填写模型 ID。'
    case 'SERVICE_UNAVAILABLE':
      return '模型列表接口不可用，可在高级设置中手动填写模型 ID。'
    case 'RATE_LIMIT_EXCEEDED':
      return '模型列表接口被限流，可稍后重试，或临时手动填写模型 ID。'
    default:
      return '模型列表接口暂不可用，可在高级设置中手动填写模型 ID。'
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
        <span>选择服务商，填写 Key 后自动获取可用模型。</span>
      </div>
      <span class="runtime-badge" :class="{ 'runtime-badge--connected': connected }">
        {{ connected ? '已连接' : '未连接' }}
      </span>
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
          <span><strong>用户 Key</strong><small>默认保存到当前标签页会话，关闭标签页后清除</small></span>
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
          v-model="userApiKey"
          type="password"
          autocomplete="off"
          placeholder="输入当前服务商的 Key"
          data-testid="api-key-input"
        />
      </label>

      <label v-if="selectedKeySource === 'USER'" class="switch-field">
        <input
          :checked="rememberApiKey"
          type="checkbox"
          data-testid="remember-key-toggle"
          @change="setRemember(($event.target as HTMLInputElement).checked)"
        />
        <span>在此设备记住 Key</span>
      </label>
      <p v-if="selectedKeySource === 'USER' && rememberApiKey" class="notice notice--security">
        Key 将保存在此浏览器的 IndexedDB 中。只在个人可信设备开启，清理站点数据会删除它。
      </p>

      <div class="field-grid">
        <label class="field">
          <span>模型服务</span>
          <select
            :value="provider"
            data-testid="provider-select"
            @change="selectProvider(($event.target as HTMLSelectElement).value)"
          >
            <option v-for="item in providers" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>模型名称</span>
          <select
            v-if="availableModels.length > 0 && !manualModelAllowed"
            :value="model"
            data-testid="model-select"
            @change="selectModel(($event.target as HTMLSelectElement).value)"
          >
            <option v-for="item in availableModels" :key="item.id" :value="item.id">
              {{ item.displayName }}
            </option>
          </select>
          <input
            v-else
            :value="model"
            data-testid="model-display"
            disabled
            :placeholder="loadingModels ? '正在获取模型列表…' : '填写 Key 后自动获取模型列表'"
          />
        </label>
      </div>

      <p v-if="modelListError" class="notice notice--warning" role="alert">{{ modelListError }}</p>

      <label class="field field--narrow">
        <span>Temperature</span>
        <input
          :value="temperature"
          type="number"
          min="0"
          max="2"
          step="0.1"
          data-testid="temperature-input"
          @input="store.setTemperature(Number(($event.target as HTMLInputElement).value))"
        />
      </label>

      <details class="advanced-settings">
        <summary>高级设置</summary>
        <label class="field">
          <span>Base URL</span>
          <input
            v-model="baseUrl"
            :disabled="provider !== 'CUSTOM'"
            data-testid="base-url-input"
            placeholder="https://example.com/v1"
          />
          <small v-if="provider !== 'CUSTOM'">{{ providerLabels[provider] }} 会自动使用此地址。</small>
          <small v-else>公网必须使用 HTTPS；仅 localhost、127.0.0.1 和 ::1 可使用 HTTP。</small>
        </label>

        <label class="field">
          <span>自定义模型 ID</span>
          <input
            :value="model"
            :disabled="!manualModelAllowed"
            data-testid="manual-model-input"
            placeholder="仅当模型列表不可用时填写"
            @input="store.setModel(($event.target as HTMLInputElement).value)"
          />
          <small>正常情况下请使用上方模型下拉；只有模型列表接口不可用时才允许手动填写。</small>
        </label>
      </details>

      <div class="settings-actions">
        <button
          class="button-primary"
          type="button"
          :disabled="!canTest"
          data-testid="test-connection"
          @click="testConnection"
        >
          {{ testing ? '正在测试…' : '测试连接并保存' }}
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

.runtime-badge--connected {
  background: #e7f6d2;
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
.field > span {
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
  border-radius: 8px;
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

.field {
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
  border-radius: 8px;
  color: var(--color-text-primary);
  background: var(--color-surface);
}

.field input:disabled {
  color: var(--color-text-secondary);
  background: var(--color-surface-muted);
}

.field small {
  margin-top: 7px;
}

.field--narrow {
  max-width: 190px;
}

.switch-field {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-primary);
  font-size: 13px;
}

.advanced-settings {
  display: grid;
  gap: 16px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}

.advanced-settings[open] {
  gap: 18px;
}

.advanced-settings summary {
  cursor: pointer;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 700;
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
  margin: 0;
  padding: 12px 14px;
  border: 1px solid #ead0b8;
  border-radius: 8px;
  background: #fffaf3;
  font-size: 12px;
  line-height: 1.5;
}

.notice--security {
  border-color: #c9d9aa;
  color: #415b32;
  background: #f7fbe8;
}
</style>
