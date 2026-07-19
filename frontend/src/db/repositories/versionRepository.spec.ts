import 'fake-indexeddb/auto'

import Dexie from 'dexie'
import { afterEach, describe, expect, it } from 'vitest'

import { createProject, type Project } from '@/features/projects/types'
import {
  createRequirementItem,
  type ClarificationAnswer,
  type ClarificationQuestion,
  type RequirementConflict,
  type RequirementItem,
} from '@/features/requirements/types'
import { createAppDatabase, type AppDatabase } from '../appDatabase'
import { RequirementRepository, type RequirementSaveInput } from './requirementRepository'
import { VersionNotFoundError, VersionRepository } from './versionRepository'

const databaseNames = new Set<string>()
const baseTime = '2026-07-17T10:00:00.000Z'

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${value.toString(16).padStart(12, '0')}`
}

function laterTime(seconds = 1): string {
  return new Date(Date.parse(baseTime) + seconds * 1000).toISOString()
}

function createTestContext() {
  const databaseName = `version-repo-${crypto.randomUUID()}`
  const database = createAppDatabase(databaseName)
  databaseNames.add(databaseName)
  const requirementRepo = new RequirementRepository(database)
  const versionRepo = new VersionRepository(database)
  return { database, requirementRepo, versionRepo, databaseName }
}

async function seedProject(database: AppDatabase): Promise<Project> {
  const project = createProject({
    id: uuid(1),
    name: '版本测试项目',
    originalPrompt: '测试版本仓库',
    now: baseTime,
  })
  await database.project.add(project)
  return project
}

async function seedVersion(
  requirementRepo: RequirementRepository,
  projectId: string,
  requirements: RequirementItem[],
  summary: string,
  time: string,
) {
  const fieldChanges = requirements.map((r) => ({
    requirementId: r.id,
    changeType: 'CREATE' as const,
    field: 'content',
    oldValue: null,
    newValue: r.content,
  }))

  return requirementRepo.save(
    {
      projectId,
      requirements,
      changeType: 'CREATE',
      summary,
      fieldChanges,
    },
    time,
  )
}

afterEach(async () => {
  for (const name of databaseNames) {
    await Dexie.delete(name)
  }
  databaseNames.clear()
})

describe('VersionRepository', () => {
  // ---- List versions ----

  it('lists versions for a project sorted by newest first', async () => {
    const { database, requirementRepo, versionRepo } = createTestContext()
    const project = await seedProject(database)

    const req1 = createRequirementItem({
      id: uuid(10),
      projectId: project.id,
      type: 'FEATURE',
      title: '功能V1',
      content: '第一版功能',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })
    const req2 = createRequirementItem({
      id: uuid(11),
      projectId: project.id,
      type: 'FEATURE',
      title: '功能V2',
      content: '第二版功能',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: laterTime(10),
    })

    await seedVersion(requirementRepo, project.id, [req1], '版本1', laterTime(5))
    await seedVersion(requirementRepo, project.id, [req1, req2], '版本2', laterTime(20))

    const versions = await versionRepo.listByProject(project.id)
    expect(versions).toHaveLength(2)
    expect(versions[0]!.summary).toBe('版本2')
    expect(versions[1]!.summary).toBe('版本1')
  })

  it('returns empty list for project with no versions', async () => {
    const { database, versionRepo } = createTestContext()
    const project = await seedProject(database)

    await expect(versionRepo.listByProject(project.id)).resolves.toEqual([])
  })

  // ---- Get version by ID ----

  it('returns a version by id', async () => {
    const { database, requirementRepo, versionRepo } = createTestContext()
    const project = await seedProject(database)

    const req = createRequirementItem({
      id: uuid(15),
      projectId: project.id,
      type: 'ROLE',
      title: '普通用户',
      content: '平台注册用户',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })

    const { version } = await seedVersion(requirementRepo, project.id, [req], '创建用户角色', laterTime(5))

    const found = await versionRepo.getById(version.id)
    expect(found).toBeDefined()
    expect(found!.summary).toBe('创建用户角色')
    expect(found!.snapshot.requirements).toHaveLength(1)
  })

  it('returns undefined for a non-existent version', async () => {
    const { versionRepo } = createTestContext()
    await expect(versionRepo.getById(uuid(999))).resolves.toBeUndefined()
  })

  // ---- Get version with changes ----

  it('returns a version with its associated changes', async () => {
    const { database, requirementRepo, versionRepo } = createTestContext()
    const project = await seedProject(database)

    const req = createRequirementItem({
      id: uuid(20),
      projectId: project.id,
      type: 'BUSINESS_RULE',
      title: '订单取消规则',
      content: '下单后15分钟内可免费取消',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })

    const { version } = await seedVersion(
      requirementRepo,
      project.id,
      [req],
      '订单取消规则',
      laterTime(10),
    )

    const result = await versionRepo.getWithChanges(version.id)
    expect(result).toBeDefined()
    expect(result!.version.id).toBe(version.id)
    expect(result!.changes).toHaveLength(1)
    expect(result!.changes[0]!.field).toBe('content')
    expect(result!.changes[0]!.newValue).toBe('下单后15分钟内可免费取消')
  })

  it('returns undefined from getWithChanges for a missing version', async () => {
    const { versionRepo } = createTestContext()
    await expect(versionRepo.getWithChanges(uuid(999))).resolves.toBeUndefined()
  })

  // ---- Restore ----

  it('restores the project to a target version, saving the current state first', async () => {
    const { database, requirementRepo, versionRepo } = createTestContext()
    const project = await seedProject(database)

    // Version 1: initial state
    const reqV1 = createRequirementItem({
      id: uuid(30),
      projectId: project.id,
      type: 'FEATURE',
      title: '初始功能',
      content: '第一版本的内容',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })

    const { version: v1 } = await seedVersion(
      requirementRepo,
      project.id,
      [reqV1],
      '初始版本',
      laterTime(5),
    )

    // Version 2: modified state
    const reqV2 = createRequirementItem({
      id: uuid(31),
      projectId: project.id,
      type: 'FEATURE',
      title: '修改后的功能',
      content: '第二版本的内容（已修改）',
      status: 'CONFIRMED',
      sourceType: 'USER_EDIT',
      sourceId: null,
      now: laterTime(10),
    })

    await seedVersion(requirementRepo, project.id, [reqV2], '修改版本', laterTime(15))

    // Now restore to v1
    const restoreResult = await versionRepo.restore(project.id, v1.id, laterTime(20))

    // Verify the restore version record
    expect(restoreResult.version.changeType).toBe('RESTORE')
    expect(restoreResult.version.summary).toContain('初始版本')

    // Verify live data matches v1
    const currentRequirements = await requirementRepo.listByProject(project.id)
    expect(currentRequirements).toHaveLength(1)
    expect(currentRequirements[0]!.id).toBe(reqV1.id)
    expect(currentRequirements[0]!.title).toBe('初始功能')
    expect(currentRequirements[0]!.content).toBe('第一版本的内容')

    // Verify versions: initial + modified + pre-restore + restore = 4
    const allVersions = await versionRepo.listByProject(project.id)
    expect(allVersions).toHaveLength(4)
    const summaries = allVersions.map((v) => v.summary)
    expect(summaries).toContain('初始版本')
    expect(summaries).toContain('修改版本')
    // The pre-restore auto-save should be present
    expect(summaries.some((s) => s.includes('恢复到版本前的自动保存'))).toBe(true)
    // The restore record should be present
    expect(summaries.some((s) => s.includes('恢复到版本：'))).toBe(true)
  })

  it('preserves restored state after database close and reopen', async () => {
    const { database, requirementRepo, versionRepo, databaseName } = createTestContext()
    const project = await seedProject(database)

    // Create v1
    const reqV1 = createRequirementItem({
      id: uuid(35),
      projectId: project.id,
      type: 'FEATURE',
      title: '功能A',
      content: '功能A的原始内容',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })

    const { version: v1 } = await seedVersion(
      requirementRepo,
      project.id,
      [reqV1],
      'A版本',
      laterTime(5),
    )

    // Create v2 (which we'll restore away from)
    const reqV2 = createRequirementItem({
      id: uuid(36),
      projectId: project.id,
      type: 'FEATURE',
      title: '功能B',
      content: '功能B的内容',
      status: 'CONFIRMED',
      sourceType: 'USER_EDIT',
      sourceId: null,
      now: laterTime(10),
    })
    await seedVersion(requirementRepo, project.id, [reqV2], 'B版本', laterTime(15))

    // Restore to v1
    await versionRepo.restore(project.id, v1.id, laterTime(20))

    // Close and reopen
    database.close()
    const reopened = createAppDatabase(databaseName)
    const reopenedRequirementRepo = new RequirementRepository(reopened)
    const reopenedVersionRepo = new VersionRepository(reopened)

    // Verify restored state persists
    const requirements = await reopenedRequirementRepo.listByProject(project.id)
    expect(requirements).toHaveLength(1)
    expect(requirements[0]!.title).toBe('功能A')

    // Verify all versions are still present
    const versions = await reopenedVersionRepo.listByProject(project.id)
    expect(versions.length).toBeGreaterThanOrEqual(4)

    reopened.close()
  })

  it('does not lose current state when restore target is invalid', async () => {
    const { database, requirementRepo, versionRepo } = createTestContext()
    const project = await seedProject(database)

    const req = createRequirementItem({
      id: uuid(40),
      projectId: project.id,
      type: 'FEATURE',
      title: '当前功能',
      content: '不容丢失的当前状态',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })

    await seedVersion(requirementRepo, project.id, [req], '当前版本', laterTime(5))

    // Attempt restore to a non-existent version
    await expect(
      versionRepo.restore(project.id, uuid(999), laterTime(10)),
    ).rejects.toBeInstanceOf(VersionNotFoundError)

    // Current state must be intact
    const current = await requirementRepo.listByProject(project.id)
    expect(current).toHaveLength(1)
    expect(current[0]!.title).toBe('当前功能')

    // No extra versions from the failed attempt
    const versions = await versionRepo.listByProject(project.id)
    expect(versions).toHaveLength(1)
  })

  it('throws when restoring a version that belongs to a different project', async () => {
    const { database, requirementRepo, versionRepo } = createTestContext()
    const projectA = await seedProject(database)

    // Create a second project
    const projectB = createProject({
      id: uuid(45),
      name: '项目B',
      originalPrompt: '另一个项目',
      now: baseTime,
    })
    await database.project.add(projectB)

    const reqA = createRequirementItem({
      id: uuid(46),
      projectId: projectA.id,
      type: 'FEATURE',
      title: '项目A功能',
      content: '项目A的内容',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })
    const { version: versionA } = await seedVersion(
      requirementRepo,
      projectA.id,
      [reqA],
      '项目A版本',
      laterTime(5),
    )

    // Try to restore projectB to versionA (which belongs to projectA)
    await expect(
      versionRepo.restore(projectB.id, versionA.id, laterTime(10)),
    ).rejects.toThrow(/belongs to project/)
  })

  it('restores all snapshot data including questions, answers, and conflicts', async () => {
    const { database, requirementRepo, versionRepo } = createTestContext()
    const project = await seedProject(database)

    // Seed questions, answers, conflicts before creating version
    const question: ClarificationQuestion = {
      id: uuid(50),
      projectId: project.id,
      batchId: uuid(51),
      text: '用户如何登录？',
      reason: '确认认证方式',
      dimension: 'SECURITY',
      targetField: 'auth',
      semanticKey: 'auth-method',
      roundNo: 1,
      coverageCategories: [] as string[],
      inputType: 'SINGLE_SELECT',
      options: [
        { id: uuid(52), label: '手机号+验证码', impact: '需要短信服务', recommended: true },
      ],
      priority: 4,
      status: 'ANSWERED',
      createdAt: baseTime,
      updatedAt: baseTime,
    }

    const answer: ClarificationAnswer = {
      id: uuid(53),
      projectId: project.id,
      questionId: question.id,
      selectedOptionIds: [uuid(52)],
      customAnswer: null,
      note: null,
      skipped: false,
      createdAt: baseTime,
      updatedAt: baseTime,
    }

    const conflict: RequirementConflict = {
      id: uuid(54),
      projectId: project.id,
      leftRequirementId: null,
      rightRequirementId: null,
      leftContent: '仅支持手机号注册',
      rightContent: '支持邮箱注册',
      impact: '注册方式冲突',
      core: true,
      status: 'OPEN',
      resolution: null,
      createdAt: baseTime,
      updatedAt: baseTime,
      resolvedAt: null,
    }

    await database.clarification_question.add(question)
    await database.clarification_answer.add(answer)
    await database.requirement_conflict.add(conflict)

    // Save version with this full state
    const req = createRequirementItem({
      id: uuid(55),
      projectId: project.id,
      type: 'FEATURE',
      title: '登录功能',
      content: '用户通过手机号验证码登录',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })

    const { version: v1 } = await seedVersion(
      requirementRepo,
      project.id,
      [req],
      '包含完整上下文的版本',
      laterTime(10),
    )

    // Clear all related data and save a different state
    await database.clarification_question.where('projectId').equals(project.id).delete()
    await database.clarification_answer.where('projectId').equals(project.id).delete()
    await database.requirement_conflict.where('projectId').equals(project.id).delete()

    const req2 = createRequirementItem({
      id: uuid(56),
      projectId: project.id,
      type: 'FEATURE',
      title: '不同功能',
      content: '完全不同的内容',
      status: 'CONFIRMED',
      sourceType: 'USER_EDIT',
      sourceId: null,
      now: laterTime(15),
    })

    await seedVersion(requirementRepo, project.id, [req2], '简化版本', laterTime(20))

    // Restore to v1 which had all the extra data
    await versionRepo.restore(project.id, v1.id, laterTime(25))

    // Verify all data is restored
    const questions = await database.clarification_question.where('projectId').equals(project.id).toArray()
    expect(questions).toHaveLength(1)
    expect(questions[0]!.text).toBe('用户如何登录？')

    const answers = await database.clarification_answer.where('projectId').equals(project.id).toArray()
    expect(answers).toHaveLength(1)
    expect(answers[0]!.customAnswer).toBeNull()

    const conflicts = await database.requirement_conflict.where('projectId').equals(project.id).toArray()
    expect(conflicts).toHaveLength(1)
    expect(conflicts[0]!.leftContent).toBe('仅支持手机号注册')

    const requirements = await requirementRepo.listByProject(project.id)
    expect(requirements).toHaveLength(1)
    expect(requirements[0]!.title).toBe('登录功能')
  })

  it('restores flowcharts captured after the schema version 2 milestone', async () => {
    const { database, requirementRepo, versionRepo } = createTestContext()
    const project = await seedProject(database)
    const requirement = createRequirementItem({
      id: uuid(70), projectId: project.id, type: 'USER_STORY', title: '提交申请',
      content: '用户提交申请后进入审核', status: 'CONFIRMED', sourceType: 'USER_ANSWER',
      sourceId: null, now: baseTime,
    })
    await database.flowchart.add({
      id: uuid(71), projectId: project.id, key: 'main', type: 'MAIN', title: '主流程 V1',
      mermaid: 'flowchart TD\nA-->B', status: 'VALID', sourceRequirementIds: [requirement.id],
      createdAt: baseTime, updatedAt: baseTime,
    })
    const { version: v1 } = await seedVersion(requirementRepo, project.id, [requirement], '流程图 V1', laterTime(5))

    await database.flowchart.where('projectId').equals(project.id).delete()
    await database.flowchart.add({
      id: uuid(72), projectId: project.id, key: 'main', type: 'MAIN', title: '主流程 V2',
      mermaid: 'flowchart TD\nA-->C', status: 'VALID', sourceRequirementIds: [requirement.id],
      createdAt: laterTime(10), updatedAt: laterTime(10),
    })
    await seedVersion(requirementRepo, project.id, [requirement], '流程图 V2', laterTime(15))

    await versionRepo.restore(project.id, v1.id, laterTime(20))
    const restored = await database.flowchart.where('projectId').equals(project.id).toArray()
    expect(restored).toHaveLength(1)
    expect(restored[0]).toMatchObject({ title: '主流程 V1', mermaid: 'flowchart TD\nA-->B' })
  })
})
