import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ProjectStage } from '@/features/projects/types'
import { PROJECT_STAGES } from '@/features/projects/types'
import ProjectHeader from './ProjectHeader.vue'

describe('ProjectHeader', () => {
  it('displays the project name', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: '宠物寄养平台' },
    })

    expect(wrapper.text()).toContain('宠物寄养平台')
  })

  it('shows completeness as a percentage', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test', completeness: 72 },
    })

    expect(wrapper.get('[data-testid="header-completeness"]').text()).toContain('72%')
  })

  it('rounds completeness to a whole number', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test', completeness: 67.8 },
    })

    expect(wrapper.get('[data-testid="header-completeness"]').text()).toContain('68%')
  })

  it('displays the correct Chinese label for each project stage', () => {
    const stageMap: Record<ProjectStage, string> = {
      CLARIFYING: '需求澄清',
      ARCHITECTURE: '架构建议',
      FLOWCHART: '流程图',
      PRD: 'PRD 生成',
      COMPLETED: '已完成',
    }

    for (const stage of PROJECT_STAGES) {
      const wrapper = mount(ProjectHeader, {
        props: { projectName: 'Test', stage },
      })
      expect(wrapper.text()).toContain(stageMap[stage])
    }
  })

  it('shows the model name when provided', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test', modelName: 'DeepSeek V3' },
    })

    expect(wrapper.text()).toContain('DeepSeek V3')
  })

  it('does not show a model badge when modelName is omitted', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test' },
    })

    // Only stage and completeness badges should appear
    const badges = wrapper.findAll('span')
    const modelBadge = badges.filter((b) => b.text() !== '' && !b.classes().some((c) => c.includes('stage') || c.includes('completeness') || c.includes('save')))
    expect(modelBadge.every((b) => b.text() !== 'DeepSeek V3')).toBe(true)
  })

  it('shows save status text when provided', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test', saveStatus: '已保存' },
    })

    expect(wrapper.get('[data-testid="header-save-status"]').text()).toBe('已保存')
  })

  it('hides save status when not provided', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test' },
    })

    expect(wrapper.find('[data-testid="header-save-status"]').exists()).toBe(false)
  })

  it('renders a generate PRD button', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test' },
    })

    const btn = wrapper.get('[data-testid="header-generate-prd"]')
    expect(btn.text()).toBe('生成 PRD')
    expect(btn.classes()).toContain('button-primary')
  })

  it('emits generatePrd when the button is clicked', async () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test', canGeneratePrd: true },
    })

    await wrapper.get('[data-testid="header-generate-prd"]').trigger('click')
    expect(wrapper.emitted('generatePrd')).toHaveLength(1)
  })

  it('disables PRD generation with a concrete reason when prerequisites are missing', () => {
    const wrapper = mount(ProjectHeader, {
      props: {
        projectName: 'Test',
        canGeneratePrd: false,
        generateDisabledReason: '还需确认3条需求后才能生成PRD',
      },
    })

    const button = wrapper.get('[data-testid="header-generate-prd"]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="header-generate-hint"]').text()).toBe('还需确认3条需求后才能生成PRD')
  })

  it('uses dark text on the primary button background', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test' },
    })

    const btn = wrapper.get('[data-testid="header-generate-prd"]')
    expect(btn.classes()).toContain('button-primary')
  })
})
