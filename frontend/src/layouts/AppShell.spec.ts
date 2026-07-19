import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import AppShell from './AppShell.vue'
import type { ProjectHomeRepository } from '@/db/repositories/projectRepository'
import ProjectHomeView from '@/views/ProjectHomeView.vue'
import tokensCss from '@/styles/tokens.css?inline'
import { createAppRouter } from '@/router'

const requiredTokens = {
  '--color-primary': '#c7eb64',
  '--color-accent': '#249da5',
  '--color-background': '#eff3f4',
  '--color-surface': '#ffffff',
  '--color-text-primary': '#262b25',
  '--color-text-secondary': '#626d72',
  '--color-text-muted': '#80898f',
  '--color-on-accent': '#111713',
}

describe('AppShell', () => {
  afterEach(() => {
    window.localStorage.removeItem('prompt2prd:layout:appSidebarWidth')
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  })

  it('shows every global navigation entry with a single current item', () => {
    const wrapper = mount(AppShell, {
      slots: {
        default: '<div>main canvas</div>',
      },
    })

    for (const label of ['全部项目', '已归档', '回收站', '模型设置']) {
      expect(wrapper.text()).toContain(label)
    }

    expect(wrapper.findAll('[aria-current="page"]')).toHaveLength(1)
    expect(wrapper.text()).not.toMatch(/深色|暗色|主题切换/)
  })

  it('switches the current project view through navigation events', async () => {
    const wrapper = mount(AppShell, { props: { activeSection: 'ARCHIVED' } })

    expect(wrapper.get('[data-navigation="ARCHIVED"]').attributes('aria-current')).toBe('page')
    expect(wrapper.findAll('[aria-current="page"]')).toHaveLength(1)
    await wrapper.get('[data-navigation="DELETED"]').trigger('click')
    expect(wrapper.emitted('navigate')).toEqual([['DELETED']])
    expect(wrapper.get('[data-navigation="MODEL_SETTINGS"]').attributes('disabled')).toBeUndefined()
  })

  it('opens the enabled model settings route', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })

    await wrapper.get('[data-navigation="MODEL_SETTINGS"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('model-settings')
  })

  it('lets users resize the global sidebar and keeps the width locally', async () => {
    const wrapper = mount(AppShell, {
      slots: {
        default: '<div>main canvas</div>',
      },
    })
    const resizer = wrapper.get('[data-testid="app-sidebar-resizer"]').element

    resizer.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 244 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 300 }))
    window.dispatchEvent(new MouseEvent('mouseup'))
    await wrapper.vm.$nextTick()

    expect((wrapper.get('.app-shell').element as HTMLElement).style.gridTemplateColumns).toContain('300px')
    expect(window.localStorage.getItem('prompt2prd:layout:appSidebarWidth')).toBe('300')
  })

  it('uses the documented light-theme tokens and primary-button pairing', () => {
    for (const [name, value] of Object.entries(requiredTokens)) {
      expect(tokensCss).toContain(`${name}: ${value};`)
    }

    expect(tokensCss).toMatch(/\.button-primary\s*\{[\s\S]*?color:\s*var\(--color-text-primary\);[\s\S]*?background:\s*var\(--color-primary\);[\s\S]*?\}/)
  })

  it('renders a real empty state without sample projects', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()
    const repository: ProjectHomeRepository = {
      listSummaries: vi.fn(async () => []),
      rename: vi.fn(async () => undefined),
      copy: vi.fn(async () => undefined),
      archive: vi.fn(async () => undefined),
      moveToTrash: vi.fn(async () => undefined),
      restore: vi.fn(async () => undefined),
      permanentlyDelete: vi.fn(async () => undefined),
    }
    const wrapper = mount(ProjectHomeView, {
      props: { repository },
      global: { plugins: [router] },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="project-home"]').text()).toContain('还没有项目')
    expect(wrapper.findAll('.button-primary')).toHaveLength(2)
    expect(wrapper.text()).toContain('项目数据仅保存在当前浏览器中')
    expect(wrapper.text()).not.toContain('宠物寄养')
  })
})
