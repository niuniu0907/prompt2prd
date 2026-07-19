import { assertUuid, type Project } from '@/features/projects/types'
import type {
  ClarificationAnswer,
  ClarificationQuestion,
  CompletenessScore,
  RequirementConflict,
  RequirementItem,
  RequirementStateSnapshot,
} from '@/features/requirements/types'
import { isArchitectureCandidateRequirement } from '@/features/requirements/requirementDisplay'
import { appDatabase, type AppDatabase } from '../appDatabase'

export interface AnalysisState extends RequirementStateSnapshot {
  completeness: CompletenessScore
}

export interface AnalysisStateStore {
  load(projectId: string): Promise<AnalysisState | undefined>
  saveFinal(projectId: string, state: unknown): Promise<AnalysisState>
}

interface FinalStatePayload {
  project: Pick<Project, 'id' | 'name' | 'language' | 'stage' | 'completeness'>
  requirements: RequirementItem[]
  questions: ClarificationQuestion[]
  answers: ClarificationAnswer[]
  conflicts: RequirementConflict[]
  completeness: CompletenessScore
}

export class AnalysisStateRepository implements AnalysisStateStore {
  constructor(private readonly database: AppDatabase = appDatabase) {}

  async load(projectId: string): Promise<AnalysisState | undefined> {
    assertUuid(projectId, 'project id')
    return this.database.transaction(
      'r',
      [
        this.database.project,
        this.database.requirement_item,
        this.database.clarification_question,
        this.database.clarification_answer,
        this.database.requirement_conflict,
        this.database.app_setting,
      ],
      async () => {
        const project = await this.database.project.get(projectId)
        if (!project) return undefined
        const [requirements, questions, answers, conflicts] = await Promise.all([
          this.database.requirement_item.where('projectId').equals(projectId).toArray(),
          this.database.clarification_question.where('projectId').equals(projectId).toArray(),
          this.database.clarification_answer.where('projectId').equals(projectId).toArray(),
          this.database.requirement_conflict.where('projectId').equals(projectId).toArray(),
        ])
        const completenessRecord = await this.database.app_setting.get(completenessKey(projectId))
        return {
          project,
          requirements,
          questions,
          answers,
          conflicts,
          completeness: isCompleteness(completenessRecord?.value)
            ? completenessRecord.value
            : emptyCompleteness(project.completeness, requirements, questions, conflicts),
        }
      },
    )
  }

  async saveFinal(projectId: string, value: unknown): Promise<AnalysisState> {
    assertUuid(projectId, 'project id')
    const state = requireFinalState(value)
    if (state.project.id !== projectId) {
      throw new TypeError('Analysis state belongs to a different project')
    }

    // Defensive deep-clone: strip any Vue reactive proxies that may have
    // leaked through shared references before IndexedDB structured-clone.
    const plainCompleteness = safeToPlain(state.completeness)
    const plainRequirements = state.requirements.map(safeToPlain)
    const plainQuestions = state.questions.map(safeToPlain)
    const plainAnswers = state.answers.map(safeToPlain)
    const plainConflicts = state.conflicts.map(safeToPlain)

    return this.database.transaction(
      'rw',
      [
        this.database.project,
        this.database.requirement_item,
        this.database.clarification_question,
        this.database.clarification_answer,
        this.database.requirement_conflict,
        this.database.app_setting,
      ],
      async () => {
        const current = await this.database.project.get(projectId)
        if (!current) throw new Error(`Project ${projectId} not found`)
        const currentRequirements = await this.database.requirement_item.where('projectId').equals(projectId).toArray()
        const incomingIds = new Set(state.requirements.map(item => item.id))
        const preservedArchitectureCandidates = currentRequirements
          .filter(item => isArchitectureCandidateRequirement(item) && !incomingIds.has(item.id))
        const now = new Date().toISOString()
        const project: Project = {
          ...current,
          name: current.userRenamed ? current.name : state.project.name,
          language: state.project.language,
          stage: state.project.stage,
          completeness: plainCompleteness.total,
          updatedAt: now,
        }

        await this.database.requirement_item.where('projectId').equals(projectId).delete()
        await this.database.clarification_question.where('projectId').equals(projectId).delete()
        await this.database.clarification_answer.where('projectId').equals(projectId).delete()
        await this.database.requirement_conflict.where('projectId').equals(projectId).delete()
        await this.database.project.put(project)
        await this.database.app_setting.put({
          key: completenessKey(projectId),
          value: plainCompleteness,
          updatedAt: now,
        })
        const requirements = [...plainRequirements, ...preservedArchitectureCandidates]
        if (requirements.length) await this.database.requirement_item.bulkAdd(requirements)
        if (plainQuestions.length) await this.database.clarification_question.bulkAdd(plainQuestions)
        if (plainAnswers.length) await this.database.clarification_answer.bulkAdd(plainAnswers)
        if (plainConflicts.length) await this.database.requirement_conflict.bulkAdd(plainConflicts)

        return { ...state, project, requirements }
      },
    )
  }
}

function requireFinalState(value: unknown): FinalStatePayload {
  if (!value || typeof value !== 'object') throw new TypeError('Final analysis state must be an object')
  const state = value as Partial<FinalStatePayload>
  if (!state.project || !state.completeness
    || !Array.isArray(state.requirements) || !Array.isArray(state.questions)
    || !Array.isArray(state.answers) || !Array.isArray(state.conflicts)) {
    throw new TypeError('Final analysis state is incomplete')
  }
  return state as FinalStatePayload
}

function emptyCompleteness(
  total: number,
  requirements: RequirementItem[],
  questions: ClarificationQuestion[],
  conflicts: RequirementConflict[],
): CompletenessScore {
  return {
    total,
    dimensions: [],
    pendingCount: requirements.filter(item => item.status === 'PENDING').length
      + questions.filter(item => item.status === 'PENDING').length,
    hasCoreConflict: conflicts.some(item => item.core && item.status === 'OPEN'),
  }
}

export const analysisStateRepository = new AnalysisStateRepository()

function completenessKey(projectId: string): `analysisCompleteness:${string}` {
  return `analysisCompleteness:${projectId}`
}

function isCompleteness(value: unknown): value is CompletenessScore {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<CompletenessScore>
  return typeof candidate.total === 'number'
    && Array.isArray(candidate.dimensions)
    && typeof candidate.pendingCount === 'number'
    && typeof candidate.hasCoreConflict === 'boolean'
}

/**
 * Returns a deep-plain copy of the value, stripping Vue reactive proxies.
 * Falls back to the original value when structured cloning succeeds directly,
 * which avoids the cost of a JSON round-trip for most IndexedDB-originated data.
 */
function safeToPlain<T>(value: T): T {
  try {
    // structuredClone succeeds for plain objects but throws for reactive proxies
    return structuredClone(value)
  } catch {
    // JSON round-trip strips all proxies and non-serializable values
    return JSON.parse(JSON.stringify(value)) as T
  }
}
