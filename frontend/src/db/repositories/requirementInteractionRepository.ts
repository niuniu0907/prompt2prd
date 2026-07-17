import { assertUuid, assertUtcIsoDateTime } from '@/features/projects/types'
import type { RequirementChangeType, RequirementItem, RequirementStateSnapshot } from '@/features/requirements/types'
import { appDatabase, type AppDatabase } from '../appDatabase'

export class RequirementInteractionRepository {
  constructor(private readonly database: AppDatabase = appDatabase, private readonly createId = () => crypto.randomUUID()) {}

  async hasBlockingCoreConflict(projectId: string): Promise<boolean> {
    assertUuid(projectId, 'project id')
    return (await this.database.requirement_conflict.where('projectId').equals(projectId).toArray())
      .some(conflict => conflict.status === 'OPEN')
  }

  async resolveConflict(conflictId: string, resolution: string, now = new Date().toISOString()) {
    assertUuid(conflictId, 'conflict id'); assertUtcIsoDateTime(now, 'resolution timestamp')
    if (!resolution.trim()) throw new TypeError('conflict resolution must not be blank')
    return this.database.transaction('rw', this.tables(), async () => {
      const conflict = await this.database.requirement_conflict.get(conflictId)
      if (!conflict) throw new Error(`Conflict ${conflictId} not found`)
      const updated = { ...conflict, status: 'RESOLVED' as const, resolution: resolution.trim(), resolvedAt: now, updatedAt: now }
      await this.database.requirement_conflict.put(updated)
      await this.recordVersion(conflict.projectId, '解决需求冲突', 'UPDATE', conflict.leftRequirementId, 'conflict', conflict, updated, now)
      return updated
    })
  }

  async decideAssumption(requirementId: string, accepted: boolean, now = new Date().toISOString()): Promise<RequirementItem> {
    assertUuid(requirementId, 'requirement id'); assertUtcIsoDateTime(now, 'assumption timestamp')
    return this.database.transaction('rw', this.tables(), async () => {
      const current = await this.requireRequirement(requirementId)
      if (current.type !== 'ASSUMPTION') throw new TypeError('Only assumptions can be accepted or rejected')
      if (current.locked) throw new Error('Unlock the requirement before changing the assumption')
      const updated: RequirementItem = {
        ...current,
        status: accepted ? 'CONFIRMED' : 'PENDING',
        sourceType: accepted ? 'USER_ANSWER' : current.sourceType,
        metadata: { ...current.metadata, decision: accepted ? 'ACCEPTED' : 'REJECTED' },
        updatedAt: now,
      }
      await this.database.requirement_item.put(updated)
      await this.recordVersion(current.projectId, accepted ? '确认 AI 假设' : '拒绝 AI 假设', 'UPDATE', current.id, 'assumptionDecision', current, updated, now)
      return updated
    })
  }

  async setLocked(requirementId: string, locked: boolean, now = new Date().toISOString()): Promise<RequirementItem> {
    assertUuid(requirementId, 'requirement id'); assertUtcIsoDateTime(now, 'lock timestamp')
    return this.database.transaction('rw', this.tables(), async () => {
      const current = await this.requireRequirement(requirementId)
      if (locked && current.status !== 'CONFIRMED') throw new TypeError('Only confirmed requirements can be locked')
      if (current.locked === locked) return current
      const updated = { ...current, locked, updatedAt: now }
      await this.database.requirement_item.put(updated)
      await this.recordVersion(current.projectId, locked ? '锁定需求' : '解锁需求', locked ? 'LOCK' : 'UNLOCK', current.id, 'locked', current.locked, locked, now)
      return updated
    })
  }

  async assertEditable(requirementId: string): Promise<void> {
    const requirement = await this.requireRequirement(requirementId)
    if (requirement.locked) throw new Error('Unlock the requirement before editing')
  }

  private tables() {
    return [this.database.project, this.database.requirement_item, this.database.clarification_question, this.database.clarification_answer, this.database.requirement_conflict, this.database.requirement_version, this.database.requirement_change, this.database.flowchart]
  }

  private async requireRequirement(id: string) {
    const requirement = await this.database.requirement_item.get(id)
    if (!requirement) throw new Error(`Requirement ${id} not found`)
    return requirement
  }

  private async recordVersion(projectId: string, summary: string, changeType: RequirementChangeType, requirementId: string | null, field: string, oldValue: unknown, newValue: unknown, now: string) {
    const project = await this.database.project.get(projectId)
    if (!project) throw new Error(`Project ${projectId} not found`)
    const [requirements, questions, answers, conflicts, flowcharts] = await Promise.all([
      this.database.requirement_item.where('projectId').equals(projectId).toArray(),
      this.database.clarification_question.where('projectId').equals(projectId).toArray(),
      this.database.clarification_answer.where('projectId').equals(projectId).toArray(),
      this.database.requirement_conflict.where('projectId').equals(projectId).toArray(),
      this.database.flowchart.where('projectId').equals(projectId).toArray(),
    ])
    const snapshot: RequirementStateSnapshot = { project: structuredClone(project), requirements: structuredClone(requirements), questions: structuredClone(questions), answers: structuredClone(answers), conflicts: structuredClone(conflicts), flowcharts: structuredClone(flowcharts) }
    const versionId = this.createId(); const changeId = this.createId()
    assertUuid(versionId, 'version id'); assertUuid(changeId, 'change id')
    await this.database.requirement_version.add({ id: versionId, projectId, changeType, summary, snapshot, createdAt: now })
    await this.database.requirement_change.add({ id: changeId, projectId, versionId, requirementId, changeType, field, oldValue, newValue, createdAt: now })
  }
}

export const requirementInteractionRepository = new RequirementInteractionRepository()
