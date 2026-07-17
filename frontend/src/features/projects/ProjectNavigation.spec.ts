import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ProjectModule } from '@/features/projects/types'
import { PROJECT_MODULES } from '@/features/projects/types'
import ProjectNavigation from './ProjectNavigation.vue'

describe('ProjectNavigation', () => {
  const moduleLabels: Record<ProjectModule, string> = {
    overview: '需求概览',
    questions: '问题向导',
    requirements: '需求卡片',
    architecture: '架构建议',
    flowchart: '流程图',
    prd: 'PRD',
  }

  it('renders all six secondary navigation items', () => {
    const wrapper = mount(ProjectNavigation, {
      props: {
        currentModule: 'overview',
        projectId: 'test-project-id',
      },
    })

    for (const label of Object.values(moduleLabels)) {
      expect(wrapper.text()).toContain(label)
    }

    const buttons = wrapper.findAll('button[data-module]')
    expect(buttons).toHaveLength(PROJECT_MODULES.length)
  })

  it('marks only the current module as active', () => {
    for (const mod of PROJECT_MODULES) {
      const wrapper = mount(ProjectNavigation, {
        props: {
          currentModule: mod,
          projectId: 'test-project-id',
        },
      })

      expect(wrapper.findAll('[aria-current="page"]')).toHaveLength(1)
      expect(
        wrapper.get(`[data-module="${mod}"]`).attributes('aria-current'),
      ).toBe('page')
    }
  })

  it('emits navigate with the clicked module', async () => {
    const wrapper = mount(ProjectNavigation, {
      props: {
        currentModule: 'overview',
        projectId: 'test-project-id',
      },
    })

    await wrapper.get('[data-module="architecture"]').trigger('click')
    expect(wrapper.emitted('navigate')).toEqual([['architecture']])

    await wrapper.get('[data-module="prd"]').trigger('click')
    expect(wrapper.emitted('navigate')).toEqual([['architecture'], ['prd']])
  })

  it('does not change the current module prop when clicked', async () => {
    const wrapper = mount(ProjectNavigation, {
      props: {
        currentModule: 'overview',
        projectId: 'test-project-id',
      },
    })

    await wrapper.get('[data-module="questions"]').trigger('click')

    // The prop should remain unchanged — navigation is parent's responsibility
    expect(wrapper.props('currentModule')).toBe('overview')
  })

  it('renders a section label for the project module area', () => {
    const wrapper = mount(ProjectNavigation, {
      props: {
        currentModule: 'overview',
        projectId: 'test-project-id',
      },
    })

    expect(wrapper.text()).toContain('项目模块')
  })
})
