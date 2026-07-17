import type { UtcIsoDateTime } from '@/features/projects/types'

export const DATABASE_NAME = 'prompt2prd'
export const DATABASE_VERSION = 1

export const PERSISTED_STORE_NAMES = [
  'project',
  'requirement_item',
  'clarification_question',
  'clarification_answer',
  'requirement_conflict',
  'requirement_version',
  'requirement_change',
  'prd_section',
  'app_setting',
] as const

export type PersistedStoreName = (typeof PERSISTED_STORE_NAMES)[number]

export type AppSettingKey = 'uploadPrivacyNoticeAccepted'

export interface AppSettingRecord {
  key: AppSettingKey
  value: boolean
  updatedAt: UtcIsoDateTime
}

export const DATABASE_STORES: Record<PersistedStoreName, string> = {
  project: 'id, status, stage, updatedAt, archivedAt, deletedAt',
  requirement_item: 'id, projectId, [projectId+type], [projectId+status], updatedAt',
  clarification_question:
    'id, projectId, batchId, semanticKey, [projectId+status], [projectId+batchId], updatedAt',
  clarification_answer:
    'id, projectId, questionId, [projectId+questionId], createdAt, updatedAt',
  requirement_conflict: 'id, projectId, [projectId+status], [projectId+core], updatedAt',
  requirement_version: 'id, projectId, [projectId+createdAt], createdAt',
  requirement_change: 'id, projectId, versionId, [projectId+versionId], createdAt',
  prd_section: 'id, projectId, [projectId+sectionKey], [projectId+status], updatedAt',
  app_setting: 'key, updatedAt',
}
