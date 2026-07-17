import type { UtcIsoDateTime, Uuid } from '@/features/projects/types'

export const PRD_SECTION_STATUSES = ['DRAFT', 'GENERATING', 'COMPLETED', 'FAILED'] as const
export type PrdSectionStatus = (typeof PRD_SECTION_STATUSES)[number]

export interface PrdSection {
  id: Uuid
  projectId: Uuid
  sectionKey: string
  title: string
  content: string
  order: number
  status: PrdSectionStatus
  locked: boolean
  errorCode: string | null
  createdAt: UtcIsoDateTime
  updatedAt: UtcIsoDateTime
}
