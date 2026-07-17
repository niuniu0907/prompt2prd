import type { UtcIsoDateTime, Uuid } from '@/features/projects/types'

export const PRD_SECTION_STATUSES = ['DRAFT', 'GENERATING', 'COMPLETED', 'FAILED'] as const
export type PrdSectionStatus = (typeof PRD_SECTION_STATUSES)[number]

export const PRD_SECTION_KEYS = [
  'coding-agent-guide', 'product-context', 'roles-permissions', 'features-priorities',
  'user-stories', 'flows-state-machine', 'rules-exceptions', 'architecture', 'data-model',
  'pages', 'apis', 'non-functional', 'acceptance', 'implementation-phases', 'test-strategy',
  'prohibitions', 'open-items',
] as const
export type PrdSectionKey = (typeof PRD_SECTION_KEYS)[number]

export const PRD_DIRECTIVE_LEVELS = ['MUST', 'SHOULD', 'MUST_NOT'] as const
export type PrdDirectiveLevel = (typeof PRD_DIRECTIVE_LEVELS)[number]

export interface PrdTraceLink {
  featureId: string
  userStoryId: string
  businessRuleIds: string[]
  acceptanceIds: string[]
}

export interface PrdSection {
  id: Uuid
  projectId: Uuid
  sectionKey: PrdSectionKey
  title: string
  content: string
  order: number
  status: PrdSectionStatus
  locked: boolean
  errorCode: string | null
  createdAt: UtcIsoDateTime
  updatedAt: UtcIsoDateTime
}
