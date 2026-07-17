import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useModelConfigStore } from './modelConfigStore'

describe('modelConfigStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
  })

  it('keeps a user API key only in the current Pinia runtime', () => {
    const localStorageWrite = vi.spyOn(Storage.prototype, 'setItem')
    const store = useModelConfigStore()

    store.setUserApiKey('sk-runtime-only')

    expect(store.userApiKey).toBe('sk-runtime-only')
    expect(store.requestKeyConfig).toEqual({
      keySource: 'USER',
      apiKey: 'sk-runtime-only',
    })
    expect(localStorageWrite).not.toHaveBeenCalled()
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('loses the user API key after a fresh application store is created', () => {
    useModelConfigStore().setUserApiKey('sk-cleared-by-refresh')

    setActivePinia(createPinia())
    const refreshedStore = useModelConfigStore()

    expect(refreshedStore.userApiKey).toBe('')
    expect(refreshedStore.selectedKeySource).toBe('USER')
    expect(refreshedStore.requestKeyConfig).toEqual({
      keySource: 'USER',
      apiKey: '',
    })
  })

  it('shows and selects system key mode only when the backend reports it available', () => {
    const store = useModelConfigStore()

    expect(store.systemKeyAvailable).toBe(false)
    expect(() => store.selectKeySource('SYSTEM')).toThrow('System key mode is unavailable')

    store.setSystemKeyAvailable(true)
    store.selectKeySource('SYSTEM')

    expect(store.requestKeyConfig).toEqual({ keySource: 'SYSTEM' })
    expect(store.userApiKey).toBe('')
  })

  it('does not silently switch sources after a key failure', () => {
    const store = useModelConfigStore()
    store.setSystemKeyAvailable(true)
    store.selectKeySource('SYSTEM')

    store.recordKeyFailure('AUTHENTICATION_FAILED')

    expect(store.selectedKeySource).toBe('SYSTEM')
    expect(store.lastKeyError).toBe('AUTHENTICATION_FAILED')
  })

  it('keeps the selected endpoint configuration in the current runtime store', () => {
    const store = useModelConfigStore()

    store.provider = 'CUSTOM'
    store.baseUrl = 'http://localhost:11434/v1'
    store.model = 'local-model'
    store.temperature = 0.6

    expect(useModelConfigStore()).toMatchObject({
      provider: 'CUSTOM',
      baseUrl: 'http://localhost:11434/v1',
      model: 'local-model',
      temperature: 0.6,
    })
  })
})
