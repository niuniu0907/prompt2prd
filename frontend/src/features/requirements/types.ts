import type { Project, UtcIsoDateTime, Uuid } from '@/features/projects/types'
import { assertUtcIsoDateTime, assertUuid } from '@/features/projects/types'
import type { FlowchartRecord } from '@/features/flowchart/types'

export const REQUIREMENT_STATUSES = [
  'UNANALYZED',
  'INFERRED',
  'PENDING',
  'CONFIRMED',
  'SKIPPED',
  'NOT_APPLICABLE',
  'CONFLICTED',
] as const
export type RequirementStatus = (typeof REQUIREMENT_STATUSES)[number]

export const REQUIREMENT_TYPES = [
  'PRODUCT_GOAL',
  'ROLE',
  'FEATURE',
  'USER_STORY',
  'BUSINESS_RULE',
  'EXCEPTION_SCENARIO',
  'TECHNICAL_CONSTRAINT',
  'DATA_MODEL',
  'ACCEPTANCE_CRITERION',
  'PAGE',
  'API',
  'IMPLEMENTATION_PHASE',
  'CODING_AGENT_CONSTRAINT',
  'NON_FUNCTIONAL_REQUIREMENT',
  'ASSUMPTION',
  'RISK_OPEN_ITEM',
  'MISSING_INFORMATION',
] as const
export type RequirementType = (typeof REQUIREMENT_TYPES)[number]

export const SOURCE_TYPES = [
  'INITIAL_INPUT',
  'UPLOADED_FILE',
  'USER_ANSWER',
  'AI_INFERENCE',
  'AI_RECOMMENDATION',
  'USER_EDIT',
  'VERSION_RESTORE',
] as const
export type RequirementSourceType = (typeof SOURCE_TYPES)[number]

export interface RequirementItem {
  id: Uuid
  projectId: Uuid
  type: RequirementType
  title: string
  content: string
  status: RequirementStatus
  sourceType: RequirementSourceType
  sourceId: Uuid | null
  locked: boolean
  metadata: Record<string, unknown>
  createdAt: UtcIsoDateTime
  updatedAt: UtcIsoDateTime
}

export interface CreateRequirementItemInput {
  id?: Uuid
  projectId: Uuid
  type: RequirementType
  title: string
  content: string
  status: RequirementStatus
  sourceType: RequirementSourceType
  sourceId: Uuid | null
  locked?: boolean
  metadata?: Record<string, unknown>
  now?: UtcIsoDateTime
}

export const QUESTION_INPUT_TYPES = [
  'SINGLE_SELECT',
  'MULTI_SELECT',
  'CUSTOM_TEXT',
  'SINGLE_SELECT_CUSTOM',
  'MULTI_SELECT_CUSTOM',
  'TEXT',
  'CONFIRMATION',
] as const
export type QuestionInputType = (typeof QUESTION_INPUT_TYPES)[number]
export type ClarificationQuestionStatus = 'PENDING' | 'ANSWERED' | 'SKIPPED'

export interface ClarificationOption {
  id: Uuid
  label: string
  impact: string
  recommended: boolean
}

export interface ClarificationQuestion {
  id: Uuid
  projectId: Uuid
  batchId: Uuid
  text: string
  reason: string
  dimension: string
  targetField: string
  semanticKey: string
  inputType: QuestionInputType
  options: ClarificationOption[]
  priority: number
  status: ClarificationQuestionStatus
  createdAt: UtcIsoDateTime
  updatedAt: UtcIsoDateTime
}

export interface ClarificationAnswer {
  id: Uuid
  projectId: Uuid
  questionId: Uuid
  selectedOptionIds: Uuid[]
  customAnswer: string | null
  note: string | null
  skipped: boolean
  createdAt: UtcIsoDateTime
  updatedAt: UtcIsoDateTime
}

export type ConflictStatus = 'OPEN' | 'RESOLVED'

export interface RequirementConflict {
  id: Uuid
  projectId: Uuid
  leftRequirementId: Uuid | null
  rightRequirementId: Uuid | null
  leftContent: string
  rightContent: string
  impact: string
  core: boolean
  status: ConflictStatus
  resolution: string | null
  createdAt: UtcIsoDateTime
  updatedAt: UtcIsoDateTime
  resolvedAt: UtcIsoDateTime | null
}

export type RequirementChangeType = 'CREATE' | 'UPDATE' | 'DELETE' | 'LOCK' | 'UNLOCK' | 'RESTORE'

export interface RequirementChange {
  id: Uuid
  projectId: Uuid
  versionId: Uuid
  requirementId: Uuid | null
  changeType: RequirementChangeType
  field: string
  oldValue: unknown
  newValue: unknown
  createdAt: UtcIsoDateTime
}

export interface RequirementStateSnapshot {
  project: Project
  requirements: RequirementItem[]
  questions: ClarificationQuestion[]
  answers: ClarificationAnswer[]
  conflicts: RequirementConflict[]
  /** Optional only for snapshots created before IndexedDB schema version 2. */
  flowcharts?: FlowchartRecord[]
}

export interface RequirementVersion {
  id: Uuid
  projectId: Uuid
  changeType: RequirementChangeType
  summary: string
  snapshot: RequirementStateSnapshot
  createdAt: UtcIsoDateTime
}

export interface CompletenessDimension {
  dimension: string
  applicable: boolean
  score: number
  reasons: string[]
}

export interface CompletenessScore {
  total: number
  dimensions: CompletenessDimension[]
  pendingCount: number
  hasCoreConflict: boolean
}

export interface ArchitectureCandidate {
  id: Uuid
  projectId: Uuid
  name: string
  stack: Record<string, string>
  responsibilities: string[]
  advantages: string[]
  disadvantages: string[]
  limitations: string[]
  scores: Record<string, number>
  recommended: boolean
  confirmed: boolean
  createdAt: UtcIsoDateTime
  updatedAt: UtcIsoDateTime
}

export function createRequirementItem(input: CreateRequirementItemInput): RequirementItem {
  const id = input.id ?? crypto.randomUUID()
  const now = input.now ?? new Date().toISOString()
  const locked = input.locked ?? false

  assertUuid(id, 'requirement id')
  assertUuid(input.projectId, 'project id')
  if (input.sourceId !== null) {
    assertUuid(input.sourceId, 'source id')
  }
  assertUtcIsoDateTime(now, 'requirement timestamp')

  if (!REQUIREMENT_TYPES.includes(input.type)) {
    throw new TypeError('invalid requirement type')
  }
  if (!REQUIREMENT_STATUSES.includes(input.status)) {
    throw new TypeError('invalid requirement status')
  }
  if (!SOURCE_TYPES.includes(input.sourceType)) {
    throw new TypeError('invalid requirement source type')
  }
  if (locked && input.status !== 'CONFIRMED') {
    throw new TypeError('Only CONFIRMED requirements may be locked')
  }
  if (!input.title.trim() || !input.content.trim()) {
    throw new TypeError('requirement title and content must not be blank')
  }

  return {
    id,
    projectId: input.projectId,
    type: input.type,
    title: input.title.trim(),
    content: input.content.trim(),
    status: input.status,
    sourceType: input.sourceType,
    sourceId: input.sourceId,
    locked,
    metadata: input.metadata ?? {},
    createdAt: now,
    updatedAt: now,
  }
}
