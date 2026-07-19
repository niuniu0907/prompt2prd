import 'fake-indexeddb/auto'

import Dexie from 'dexie'
import { afterEach, describe, expect, it } from 'vitest'

import { createProject } from '@/features/projects/types'
import { createAppDatabase, DATABASE_VERSION } from './appDatabase'
import { DATABASE_STORES, DATABASE_STORES_V1, PERSISTED_STORE_NAMES } from './schema'

const databaseNames = new Set<string>()

function createTestDatabase() {
  const name = `prompt2prd-test-${crypto.randomUUID()}`
  databaseNames.add(name)
  return createAppDatabase(name)
}

afterEach(async () => {
  for (const name of databaseNames) {
    await Dexie.delete(name)
  }
  databaseNames.clear()
})

describe('AppDatabase', () => {
  it('creates version 2 with the ten documented object stores and indexes', async () => {
    const database = createTestDatabase()
    await database.open()

    expect(database.verno).toBe(DATABASE_VERSION)
    expect(database.tables.map((table) => table.name).sort()).toEqual(
      [...PERSISTED_STORE_NAMES].sort(),
    )
    expect(database.project.schema.primKey.name).toBe('id')
    expect(database.requirement_item.schema.indexes.map((index) => index.name)).toContain(
      '[projectId+status]',
    )
    expect(database.clarification_question.schema.indexes.map((index) => index.name)).toContain(
      '[projectId+status]',
    )
    expect(database.prd_section.schema.indexes.map((index) => index.name)).toContain(
      '[projectId+sectionKey]',
    )
    expect(database.flowchart.schema.indexes.map((index) => index.name)).toContain('[projectId+key]')

    database.close()
  })

  it('upgrades a version 1 database without losing existing projects', async () => {
    const name = `prompt2prd-v1-${crypto.randomUUID()}`
    databaseNames.add(name)
    const legacy = new Dexie(name)
    legacy.version(1).stores(DATABASE_STORES_V1)
    const project = createProject({ id: '66deeeab-70cf-41af-92a2-24ff466ca1b1', name: '迁移项目', originalPrompt: '验证数据库迁移', now: '2026-07-17T07:00:00.000Z' })
    await legacy.open()
    await legacy.table('project').add(project)
    legacy.close()

    const upgraded = createAppDatabase(name)
    await upgraded.open()
    expect(upgraded.verno).toBe(DATABASE_VERSION)
    await expect(upgraded.project.get(project.id)).resolves.toEqual(project)
    expect(upgraded.tables.map(table => table.name)).toContain('flowchart')
    upgraded.close()
  })

  it('persists a project after close and reopen', async () => {
    const database = createTestDatabase()
    const name = database.name
    const project = createProject({
      id: '76deeeab-70cf-41af-92a2-24ff466ca1b1',
      name: '离线项目',
      originalPrompt: '验证关闭重开',
      now: '2026-07-17T07:20:00.000Z',
    })

    await database.open()
    await database.project.add(project)
    database.close()

    const reopened = createAppDatabase(name)
    await reopened.open()
    await expect(reopened.project.get(project.id)).resolves.toEqual(project)
    reopened.close()
  })

  it('has no credential store or API-key field in the schema contract', () => {
    const serializedSchema = JSON.stringify(DATABASE_STORES).toLowerCase()

    expect(serializedSchema).not.toMatch(/api.?key|authorization|credential|secret|token/)
    expect(PERSISTED_STORE_NAMES).toEqual([
      'project',
      'requirement_item',
      'clarification_question',
      'clarification_answer',
      'clarification_round',
      'requirement_conflict',
      'requirement_version',
      'requirement_change',
      'prd_section',
      'flowchart',
      'app_setting',
    ])
  })
})
