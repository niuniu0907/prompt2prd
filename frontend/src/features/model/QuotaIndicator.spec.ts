import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

import QuotaIndicator from './QuotaIndicator.vue'
import { useModelConfigStore } from '@/stores/modelConfigStore'
import type { QuotaSnapshot, QuotaClient } from '@/api/quotaApi'

function quotaSnapshot(overrides: Partial<QuotaSnapshot> = {}): QuotaSnapshot {
  return {
    systemKeyAvailable: true,
    analysisRemaining: 3,
    fullPrdRemaining: 1,
    globalCallsRemaining: 100,
    ...overrides,
  }
}

function mountQuota(client: QuotaClient, setupStore?: (store: ReturnType<typeof useModelConfigStore>) => void) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (setupStore) {
    setupStore(useModelConfigStore())
  }
  return mount(QuotaIndicator, {
    global: { plugins: [pinia] },
    props: { client },
  })
}

describe('QuotaIndicator', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows loading state initially', () => {
    const client: QuotaClient = {
      getQuota: () => new Promise(() => { /* never resolves */ }),
    }
    const wrapper = mountQuota(client)
    expect(wrapper.text()).toContain('正在读取额度')
  })

  it('displays remaining quota counts', async () => {
    const client: QuotaClient = {
      getQuota: async () => quotaSnapshot({ analysisRemaining: 2, fullPrdRemaining: 1, globalCallsRemaining: 95 }),
    }
    const wrapper = mountQuota(client)
    await nextTick()
    await nextTick()

    const analysis = wrapper.find('[data-testid="quota-analysis"] dd')
    const prd = wrapper.find('[data-testid="quota-prd"] dd')
    const global = wrapper.find('[data-testid="quota-global"] dd')
    expect(analysis.text()).toBe('2')
    expect(prd.text()).toBe('1')
    expect(global.text()).toBe('95')
  })

  it('shows exhausted state when analysis quota is zero', async () => {
    const client: QuotaClient = {
      getQuota: async () => quotaSnapshot({ analysisRemaining: 0, fullPrdRemaining: 1, globalCallsRemaining: 100 }),
    }
    const wrapper = mountQuota(client, (store) => {
      store.setSystemKeyAvailable(true)
      store.selectKeySource('SYSTEM')
    })
    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('今日分析次数已用尽')
    expect(wrapper.find('[data-testid="quota-switch-to-user"]').exists()).toBe(true)
  })

  it('shows exhausted state when PRD quota is zero', async () => {
    const client: QuotaClient = {
      getQuota: async () => quotaSnapshot({ analysisRemaining: 3, fullPrdRemaining: 0, globalCallsRemaining: 100 }),
    }
    const wrapper = mountQuota(client)
    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('今日 PRD 生成次数已用尽')
  })

  it('shows exhausted state when global calls budget is exhausted', async () => {
    const client: QuotaClient = {
      getQuota: async () => quotaSnapshot({ analysisRemaining: 3, fullPrdRemaining: 1, globalCallsRemaining: 0 }),
    }
    const wrapper = mountQuota(client)
    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('全局调用预算已耗尽')
  })

  it('shows urgent warning when one analysis remains', async () => {
    const client: QuotaClient = {
      getQuota: async () => quotaSnapshot({ analysisRemaining: 1, fullPrdRemaining: 1, globalCallsRemaining: 100 }),
    }
    const wrapper = mountQuota(client)
    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('分析次数仅剩 1 次')
  })

  it('shows disabled message when system key is not available', async () => {
    const client: QuotaClient = {
      getQuota: async () => quotaSnapshot({ systemKeyAvailable: false }),
    }
    const wrapper = mountQuota(client)
    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('系统 Key 未启用')
  })

  it('switches to user key when switch link is clicked', async () => {
    const client: QuotaClient = {
      getQuota: async () => quotaSnapshot({ analysisRemaining: 0, fullPrdRemaining: 1, globalCallsRemaining: 100 }),
    }
    const wrapper = mountQuota(client, (store) => {
      store.setSystemKeyAvailable(true)
      store.selectKeySource('SYSTEM')
    })
    await nextTick()
    await nextTick()

    expect(wrapper.find('[data-testid="quota-switch-to-user"]').exists()).toBe(true)

    await wrapper.find('[data-testid="quota-switch-to-user"]').trigger('click')
    const store = useModelConfigStore()
    expect(store.selectedKeySource).toBe('USER')
  })

  it('shows error message when quota fetch fails', async () => {
    const client: QuotaClient = {
      getQuota: async () => { throw new Error('network error') },
    }
    const wrapper = mountQuota(client)
    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('无法读取系统额度信息')
  })

  it('applies exhausted class to zero values', async () => {
    const client: QuotaClient = {
      getQuota: async () => quotaSnapshot({ analysisRemaining: 0, fullPrdRemaining: 1, globalCallsRemaining: 100 }),
    }
    const wrapper = mountQuota(client)
    await nextTick()
    await nextTick()

    expect(wrapper.find('.quota-indicator__value--exhausted').exists()).toBe(true)
  })
})
