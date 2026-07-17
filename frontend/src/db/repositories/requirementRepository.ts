import { assertUuid, assertUtcIsoDateTime } from '@/features/projects/types'
import type { Project } from '@/features/projects/types'
import type {
  ClarificationAnswer,
  ClarificationQuestion,
  RequirementChange,
  RequirementChangeType,
  RequirementConflict,
  RequirementItem,
  RequirementStateSnapshot,
  RequirementVersion,
} from '@/features/requirements/types'
import type { AppDatabase } from '../appDatabase'
import { appDatabase } from '../appDatabase'
import { calculateCompleteness } from '@/features/requirements/completeness'

export interface RequirementFieldChange {
  requirementId: string | null
  changeType: RequirementChangeType
  field: string
  oldValue: unknown
  newValue: unknown
}

export interface RequirementSaveInput {
  projectId: string
  requirements: RequirementItem[]
  changeType: RequirementChangeType
  summary: string
  fieldChanges: RequirementFieldChange[]
}

export interface RequirementSaveResult {
  version: RequirementVersion
  changes: RequirementChange[]
}

export interface ManualRequirementEdit {
  title: string
  content: string
  affectedArtifacts: string[]
}

export class RequirementRepository {
  constructor(
    private readonly database: AppDatabase = appDatabase,
    private readonly createId: () => string = () => crypto.randomUUID(),
  ) {}

  async getById(id: string): Promise<RequirementItem | undefined> {
    assertUuid(id, 'requirement id')
    return this.database.requirement_item.get(id)
  }

  async listByProject(projectId: string): Promise<RequirementItem[]> {
    assertUuid(projectId, 'project id')
    return this.database.requirement_item
      .where('projectId')
      .equals(projectId)
      .toArray()
  }

  /**
   * Updates a single requirement item without creating a version record.
   * Use this for in-progress edits that should not generate history entries.
   * Call {@link save} when the user explicitly saves or leaves edit mode.
   */
  async updateRequirement(
    id: string,
    changes: Partial<Omit<RequirementItem, 'id' | 'projectId' | 'createdAt'>>,
    now = new Date().toISOString(),
  ): Promise<RequirementItem> {
    assertUuid(id, 'requirement id')
    assertUtcIsoDateTime(now, 'update timestamp')

    return this.database.transaction('rw', this.database.requirement_item, async () => {
      const current = await this.database.requirement_item.get(id)
      if (!current) {
        throw new RequirementNotFoundError(id)
      }
      if (current.locked) {
        throw new Error('Unlock the requirement before editing')
      }
      const updated: RequirementItem = {
        ...current,
        ...changes,
        id: current.id,
        projectId: current.projectId,
        createdAt: current.createdAt,
        updatedAt: now,
      }
      await this.database.requirement_item.put(updated)
      return updated
    })
  }

  async commitManualEdit(id: string, edit: ManualRequirementEdit, now = new Date().toISOString()): Promise<RequirementItem> {
    assertUuid(id, 'requirement id'); assertUtcIsoDateTime(now, 'manual edit timestamp')
    const title = edit.title.trim(); const content = edit.content.trim()
    if (!title || !content) throw new TypeError('requirement title and content must not be blank')
    return this.database.transaction('rw', [this.database.project, this.database.requirement_item, this.database.clarification_question, this.database.clarification_answer, this.database.requirement_conflict, this.database.requirement_version, this.database.requirement_change, this.database.app_setting, this.database.flowchart], async () => {
      const current = await this.database.requirement_item.get(id)
      if (!current) throw new RequirementNotFoundError(id)
      if (current.locked) throw new Error('Unlock the requirement before editing')
      const updated: RequirementItem = { ...current, title, content, status: 'CONFIRMED', sourceType: 'USER_EDIT', metadata: { ...current.metadata, affectedArtifacts: [...new Set(edit.affectedArtifacts)] }, updatedAt: now }
      await this.database.requirement_item.put(updated)
      const project = await this.database.project.get(current.projectId)
      if (!project) throw new RequirementSaveError(`Project ${current.projectId} not found`)
      const [requirements, questions, answers, conflicts, flowcharts] = await Promise.all([
        this.database.requirement_item.where('projectId').equals(current.projectId).toArray(),
        this.database.clarification_question.where('projectId').equals(current.projectId).toArray(),
        this.database.clarification_answer.where('projectId').equals(current.projectId).toArray(),
        this.database.requirement_conflict.where('projectId').equals(current.projectId).toArray(),
        this.database.flowchart.where('projectId').equals(current.projectId).toArray(),
      ])
      const completeness = calculateCompleteness(requirements, questions, conflicts)
      const updatedProject = { ...project, completeness: completeness.total, updatedAt: now }
      await this.database.project.put(updatedProject)
      await this.database.app_setting.put({ key: `analysisCompleteness:${current.projectId}`, value: completeness, updatedAt: now })
      const versionId = this.createId(); const changeId = this.createId()
      assertUuid(versionId, 'version id'); assertUuid(changeId, 'change id')
      const snapshot: RequirementStateSnapshot = { project: structuredClone(updatedProject), requirements: structuredClone(requirements), questions: structuredClone(questions), answers: structuredClone(answers), conflicts: structuredClone(conflicts), flowcharts: structuredClone(flowcharts) }
      await this.database.requirement_version.add({ id: versionId, projectId: current.projectId, changeType: 'UPDATE', summary: `手动编辑：${title}`, snapshot, createdAt: now })
      await this.database.requirement_change.add({ id: changeId, projectId: current.projectId, versionId, requirementId: id, changeType: 'UPDATE', field: 'manualEdit', oldValue: current, newValue: updated, createdAt: now })
      return updated
    })
  }

