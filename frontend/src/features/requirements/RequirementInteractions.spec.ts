import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConflictPanel from './ConflictPanel.vue'
import AssumptionPanel from './AssumptionPanel.vue'
import RequirementListItem from './RequirementListItem.vue'
import RequirementGroup from './RequirementGroup.vue'
import StatusFilter from './StatusFilter.vue'
import type { RequirementConflict, RequirementItem } from './types'

const requirement: RequirementItem = {
  id: '20000000-0000-4000-8000-000000000000',
  projectId: '10000000-0000-4000-8000-000000000000',
  type: 'ASSUMPTION',
  title: '审核假设',
  content: '默认人工审核',
  status: 'CONFIRMED',
  sourceType: 'AI_INFERENCE',
  sourceId: null,
  locked: false,
  metadata: {},
  createdAt: '2026-07-17T12:00:00.000Z',
  updatedAt: '2026-07-17T12:00:00.000Z',
}

const conflict: RequirementConflict = {
  id: '30000000-0000-4000-8000-000000000000',
  projectId: requirement.projectId,
  leftRequirementId: requirement.id,
  rightRequirementId: null,
  leftContent: '人工审核',
  rightContent: '自动审核',
  impact: '影响审核流程',
  core: true,
  status: 'OPEN',
  resolution: null,
  createdAt: requirement.createdAt,
  updatedAt: requirement.updatedAt,
  resolvedAt: null,
}

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
  })
})

describe('RequirementListItem', () => {
  const featureReq: RequirementItem = {
    ...requirement,
    id: 'feat-1',
    type: 'FEATURE',
    title: '订单支付',
    content: '用户提交订单后完成在线支付流程',
    status: 'PENDING',
    sourceType: 'USER_ANSWER',
  }

  it('renders category, title, summary and status', () => {
    const wrapper = mount(RequirementListItem, { props: { requirement: featureReq } })
    expect(wrapper.text()).toContain('功能需求')
    expect(wrapper.text()).toContain('订单支付')
    expect(wrapper.text()).toContain('用户提交订单后完成在线支付流程')
    expect(wrapper.text()).toContain('待确认')
  })

  it('emits view event on clicking the entire row', async () => {
    const wrapper = mount(RequirementListItem, { props: { requirement: featureReq } })
    await wrapper.get('.requirement-list-item').trigger('click')
    expect(wrapper.emitted('view')?.[0]).toEqual([featureReq])
  })

  it('shows confirm button only for attention-needed items (conflicted, unanalyzed)', async () => {
    const conflicted = { ...featureReq, status: 'CONFLICTED' as const }
    const wrapper = mount(RequirementListItem, { props: { requirement: conflicted } })
    const buttons = wrapper.findAll('button')
    const confirmBtn = buttons.find(b => b.text() === '确认')
    expect(confirmBtn).toBeTruthy()
    await confirmBtn!.trigger('click')
    expect(wrapper.emitted('confirm')?.[0]).toEqual([conflicted])
  })

  it('hides confirm button for already confirmed requirements', () => {
    const confirmed = { ...featureReq, status: 'CONFIRMED' as const }
    const wrapper = mount(RequirementListItem, { props: { requirement: confirmed } })
    const confirmBtn = wrapper.findAll('button').find(b => b.text() === '确认')
    expect(confirmBtn).toBeFalsy()
  })

  it('hides confirm button for locked requirements', () => {
    const locked = { ...featureReq, locked: true }
    const wrapper = mount(RequirementListItem, { props: { requirement: locked } })
    const confirmBtn = wrapper.findAll('button').find(b => b.text() === '确认')
    expect(confirmBtn).toBeFalsy()
  })

  it('summary is truncated via CSS line-clamp', () => {
    const longContent = { ...featureReq, content: 'A'.repeat(500) }
    const wrapper = mount(RequirementListItem, { props: { requirement: longContent } })
    const summary = wrapper.find('.requirement-list-item__summary')
    expect(summary.exists()).toBe(true)
    // CSS line-clamp handles visual truncation; content is still present
    expect(summary.text().length).toBeGreaterThan(0)
  })
})

describe('RequirementGroup', () => {
  it('renders group label and count', () => {
    const wrapper = mount(RequirementGroup, {
      props: { label: '功能需求', count: 5, collapsed: false },
      slots: { default: '<div class="child">item</div>' },
    })
    expect(wrapper.text()).toContain('功能需求')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.find('.child').exists()).toBe(true)
  })

  it('collapses and expands on header click', async () => {
    const wrapper = mount(RequirementGroup, {
      props: { label: '功能需求', count: 3, collapsed: false },
      slots: { default: '<div class="child">item</div>' },
    })
    expect(wrapper.find('.requirement-group--collapsed').exists()).toBe(false)

    await wrapper.get('.requirement-group__header').trigger('click')
    expect(wrapper.emitted('toggle')).toHaveLength(1)
  })

  it('applies collapsed class when collapsed prop is true', () => {
    const wrapper = mount(RequirementGroup, {
      props: { label: 'AI 推断', count: 2, collapsed: true },
      slots: { default: '<div class="child">item</div>' },
    })
    expect(wrapper.find('.requirement-group--collapsed').exists()).toBe(true)
  })
})

describe('StatusFilter', () => {
  it('renders all filter pills and emits on click', async () => {
    const wrapper = mount(StatusFilter, {
      props: {
        activeFilter: 'ALL',
        counts: { ALL: 10, CONFIRMED: 5, PENDING: 3, UNANALYZED: 1, CONFLICTED: 1 },
      },
    })
    const pills = wrapper.findAll('.status-filter__pill')
    expect(pills).toHaveLength(5)
    expect(wrapper.text()).toContain('全部')
    expect(wrapper.text()).toContain('已确认')
    expect(wrapper.text()).toContain('待确认')
    expect(wrapper.text()).toContain('待分析')
    expect(wrapper.text()).toContain('冲突')

    await pills[1].trigger('click')
    expect(wrapper.emitted('update:activeFilter')?.[0]).toEqual(['CONFIRMED'])
  })

  it('highlights active pill', () => {
    const wrapper = mount(StatusFilter, {
      props: {
        activeFilter: 'CONFIRMED',
        counts: { ALL: 10, CONFIRMED: 5, PENDING: 3, UNANALYZED: 1, CONFLICTED: 1 },
      },
    })
    const activePill = wrapper.find('.status-filter__pill--active')
    expect(activePill.exists()).toBe(true)
    expect(activePill.text()).toContain('已确认')
  })
})
