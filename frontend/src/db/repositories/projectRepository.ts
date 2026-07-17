import type { PrdSection } from '@/features/prd/types'
import {
  assertUtcIsoDateTime,
  assertUuid,
  createProject,
  PROJECT_STATUSES,
  type CreateProjectInput,
  type Project,
  type ProjectStatus,
} from '@/features/projects/types'
import type {
  ClarificationAnswer,
  ClarificationOption,
  ClarificationQuestion,
  RequirementChange,
  RequirementConflict,
  RequirementItem,
  RequirementStateSnapshot,
  RequirementVersion,
} from '@/features/requirements/types'
import { appDatabase, type AppDatabase } from '../appDatabase'

export type ProjectListFilter = ProjectStatus

export interface CopyProjectOptions {
  now?: string
}

export interface ProjectSummary {
  project: Project
  pendingCount: number
}

export interface ProjectHomeRepository {
  listSummaries(filter?: ProjectListFilter): Promise<ProjectSummary[]>
  rename(projectId: string, name: string): Promise<unknown>
  copy(projectId: string): Promise<unknown>
  archive(projectId: string): Promise<unknown>
  moveToTrash(projectId: string): Promise<unknown>
  restore(projectId: string): Promise<unknown>
  permanentlyDelete(projectId: string): Promise<unknown>
}

export interface ProjectCreateRepository {
  create(input: CreateProjectInput): Promise<Project>
  applySuggestedName(projectId: string, suggestedName: string, now?: string): Promise<Project>
}

export interface ProjectLookupRepository {
  getById(projectId: string): Promise<Project | undefined>
}

export class ProjectNotFoundError extends Error {
  constructor(projectId: string) {
    super(`Project ${projectId} was not found`)
    this.name = 'ProjectNotFoundError'
  }
}

export class ProjectStateError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ProjectStateError'
  }
}

type IdMap = Map<string, string>

interface CopyMaps {
  requirement: IdMap
  question: IdMap
  questionBatch: IdMap
  option: IdMap
  answer: IdMap
  conflict: IdMap
  version: IdMap
  change: IdMap
  section: IdMap
}

