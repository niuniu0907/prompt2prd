import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ProjectModule } from '@/features/projects/types'
import { OPTIONAL_PROJECT_MODULES, PRIMARY_PROJECT_MODULES, PROJECT_MODULES } from '@/features/projects/types'
import ProjectNavigation from './ProjectNavigation.vue'

describe('ProjectNavigation', () => {
  const moduleLabels: Record<ProjectModule, string> = {
    overview: '需求输入',
    questions: 'AI澄清',
    requirements: '需求结果',
    architecture: '架构建议',
    flowchart: '流程图',
    prd: 'PRD文档',
  }

  it('renders only the four primary workflow items by default', () => {
    const wrapper = mount(ProjectNavigation, {
      props: {
        currentModule: 'overview',
        projectId: 'test-project-id',
      },
    })

    for (const module of PRIMARY_PROJECT_MODULES) {
      expect(wrapper.text()).toContain(moduleLabels[module])
    }
    for (const module of OPTIONAL_PROJECT_MODULES) {
      expect(wrapper.text()).not.toContain(moduleLabels[module])
    }

    const buttons = wrapper.findAll('button[data-module]')
    expect(buttons).toHaveLength(PRIMARY_PROJECT_MODULES.length)
  })

  it('keeps flowchart and architecture under a collapsed more tools group', async () => {
    const wrapper = mount(ProjectNavigation, {
      props: {
        currentModule: 'overview',
        projectId: 'test-project-id',
      },
    })

    expect(wrapper.get('[data-testid="more-tools-toggle"]').attributes('aria-expanded')).toBe('false')
    await wrapper.get('[data-testid="more-tools-toggle"]').trigger('click')
    expect(wrapper.get('[data-testid="more-tools-toggle"]').attributes('aria-expanded')).toBe('true')
    expect(wrapper.text()).toContain('流程图')
    expect(wrapper.text()).toContain('架构建议')
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
      expect(wrapper.get(`[data-module="${mod}"]`).attributes('aria-current')).toBe('page')
    }
  })

  it('emits navigate with the clicked module', async () => {
    const wrapper = mount(ProjectNavigation, {
      props: {
        currentModule: 'overview',
        projectId: 'test-project-id',
      },
    })

    await wrapper.get('[data-testid="more-tools-toggle"]').trigger('click')
    await wrapper.get('[data-module="flowchart"]').trigger('click')
    expect(wrapper.emitted('navigate')).toEqual([['flowchart']])

    await wrapper.get('[data-module="prd"]').trigger('click')
    expect(wrapper.emitted('navigate')).toEqual([['flowchart'], ['prd']])
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

    expect(wrapper.text()).toContain('主流程')
  })
})
