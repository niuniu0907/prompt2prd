import { assertUuid, assertUtcIsoDateTime } from '@/features/projects/types'
import type { Project } from '@/features/projects/types'
import type {
  RequirementChange,
  RequirementConflict,
  RequirementItem,
  RequirementStateSnapshot,
  RequirementVersion,
} from '@/features/requirements/types'
import type { AppDatabase } from '../appDatabase'
import { appDatabase } from '../appDatabase'
import type { RequirementSaveResult } from './requirementRepository'
import { RequirementSaveError } from './requirementRepository'

export class VersionRepository {
  constructor(
    private readonly database: AppDatabase = appDatabase,
    private readonly createId: () => string = () => crypto.randomUUID(),
  ) {}

  async listByProject(projectId: string): Promise<RequirementVersion[]> {
    assertUuid(projectId, 'project id')
    return this.database.requirement_version
      .where('projectId')
      .equals(projectId)
      .toArray()
      .then((versions) =>
        versions.sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
      )
  }

  async getById(versionId: string): Promise<RequirementVersion | undefined> {
    assertUuid(versionId, 'version id')
    return this.database.requirement_version.get(versionId)
  }

  async getWithChanges(
    versionId: string,
  ): Promise<{ version: RequirementVersion; changes: RequirementChange[] } | undefined> {
    assertUuid(versionId, 'version id')
    const version = await this.database.requirement_version.get(versionId)
    if (!version) return undefined

    const changes = await this.database.requirement_change
      .where('versionId')
      .equals(versionId)
      .toArray()

    return { version, changes }
  }

