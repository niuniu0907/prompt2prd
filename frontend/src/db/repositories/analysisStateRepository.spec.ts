import 'fake-indexeddb/auto'

import { afterEach, describe, expect, it } from 'vitest'

import stateFixture from '../../../../contracts/analysis-state.sample.json'
import { createAppDatabase, type AppDatabase } from '../appDatabase'
import { AnalysisStateRepository } from './analysisStateRepository'
import { createProject } from '@/features/projects/types'

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
})
