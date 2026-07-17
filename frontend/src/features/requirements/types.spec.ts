import { describe, expect, it } from 'vitest'

import { createRequirementItem } from './types'

const itemId = 'ce617340-3b0f-43e6-8e2b-524871c828ab'
const projectId = '76deeeab-70cf-41af-92a2-24ff466ca1b1'
const answerId = 'df423353-4358-48c6-ae0a-82104962e51d'
const now = '2026-07-17T07:10:00.000Z'

describe('createRequirementItem', () => {
  it('preserves source and update metadata on a confirmed locked item', () => {
    const item = createRequirementItem({
      id: itemId,
      projectId,
      type: 'BUSINESS_RULE',
      title: '保存规则',
      content: '每次有效状态变化后保存',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: answerId,
      locked: true,
      now,
    })

    expect(item).toMatchObject({
      sourceType: 'USER_ANSWER',
      sourceId: answerId,
      status: 'CONFIRMED',
      locked: true,
      createdAt: now,
      updatedAt: now,
    })
  })

  it('rejects locking any non-confirmed requirement', () => {
    expect(() =>
      createRequirementItem({
        id: itemId,
        projectId,
        type: 'FEATURE',
        title: '候选功能',
        content: '等待确认',
        status: 'PENDING',
        sourceType: 'AI_INFERENCE',
        sourceId: null,
        locked: true,
        now,
      }),
    ).toThrow('Only CONFIRMED')
  })

  it('rejects runtime status values outside the persisted enum', () => {
    expect(() =>
      createRequirementItem({
        id: itemId,
        projectId,
        type: 'FEATURE',
        title: '非法状态',
        content: '不能进入正式对象',
        status: 'APPROVED' as never,
        sourceType: 'USER_EDIT',
        sourceId: null,
        now,
      }),
    ).toThrow('requirement status')
  })
})
