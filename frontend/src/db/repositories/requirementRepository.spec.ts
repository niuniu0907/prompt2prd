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
import {
  RequirementNotFoundError,
  RequirementRepository,
  type RequirementFieldChange,
  type RequirementSaveInput,
} from './requirementRepository'

const databaseNames = new Set<string>()
const baseTime = '2026-07-17T10:00:00.000Z'

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${value.toString(16).padStart(12, '0')}`
}

function laterTime(seconds = 1): string {
  return new Date(Date.parse(baseTime) + seconds * 1000).toISOString()
}

function createTestContext() {
  const databaseName = `requirement-repo-${crypto.randomUUID()}`
  const database = createAppDatabase(databaseName)
  databaseNames.add(databaseName)
  const repository = new RequirementRepository(database)
  return { database, repository, databaseName }
}

async function seedProject(database: AppDatabase): Promise<Project> {
  const project = createProject({
    id: uuid(1),
    name: '测试项目',
    originalPrompt: '测试需求仓库的项目',
    now: baseTime,
  })
  await database.project.add(project)
  return project
}

async function seedRequirement(
  repository: RequirementRepository,
  projectId: string,
  idNum: number,
  overrides: Partial<RequirementItem> = {},
): Promise<RequirementItem> {
  const input: RequirementSaveInput = {
    projectId,
    requirements: [
      createRequirementItem({
        id: uuid(idNum),
        projectId,
        type: 'FEATURE',
        title: `功能 ${idNum}`,
        content: `功能 ${idNum} 的详细内容`,
        status: 'CONFIRMED',
        sourceType: 'USER_EDIT',
        sourceId: null,
        now: baseTime,
        ...overrides,
      }),
    ],
    changeType: 'CREATE',
    summary: `创建功能 ${idNum}`,
    fieldChanges: [
      {
        requirementId: uuid(idNum),
        changeType: 'CREATE',
        field: 'content',
        oldValue: null,
        newValue: `功能 ${idNum} 的详细内容`,
      },
    ],
  }
  const result = await repository.save(input, baseTime)
  return result.version.snapshot.requirements[0]!
}

/**
 * Seeds multiple requirements in a single save operation.
 * Unlike calling seedRequirement multiple times (which replaces all each time),
 * this saves all requirements together in one transaction.
 */
async function seedRequirements(
  repository: RequirementRepository,
  projectId: string,
  ids: number[],
  overrides: Partial<RequirementItem> = {},
): Promise<RequirementItem[]> {
  const requirements = ids.map((idNum) =>
    createRequirementItem({
      id: uuid(idNum),
      projectId,
      type: 'FEATURE',
      title: `功能 ${idNum}`,
      content: `功能 ${idNum} 的详细内容`,
      status: 'CONFIRMED',
      sourceType: 'USER_EDIT',
      sourceId: null,
      now: baseTime,
      ...overrides,
    }),
  )

  const fieldChanges = requirements.map((r) => ({
    requirementId: r.id,
    changeType: 'CREATE' as const,
    field: 'content',
    oldValue: null,
    newValue: r.content,
  }))

  const result = await repository.save(
    {
      projectId,
      requirements,
      changeType: 'CREATE',
      summary: `批量创建功能 ${ids.join(', ')}`,
      fieldChanges,
    },
    baseTime,
  )
  return result.version.snapshot.requirements
}

afterEach(async () => {
  for (const name of databaseNames) {
    await Dexie.delete(name)
  }
  databaseNames.clear()
})

describe('RequirementRepository', () => {
  // ---- Basic CRUD ----

  it('returns undefined for a missing requirement', async () => {
    const { repository } = createTestContext()
    await expect(repository.getById(uuid(999))).resolves.toBeUndefined()
  })

  it('returns a requirement by id after save', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)
    const saved = await seedRequirement(repository, project.id, 10)

    const found = await repository.getById(saved.id)
    expect(found).toEqual(saved)
  })

  it('lists requirements filtered by project', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)
    await seedRequirements(repository, project.id, [10, 11, 12])

    const list = await repository.listByProject(project.id)
    expect(list).toHaveLength(3)
    expect(list.map((r) => r.title).sort()).toEqual(['功能 10', '功能 11', '功能 12'])
  })

  it('returns an empty list for a project with no requirements', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)

    await expect(repository.listByProject(project.id)).resolves.toEqual([])
  })

  // ---- Update without versioning ----

  it('updates a requirement without creating a version record', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)
    const saved = await seedRequirement(repository, project.id, 20)

    const beforeVersionCount = await database.requirement_version.count()

    const updated = await repository.updateRequirement(
      saved.id,
      { content: '草稿修改中', title: '草稿标题' },
      laterTime(1),
    )

    expect(updated.content).toBe('草稿修改中')
    expect(updated.title).toBe('草稿标题')
    expect(updated.updatedAt).toBe(laterTime(1))
    expect(updated.id).toBe(saved.id)
    expect(updated.createdAt).toBe(saved.createdAt)

    // Verify no version was created
    const afterVersionCount = await database.requirement_version.count()
    expect(afterVersionCount).toBe(beforeVersionCount)

    // Verify the update persisted
    const refetched = await repository.getById(saved.id)
    expect(refetched?.content).toBe('草稿修改中')
  })

  it('throws RequirementNotFoundError when updating a missing requirement', async () => {
    const { repository } = createTestContext()
    await expect(
      repository.updateRequirement(uuid(999), { content: '不存在' }),
    ).rejects.toBeInstanceOf(RequirementNotFoundError)
  })

  // ---- Continuous updates do not create versions ----

  it('does not create any version after multiple updateRequirement calls', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)
    const saved = await seedRequirement(repository, project.id, 30)

    const versionCountBefore = await database.requirement_version.count()

    // Simulate multiple keystroke-level updates
    await repository.updateRequirement(saved.id, { content: '第1次编辑' }, laterTime(1))
    await repository.updateRequirement(saved.id, { content: '第2次编辑' }, laterTime(2))
    await repository.updateRequirement(saved.id, { content: '第3次编辑' }, laterTime(3))
    await repository.updateRequirement(saved.id, { content: '第4次编辑' }, laterTime(4))
    await repository.updateRequirement(saved.id, { content: '最终版本' }, laterTime(5))

    const versionCountAfter = await database.requirement_version.count()
    expect(versionCountAfter).toBe(versionCountBefore)

    const refetched = await repository.getById(saved.id)
    expect(refetched?.content).toBe('最终版本')
    expect(refetched?.updatedAt).toBe(laterTime(5))
  })

  // ---- Transactional save with versioning ----

  it('saves requirements, creates a version with snapshot and field-level changes', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)

    const req1 = createRequirementItem({
      id: uuid(40),
      projectId: project.id,
      type: 'ROLE',
      title: '管理员',
      content: '系统管理员拥有全部权限',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })
    const req2 = createRequirementItem({
      id: uuid(41),
      projectId: project.id,
      type: 'BUSINESS_RULE',
      title: '退款规则',
      content: '24小时内可退款',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })

    const fieldChanges: RequirementFieldChange[] = [
      {
        requirementId: req1.id,
        changeType: 'CREATE',
        field: 'content',
        oldValue: null,
        newValue: req1.content,
      },
      {
        requirementId: req2.id,
        changeType: 'CREATE',
        field: 'content',
        oldValue: null,
        newValue: req2.content,
      },
    ]

    const result = await repository.save(
      {
        projectId: project.id,
        requirements: [req1, req2],
        changeType: 'CREATE',
        summary: '初始需求创建',
        fieldChanges,
      },
      laterTime(10),
    )

    // Verify version
    expect(result.version).toBeDefined()
    expect(result.version.projectId).toBe(project.id)
    expect(result.version.changeType).toBe('CREATE')
    expect(result.version.summary).toBe('初始需求创建')
    expect(result.version.createdAt).toBe(laterTime(10))

    // Verify snapshot
    const snapshot = result.version.snapshot
    expect(snapshot.project.id).toBe(project.id)
    expect(snapshot.requirements).toHaveLength(2)
    expect(snapshot.requirements.map((r) => r.title).sort()).toEqual(['管理员', '退款规则'])

    // Verify changes
    expect(result.changes).toHaveLength(2)
    expect(result.changes[0]!.versionId).toBe(result.version.id)
    expect(result.changes[1]!.versionId).toBe(result.version.id)
    expect(result.changes.map((c) => c.field).sort()).toEqual(['content', 'content'])

    // Verify requirements persisted
    const list = await repository.listByProject(project.id)
    expect(list).toHaveLength(2)
  })

  it('replaces all requirements on subsequent saves', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)

    // First save: 3 requirements in one transaction
    await seedRequirements(repository, project.id, [50, 51, 52])

    expect(await repository.listByProject(project.id)).toHaveLength(3)

    // Second save: replace with only 1 completely different requirement
    const newReq = createRequirementItem({
      id: uuid(53),
      projectId: project.id,
      type: 'EXCEPTION_SCENARIO',
      title: '网络超时',
      content: '请求超过30秒应提示用户重试',
      status: 'CONFIRMED',
      sourceType: 'USER_EDIT',
      sourceId: null,
      now: laterTime(20),
    })

    const result = await repository.save(
      {
        projectId: project.id,
        requirements: [newReq],
        changeType: 'UPDATE',
        summary: '替换为单个需求',
        fieldChanges: [
          {
            requirementId: newReq.id,
            changeType: 'CREATE',
            field: 'content',
            oldValue: null,
            newValue: newReq.content,
          },
        ],
      },
      laterTime(20),
    )

    const list = await repository.listByProject(project.id)
    expect(list).toHaveLength(1)
    expect(list[0]!.id).toBe(newReq.id)
    expect(list[0]!.title).toBe('网络超时')

    // Snapshot should contain only the new requirement
    expect(result.version.snapshot.requirements).toHaveLength(1)
    expect(result.version.snapshot.requirements[0]!.id).toBe(newReq.id)
  })

  it('captures the full project state including questions, answers, and conflicts in the snapshot', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)

    // Add questions, answers, and conflicts to the database
    const question: ClarificationQuestion = {
      id: uuid(60),
      projectId: project.id,
      batchId: uuid(61),
      text: '支持的支付方式？',
      reason: '确认支付集成范围',
      dimension: 'PAYMENT',
      targetField: 'payment',
      semanticKey: 'payment-methods',
      roundNo: 1,
      coverageCategories: [] as string[],
      inputType: 'MULTI_SELECT',
      options: [
        { id: uuid(62), label: '微信支付', impact: '需要微信商户平台', recommended: true },
        { id: uuid(63), label: '支付宝', impact: '需要支付宝商户平台', recommended: false },
      ],
      priority: 5,
      status: 'PENDING',
      createdAt: baseTime,
      updatedAt: baseTime,
    }

    const answer: ClarificationAnswer = {
      id: uuid(64),
      projectId: project.id,
      questionId: question.id,
      selectedOptionIds: [uuid(62)],
      customAnswer: '暂时只支持微信支付',
      note: null,
      skipped: false,
      createdAt: baseTime,
      updatedAt: baseTime,
    }

    const conflict: RequirementConflict = {
      id: uuid(65),
      projectId: project.id,
      leftRequirementId: null,
      rightRequirementId: null,
      leftContent: '免费配送',
      rightContent: '满99包邮',
      impact: '运费规则不一致',
      core: false,
      status: 'OPEN',
      resolution: null,
      createdAt: baseTime,
      updatedAt: baseTime,
      resolvedAt: null,
    }

    await database.clarification_question.add(question)
    await database.clarification_answer.add(answer)
    await database.requirement_conflict.add(conflict)

    const req = createRequirementItem({
      id: uuid(66),
      projectId: project.id,
      type: 'FEATURE',
      title: '支付功能',
      content: '支持在线支付',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      now: baseTime,
    })

    const result = await repository.save(
      {
        projectId: project.id,
        requirements: [req],
        changeType: 'CREATE',
        summary: '包含完整状态的快照',
        fieldChanges: [],
      },
      laterTime(30),
    )

    const snapshot = result.version.snapshot
    expect(snapshot.project.id).toBe(project.id)
    expect(snapshot.questions).toHaveLength(1)
    expect(snapshot.questions[0]!.text).toBe('支持的支付方式？')
    expect(snapshot.answers).toHaveLength(1)
    expect(snapshot.answers[0]!.customAnswer).toBe('暂时只支持微信支付')
    expect(snapshot.conflicts).toHaveLength(1)
    expect(snapshot.conflicts[0]!.leftContent).toBe('免费配送')
  })

  // ---- Transaction rollback ----

  it('leaves no partial data when a write inside the save transaction fails', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)

    // First, save one requirement normally
    await seedRequirement(repository, project.id, 70)

    const beforeVersions = await database.requirement_version.count()
    const beforeChanges = await database.requirement_change.count()
    const beforeItems = await database.requirement_item.count()

    // Attempt a save that will fail inside the transaction.
    // We monkey-patch requirement_version.add to throw after requirements are written,
    // verifying that the transaction rolls back completely.
    const originalAdd = database.requirement_version.add.bind(database.requirement_version)
    let addCalled = false
    database.requirement_version.add = function (...args: Parameters<typeof originalAdd>) {
      addCalled = true
      throw new Error('Simulated IndexedDB write failure')
    } as typeof originalAdd

    const newReq = createRequirementItem({
      id: uuid(71),
      projectId: project.id,
      type: 'FEATURE',
      title: '应该被回滚的功能',
      content: '这个需求不应该被持久化',
      status: 'CONFIRMED',
      sourceType: 'USER_EDIT',
      sourceId: null,
      now: laterTime(5),
    })

    await expect(
      repository.save(
        {
          projectId: project.id,
          requirements: [newReq],
          changeType: 'CREATE',
          summary: '会失败的事务',
          fieldChanges: [
            {
              requirementId: newReq.id,
              changeType: 'CREATE',
              field: 'content',
              oldValue: null,
              newValue: newReq.content,
            },
          ],
        },
        laterTime(10),
      ),
    ).rejects.toThrow('Simulated IndexedDB write failure')

    // Restore the original method
    database.requirement_version.add = originalAdd

    // Verify the transaction was attempted
    expect(addCalled).toBe(true)

    // Verify NO partial data persisted (transaction rolled back)
    const afterVersions = await database.requirement_version.count()
    const afterChanges = await database.requirement_change.count()
    const afterItems = await database.requirement_item.count()

    expect(afterVersions).toBe(beforeVersions)
    expect(afterChanges).toBe(beforeChanges)
    expect(afterItems).toBe(beforeItems)

    // The original requirement should still be there
    const list = await repository.listByProject(project.id)
    expect(list).toHaveLength(1)
    expect(list[0]!.title).toBe('功能 70')
  })

  // ---- Recoverability after close/reopen ----

  it('preserves the full snapshot and version diffs after database close and reopen', async () => {
    const { database, repository, databaseName } = createTestContext()
    const project = await seedProject(database)

    const req = createRequirementItem({
      id: uuid(80),
      projectId: project.id,
      type: 'BUSINESS_RULE',
      title: '支付超时规则',
      content: '支付超时30分钟后自动取消订单',
      status: 'CONFIRMED',
      sourceType: 'USER_ANSWER',
      sourceId: null,
      locked: true,
      now: baseTime,
    })

    const fieldChanges: RequirementFieldChange[] = [
      {
        requirementId: req.id,
        changeType: 'CREATE',
        field: 'content',
        oldValue: null,
        newValue: req.content,
      },
      {
        requirementId: req.id,
        changeType: 'LOCK',
        field: 'locked',
        oldValue: false,
        newValue: true,
      },
    ]

    const result = await repository.save(
      {
        projectId: project.id,
        requirements: [req],
        changeType: 'CREATE',
        summary: '创建支付超时规则并锁定',
        fieldChanges,
      },
      laterTime(15),
    )

    const savedVersionId = result.version.id

    // Close and reopen
    database.close()
    const reopened = createAppDatabase(databaseName)
    const reopenedRepo = new RequirementRepository(reopened)

    // Verify snapshot is recoverable
    const version = await reopened.requirement_version.get(savedVersionId)
    expect(version).toBeDefined()
    expect(version!.summary).toBe('创建支付超时规则并锁定')
    expect(version!.snapshot.requirements).toHaveLength(1)
    expect(version!.snapshot.requirements[0]!.locked).toBe(true)
    expect(version!.snapshot.project.id).toBe(project.id)

    // Verify version diffs are readable
    const changes = await reopened.requirement_change
      .where('versionId')
      .equals(savedVersionId)
      .toArray()
    expect(changes).toHaveLength(2)
    expect(changes.map((c) => c.field).sort()).toEqual(['content', 'locked'])

    // Verify requirements are still accessible
    const list = await reopenedRepo.listByProject(project.id)
    expect(list).toHaveLength(1)
    expect(list[0]!.content).toBe('支付超时30分钟后自动取消订单')

    reopened.close()
  })

  // ---- Input validation ----

  it('throws when saving with a blank version summary', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)

    await expect(
      repository.save({
        projectId: project.id,
        requirements: [],
        changeType: 'UPDATE',
        summary: '   ',
        fieldChanges: [],
      }),
    ).rejects.toThrow('version summary must not be blank')
  })

  it('throws when a requirement belongs to a different project', async () => {
    const { database, repository } = createTestContext()
    const project = await seedProject(database)

    const foreignReq = createRequirementItem({
      id: uuid(90),
      projectId: uuid(91), // Different project
      type: 'FEATURE',
      title: '不属于此项目',
      content: '这个需求不属于当前项目',
      status: 'CONFIRMED',
      sourceType: 'USER_EDIT',
      sourceId: null,
      now: baseTime,
    })

    await expect(
      repository.save({
        projectId: project.id,
        requirements: [foreignReq],
        changeType: 'CREATE',
        summary: '应失败',
        fieldChanges: [],
      }),
    ).rejects.toThrow(/belongs to project/)
  })

  it('throws when saving for a non-existent project', async () => {
    const { repository } = createTestContext()
    const req = createRequirementItem({
      id: uuid(92),
      projectId: uuid(93),
      type: 'FEATURE',
      title: '无主需求',
      content: '没有对应项目',
      status: 'CONFIRMED',
      sourceType: 'USER_EDIT',
      sourceId: null,
      now: baseTime,
    })

    await expect(
      repository.save({
        projectId: uuid(93),
        requirements: [req],
        changeType: 'CREATE',
        summary: '应失败',
        fieldChanges: [],
      }),
    ).rejects.toThrow(/not found/)
  })
})
