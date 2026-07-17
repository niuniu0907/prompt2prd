import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type ModelKeySource = 'SYSTEM' | 'USER'
export type ModelProvider = 'OPENAI' | 'DEEPSEEK' | 'QWEN' | 'CUSTOM'

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

/** Runtime-only model credential state. No persistence plugin or browser storage is used. */
export const useModelConfigStore = defineStore('model-config', () => {
  const selectedKeySource = ref<ModelKeySource>('USER')
  const userApiKey = ref('')
  const systemKeyAvailable = ref(false)
  const lastKeyError = ref<ModelKeyError | null>(null)
  const provider = ref<ModelProvider>('OPENAI')
  const baseUrl = ref('')
  const model = ref('')
  const temperature = ref(0.2)

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
    lastKeyError.value = null
  }

  function clearUserApiKey() {
    userApiKey.value = ''
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
  }

  function recordKeyFailure(error: ModelKeyError) {
    lastKeyError.value = error
  }

  return {
    selectedKeySource,
    userApiKey,
    systemKeyAvailable,
    lastKeyError,
    provider,
    baseUrl,
    model,
    temperature,
    requestKeyConfig,
    setUserApiKey,
    clearUserApiKey,
    setSystemKeyAvailable,
    selectKeySource,
    recordKeyFailure,
  }
})
