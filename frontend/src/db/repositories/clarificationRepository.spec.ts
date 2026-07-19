import 'fake-indexeddb/auto'

import { afterEach, describe, expect, it } from 'vitest'
import { createAppDatabase, type AppDatabase } from '../appDatabase'
import { ClarificationRepository } from './clarificationRepository'
import { createProject } from '@/features/projects/types'
import type { ClarificationQuestion } from '@/features/requirements/types'

describe('ClarificationRepository', () => {
  let database: AppDatabase | undefined
  afterEach(async () => { database?.close(); if (database) await database.delete() })

  it('persists mixed answers and reuses the same answer on repeated submission', async () => {
    database = createAppDatabase(`clarification-${crypto.randomUUID()}`)
    const projectId = '10000000-0000-4000-8000-000000000000'
    await database.project.add(createProject({ id: projectId, name: '测试', originalPrompt: '创建一个测试项目', now: '2026-07-17T12:00:00.000Z' }))
    await database.clarification_question.bulkAdd([question(projectId, 1), question(projectId, 2)])
    let nextId = 1
    const repository = new ClarificationRepository(database, () => `90000000-0000-4000-8000-${String(nextId++).padStart(12, '0')}`)
    const drafts = [
      { questionId: question(projectId, 1).id, selectedOptionIds: [], customAnswer: '宠物店', note: null, skipped: false },
      { questionId: question(projectId, 2).id, selectedOptionIds: [], customAnswer: null, note: null, skipped: true },
    ]

    const first = await repository.submitBatch(projectId, drafts, '2026-07-17T12:01:00.000Z')
    const second = await repository.submitBatch(projectId, drafts, '2026-07-17T12:02:00.000Z')

    expect(first.answers).toHaveLength(2)
    expect(second.answers.map(item => item.id)).toEqual(first.answers.map(item => item.id))
    expect(await database.clarification_answer.count()).toBe(2)
    expect((await database.clarification_question.toArray()).map(item => item.status).sort()).toEqual(['ANSWERED', 'SKIPPED'])
  })
})

function question(projectId: string, index: number): ClarificationQuestion {
  const suffix = index.toString().padStart(12, '0')
  return {
    id: `00000000-0000-4000-8000-${suffix}`, projectId,
    batchId: '20000000-0000-4000-8000-000000000000', text: `问题 ${index}`,
    reason: '测试', dimension: 'FEATURES', targetField: `field.${index}`, semanticKey: `q-${index}`,
    roundNo: 1,
    coverageCategories: [] as string[],
    inputType: 'TEXT', options: [], priority: 1, status: 'PENDING',
    createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z',
  }
}
