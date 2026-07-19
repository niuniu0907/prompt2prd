import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectHeader from './ProjectHeader.vue'

describe('ProjectHeader', () => {
  it('displays the project name', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: '宠物寄养平台' },
    })

    expect(wrapper.text()).toContain('宠物寄养平台')
  })

  it('shows only requirement completeness as the persistent top metric', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test', completeness: 72 },
    })

    expect(wrapper.text()).toContain('需求完整度：72%')
    expect(wrapper.text()).not.toContain('已确认')
    expect(wrapper.text()).not.toContain('待确认')
    expect(wrapper.text()).not.toContain('待分析')
    expect(wrapper.text()).not.toContain('冲突')
    expect(wrapper.find('[data-testid="header-completeness"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('总体进度')
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

    expect(wrapper.text()).not.toContain('DeepSeek V3')
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

  it('disables PRD generation before initial analysis is available', () => {
    const wrapper = mount(ProjectHeader, {
      props: {
        projectName: 'Test',
        canGeneratePrd: false,
        generateHint: '首次 AI 解析完成后会进入 AI 澄清。',
      },
    })

    const button = wrapper.get('[data-testid="header-generate-prd"]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="header-generate-hint"]').text()).toBe('首次 AI 解析完成后会进入 AI 澄清。')
  })

  it('uses dark text on the primary button background', () => {
    const wrapper = mount(ProjectHeader, {
      props: { projectName: 'Test' },
    })

    const btn = wrapper.get('[data-testid="header-generate-prd"]')
    expect(btn.classes()).toContain('button-primary')
  })
})
