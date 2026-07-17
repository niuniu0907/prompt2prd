export type Uuid = string
export type UtcIsoDateTime = string

export const PROJECT_MODULES = [
  'overview',
  'questions',
  'requirements',
  'architecture',
  'flowchart',
  'prd',
] as const

export type ProjectModule = (typeof PROJECT_MODULES)[number]

export const PROJECT_STAGES = [
  'CLARIFYING',
  'ARCHITECTURE',
  'FLOWCHART',
  'PRD',
  'COMPLETED',
] as const

export type ProjectStage = (typeof PROJECT_STAGES)[number]

export const PROJECT_STATUSES = ['ACTIVE', 'ARCHIVED', 'DELETED'] as const
export type ProjectStatus = (typeof PROJECT_STATUSES)[number]

export interface Project {
  id: Uuid
  name: string
  originalPrompt: string
  uploadedFileName: string | null
  uploadedFileContent: string | null
  supplementalPrompt: string | null
  language: string
  stage: ProjectStage
  status: ProjectStatus
  completeness: number
  userRenamed: boolean
  archivedAt: UtcIsoDateTime | null
  deletedAt: UtcIsoDateTime | null
  createdAt: UtcIsoDateTime
  updatedAt: UtcIsoDateTime
}

export interface CreateProjectInput {
  id?: Uuid
  name: string
  originalPrompt: string
  uploadedFileName?: string | null
  uploadedFileContent?: string | null
  supplementalPrompt?: string | null
  language?: string
  completeness?: number
  now?: UtcIsoDateTime
}

export function createTemporaryProjectName(source: string): string {
  const normalizedSource = source.trim()
  if (!normalizedSource) {
    throw new TypeError('temporary project name source must not be blank')
  }
  return Array.from(normalizedSource).slice(0, 20).join('')
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const UTC_ISO_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/

export function assertUuid(value: string, fieldName = 'id'): asserts value is Uuid {
  if (!UUID_PATTERN.test(value)) {
    throw new TypeError(`${fieldName} must be a UUID`)
  }
}

export function assertUtcIsoDateTime(
  value: string,
  fieldName = 'timestamp',
): asserts value is UtcIsoDateTime {
  if (!UTC_ISO_PATTERN.test(value) || Number.isNaN(Date.parse(value))) {
    throw new TypeError(`${fieldName} must be a UTC ISO-8601 timestamp`)
  }
}

export function createProject(input: CreateProjectInput): Project {
  const id = input.id ?? crypto.randomUUID()
  const now = input.now ?? new Date().toISOString()
  const completeness = input.completeness ?? 0

  assertUuid(id, 'project id')
  assertUtcIsoDateTime(now, 'project timestamp')

  if (!input.name.trim()) {
    throw new TypeError('project name must not be blank')
  }
  const normalizedPrompt = input.originalPrompt.trim()
  const hasFileContent = Boolean(input.uploadedFileContent?.trim())
  if (!normalizedPrompt && !hasFileContent) {
    throw new TypeError('project source must not be blank')
  }
  if (!hasFileContent && Array.from(normalizedPrompt).length < 5) {
    throw new TypeError('original prompt must contain at least 5 Unicode characters')
  }
  if (!Number.isInteger(completeness) || completeness < 0 || completeness > 100) {
    throw new TypeError('project completeness must be an integer between 0 and 100')
  }

  return {
    id,
    name: input.name.trim(),
    originalPrompt: input.originalPrompt,
    uploadedFileName: input.uploadedFileName ?? null,
    uploadedFileContent: input.uploadedFileContent ?? null,
    supplementalPrompt: input.supplementalPrompt ?? null,
    language: input.language ?? 'zh-CN',
    stage: 'CLARIFYING',
    status: 'ACTIVE',
    completeness,
    userRenamed: false,
    archivedAt: null,
    deletedAt: null,
    createdAt: now,
    updatedAt: now,
  }
}
