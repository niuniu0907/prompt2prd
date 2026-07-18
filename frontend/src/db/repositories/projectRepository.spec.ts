import 'fake-indexeddb/auto'

import Dexie from 'dexie'
import { afterEach, describe, expect, it } from 'vitest'

import type { PrdSection } from '@/features/prd/types'
import type { FlowchartRecord } from '@/features/flowchart/types'
import { createProject, type Project } from '@/features/projects/types'
import {
  createRequirementItem,
  type ClarificationAnswer,
  type ClarificationQuestion,
  type RequirementChange,
  type RequirementConflict,
  type RequirementVersion,
} from '@/features/requirements/types'
import { createAppDatabase, type AppDatabase } from '../appDatabase'
import { ProjectRepository, ProjectStateError } from './projectRepository'

const databaseNames = new Set<string>()
const baseTime = '2026-07-17T08:00:00.000Z'

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${value.toString(16).padStart(12, '0')}`
}

function createTestContext() {
  const databaseName = `project-repository-${crypto.randomUUID()}`
  const database = createAppDatabase(databaseName)
  databaseNames.add(databaseName)
  return { database, repository: new ProjectRepository(database), databaseName }
}

function projectInput(id: string, name: string, now: string) {
  return {
    id,
    name,
    originalPrompt: `${name}的有效产品需求`,
    now,
  }
}

async function seedProjectGraph(database: AppDatabase, project: Project) {
  const requirement = createRequirementItem({
    id: uuid(101),
    projectId: project.id,
    type: 'BUSINESS_RULE',
    title: '本地保存',
    content: '项目状态保存在浏览器中',
    status: 'CONFIRMED',
    sourceType: 'USER_ANSWER',
    sourceId: uuid(104),
    locked: true,
    now: baseTime,
  })
  const question: ClarificationQuestion = {
    id: uuid(102),
    projectId: project.id,
    batchId: uuid(103),
    text: '数据保存在哪里？',
    reason: '确认存储边界',
    dimension: 'DATA',
    targetField: 'storage',
    semanticKey: 'data-storage',
    inputType: 'SINGLE_SELECT',
    options: [{ id: uuid(105), label: '浏览器本地', impact: '不跨设备同步', recommended: true }],
    priority: 5,
    status: 'ANSWERED',
    createdAt: baseTime,
    updatedAt: baseTime,
  }
  const answer: ClarificationAnswer = {
    id: uuid(104),
    projectId: project.id,
    questionId: question.id,
    selectedOptionIds: [question.options[0]!.id],
    customAnswer: null,
    note: '使用 IndexedDB',
    skipped: false,
    createdAt: baseTime,
    updatedAt: baseTime,
  }
  const conflict: RequirementConflict = {
    id: uuid(106),
    projectId: project.id,
    leftRequirementId: requirement.id,
    rightRequirementId: requirement.id,
    leftContent: '本地保存',
    rightContent: '云端保存',
    impact: '部署边界',
    core: true,
    status: 'RESOLVED',
    resolution: '保留本地保存',
    createdAt: baseTime,
    updatedAt: baseTime,
    resolvedAt: baseTime,
  }
  const version: RequirementVersion = {
    id: uuid(107),
    projectId: project.id,
    changeType: 'CREATE',
    summary: '建立本地保存规则',
    snapshot: {
      project,
      requirements: [requirement],
      questions: [question],
      answers: [answer],
      conflicts: [conflict],
    },
    createdAt: baseTime,
  }
  const change: RequirementChange = {
    id: uuid(108),
    projectId: project.id,
    versionId: version.id,
    requirementId: requirement.id,
    changeType: 'CREATE',
    field: 'content',
    oldValue: null,
    newValue: requirement.content,
    createdAt: baseTime,
  }
  const section: PrdSection = {
    id: uuid(109),
    projectId: project.id,
    sectionKey: 'product-background-goals',
    title: '产品背景与目标',
    content: '本地项目副本测试',
    order: 1,
    status: 'COMPLETED',
    locked: false,
    errorCode: null,
    createdAt: baseTime,
    updatedAt: baseTime,
  }
  const flowchart: FlowchartRecord = {
    id: uuid(110), projectId: project.id, key: 'main', type: 'MAIN', title: '主流程',
    mermaid: 'flowchart TD\nA-->B', status: 'VALID', sourceRequirementIds: [requirement.id],
    createdAt: baseTime, updatedAt: baseTime,
  }

  await database.transaction(
    'rw',
    [
      database.requirement_item,
      database.clarification_question,
      database.clarification_answer,
      database.requirement_conflict,
      database.requirement_version,
      database.requirement_change,
      database.prd_section,
      database.flowchart,
    ],
    async () => {
      await database.requirement_item.add(requirement)
      await database.clarification_question.add(question)
      await database.clarification_answer.add(answer)
      await database.requirement_conflict.add(conflict)
      await database.requirement_version.add(version)
      await database.requirement_change.add(change)
      await database.prd_section.add(section)
      await database.flowchart.add(flowchart)
    },
  )

  version.snapshot.flowcharts = [flowchart]
  await database.requirement_version.put(version)
  return { requirement, question, answer, conflict, version, change, section, flowchart }
}

afterEach(async () => {
  for (const name of databaseNames) {
    await Dexie.delete(name)
  }
  databaseNames.clear()
})

describe('ProjectRepository', () => {
  it('lists project summaries with pending requirement and question counts', async () => {
    const { database, repository } = createTestContext()
    const older = await repository.create(projectInput(uuid(301), '较早项目', baseTime))
    const newer = await repository.create(
      projectInput(uuid(302), '较新项目', '2026-07-17T08:10:00.000Z'),
    )
    await database.requirement_item.bulkAdd([
      createRequirementItem({
        id: uuid(303),
        projectId: older.id,
        type: 'FEATURE',
        title: '待确认功能',
        content: '等待用户确认',
        status: 'PENDING',
        sourceType: 'AI_INFERENCE',
        sourceId: null,
        now: baseTime,
      }),
      createRequirementItem({
        id: uuid(304),
        projectId: older.id,
        type: 'FEATURE',
        title: '已确认功能',
        content: '不应计入待确认',
        status: 'CONFIRMED',
        sourceType: 'USER_EDIT',
        sourceId: null,
        now: baseTime,
      }),
    ])
    await database.clarification_question.add({
      id: uuid(305),
      projectId: older.id,
      batchId: uuid(306),
      text: '待回答问题',
      reason: '补充信息',
      dimension: 'FEATURE',
      targetField: 'scope',
      semanticKey: 'feature-scope',
      inputType: 'TEXT',
      options: [],
      priority: 4,
      status: 'PENDING',
      createdAt: baseTime,
      updatedAt: baseTime,
    })

    await expect(repository.listSummaries()).resolves.toEqual([
      { project: newer, pendingCount: 0 },
      { project: older, pendingCount: 2 },
    ])
  })

  it('creates, reads, lists by lifecycle state, renames, and survives reopen', async () => {
    const { database, repository, databaseName } = createTestContext()
    const first = await repository.create(projectInput(uuid(1), '第一个项目', baseTime))
    const second = await repository.create(
      projectInput(uuid(2), '第二个项目', '2026-07-17T08:10:00.000Z'),
    )

    await expect(repository.getById(first.id)).resolves.toEqual(first)
    await expect(repository.list()).resolves.toEqual([second, first])

    const renamed = await repository.rename(first.id, '  已重命名项目  ', '2026-07-17T08:20:00.000Z')
    expect(renamed).toMatchObject({
      id: first.id,
      name: '已重命名项目',
      originalPrompt: first.originalPrompt,
      userRenamed: true,
      updatedAt: '2026-07-17T08:20:00.000Z',
    })

    database.close()
    const reopened = createAppDatabase(databaseName)
    const reopenedRepository = new ProjectRepository(reopened)
    await expect(reopenedRepository.getById(first.id)).resolves.toEqual(renamed)
    reopened.close()
  })

  it('applies a suggested model name only before the user renames the project', async () => {
    const { repository } = createTestContext()
    const project = await repository.create(projectInput(uuid(7), '临时项目名称', baseTime))

    const suggested = await repository.applySuggestedName(
      project.id,
      '  模型建议名称  ',
      '2026-07-17T08:15:00.000Z',
    )
    expect(suggested).toMatchObject({
      name: '模型建议名称',
      userRenamed: false,
      updatedAt: '2026-07-17T08:15:00.000Z',
    })

    await repository.rename(project.id, '用户最终名称', '2026-07-17T08:20:00.000Z')
    const preserved = await repository.applySuggestedName(
      project.id,
      '迟到的模型名称',
      '2026-07-17T08:25:00.000Z',
    )
    expect(preserved).toMatchObject({
      name: '用户最终名称',
      userRenamed: true,
      updatedAt: '2026-07-17T08:20:00.000Z',
    })
  })

  it('updates the original prompt for inline clarification edits and survives reopen', async () => {
    const { database, repository, databaseName } = createTestContext()
    const project = await repository.create(projectInput(uuid(70), '原始需求项目', baseTime))

    const updated = await repository.updateOriginalPrompt(
      project.id,
      '这是用户在 AI 澄清页修改后的原始需求',
      '2026-07-17T08:16:00.000Z',
    )

    expect(updated).toMatchObject({
      id: project.id,
      originalPrompt: '这是用户在 AI 澄清页修改后的原始需求',
      updatedAt: '2026-07-17T08:16:00.000Z',
    })
    await expect(repository.updateOriginalPrompt(project.id, '短', '2026-07-17T08:17:00.000Z')).rejects.toThrow(
      'original prompt must contain at least 5 Unicode characters',
    )

    database.close()
    const reopened = createAppDatabase(databaseName)
    const reopenedRepository = new ProjectRepository(reopened)
    await expect(reopenedRepository.getById(project.id)).resolves.toMatchObject({
      originalPrompt: '这是用户在 AI 澄清页修改后的原始需求',
    })
    reopened.close()
  })

  it('preserves the temporary name when the model suggestion is blank', async () => {
    const { repository } = createTestContext()
    const project = await repository.create(projectInput(uuid(8), '保留临时名称', baseTime))

    await expect(
      repository.applySuggestedName(project.id, '   ', '2026-07-17T08:15:00.000Z'),
    ).resolves.toEqual(project)
  })

  it('does not leave another project when creation fails', async () => {
    const { repository } = createTestContext()
    const input = projectInput(uuid(9), '唯一项目', baseTime)
    await repository.create(input)

    await expect(repository.create(input)).rejects.toThrow()
    await expect(repository.list()).resolves.toHaveLength(1)
  })

  it('archives, moves to trash without deleting data, and restores to active', async () => {
    const { database, repository } = createTestContext()
    const project = await repository.create(projectInput(uuid(3), '生命周期项目', baseTime))
    const graph = await seedProjectGraph(database, project)

    const archived = await repository.archive(project.id, '2026-07-17T08:30:00.000Z')
    expect(archived).toMatchObject({ status: 'ARCHIVED', archivedAt: '2026-07-17T08:30:00.000Z' })
    await expect(repository.list('ACTIVE')).resolves.toEqual([])
    await expect(repository.list('ARCHIVED')).resolves.toEqual([archived])

    const deleted = await repository.moveToTrash(project.id, '2026-07-17T08:40:00.000Z')
    expect(deleted).toMatchObject({ status: 'DELETED', deletedAt: '2026-07-17T08:40:00.000Z' })
    await expect(database.requirement_item.get(graph.requirement.id)).resolves.toEqual(graph.requirement)
    await expect(repository.list('DELETED')).resolves.toEqual([deleted])

    const restored = await repository.restore(project.id, '2026-07-17T08:50:00.000Z')
    expect(restored).toMatchObject({
      status: 'ACTIVE',
      archivedAt: null,
      deletedAt: null,
      updatedAt: '2026-07-17T08:50:00.000Z',
    })
  })

  it('copies the complete project graph with new IDs and independent references', async () => {
    const { database, repository } = createTestContext()
    const original = await repository.create(projectInput(uuid(4), '原项目', baseTime))
    const source = await seedProjectGraph(database, original)

    const copy = await repository.copy(original.id, { now: '2026-07-17T09:00:00.000Z' })
    expect(copy.id).not.toBe(original.id)
    expect(copy).toMatchObject({
      name: '原项目 副本',
      status: 'ACTIVE',
      archivedAt: null,
      deletedAt: null,
    })

    const [requirements, questions, answers, conflicts, versions, changes, sections, flowcharts] =
      await Promise.all([
        database.requirement_item.where('projectId').equals(copy.id).toArray(),
        database.clarification_question.where('projectId').equals(copy.id).toArray(),
        database.clarification_answer.where('projectId').equals(copy.id).toArray(),
        database.requirement_conflict.where('projectId').equals(copy.id).toArray(),
        database.requirement_version.where('projectId').equals(copy.id).toArray(),
        database.requirement_change.where('projectId').equals(copy.id).toArray(),
        database.prd_section.where('projectId').equals(copy.id).toArray(),
        database.flowchart.where('projectId').equals(copy.id).toArray(),
      ])

    expect([requirements, questions, answers, conflicts, versions, changes, sections, flowcharts].map((rows) => rows.length)).toEqual(
      [1, 1, 1, 1, 1, 1, 1, 1],
    )
    expect(requirements[0]!.id).not.toBe(source.requirement.id)
    expect(questions[0]!.id).not.toBe(source.question.id)
    expect(answers[0]!.questionId).toBe(questions[0]!.id)
    expect(answers[0]!.selectedOptionIds).toEqual([questions[0]!.options[0]!.id])
    expect(requirements[0]!.sourceId).toBe(answers[0]!.id)
    expect(conflicts[0]!.leftRequirementId).toBe(requirements[0]!.id)
    expect(changes[0]!.versionId).toBe(versions[0]!.id)
    expect(changes[0]!.requirementId).toBe(requirements[0]!.id)
    expect(versions[0]!.snapshot.project.id).toBe(copy.id)
    expect(versions[0]!.snapshot.requirements[0]!.id).toBe(requirements[0]!.id)
    expect(flowcharts[0]!.id).not.toBe(source.flowchart.id)
    expect(flowcharts[0]!.sourceRequirementIds).toEqual([requirements[0]!.id])
    expect(versions[0]!.snapshot.flowcharts?.[0]?.id).toBe(flowcharts[0]!.id)

    await repository.rename(copy.id, '副本已修改', '2026-07-17T09:10:00.000Z')
    await database.requirement_item.update(requirements[0]!.id, { content: '副本独立内容' })
    expect((await repository.getById(original.id))!.name).toBe('原项目')
    expect((await database.requirement_item.get(source.requirement.id))!.content).toBe(
      source.requirement.content,
    )
  })

  it('permanently deletes only one trashed project graph and preserves settings', async () => {
    const { database, repository } = createTestContext()
    const target = await repository.create(projectInput(uuid(5), '待永久删除', baseTime))
    const survivor = await repository.create(projectInput(uuid(6), '保留项目', baseTime))
    await seedProjectGraph(database, target)
    await database.requirement_item.add(
      createRequirementItem({
        id: uuid(201),
        projectId: survivor.id,
        type: 'FEATURE',
        title: '保留功能',
        content: '不得被其他项目删除影响',
        status: 'CONFIRMED',
        sourceType: 'USER_EDIT',
        sourceId: null,
        now: baseTime,
      }),
    )
    await database.app_setting.add({
      key: 'uploadPrivacyNoticeAccepted',
      value: true,
      updatedAt: baseTime,
    })

    await expect(repository.permanentlyDelete(target.id)).rejects.toBeInstanceOf(ProjectStateError)
    await repository.moveToTrash(target.id, '2026-07-17T09:20:00.000Z')
    await repository.permanentlyDelete(target.id)

    await expect(repository.getById(target.id)).resolves.toBeUndefined()
    const targetCounts = await Promise.all([
      database.requirement_item.where('projectId').equals(target.id).count(),
      database.clarification_question.where('projectId').equals(target.id).count(),
      database.clarification_answer.where('projectId').equals(target.id).count(),
      database.requirement_conflict.where('projectId').equals(target.id).count(),
      database.requirement_version.where('projectId').equals(target.id).count(),
      database.requirement_change.where('projectId').equals(target.id).count(),
      database.prd_section.where('projectId').equals(target.id).count(),
      database.flowchart.where('projectId').equals(target.id).count(),
    ])
    expect(targetCounts).toEqual([0, 0, 0, 0, 0, 0, 0, 0])
    await expect(repository.getById(survivor.id)).resolves.toEqual(survivor)
    expect(await database.requirement_item.where('projectId').equals(survivor.id).count()).toBe(1)
    await expect(database.app_setting.get('uploadPrivacyNoticeAccepted')).resolves.toMatchObject({
      value: true,
    })
  })
})
