import 'fake-indexeddb/auto'

import { afterEach, describe, expect, it } from 'vitest'

import stateFixture from '../../../../contracts/analysis-state.sample.json'
import { createAppDatabase, type AppDatabase } from '../appDatabase'
import { AnalysisStateRepository } from './analysisStateRepository'
import { createProject } from '@/features/projects/types'
import type { RequirementItem } from '@/features/requirements/types'

describe('AnalysisStateRepository', () => {
  let database: AppDatabase | undefined

  afterEach(async () => {
    database?.close()
    if (database) await database.delete()
    database = undefined
  })

  it('atomically persists a completed state and restores it after refresh', async () => {
    database = createAppDatabase(`analysis-state-${crypto.randomUUID()}`)
    const project = createProject({
      id: stateFixture.project.id,
      name: '用户自定义名称',
      originalPrompt: '我要做一个宠物寄养平台',
      now: '2026-07-17T12:00:00.000Z',
    })
    project.userRenamed = true
    await database.project.add(project)
    const repository = new AnalysisStateRepository(database)

    const saved = await repository.saveFinal(project.id, stateFixture)
    const restored = await repository.load(project.id)

    expect(saved.project.name).toBe('用户自定义名称')
    expect(restored).toEqual(saved)
    expect(restored?.requirements).toHaveLength(1)
    expect(restored?.questions).toHaveLength(1)
    expect(restored?.project.completeness).toBe(40)
  })

  it('preserves stored architecture candidates when saving a new analysis result', async () => {
    database = createAppDatabase(`analysis-state-${crypto.randomUUID()}`)
    const project = createProject({
      id: stateFixture.project.id,
      name: '美妆产品网站',
      originalPrompt: '我要做一个美妆产品网站',
      now: '2026-07-17T12:00:00.000Z',
    })
    await database.project.add(project)
    await database.requirement_item.add(architectureCandidate(project.id))
    const repository = new AnalysisStateRepository(database)

    const saved = await repository.saveFinal(project.id, stateFixture)
    const restored = await repository.load(project.id)

    expect(saved.requirements.some(item => item.metadata.kind === 'ARCHITECTURE_CANDIDATE')).toBe(true)
    expect(restored?.requirements.some(item => item.metadata.kind === 'ARCHITECTURE_CANDIDATE')).toBe(true)
    expect(restored?.requirements.some(item => item.title === '产品目标')).toBe(true)
  })
})

function architectureCandidate(projectId: string): RequirementItem {
  return {
    id: '50000000-0000-4000-8000-000000000000',
    projectId,
    type: 'TECHNICAL_CONSTRAINT',
    title: 'Vue 3 + Spring Boot 单体',
    content: 'frontend: Vue 3 + TypeScript',
    status: 'CONFIRMED',
    sourceType: 'USER_ANSWER',
    sourceId: null,
    locked: false,
    metadata: {
      kind: 'ARCHITECTURE_CANDIDATE',
      candidate: { id: '50000000-0000-4000-8000-000000000000', name: 'Vue 3 + Spring Boot 单体', stack: { frontend: 'Vue 3 + TypeScript' } },
    },
    createdAt: '2026-07-17T12:00:00.000Z',
    updatedAt: '2026-07-17T12:00:00.000Z',
  }
}
