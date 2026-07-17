import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConflictPanel from './ConflictPanel.vue'
import AssumptionPanel from './AssumptionPanel.vue'
import RequirementCard from './RequirementCard.vue'
import type { RequirementConflict, RequirementItem } from './types'

const requirement: RequirementItem = { id: '20000000-0000-4000-8000-000000000000', projectId: '10000000-0000-4000-8000-000000000000', type: 'ASSUMPTION', title: '审核假设', content: '默认人工审核', status: 'CONFIRMED', sourceType: 'AI_INFERENCE', sourceId: null, locked: false, metadata: {}, createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z' }
const conflict: RequirementConflict = { id: '30000000-0000-4000-8000-000000000000', projectId: requirement.projectId, leftRequirementId: requirement.id, rightRequirementId: null, leftContent: '人工审核', rightContent: '自动审核', impact: '影响审核流程', core: true, status: 'OPEN', resolution: null, createdAt: requirement.createdAt, updatedAt: requirement.updatedAt, resolvedAt: null }

describe('requirement interactions', () => {
  it('shows both conflict sides, impact, core status, and emits resolution', async () => {
    const wrapper = mount(ConflictPanel, { props: { conflicts: [conflict] } })
    expect(wrapper.text()).toContain('人工审核')
    expect(wrapper.text()).toContain('自动审核')
    expect(wrapper.text()).toContain('核心冲突')
    expect(wrapper.text()).toContain('影响审核流程')
    await wrapper.get('[data-testid="resolve-conflict"]').trigger('click')
    expect(wrapper.emitted('resolve')?.[0]).toEqual([conflict.id, '保留左侧内容'])
  })

  it('emits assumption decisions and lock changes', async () => {
    const assumption = mount(AssumptionPanel, { props: { assumptions: [requirement] } })
    await assumption.get('[data-testid="accept-assumption"]').trigger('click')
    expect(assumption.emitted('decide')?.[0]).toEqual([requirement.id, true])
    const card = mount(RequirementCard, { props: { requirement } })
    expect(card.text()).toContain('AI 推断')
    await card.get('[data-testid="toggle-lock"]').trigger('click')
    expect(card.emitted('lock')?.[0]).toEqual([requirement.id, true])
  })
})