export class ProjectRepository
  implements ProjectHomeRepository, ProjectCreateRepository, ProjectLookupRepository
{
  constructor(
    private readonly database: AppDatabase = appDatabase,
    private readonly createId: () => string = () => crypto.randomUUID(),
  ) {}

  async create(input: CreateProjectInput): Promise<Project> {
    const project = createProject(input)
    await this.database.project.add(project)
    return project
  }

  async applySuggestedName(
    projectId: string,
    suggestedName: string,
    now = new Date().toISOString(),
  ): Promise<Project> {
    const trimmedName = suggestedName.trim()
    return this.updateProject(projectId, now, (project) => {
      if (project.userRenamed || !trimmedName) return project
      return {
        ...project,
        name: trimmedName,
        updatedAt: now,
      }
    })
  }

  async getById(projectId: string): Promise<Project | undefined> {
    assertUuid(projectId, 'project id')
    return this.database.project.get(projectId)
  }

  async list(filter: ProjectListFilter = 'ACTIVE'): Promise<Project[]> {
    if (!PROJECT_STATUSES.includes(filter)) {
      throw new TypeError('invalid project list filter')
    }

    const projects = await this.database.project.where('status').equals(filter).toArray()
    return projects.sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
  }

  async listSummaries(filter: ProjectListFilter = 'ACTIVE'): Promise<ProjectSummary[]> {
    return this.database.transaction(
      'r',
      [
        this.database.project,
        this.database.requirement_item,
        this.database.clarification_question,
      ],
      async () => {
        const projects = await this.list(filter)
        return Promise.all(
          projects.map(async (project) => {
            const [pendingRequirements, pendingQuestions] = await Promise.all([
              this.database.requirement_item
                .where('[projectId+status]')
                .equals([project.id, 'PENDING'])
                .count(),
              this.database.clarification_question
                .where('[projectId+status]')
                .equals([project.id, 'PENDING'])
                .count(),
            ])
            return {
              project,
              pendingCount: pendingRequirements + pendingQuestions,
            }
          }),
        )
      },
    )
  }

  async rename(projectId: string, name: string, now = new Date().toISOString()): Promise<Project> {
    const trimmedName = name.trim()
    if (!trimmedName) {
      throw new TypeError('project name must not be blank')
    }

    return this.updateProject(projectId, now, (project) => ({
      ...project,
      name: trimmedName,
      userRenamed: true,
      updatedAt: now,
    }))
  }

  async archive(projectId: string, now = new Date().toISOString()): Promise<Project> {
    return this.updateProject(projectId, now, (project) => ({
      ...project,
      status: 'ARCHIVED',
      archivedAt: now,
      deletedAt: null,
      updatedAt: now,
    }))
  }

  async moveToTrash(projectId: string, now = new Date().toISOString()): Promise<Project> {
    return this.updateProject(projectId, now, (project) => ({
      ...project,
      status: 'DELETED',
      archivedAt: null,
      deletedAt: now,
      updatedAt: now,
    }))
  }

  async restore(projectId: string, now = new Date().toISOString()): Promise<Project> {
    return this.updateProject(projectId, now, (project) => ({
      ...project,
      status: 'ACTIVE',
      archivedAt: null,
      deletedAt: null,
      updatedAt: now,
    }))
  }

  async copy(projectId: string, options: CopyProjectOptions = {}): Promise<Project> {
    assertUuid(projectId, 'project id')
    const now = options.now ?? new Date().toISOString()
    assertUtcIsoDateTime(now, 'copy timestamp')

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
        this.database.prd_section,
      ],
      async () => {
        const sourceProject = await this.requireProject(projectId)
        const [requirements, questions, answers, conflicts, versions, changes, sections] =
          await Promise.all([
            this.database.requirement_item.where('projectId').equals(projectId).toArray(),
            this.database.clarification_question.where('projectId').equals(projectId).toArray(),
            this.database.clarification_answer.where('projectId').equals(projectId).toArray(),
            this.database.requirement_conflict.where('projectId').equals(projectId).toArray(),
            this.database.requirement_version.where('projectId').equals(projectId).toArray(),
            this.database.requirement_change.where('projectId').equals(projectId).toArray(),
            this.database.prd_section.where('projectId').equals(projectId).toArray(),
          ])

        const maps = this.createCopyMaps()
        this.allocateGraphIds(maps, requirements, questions, answers, conflicts, versions, changes, sections)

        const copiedProject: Project = {
          ...structuredClone(sourceProject),
          id: this.nextId(),
          name: `${sourceProject.name} 副本`,
          status: 'ACTIVE',
          userRenamed: false,
          archivedAt: null,
          deletedAt: null,
          createdAt: now,
          updatedAt: now,
        }
        const copiedRequirements = requirements.map((item) =>
          this.copyRequirement(item, copiedProject.id, maps, now),
        )
        const copiedQuestions = questions.map((question) =>
          this.copyQuestion(question, copiedProject.id, maps, now),
        )
        const copiedAnswers = answers.map((answer) =>
          this.copyAnswer(answer, copiedProject.id, maps, now),
        )
        const copiedConflicts = conflicts.map((conflict) =>
          this.copyConflict(conflict, copiedProject.id, maps, now),
        )
        const copiedVersions = versions.map((version) => ({
          ...structuredClone(version),
          id: this.mappedId(maps.version, version.id),
          projectId: copiedProject.id,
          snapshot: this.copySnapshot(version.snapshot, copiedProject, maps, now),
          createdAt: now,
        }))
        const copiedChanges = changes.map((change) => ({
          ...structuredClone(change),
          id: this.mappedId(maps.change, change.id),
          projectId: copiedProject.id,
          versionId: this.mappedId(maps.version, change.versionId),
          requirementId: this.mapOptionalId(maps.requirement, change.requirementId),
          createdAt: now,
        }))
        const copiedSections = sections.map((section) => ({
          ...structuredClone(section),
          id: this.mappedId(maps.section, section.id),
          projectId: copiedProject.id,
          createdAt: now,
          updatedAt: now,
        }))

        await this.database.project.add(copiedProject)
        await this.bulkAdd(this.database.requirement_item, copiedRequirements)
        await this.bulkAdd(this.database.clarification_question, copiedQuestions)
        await this.bulkAdd(this.database.clarification_answer, copiedAnswers)
        await this.bulkAdd(this.database.requirement_conflict, copiedConflicts)
        await this.bulkAdd(this.database.requirement_version, copiedVersions)
        await this.bulkAdd(this.database.requirement_change, copiedChanges)
        await this.bulkAdd(this.database.prd_section, copiedSections)

        return copiedProject
      },
    )
  }

  async permanentlyDelete(projectId: string): Promise<void> {
    assertUuid(projectId, 'project id')

    await this.database.transaction(
      'rw',
      [
        this.database.project,
        this.database.requirement_item,
        this.database.clarification_question,
        this.database.clarification_answer,
        this.database.requirement_conflict,
        this.database.requirement_version,
        this.database.requirement_change,
        this.database.prd_section,
      ],
      async () => {
        const project = await this.requireProject(projectId)
        if (project.status !== 'DELETED') {
          throw new ProjectStateError('Only a project in the recycle bin may be permanently deleted')
        }

        await Promise.all([
          this.database.requirement_item.where('projectId').equals(projectId).delete(),
          this.database.clarification_question.where('projectId').equals(projectId).delete(),
          this.database.clarification_answer.where('projectId').equals(projectId).delete(),
          this.database.requirement_conflict.where('projectId').equals(projectId).delete(),
          this.database.requirement_version.where('projectId').equals(projectId).delete(),
          this.database.requirement_change.where('projectId').equals(projectId).delete(),
          this.database.prd_section.where('projectId').equals(projectId).delete(),
        ])
        await this.database.project.delete(projectId)
      },
    )
  }

  private async updateProject(
    projectId: string,
    now: string,
    update: (project: Project) => Project,
  ): Promise<Project> {
    assertUuid(projectId, 'project id')
    assertUtcIsoDateTime(now, 'project mutation timestamp')

    return this.database.transaction('rw', this.database.project, async () => {
      const current = await this.requireProject(projectId)
      const updated = update(current)
      await this.database.project.put(updated)
      return updated
    })
  }

  private async requireProject(projectId: string): Promise<Project> {
    const project = await this.database.project.get(projectId)
    if (!project) {
      throw new ProjectNotFoundError(projectId)
    }
    return project
  }

  private createCopyMaps(): CopyMaps {
    return {
      requirement: new Map(),
      question: new Map(),
      questionBatch: new Map(),
      option: new Map(),
      answer: new Map(),
      conflict: new Map(),
      version: new Map(),
      change: new Map(),
      section: new Map(),
    }
  }

  private allocateGraphIds(
    maps: CopyMaps,
    requirements: RequirementItem[],
    questions: ClarificationQuestion[],
    answers: ClarificationAnswer[],
    conflicts: RequirementConflict[],
    versions: RequirementVersion[],
    changes: RequirementChange[],
    sections: PrdSection[],
  ): void {
    for (const item of requirements) this.allocate(maps.requirement, item.id)
    for (const question of questions) this.allocateQuestion(maps, question)
    for (const answer of answers) this.allocate(maps.answer, answer.id)
    for (const conflict of conflicts) this.allocate(maps.conflict, conflict.id)
    for (const version of versions) {
      this.allocate(maps.version, version.id)
      for (const item of version.snapshot.requirements) this.allocate(maps.requirement, item.id)
      for (const question of version.snapshot.questions) this.allocateQuestion(maps, question)
      for (const answer of version.snapshot.answers) this.allocate(maps.answer, answer.id)
      for (const conflict of version.snapshot.conflicts) this.allocate(maps.conflict, conflict.id)
    }
    for (const change of changes) this.allocate(maps.change, change.id)
    for (const section of sections) this.allocate(maps.section, section.id)
  }

  private allocateQuestion(maps: CopyMaps, question: ClarificationQuestion): void {
    this.allocate(maps.question, question.id)
    this.allocate(maps.questionBatch, question.batchId)
    for (const option of question.options) this.allocate(maps.option, option.id)
  }

  private allocate(map: IdMap, sourceId: string): void {
    if (!map.has(sourceId)) map.set(sourceId, this.nextId())
  }

  private nextId(): string {
    const id = this.createId()
    assertUuid(id, 'generated id')
    return id
  }

  private mappedId(map: IdMap, sourceId: string): string {
    const id = map.get(sourceId)
    if (!id) throw new TypeError(`Missing copied ID mapping for ${sourceId}`)
    return id
  }

  private mapOptionalId(map: IdMap, sourceId: string | null): string | null {
    if (sourceId === null) return null
    return map.get(sourceId) ?? sourceId
  }

  private mapKnownReference(sourceId: string | null, maps: CopyMaps): string | null {
    if (sourceId === null) return null
    return (
      maps.answer.get(sourceId) ??
      maps.question.get(sourceId) ??
      maps.requirement.get(sourceId) ??
      maps.conflict.get(sourceId) ??
      maps.version.get(sourceId) ??
      maps.change.get(sourceId) ??
      maps.section.get(sourceId) ??
      maps.option.get(sourceId) ??
      sourceId
    )
  }

  private copyRequirement(
    item: RequirementItem,
    projectId: string,
    maps: CopyMaps,
    now: string,
  ): RequirementItem {
    return {
      ...structuredClone(item),
      id: this.mappedId(maps.requirement, item.id),
      projectId,
      sourceId: this.mapKnownReference(item.sourceId, maps),
      createdAt: now,
      updatedAt: now,
    }
  }

  private copyQuestion(
    question: ClarificationQuestion,
    projectId: string,
    maps: CopyMaps,
    now: string,
  ): ClarificationQuestion {
    return {
      ...structuredClone(question),
      id: this.mappedId(maps.question, question.id),
      projectId,
      batchId: this.mappedId(maps.questionBatch, question.batchId),
      options: question.options.map((option): ClarificationOption => ({
        ...structuredClone(option),
        id: this.mappedId(maps.option, option.id),
      })),
      createdAt: now,
      updatedAt: now,
    }
  }

  private copyAnswer(
    answer: ClarificationAnswer,
    projectId: string,
    maps: CopyMaps,
    now: string,
  ): ClarificationAnswer {
    return {
      ...structuredClone(answer),
      id: this.mappedId(maps.answer, answer.id),
      projectId,
      questionId: this.mappedId(maps.question, answer.questionId),
      selectedOptionIds: answer.selectedOptionIds.map((id) => this.mappedId(maps.option, id)),
      createdAt: now,
      updatedAt: now,
    }
  }

  private copyConflict(
    conflict: RequirementConflict,
    projectId: string,
    maps: CopyMaps,
    now: string,
  ): RequirementConflict {
    return {
      ...structuredClone(conflict),
      id: this.mappedId(maps.conflict, conflict.id),
      projectId,
      leftRequirementId: this.mapOptionalId(maps.requirement, conflict.leftRequirementId),
      rightRequirementId: this.mapOptionalId(maps.requirement, conflict.rightRequirementId),
      createdAt: now,
      updatedAt: now,
      resolvedAt: conflict.resolvedAt === null ? null : now,
    }
  }

  private copySnapshot(
    snapshot: RequirementStateSnapshot,
    project: Project,
    maps: CopyMaps,
    now: string,
  ): RequirementStateSnapshot {
    return {
      project: structuredClone(project),
      requirements: snapshot.requirements.map((item) =>
        this.copyRequirement(item, project.id, maps, now),
      ),
      questions: snapshot.questions.map((question) =>
        this.copyQuestion(question, project.id, maps, now),
      ),
      answers: snapshot.answers.map((answer) => this.copyAnswer(answer, project.id, maps, now)),
      conflicts: snapshot.conflicts.map((conflict) =>
        this.copyConflict(conflict, project.id, maps, now),
      ),
    }
  }

  private async bulkAdd<T, TKey>(
    table: { bulkAdd(items: T[]): Promise<TKey> },
    items: T[],
  ): Promise<void> {
    if (items.length > 0) await table.bulkAdd(items)
  }
}

export const projectRepository = new ProjectRepository()
