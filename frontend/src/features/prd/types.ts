import type { UtcIsoDateTime, Uuid } from '@/features/projects/types'

export const PRD_SECTION_STATUSES = ['DRAFT', 'GENERATING', 'COMPLETED', 'FAILED'] as const
export type PrdSectionStatus = (typeof PRD_SECTION_STATUSES)[number]

export const PRD_SECTION_KEYS = [
  'product-background-goals',
  'target-users-scenarios',
  'product-scope',
  'feature-modules-priority',
  'user-stories',
  'business-rules',
  'exception-scenarios',
  'page-list-states',
  'data-requirements',
  'acceptance-criteria',
  'non-functional-requirements',
  'risks-assumptions-open-items',
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
