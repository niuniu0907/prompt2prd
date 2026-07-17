import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ArchitectureComparison from './ArchitectureComparison.vue'
import type { ArchitectureCandidate } from './types'

function candidate(index: number): ArchitectureCandidate {
  return { id: `123e4567-e89b-42d3-a456-42661417400${index}`, name: `方案 ${index}`, stack: { frontend: 'Vue', backend: 'Spring Boot', storage: 'IndexedDB', authentication: 'Token', fileStorage: 'Local', ai: 'Spring AI', deployment: 'Docker', testing: 'Vitest' }, responsibilities: ['前端负责界面'], advantages: ['易维护'], disadvantages: ['双语言'], limitations: ['单实例'], unselectedReasons: ['技能不完全匹配'], scores: { LEARNING_COST: 5, DEVELOPMENT_SPEED: 4, DEPLOYMENT_SIMPLICITY: 5, RUNNING_COST: 4, MAINTAINABILITY: 5, SCALABILITY: 3, AI_SUPPORT: 5 }, totalScore: 31, recommended: index === 0 }
}

describe('ArchitectureComparison', () => {
  it('shows 2-3 candidates with every score dimension and selects an alternative', async () => {
    const wrapper = mount(ArchitectureComparison, { props: { candidates: [candidate(0), candidate(1), candidate(2)], pendingFields: ['budget'] } })
    expect(wrapper.findAll('.candidate')).toHaveLength(3)
    expect(wrapper.text()).toContain('学习成本')
    expect(wrapper.text()).toContain('AI 支持')
    await wrapper.findAll('.candidate')[1]!.findAll('button')[0]!.trigger('click')
    expect(wrapper.emitted('confirm')?.[0]).toEqual([candidate(1), false])
    expect(wrapper.text()).toContain('当前结果保持草稿标记')
  })

  it('creates a separately identified manual candidate', async () => {
    const wrapper = mount(ArchitectureComparison, { props: { candidates: [candidate(0), candidate(1)] } })
    await wrapper.findAll('.candidate')[0]!.findAll('button')[1]!.trigger('click')
    await wrapper.find('input[name="manualName"]').setValue('我的 Vue 架构')
    await wrapper.find('[data-testid="manual-editor"] .button-primary').trigger('click')
    const emitted = wrapper.emitted('confirm')?.[0]
    expect((emitted?.[0] as ArchitectureCandidate).name).toBe('我的 Vue 架构')
    expect((emitted?.[0] as ArchitectureCandidate).id).not.toBe(candidate(0).id)
    expect(emitted?.[1]).toBe(true)
  })
})
