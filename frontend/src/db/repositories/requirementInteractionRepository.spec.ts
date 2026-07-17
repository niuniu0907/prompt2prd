import 'fake-indexeddb/auto'
import { afterEach, describe, expect, it } from 'vitest'
import { createAppDatabase, type AppDatabase } from '../appDatabase'
import { RequirementInteractionRepository } from './requirementInteractionRepository'
import { createProject } from '@/features/projects/types'
import { createRequirementItem, type RequirementConflict } from '@/features/requirements/types'

describe('RequirementInteractionRepository', () => {
  let database: AppDatabase | undefined
  afterEach(async () => { database?.close(); if (database) await database.delete() })

  it('distinguishes blocking core conflicts and versions their resolution', async () => {
    const context = await seed()
    expect(await context.repository.hasBlockingCoreConflict(context.projectId)).toBe(true)
    await context.repository.resolveConflict(context.conflictId, '保留已确认规则', '2026-07-17T12:02:00.000Z')
    expect(await context.repository.hasBlockingCoreConflict(context.projectId)).toBe(false)
    expect((await context.database.requirement_conflict.get(context.conflictId))?.status).toBe('RESOLVED')
    expect(await context.database.requirement_version.count()).toBe(1)
  })

  it('requires confirmed content before locking and requires unlock before editing', async () => {
    const context = await seed()
    await expect(context.repository.setLocked(context.assumptionId, true)).rejects.toThrow(/confirmed/i)
    await context.repository.decideAssumption(context.assumptionId, true, '2026-07-17T12:02:00.000Z')
    await context.repository.setLocked(context.assumptionId, true, '2026-07-17T12:03:00.000Z')
    await expect(context.repository.assertEditable(context.assumptionId)).rejects.toThrow(/unlock/i)
    await context.repository.setLocked(context.assumptionId, false, '2026-07-17T12:04:00.000Z')
    await expect(context.repository.assertEditable(context.assumptionId)).resolves.toBeUndefined()
  })

  it('keeps a rejected assumption pending and records the decision', async () => {
    const context = await seed()
    const rejected = await context.repository.decideAssumption(context.assumptionId, false, '2026-07-17T12:02:00.000Z')
    expect(rejected.status).toBe('PENDING')
    expect(rejected.metadata.decision).toBe('REJECTED')
    expect(await context.database.requirement_version.count()).toBe(1)
  })
})

async function seed() {
  const database = createAppDatabase(`interaction-${crypto.randomUUID()}`)
  const projectId = '10000000-0000-4000-8000-000000000000'
  const assumptionId = '20000000-0000-4000-8000-000000000000'
  const conflictId = '30000000-0000-4000-8000-000000000000'
  await database.project.add(createProject({ id: projectId, name: '测试', originalPrompt: '创建一个测试项目', now: '2026-07-17T12:00:00.000Z' }))
  await database.requirement_item.add(createRequirementItem({ id: assumptionId, projectId, type: 'ASSUMPTION', title: '审核假设', content: '默认人工审核', status: 'INFERRED', sourceType: 'AI_INFERENCE', sourceId: null, now: '2026-07-17T12:00:00.000Z' }))
  const conflict: RequirementConflict = { id: conflictId, projectId, leftRequirementId: assumptionId, rightRequirementId: null, leftContent: '人工审核', rightContent: '自动审核', impact: '影响审核流程', core: true, status: 'OPEN', resolution: null, createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z', resolvedAt: null }
  await database.requirement_conflict.add(conflict)
  let counter = 1
  return { database, projectId, assumptionId, conflictId, repository: new RequirementInteractionRepository(database, () => `90000000-0000-4000-8000-${String(counter++).padStart(12, '0')}`) }
}
