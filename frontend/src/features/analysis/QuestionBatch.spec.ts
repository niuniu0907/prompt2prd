import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { ClarificationQuestion } from '@/features/requirements/types'
import QuestionBatch from './QuestionBatch.vue'

describe('QuestionBatch', () => {
  it('requires the whole current batch before one submission', async () => {
    const wrapper = mount(QuestionBatch, { props: { questions: questions() } })

    expect(wrapper.text()).toContain('已回答 0/4')
    expect(wrapper.find('[data-testid="batch-top-actions"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="submit-batch"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('影响：需要资质审核')
    expect(wrapper.text()).toContain('支持哪些支付方式？')
    await wrapper.get('[data-testid="option-role-11"]').setValue(true)
    expect(wrapper.text()).toContain('已回答 1/4')
    expect(wrapper.get('[data-testid="submit-batch"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="option-pay-22"]').setValue(true)
    await wrapper.get('[data-testid="option-refund-33"]').setValue(true)
    await wrapper.get('[data-testid="option-audit-44"]').setValue(true)
    expect(wrapper.get('[data-testid="submit-batch"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="custom-role"]').setValue('也允许救助站')
    await wrapper.get('[data-testid="note-role"]').setValue('先做白名单')
    expect(wrapper.get('[data-testid="submit-batch"]').text()).toBe('提交并继续')
    await wrapper.get('[data-testid="submit-batch"]').trigger('click')

    const emitted = wrapper.emitted('submit')?.[0]?.[0] as Array<Record<string, unknown>>
    expect(emitted).toHaveLength(4)
    expect(emitted[0]).toMatchObject({
      questionId: '00000000-0000-4000-8000-000000000001',
      selectedOptionIds: ['00000000-0000-4000-8000-000000000011'],
      customAnswer: '也允许救助站',
      note: '先做白名单',
      skipped: false,
    })
    expect(emitted[1]).toMatchObject({
      questionId: '00000000-0000-4000-8000-000000000002',
      selectedOptionIds: ['00000000-0000-4000-8000-000000000022'],
      skipped: false,
    })
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('can adopt AI suggestions for the whole batch', async () => {
    const wrapper = mount(QuestionBatch, { props: { questions: questions() } })

    await wrapper.get('[data-testid="adopt-suggestion"]').trigger('click')
    expect(wrapper.text()).toContain('已回答 4/4')
    await wrapper.get('[data-testid="submit-batch"]').trigger('click')

    const emitted = wrapper.emitted('submit')?.[0]?.[0] as Array<Record<string, unknown>>
    expect(emitted).toHaveLength(4)
    expect(emitted.map(item => item.selectedOptionIds)).toEqual([
      ['00000000-0000-4000-8000-000000000011'],
      ['00000000-0000-4000-8000-000000000022'],
      ['00000000-0000-4000-8000-000000000033'],
      ['00000000-0000-4000-8000-000000000044'],
    ])
  })

  it('does not offer inline PRD generation from the question batch', async () => {
    const wrapper = mount(QuestionBatch, { props: { questions: questions() } })

    expect(wrapper.find('[data-testid="generate-prd-now"]').exists()).toBe(false)
  })

  it('supports skipping the whole batch and blocks duplicate clicks while analyzing', async () => {
    const wrapper = mount(QuestionBatch, { props: { questions: questions(), submitStatus: 'ANALYZING' } })
    expect(wrapper.get('[data-testid="submit-batch"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="skip-batch"]').attributes('disabled')).toBeDefined()

    await wrapper.setProps({ submitStatus: 'IDLE' })
    await wrapper.get('[data-testid="skip-batch"]').trigger('click')

    const emitted = wrapper.emitted('submit')?.[0]?.[0] as Array<{ skipped: boolean }>
    expect(emitted).toHaveLength(4)
    expect(emitted.every(item => item.skipped)).toBe(true)
  })
})

function questions(): ClarificationQuestion[] {
  const base = {
    projectId: '10000000-0000-4000-8000-000000000000',
    batchId: '20000000-0000-4000-8000-000000000000',
    roundNo: 1, coverageCategories: [] as string[],
    reason: '这会影响后续规则', dimension: 'BUSINESS_RULES', targetField: 'rule',
    priority: 4, status: 'PENDING' as const,
    createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z',
  }
  return [
    { ...base, id: '00000000-0000-4000-8000-000000000001', semanticKey: 'role', text: '支持哪些寄养方？', inputType: 'SINGLE_SELECT', options: [{ id: '00000000-0000-4000-8000-000000000011', label: '宠物店', impact: '需要资质审核', recommended: true }] },
    { ...base, id: '00000000-0000-4000-8000-000000000002', semanticKey: 'pay', text: '支持哪些支付方式？', inputType: 'MULTI_SELECT', options: [{ id: '00000000-0000-4000-8000-000000000022', label: '微信支付', impact: '接入支付平台', recommended: true }] },
    { ...base, id: '00000000-0000-4000-8000-000000000003', semanticKey: 'refund', text: '退款期限？', inputType: 'SINGLE_SELECT', options: [{ id: '00000000-0000-4000-8000-000000000033', label: '24小时内', impact: '补充退款规则', recommended: true }] },
    { ...base, id: '00000000-0000-4000-8000-000000000004', semanticKey: 'audit', text: '需要人工审核吗？', inputType: 'CONFIRMATION', options: [{ id: '00000000-0000-4000-8000-000000000044', label: '需要', impact: '增加审核队列', recommended: true }] },
  ]
}