  /**
   * Saves the complete set of requirements for a project within a single transaction.
   * This operation:
   * 1. Writes all requirement items (replacing the previous set)
   * 2. Builds a full project state snapshot
   * 3. Creates a version record with the snapshot
   * 4. Records field-level changes
   *
   * On any failure the entire transaction rolls back, leaving no partial data.
   */
  async save(input: RequirementSaveInput, now = new Date().toISOString()): Promise<RequirementSaveResult> {
    assertUuid(input.projectId, 'project id')
    assertUtcIsoDateTime(now, 'save timestamp')
    this.validateSaveInput(input, now)

    return this.database.transaction(
      'rw',
      [
        this.database.project,
        this.database.requirement_item,
        this.database.clarification_question,
        this.database.clarification_answer,
        this.database.requirement_conflict,
        this.database.requirement_version,
        this.database.requirement_change,
        this.database.flowchart,
      ],
      async () => {
        const project = await this.database.project.get(input.projectId)
        if (!project) {
          throw new RequirementSaveError(`Project ${input.projectId} not found`)
        }

        const [questions, answers, conflicts, flowcharts] = await Promise.all([
          this.database.clarification_question.where('projectId').equals(input.projectId).toArray(),
          this.database.clarification_answer.where('projectId').equals(input.projectId).toArray(),
          this.database.requirement_conflict.where('projectId').equals(input.projectId).toArray(),
          this.database.flowchart.where('projectId').equals(input.projectId).toArray(),
        ])

        // Replace all requirements for this project
        await this.database.requirement_item.where('projectId').equals(input.projectId).delete()
        if (input.requirements.length > 0) {
          await this.database.requirement_item.bulkAdd(input.requirements)
        }

        const snapshot: RequirementStateSnapshot = {
          project: structuredClone(project),
          requirements: input.requirements.map((r) => structuredClone(r)),
          questions: structuredClone(questions),
          answers: structuredClone(answers),
          conflicts: structuredClone(conflicts),
          flowcharts: structuredClone(flowcharts),
        }

        const versionId = this.createId()
        assertUuid(versionId, 'version id')

        const changes: RequirementChange[] = input.fieldChanges.map((fc, index) => {
          const changeId = this.createId()
          assertUuid(changeId, `change[${index}] id`)
          return {
            id: changeId,
            projectId: input.projectId,
            versionId,
            requirementId: fc.requirementId,
            changeType: fc.changeType,
            field: fc.field,
            oldValue: fc.oldValue,
            newValue: fc.newValue,
            createdAt: now,
          }
        })

        const version: RequirementVersion = {
          id: versionId,
          projectId: input.projectId,
          changeType: input.changeType,
          summary: input.summary,
          snapshot,
          createdAt: now,
        }

        await this.database.requirement_version.add(version)
        if (changes.length > 0) {
          await this.database.requirement_change.bulkAdd(changes)
        }

        return { version, changes }
      },
    )
  }

  private validateSaveInput(input: RequirementSaveInput, now: string): void {
    if (!input.summary.trim()) {
      throw new TypeError('version summary must not be blank')
    }
    for (const item of input.requirements) {
      assertUuid(item.id, 'requirement id')
      assertUuid(item.projectId, 'requirement projectId')
      if (item.projectId !== input.projectId) {
        throw new TypeError(
          `Requirement ${item.id} belongs to project ${item.projectId}, expected ${input.projectId}`,
        )
      }
    }
    for (const fc of input.fieldChanges) {
      if (fc.requirementId !== null) {
        assertUuid(fc.requirementId, 'field change requirementId')
      }
    }
  }
}

export class RequirementNotFoundError extends Error {
  constructor(requirementId: string) {
    super(`Requirement ${requirementId} was not found`)
    this.name = 'RequirementNotFoundError'
  }
}

export class RequirementSaveError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'RequirementSaveError'
  }
}

export const requirementRepository = new RequirementRepository()
