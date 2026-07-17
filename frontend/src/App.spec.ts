import 'fake-indexeddb/auto'

import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { describe, expect, it } from 'vitest'

import App from './App.vue'
import { createAppRouter } from './router'
import './styles/tokens.css'

describe('App', () => {
  it('mounts with Router and Pinia initialized', async () => {
    const pinia = createPinia()
    const router = createAppRouter(createMemoryHistory())

    await router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router],
      },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="project-home"]').text()).toContain('全部项目')
    expect(wrapper.get('[data-testid="new-project-button"]').text()).toContain('新建项目')
    expect(wrapper.text()).toContain('项目数据仅保存在当前浏览器中')
    expect(wrapper.vm.$pinia).toBe(pinia)
    expect(wrapper.vm.$router).toBe(router)
  })

  it('routes the header creation entry to the new-project page', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()
    const wrapper = mount(App, {
      global: { plugins: [createPinia(), router] },
    })
    await flushPromises()

    await wrapper.get('[data-testid="new-project-button"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('new-project')
  })
})
