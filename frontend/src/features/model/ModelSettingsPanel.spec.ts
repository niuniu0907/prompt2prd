import 'fake-indexeddb/auto'

import Dexie from 'dexie'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ModelConfigClient } from '@/api/modelConfigApi'
import { ModelConfigApiError } from '@/api/modelConfigApi'
import { useModelConfigStore } from '@/stores/modelConfigStore'
import ModelSettingsPanel from './ModelSettingsPanel.vue'

function createClient(systemKeyAvailable = false): ModelConfigClient {
  return {
    getCapabilities: vi.fn(async () => ({ systemKeyAvailable })),
    listModels: vi.fn(async () => ({
      success: true as const,
      models: [
        { id: 'deepseek-chat', displayName: 'DeepSeek Chat' },
        { id: 'deepseek-reasoner', displayName: 'DeepSeek Reasoner' },
      ],
      latencyMs: 14,
    })),
    testConnection: vi.fn(async (input) => ({
      success: true as const,
      keySource: input.keySource,
      model: input.model,
      latencyMs: 18,
    })),
  }
}

describe('ModelSettingsPanel', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
    await Dexie.delete('prompt2prd')
  })

  it('shows only the three supported providers and hides preset Base URL in advanced settings', async () => {
    const wrapper = mount(ModelSettingsPanel, { props: { client: createClient(false) } })
    await flushPromises()

    expect(wrapper.text()).toContain('用户 Key')
    expect(wrapper.find('[data-key-source="SYSTEM"]').exists()).toBe(false)
    const options = wrapper.findAll('[data-testid="provider-select"] option').map((item) => item.text())
    expect(options).toEqual(['DeepSeek', 'OpenAI', '其他 OpenAI 兼容服务'])
    expect(wrapper.text()).toContain('高级设置')
    expect(wrapper.find('[data-testid="base-url-input"]').attributes('disabled')).toBeDefined()
  })

  it('fetches models after a user key is entered and submits the selected official model ID', async () => {
    const client = createClient(false)
    const wrapper = mount(ModelSettingsPanel, { props: { client } })
    await flushPromises()

    await wrapper.get('[data-testid="api-key-input"]').setValue('sk-session-key')
    await vi.waitFor(() => expect(wrapper.find('[data-testid="model-select"]').exists()).toBe(true))
    await wrapper.get('[data-testid="model-select"]').setValue('deepseek-reasoner')
    await wrapper.get('[data-testid="test-connection"]').trigger('click')
    await flushPromises()

    expect(client.listModels).toHaveBeenCalledWith({
      keySource: 'USER',
      provider: 'DEEPSEEK',
      apiKey: 'sk-session-key',
    })
    expect(client.testConnection).toHaveBeenCalledWith({
      keySource: 'USER',
      provider: 'DEEPSEEK',
      model: 'deepseek-reasoner',
      apiKey: 'sk-session-key',
      parameters: { temperature: 0.2 },
    })
    expect(wrapper.get('[role="status"]').text()).toContain('已连接')
    expect(useModelConfigStore().connected).toBe(true)
  })

  it('allows manual model ID only when the model-list endpoint is unavailable', async () => {
    const client = createClient(true)
    vi.mocked(client.listModels).mockRejectedValue(new ModelConfigApiError(
      'SERVICE_UNAVAILABLE',
      'Model list is unavailable.',
      502,
    ))
    const wrapper = mount(ModelSettingsPanel, { props: { client } })
    await flushPromises()

    await wrapper.get('[data-testid="provider-select"]').setValue('CUSTOM')
    await wrapper.get('[data-testid="base-url-input"]').setValue('http://localhost:11434/v1')
    await wrapper.get('[data-testid="api-key-input"]').setValue('sk-runtime-only')
    await vi.waitFor(() => expect(wrapper.find('[role="alert"]').exists()).toBe(true))

    expect(wrapper.get('[role="alert"]').text()).toContain('模型列表接口不可用')
    expect(wrapper.get('[data-testid="manual-model-input"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('[data-testid="manual-model-input"]').setValue('local-model')
    await wrapper.get('[data-testid="temperature-input"]').setValue('0.6')
    await wrapper.get('[data-testid="test-connection"]').trigger('click')
    await flushPromises()

    expect(client.testConnection).toHaveBeenCalledWith({
      keySource: 'USER',
      provider: 'CUSTOM',
      baseUrl: 'http://localhost:11434/v1',
      model: 'local-model',
      apiKey: 'sk-runtime-only',
      parameters: { temperature: 0.6 },
    })
  })

  it('saves the remember-key opt-in state through Dexie and shows the security warning', async () => {
    const wrapper = mount(ModelSettingsPanel, { props: { client: createClient(false) } })
    await flushPromises()

    await wrapper.get('[data-testid="api-key-input"]').setValue('sk-remember')
    await wrapper.get('[data-testid="remember-key-toggle"]').setValue(true)
    await flushPromises()

    expect(useModelConfigStore().rememberApiKey).toBe(true)
    expect(wrapper.text()).toContain('IndexedDB')
  })

  it('keeps the selected source after authentication failure', async () => {
    const client = createClient(true)
    vi.mocked(client.testConnection).mockRejectedValueOnce(new ModelConfigApiError(
      'UNAUTHORIZED',
      'Model service authentication failed.',
      502,
    ))
    const wrapper = mount(ModelSettingsPanel, { props: { client } })
    await flushPromises()

    await wrapper.get('[data-testid="api-key-input"]').setValue('sk-runtime-only')
    await vi.waitFor(() => expect(wrapper.find('[data-testid="model-select"]').exists()).toBe(true))
    await wrapper.get('[data-testid="test-connection"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('鉴权失败')
    expect(useModelConfigStore().selectedKeySource).toBe('USER')
    expect(useModelConfigStore().lastKeyError).toBe('AUTHENTICATION_FAILED')
  })
})
