import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'

import { appDatabase, type AppDatabase } from '@/db/appDatabase'

export type ModelKeySource = 'SYSTEM' | 'USER'
export type ModelProvider = 'OPENAI' | 'DEEPSEEK' | 'CUSTOM'

export type ModelKeyError =
  | 'AUTHENTICATION_FAILED'
  | 'MODEL_NOT_FOUND'
  | 'RATE_LIMITED'
  | 'UNREACHABLE'
  | 'INCOMPATIBLE_RESPONSE'
  | 'TIMEOUT'
  | 'INVALID_CONFIGURATION'
  | 'INTERNAL'

export type RequestKeyConfig =
  | Readonly<{ keySource: 'SYSTEM' }>
  | Readonly<{ keySource: 'USER'; apiKey: string }>

const PRESET_BASE_URLS: Record<Exclude<ModelProvider, 'CUSTOM'>, string> = {
  OPENAI: 'https://api.openai.com/v1',
  DEEPSEEK: 'https://api.deepseek.com/v1',
}

const SESSION_KEY = 'prompt2prd:model-api-key'
const STORAGE_KEY = 'prompt2prd:model-config'
const REMEMBERED_KEY = 'rememberedModelApiKey'

interface StoredModelConfig {
  provider?: ModelProvider
  baseUrl?: string
  model?: string
  temperature?: number
}

interface RememberedModelApiKey {
  apiKey: string
}

export const providerLabels: Record<ModelProvider, string> = {
  OPENAI: 'OpenAI',
  DEEPSEEK: 'DeepSeek',
  CUSTOM: '其他 OpenAI 兼容服务',
}

export function presetBaseUrl(provider: ModelProvider): string {
  return provider === 'CUSTOM' ? '' : PRESET_BASE_URLS[provider]
}

/** Model settings persistence: API Key defaults to sessionStorage; non-sensitive endpoint choices use localStorage. */
export const useModelConfigStore = defineStore('model-config', () => {
  const stored = readStoredModelConfig()
  const selectedKeySource = ref<ModelKeySource>('USER')
  const userApiKey = ref(readSessionApiKey())
  const rememberApiKey = ref(false)
  const systemKeyAvailable = ref(false)
  const lastKeyError = ref<ModelKeyError | null>(null)
  const provider = ref<ModelProvider>(stored.provider ?? 'DEEPSEEK')
  const baseUrl = ref(stored.baseUrl ?? presetBaseUrl(provider.value))
  const model = ref(stored.model ?? '')
  const temperature = ref(stored.temperature ?? 0.2)
  const connected = ref(false)

  normalizeProviderConfiguration()

  watch(userApiKey, (apiKey) => {
    lastKeyError.value = null
    connected.value = false
    if (apiKey.trim()) {
      sessionStorage.setItem(SESSION_KEY, apiKey)
    } else {
      sessionStorage.removeItem(SESSION_KEY)
    }
  }, { flush: 'sync' })

  watch([provider, baseUrl, model, temperature], persistModelConfig)

  const requestKeyConfig = computed<RequestKeyConfig>(() => {
    if (selectedKeySource.value === 'SYSTEM') {
      return { keySource: 'SYSTEM' }
    }
    return {
      keySource: 'USER',
      apiKey: userApiKey.value,
    }
  })

  function setUserApiKey(apiKey: string) {
    userApiKey.value = apiKey
  }

  function clearUserApiKey() {
    setUserApiKey('')
  }

  function setSystemKeyAvailable(available: boolean) {
    systemKeyAvailable.value = available
    if (!available && selectedKeySource.value === 'SYSTEM') {
      selectedKeySource.value = 'USER'
    }
  }

  function selectKeySource(keySource: ModelKeySource) {
    if (keySource === 'SYSTEM' && !systemKeyAvailable.value) {
      throw new Error('System key mode is unavailable')
    }
    selectedKeySource.value = keySource
    lastKeyError.value = null
    connected.value = false
  }

  function setProvider(nextProvider: ModelProvider) {
    if (provider.value === nextProvider) return
    provider.value = nextProvider
    baseUrl.value = presetBaseUrl(nextProvider)
    model.value = ''
    connected.value = false
    persistModelConfig()
  }

  function setBaseUrl(nextBaseUrl: string) {
    baseUrl.value = nextBaseUrl
    connected.value = false
    persistModelConfig()
  }

  function setModel(nextModel: string) {
    model.value = nextModel
    connected.value = false
    persistModelConfig()
  }

  function setTemperature(nextTemperature: number) {
    temperature.value = Number.isFinite(nextTemperature) ? nextTemperature : 0.2
    connected.value = false
    persistModelConfig()
  }

  function recordKeyFailure(error: ModelKeyError) {
    lastKeyError.value = error
    connected.value = false
  }

  function markConnected() {
    connected.value = true
    lastKeyError.value = null
    persistModelConfig()
  }

  async function loadRememberedApiKey(database: AppDatabase = appDatabase) {
    const record = await database.app_setting.get(REMEMBERED_KEY)
    const value = record?.value as Partial<RememberedModelApiKey> | undefined
    if (typeof value?.apiKey === 'string' && value.apiKey) {
      rememberApiKey.value = true
      setUserApiKey(value.apiKey)
    }
  }

  async function setRememberApiKey(remember: boolean, database: AppDatabase = appDatabase) {
    rememberApiKey.value = remember
    if (!remember) {
      await database.app_setting.delete(REMEMBERED_KEY)
      return
    }
    await database.app_setting.put({
      key: REMEMBERED_KEY,
      value: { apiKey: userApiKey.value },
      updatedAt: new Date().toISOString(),
    })
  }

  function persistModelConfig() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      provider: provider.value,
      baseUrl: baseUrl.value,
      model: model.value,
      temperature: temperature.value,
    } satisfies StoredModelConfig))
  }

  function normalizeProviderConfiguration() {
    if (provider.value !== 'CUSTOM') {
      baseUrl.value = presetBaseUrl(provider.value)
    }
    persistModelConfig()
  }

  return {
    selectedKeySource,
    userApiKey,
    rememberApiKey,
    systemKeyAvailable,
    lastKeyError,
    provider,
    baseUrl,
    model,
    temperature,
    connected,
    requestKeyConfig,
    setUserApiKey,
    clearUserApiKey,
    setSystemKeyAvailable,
    selectKeySource,
    setProvider,
    setBaseUrl,
    setModel,
    setTemperature,
    recordKeyFailure,
    markConnected,
    loadRememberedApiKey,
    setRememberApiKey,
    persistModelConfig,
  }
})

function readSessionApiKey(): string {
  return sessionStorage.getItem(SESSION_KEY) ?? ''
}

function readStoredModelConfig(): StoredModelConfig {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return {}
  try {
    const parsed = JSON.parse(raw) as StoredModelConfig
    return {
      provider: isProvider(parsed.provider) ? parsed.provider : undefined,
      baseUrl: typeof parsed.baseUrl === 'string' ? parsed.baseUrl : undefined,
      model: typeof parsed.model === 'string' ? parsed.model : undefined,
      temperature: typeof parsed.temperature === 'number' ? parsed.temperature : undefined,
    }
  } catch {
    return {}
  }
}

function isProvider(value: unknown): value is ModelProvider {
  return value === 'OPENAI' || value === 'DEEPSEEK' || value === 'CUSTOM'
}
