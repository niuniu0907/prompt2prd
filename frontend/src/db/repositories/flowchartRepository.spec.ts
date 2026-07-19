import 'fake-indexeddb/auto'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createAppDatabase, type AppDatabase } from '@/db/appDatabase'
import { createProject } from '@/features/projects/types'
import type { FlowchartDraft } from '@/features/flowchart/types'
import { FlowchartRepository } from './flowchartRepository'

const projectId = '123e4567-e89b-42d3-a456-426614174000'
const valid = async (source: string) => source.includes('INVALID')
  ? { valid: false, diagramType: null, message: '语法错误' }
  : { valid: true, diagramType: 'flowchart-v2', message: null }

function draft(key: string, source = 'flowchart TD\nA-->B'): FlowchartDraft {
  return { key, type: key === 'main' ? 'MAIN' : 'EXCEPTION', title: key, mermaid: source,
    sourceRequirementIds: key === 'main' ? [] : ['123e4567-e89b-42d3-a456-426614174001'] }
}

describe('FlowchartRepository', () => {
  let database: AppDatabase
  let repository: FlowchartRepository
  beforeEach(async () => {
    database = createAppDatabase(`flowchart-${crypto.randomUUID()}`)
    repository = new FlowchartRepository(database, valid)
    await database.project.add(createProject({ id: projectId, name: '流程图测试', originalPrompt: '测试流程图持久化', now: '2026-07-17T00:00:00.000Z' }))
  })
  afterEach(async () => { await database.delete() })

  it('saves valid diagrams independently and reports invalid siblings', async () => {
    const result = await repository.saveGenerated(projectId,
      [draft('main'), draft('exception-a', 'INVALID')], '2026-07-17T01:00:00.000Z')
    expect(result.saved).toHaveLength(1)
    expect(result.failures).toHaveLength(1)
    expect(await repository.listByProject(projectId)).toHaveLength(1)
    expect((await repository.getByKey(projectId, 'main'))?.mermaid).toContain('A-->B')
    expect(await database.requirement_version.where('projectId').equals(projectId).count()).toBe(1)
    expect((await database.project.get(projectId))?.stage).toBe('FLOWCHART')
  })

  it('requires confirmation path to replace an existing diagram', async () => {
    await repository.saveGenerated(projectId, [draft('main')], '2026-07-17T01:00:00.000Z')
    const duplicate = await repository.saveGenerated(projectId,
      [draft('main', 'flowchart TD\nA-->C')], '2026-07-17T02:00:00.000Z')
    expect(duplicate.saved).toHaveLength(0)
    expect((await repository.getByKey(projectId, 'main'))?.mermaid).toContain('A-->B')

    const replaced = await repository.replaceAfterConfirmation(projectId,
      draft('main', 'flowchart TD\nA-->C'), '2026-07-17T03:00:00.000Z')
    expect(replaced.mermaid).toContain('A-->C')
    expect(await database.flowchart.where('projectId').equals(projectId).count()).toBe(1)
    expect(await database.requirement_version.where('projectId').equals(projectId).count()).toBe(2)
  })

  it('rejects invalid replacement and preserves the old diagram', async () => {
    await repository.saveGenerated(projectId, [draft('main')])
    await expect(repository.replaceAfterConfirmation(projectId, draft('main', 'INVALID'))).rejects.toThrow('语法错误')
    expect((await repository.getByKey(projectId, 'main'))?.mermaid).toContain('A-->B')
  })
})
