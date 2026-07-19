import 'fake-indexeddb/auto'
import { afterEach, describe, expect, it } from 'vitest'
import { createAppDatabase, type AppDatabase } from '../appDatabase'
import { RequirementRepository } from './requirementRepository'
import { createProject } from '@/features/projects/types'
import { createRequirementItem } from '@/features/requirements/types'

let activeDatabase: AppDatabase | undefined

describe('manual requirement editing', () => {
  afterEach(async () => { activeDatabase?.close(); if (activeDatabase) await activeDatabase.delete(); activeDatabase = undefined })

  it('commits an unlocked edit as confirmed user content with affected artifacts and a version', async () => {
    const context = await seed(false)
    const updated = await context.repository.commitManualEdit(context.requirementId, {
      title: '退款规则', content: '支付后 7 天内允许退款', affectedArtifacts: ['PRD', 'FLOWCHART', 'PRD'],
    }, '2026-07-17T12:01:00.000Z')
    expect(updated).toMatchObject({ status: 'CONFIRMED', sourceType: 'USER_EDIT', title: '退款规则' })
    expect(updated.metadata.affectedArtifacts).toEqual(['PRD', 'FLOWCHART'])
    expect(await context.database.requirement_version.count()).toBe(1)
    context.database.close()
    activeDatabase = createAppDatabase(context.databaseName)
    expect((await activeDatabase.requirement_item.get(context.requirementId))?.content).toBe('支付后 7 天内允许退款')
  })

  it('blocks both draft and committed edits while locked', async () => {
    const context = await seed(true)
    await expect(context.repository.updateRequirement(context.requirementId, { content: '修改' })).rejects.toThrow(/unlock/i)
    await expect(context.repository.commitManualEdit(context.requirementId, { title: '规则', content: '修改', affectedArtifacts: [] })).rejects.toThrow(/unlock/i)
  })
})

async function seed(locked: boolean) {
  const databaseName = `editing-${crypto.randomUUID()}`
  activeDatabase = createAppDatabase(databaseName)
  const projectId = '10000000-0000-4000-8000-000000000000'
  const requirementId = '20000000-0000-4000-8000-000000000000'
  await activeDatabase.project.add(createProject({ id: projectId, name: '测试', originalPrompt: '创建一个测试项目', now: '2026-07-17T12:00:00.000Z' }))
  await activeDatabase.requirement_item.add(createRequirementItem({ id: requirementId, projectId, type: 'BUSINESS_RULE', title: '原规则', content: '原内容', status: 'CONFIRMED', sourceType: 'USER_ANSWER', sourceId: null, locked, now: '2026-07-17T12:00:00.000Z' }))
  let counter = 1
  return { database: activeDatabase, databaseName, projectId, requirementId, repository: new RequirementRepository(activeDatabase, () => `90000000-0000-4000-8000-${String(counter++).padStart(12, '0')}`) }
}
