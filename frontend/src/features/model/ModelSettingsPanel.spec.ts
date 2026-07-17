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
    testConnection: vi.fn(async (input) => ({
      success: true as const,
      keySource: input.keySource,
      model: input.model,
      latencyMs: 18,
    })),
  }
}

describe('ModelSettingsPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('hides system key mode when the backend reports it unavailable', async () => {
    const wrapper = mount(ModelSettingsPanel, { props: { client: createClient(false) } })
    await flushPromises()

    expect(wrapper.text()).toContain('用户 Key')
    expect(wrapper.find('[data-key-source="SYSTEM"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('不会写入项目数据、浏览器存储或服务端日志')
  })

  it('sends the explicitly selected system-key preset and never includes a user key', async () => {
    const client = createClient(true)
    const wrapper = mount(ModelSettingsPanel, { props: { client } })
    await flushPromises()

    await wrapper.get('[data-key-source="SYSTEM"]').setValue(true)
    await wrapper.get('[data-testid="provider-select"]').setValue('QWEN')
    await wrapper.get('[data-testid="model-input"]').setValue('qwen-plus')
    await wrapper.get('[data-testid="test-connection"]').trigger('click')
    await flushPromises()

    expect(client.testConnection).toHaveBeenCalledWith({
      keySource: 'SYSTEM',
      provider: 'QWEN',
      model: 'qwen-plus',
      parameters: { temperature: 0.2 },
    })
    expect(wrapper.get('[role="status"]').text()).toContain('连接成功')
  })

  it('sends a custom user configuration and keeps the source after authentication failure', async () => {
    const client = createClient(true)
    vi.mocked(client.testConnection).mockRejectedValueOnce(new ModelConfigApiError(
      'UNAUTHORIZED',
      'Model service authentication failed.',
      502,
    ))
    const wrapper = mount(ModelSettingsPanel, { props: { client } })
    await flushPromises()

    await wrapper.get('[data-testid="provider-select"]').setValue('CUSTOM')
    await wrapper.get('[data-testid="base-url-input"]').setValue('http://localhost:11434/v1')
    await wrapper.get('[data-testid="model-input"]').setValue('local-model')
    await wrapper.get('[data-testid="api-key-input"]').setValue('sk-runtime-only')
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
    expect(wrapper.get('[role="alert"]').text()).toContain('鉴权失败')
    expect(useModelConfigStore().selectedKeySource).toBe('USER')
    expect(useModelConfigStore().lastKeyError).toBe('AUTHENTICATION_FAILED')
  })
})
