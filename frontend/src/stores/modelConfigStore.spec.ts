import 'fake-indexeddb/auto'

import Dexie from 'dexie'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { createAppDatabase } from '@/db/appDatabase'
import { useModelConfigStore } from './modelConfigStore'

describe('modelConfigStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
  })

  it('keeps a user API key in sessionStorage by default across a fresh store', () => {
    const store = useModelConfigStore()

    store.setUserApiKey('sk-session-only')

    expect(store.userApiKey).toBe('sk-session-only')
    expect(store.requestKeyConfig).toEqual({
      keySource: 'USER',
      apiKey: 'sk-session-only',
    })

    setActivePinia(createPinia())
    expect(useModelConfigStore().userApiKey).toBe('sk-session-only')
    expect(localStorage.getItem('prompt2prd:model-api-key')).toBeNull()
  })

  it('persists provider base URL model and temperature to localStorage', () => {
    const store = useModelConfigStore()

    store.setProvider('CUSTOM')
    store.setBaseUrl('http://localhost:11434/v1')
    store.setModel('local-model')
    store.setTemperature(0.6)

    setActivePinia(createPinia())
    expect(useModelConfigStore()).toMatchObject({
      provider: 'CUSTOM',
      baseUrl: 'http://localhost:11434/v1',
      model: 'local-model',
      temperature: 0.6,
    })
  })

  it('resets preset base URL and model when the provider changes', () => {
    const store = useModelConfigStore()

    store.setProvider('CUSTOM')
    store.setBaseUrl('http://localhost:11434/v1')
    store.setModel('local-model')
    store.setProvider('OPENAI')

    expect(store.baseUrl).toBe('https://api.openai.com/v1')
    expect(store.model).toBe('')
  })

  it('can remember and clear the user API key in Dexie only after explicit opt-in', async () => {
    const database = createAppDatabase(`model-store-${crypto.randomUUID()}`)
    const store = useModelConfigStore()
    store.setUserApiKey('sk-remembered')

    await store.setRememberApiKey(true, database)

    const record = await database.app_setting.get('rememberedModelApiKey')
    expect(record?.value).toEqual({ apiKey: 'sk-remembered' })

    setActivePinia(createPinia())
    sessionStorage.clear()
    const refreshedStore = useModelConfigStore()
    await refreshedStore.loadRememberedApiKey(database)

    expect(refreshedStore.rememberApiKey).toBe(true)
    expect(refreshedStore.userApiKey).toBe('sk-remembered')

    await refreshedStore.setRememberApiKey(false, database)
    expect(await database.app_setting.get('rememberedModelApiKey')).toBeUndefined()
    await database.close()
    await Dexie.delete(database.name)
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
    expect(store.connected).toBe(false)
  })
})
