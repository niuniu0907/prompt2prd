import 'fake-indexeddb/auto'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createAppDatabase, type AppDatabase } from '@/db/appDatabase'
import { createProject } from '@/features/projects/types'
import { PRD_SECTION_KEYS } from '@/features/prd/types'
import { PrdRepository } from './prdRepository'

const projectId = '123e4567-e89b-42d3-a456-426614174000'

describe('PrdRepository', () => {
  let database: AppDatabase
  let repository: PrdRepository

  beforeEach(async () => {
    database = createAppDatabase(`prd-${crypto.randomUUID()}`)
    repository = new PrdRepository(database)
    await database.project.add(createProject({
      id: projectId, name: 'PRD 测试', originalPrompt: '测试 PRD 持久化',
      now: '2026-07-17T00:00:00.000Z',
    }))
  })

  afterEach(async () => { await database.delete() })

  it('initializes 17 sections on first access', async () => {
    const sections = await repository.initializeSections(projectId, '2026-07-17T01:00:00.000Z')
    expect(sections).toHaveLength(17)
    expect(sections[0].order).toBe(1)
    expect(sections[16].order).toBe(17)
    expect(sections[0].status).toBe('DRAFT')
    // Second call returns existing sections
    const again = await repository.initializeSections(projectId, '2026-07-17T02:00:00.000Z')
    expect(again).toHaveLength(17)
    expect(await database.prd_section.where('projectId').equals(projectId).count()).toBe(17)
  })

  it('updates content and rejects locked sections', async () => {
    await repository.initializeSections(projectId)
    const updated = await repository.updateContent(
      projectId, 'apis', '## 接口契约\n\n内容', '2026-07-17T02:00:00.000Z')
    expect(updated.content).toContain('接口契约')

    // Lock then reject edit
    await repository.lockSection(projectId, 'apis', true)
    await expect(repository.updateContent(projectId, 'apis', '新内容')).rejects.toThrow('locked')
  })

  it('saveSection creates version record', async () => {
    await repository.initializeSections(projectId)
    await repository.updateContent(projectId, 'apis', '草稿内容', '2026-07-17T01:00:00.000Z')
    const saved = await repository.saveSection(projectId, 'apis', '最终内容', '2026-07-17T02:00:00.000Z')
    expect(saved.status).toBe('COMPLETED')
    expect(saved.content).toBe('最终内容')
    expect(await database.requirement_version.where('projectId').equals(projectId).count()).toBe(1)
  })

  it('saveGeneratedContent does not overwrite locked sections', async () => {
    await repository.initializeSections(projectId)
    await repository.updateContent(projectId, 'apis', '旧内容')
    await repository.lockSection(projectId, 'apis', true)
    const result = await repository.saveGeneratedContent(projectId, 'apis', '新生成内容')
    expect(result.content).toBe('旧内容')
  })

  it('saveBeforeRegeneration creates backup version', async () => {
    await repository.initializeSections(projectId)
    await repository.saveSection(projectId, 'apis', '当前内容', '2026-07-17T01:00:00.000Z')
    await repository.saveBeforeRegeneration(projectId, 'apis', '2026-07-17T02:00:00.000Z')
    expect(await database.requirement_version.where('projectId').equals(projectId).count()).toBe(2)
  })

  it('replaceAfterRegeneration updates and creates version', async () => {
    await repository.initializeSections(projectId)
    await repository.saveSection(projectId, 'apis', '旧内容')
    await repository.saveBeforeRegeneration(projectId, 'apis')
    const replaced = await repository.replaceAfterRegeneration(
      projectId, 'apis', '新生成内容', '2026-07-17T03:00:00.000Z')
    expect(replaced.content).toBe('新生成内容')
    expect(replaced.status).toBe('COMPLETED')
    expect(await database.requirement_version.where('projectId').equals(projectId).count()).toBe(3)
  })

  it('listByProject sorts by order', async () => {
    await repository.initializeSections(projectId)
    const sections = await repository.listByProject(projectId)
    expect(sections).toHaveLength(17)
    expect(sections.map(s => s.order)).toEqual(Array.from({ length: 17 }, (_, i) => i + 1))
  })

  it('getByKey returns undefined for unknown key', async () => {
    await repository.initializeSections(projectId)
    const result = await repository.getByKey(projectId, 'nonexistent')
    expect(result).toBeUndefined()
  })
})
