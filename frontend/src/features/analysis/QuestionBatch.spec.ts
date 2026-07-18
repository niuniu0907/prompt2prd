import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { ClarificationQuestion } from '@/features/requirements/types'
import QuestionBatch from './QuestionBatch.vue'

describe('QuestionBatch', () => {
  it('supports the current single question, custom answers, AI suggestion, and submission', async () => {
    const wrapper = mount(QuestionBatch, { props: { questions: questions() } })

    expect(wrapper.text()).toContain('当前问题 1/4')
    expect(wrapper.get('[data-testid="submit-batch"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('影响：需要资质审核')
    expect(wrapper.text()).not.toContain('旧版本生成的无选项问题')
    await wrapper.get('[data-testid="adopt-suggestion"]').trigger('click')
    expect(wrapper.get('[data-testid="submit-batch"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="option-role-11"]').setValue(true)
    await wrapper.get('[data-testid="custom-role"]').setValue('也允许救助站')
    await wrapper.get('[data-testid="note-role"]').setValue('先做白名单')
    expect(wrapper.get('[data-testid="submit-batch"]').text()).toBe('提交并继续')
    await wrapper.get('[data-testid="submit-batch"]').trigger('click')

    const emitted = wrapper.emitted('submit')?.[0]?.[0] as Array<Record<string, unknown>>
    expect(emitted).toHaveLength(1)
    expect(emitted[0]).toMatchObject({
      questionId: '00000000-0000-4000-8000-000000000001',
      selectedOptionIds: ['00000000-0000-4000-8000-000000000011'],
      customAnswer: '也允许救助站',
      note: '先做白名单',
      skipped: false,
    })
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('submits only the visible current question', async () => {
    const wrapper = mount(QuestionBatch, { props: { questions: questions() } })

    await wrapper.get('[data-testid="option-role-11"]').setValue(true)
    await wrapper.get('[data-testid="submit-batch"]').trigger('click')

    const emitted = wrapper.emitted('submit')?.[0]?.[0] as Array<Record<string, unknown>>
    expect(emitted).toHaveLength(1)
    expect(emitted[0]).toMatchObject({
      questionId: '00000000-0000-4000-8000-000000000001',
      selectedOptionIds: ['00000000-0000-4000-8000-000000000011'],
      skipped: false,
    })
  })

  it('supports skipping the current question and blocks duplicate clicks while busy', async () => {
    const wrapper = mount(QuestionBatch, { props: { questions: questions(), busy: true } })
    expect(wrapper.get('[data-testid="submit-batch"]').attributes('disabled')).toBeDefined()

    await wrapper.setProps({ busy: false })
    await wrapper.get('[data-testid="skip-role"]').trigger('click')
    expect(wrapper.text()).toContain('已跳过')
    await wrapper.get('[data-testid="skip-batch"]').trigger('click')

    const emitted = wrapper.emitted('submit')?.[0]?.[0] as Array<{ skipped: boolean }>
    expect(emitted).toHaveLength(1)
    expect(emitted.every(item => item.skipped)).toBe(true)
  })
})

function questions(): ClarificationQuestion[] {
  const base = {
    projectId: '10000000-0000-4000-8000-000000000000',
    batchId: '20000000-0000-4000-8000-000000000000',
    reason: '这会影响后续规则', dimension: 'BUSINESS_RULES', targetField: 'rule',
    priority: 4, status: 'PENDING' as const,
    createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z',
  }
  return [
    { ...base, id: '00000000-0000-4000-8000-000000000001', semanticKey: 'role', text: '支持哪些寄养方？', inputType: 'SINGLE_SELECT', options: [{ id: '00000000-0000-4000-8000-000000000011', label: '宠物店', impact: '需要资质审核', recommended: true }] },
    { ...base, id: '00000000-0000-4000-8000-000000000002', semanticKey: 'pay', text: '支持哪些支付方式？', inputType: 'MULTI_SELECT', options: [{ id: '00000000-0000-4000-8000-000000000022', label: '微信支付', impact: '接入支付平台', recommended: true }] },
    { ...base, id: '00000000-0000-4000-8000-000000000003', semanticKey: 'refund', text: '退款期限？', inputType: 'TEXT', options: [] },
    { ...base, id: '00000000-0000-4000-8000-000000000004', semanticKey: 'audit', text: '需要人工审核吗？', inputType: 'CONFIRMATION', options: [{ id: '00000000-0000-4000-8000-000000000044', label: '需要', impact: '增加审核队列', recommended: true }] },
  ]
}