  /**
   * Restores the project to a target version's snapshot.
   *
   * The current state is saved as a version (with changeType RESTORE) before
   * applying the restore, so the operation is always reversible. The restore
   * itself also creates a version record.
   *
   * Everything runs in a single transaction — on failure the current state
   * is preserved and no partial restore occurs.
   */
  async restore(
    projectId: string,
    targetVersionId: string,
    now = new Date().toISOString(),
  ): Promise<RequirementSaveResult> {
    assertUuid(projectId, 'project id')
    assertUuid(targetVersionId, 'target version id')
    assertUtcIsoDateTime(now, 'restore timestamp')

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
      ],
      async () => {
        const targetVersion = await this.database.requirement_version.get(targetVersionId)
        if (!targetVersion) {
          throw new VersionNotFoundError(targetVersionId)
        }
        if (targetVersion.projectId !== projectId) {
          throw new RequirementSaveError(
            `Version ${targetVersionId} belongs to project ${targetVersion.projectId}, not ${projectId}`,
          )
        }

        // ---- Save current state as a pre-restore version ----
        const currentProject = await this.database.project.get(projectId)
        if (!currentProject) {
          throw new RequirementSaveError(`Project ${projectId} not found`)
        }

        const [currentRequirements, currentQuestions, currentAnswers, currentConflicts] =
          await Promise.all([
            this.database.requirement_item.where('projectId').equals(projectId).toArray(),
            this.database.clarification_question.where('projectId').equals(projectId).toArray(),
            this.database.clarification_answer.where('projectId').equals(projectId).toArray(),
            this.database.requirement_conflict.where('projectId').equals(projectId).toArray(),
          ])

        const preRestoreSnapshot: RequirementStateSnapshot = {
          project: structuredClone(currentProject),
          requirements: structuredClone(currentRequirements),
          questions: structuredClone(currentQuestions),
          answers: structuredClone(currentAnswers),
          conflicts: structuredClone(currentConflicts),
        }

        const preRestoreVersionId = this.createId()
        assertUuid(preRestoreVersionId, 'pre-restore version id')

        const preRestoreVersion: RequirementVersion = {
          id: preRestoreVersionId,
          projectId,
          changeType: 'RESTORE',
          summary: `恢复到版本前的自动保存：${targetVersion.summary}`,
          snapshot: preRestoreSnapshot,
          createdAt: now,
        }

        const preRestoreChange: RequirementChange = {
          id: this.createId(),
          projectId,
          versionId: preRestoreVersionId,
          requirementId: null,
          changeType: 'RESTORE',
          field: 'snapshot',
          oldValue: null,
          newValue: `Pre-restore save before restoring to ${targetVersionId}`,
          createdAt: now,
        }

        // ---- Apply the target snapshot ----
        const snapshot = targetVersion.snapshot

        // Restore project fields that were captured in the snapshot
        const restoredProject: Project = {
          ...structuredClone(snapshot.project),
          id: projectId,
          updatedAt: now,
        }
        await this.database.project.put(restoredProject)

        // Replace all requirement items
        await this.database.requirement_item.where('projectId').equals(projectId).delete()
        if (snapshot.requirements.length > 0) {
          await this.database.requirement_item.bulkAdd(
            snapshot.requirements.map((r) => ({
              ...structuredClone(r),
              projectId,
              updatedAt: now,
            })),
          )
        }

        // Replace all questions
        await this.database.clarification_question.where('projectId').equals(projectId).delete()
        if (snapshot.questions.length > 0) {
          await this.database.clarification_question.bulkAdd(
            snapshot.questions.map((q) => ({
              ...structuredClone(q),
              projectId,
              updatedAt: now,
            })),
          )
        }

        // Replace all answers
        await this.database.clarification_answer.where('projectId').equals(projectId).delete()
        if (snapshot.answers.length > 0) {
          await this.database.clarification_answer.bulkAdd(
            snapshot.answers.map((a) => ({
              ...structuredClone(a),
              projectId,
              updatedAt: now,
            })),
          )
        }

        // Replace all conflicts
        await this.database.requirement_conflict.where('projectId').equals(projectId).delete()
        if (snapshot.conflicts.length > 0) {
          await this.database.requirement_conflict.bulkAdd(
            snapshot.conflicts.map((c) => ({
              ...structuredClone(c),
              projectId,
              updatedAt: now,
            })),
          )
        }

        // ---- Create the restore version record ----
        const restoreVersionId = this.createId()
        assertUuid(restoreVersionId, 'restore version id')

        const restoreSnapshot: RequirementStateSnapshot = {
          project: structuredClone(restoredProject),
          requirements: snapshot.requirements.map((r) => ({
            ...structuredClone(r),
            projectId,
            updatedAt: now,
          })),
          questions: snapshot.questions.map((q) => ({
            ...structuredClone(q),
            projectId,
            updatedAt: now,
          })),
          answers: snapshot.answers.map((a) => ({
            ...structuredClone(a),
            projectId,
            updatedAt: now,
          })),
          conflicts: snapshot.conflicts.map((c) => ({
            ...structuredClone(c),
            projectId,
            updatedAt: now,
          })),
        }

        const restoreVersion: RequirementVersion = {
          id: restoreVersionId,
          projectId,
          changeType: 'RESTORE',
          summary: `恢复到版本：${targetVersion.summary}`,
          snapshot: restoreSnapshot,
          createdAt: now,
        }

        const restoreChange: RequirementChange = {
          id: this.createId(),
          projectId,
          versionId: restoreVersionId,
          requirementId: null,
          changeType: 'RESTORE',
          field: 'snapshot',
          oldValue: null,
          newValue: `Restored to version ${targetVersionId}`,
          createdAt: now,
        }

        // Write pre-restore version + change, then restore version + change
        await this.database.requirement_version.add(preRestoreVersion)
        await this.database.requirement_change.add(preRestoreChange)
        await this.database.requirement_version.add(restoreVersion)
        await this.database.requirement_change.add(restoreChange)

        return {
          version: restoreVersion,
          changes: [restoreChange],
        }
      },
    )
  }
}

export class VersionNotFoundError extends Error {
  constructor(versionId: string) {
    super(`Version ${versionId} was not found`)
    this.name = 'VersionNotFoundError'
  }
}

export const versionRepository = new VersionRepository()
