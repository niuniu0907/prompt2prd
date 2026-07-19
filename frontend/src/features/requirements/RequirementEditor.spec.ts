import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RequirementEditor from './RequirementEditor.vue'
import type { RequirementItem } from './types'

const item: RequirementItem = { id: '20000000-0000-4000-8000-000000000000', projectId: '10000000-0000-4000-8000-000000000000', type: 'FEATURE', title: '预约', content: '用户可以预约', status: 'INFERRED', sourceType: 'AI_INFERENCE', sourceId: null, locked: false, metadata: {}, createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z' }

describe('RequirementEditor', () => {
  it('saves on blur and marks affected future artifacts', async () => {
    const wrapper = mount(RequirementEditor, { props: { requirement: item } })
    await wrapper.get('[data-testid="requirement-content"]').setValue('用户可以按日期预约')
    await wrapper.get('[data-testid="requirement-content"]').trigger('blur')
    expect(wrapper.emitted('save')?.[0]?.[0]).toEqual({ title: '预约', content: '用户可以按日期预约', affectedArtifacts: ['FLOWCHART', 'PRD'] })
  })

  it('disables editing for locked content', () => {
    const wrapper = mount(RequirementEditor, { props: { requirement: { ...item, locked: true } } })
    expect(wrapper.get('[data-testid="requirement-content"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('请先解锁')
  })
})
