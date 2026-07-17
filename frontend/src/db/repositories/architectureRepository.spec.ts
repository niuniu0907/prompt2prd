import 'fake-indexeddb/auto'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createAppDatabase, type AppDatabase } from '@/db/appDatabase'
import { createProject } from '@/features/projects/types'
import type { ArchitectureCandidate } from '@/features/architecture/types'
import { ArchitectureRepository } from './architectureRepository'

const projectId = '123e4567-e89b-42d3-a456-426614174000'
function candidate(index: number): ArchitectureCandidate { return { id: `123e4567-e89b-42d3-a456-42661417400${index}`, name: `方案 ${index}`, stack: { frontend: 'Vue' }, responsibilities: ['界面'], advantages: ['快'], disadvantages: ['限制'], limitations: ['单体'], unselectedReasons: ['不匹配'], scores: { LEARNING_COST: 5, DEVELOPMENT_SPEED: 4, DEPLOYMENT_SIMPLICITY: 5, RUNNING_COST: 4, MAINTAINABILITY: 5, SCALABILITY: 3, AI_SUPPORT: 5 }, totalScore: 31, recommended: index === 0 } }

describe('ArchitectureRepository', () => {
  let database: AppDatabase
  let repository: ArchitectureRepository
  beforeEach(async () => { database = createAppDatabase(`architecture-${crypto.randomUUID()}`); repository = new ArchitectureRepository(database); await database.project.add(createProject({ id: projectId, name: '架构测试', originalPrompt: '这是一个架构测试项目', now: '2026-07-17T00:00:00.000Z' })) })
  afterEach(async () => { await database.delete() })

  it('stores unconfirmed candidates as drafts', async () => {
    await repository.saveCandidates(projectId, [candidate(0), candidate(1)])
    const saved = await database.requirement_item.where('projectId').equals(projectId).toArray()
    expect(saved).toHaveLength(2)
    expect(saved.every(item => item.status === 'PENDING' && item.metadata.draft === true)).toBe(true)
    expect(await repository.selected(projectId)).toBeNull()
  })

  it('keeps exactly one confirmed architecture and versions every switch', async () => {
    await repository.saveCandidates(projectId, [candidate(0), candidate(1), candidate(2)])
    const first = await repository.confirm(projectId, candidate(0), false, '2026-07-17T01:00:00.000Z')
    const second = await repository.confirm(projectId, candidate(1), false, '2026-07-17T02:00:00.000Z')
    const saved = await database.requirement_item.where('projectId').equals(projectId).toArray()
    expect(saved.filter(item => item.status === 'CONFIRMED')).toHaveLength(1)
    expect((await repository.selected(projectId))?.id).toBe(candidate(1).id)
    expect(await database.requirement_version.where('projectId').equals(projectId).count()).toBe(2)
    expect((await database.project.get(projectId))?.completeness).toBe(12)
    expect(await database.app_setting.get(`analysisCompleteness:${projectId}`)).toBeDefined()
    expect(first.event.type).toBe('architecture_confirmed')
    expect(second.event.data.architectureId).toBe(candidate(1).id)
  })
})
