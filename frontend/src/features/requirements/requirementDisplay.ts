import type { RequirementItem } from './types'

export const ARCHITECTURE_CANDIDATE_KIND = 'ARCHITECTURE_CANDIDATE'

export interface StructuredRequirementField {
  label: string
  value: string
}

const fieldLabels: Record<string, string> = {
  name: '方案名称',
  frontend: '前端',
  backend: '后端',
  storage: '数据存储',
  authentication: '鉴权',
  fileStorage: '文件存储',
  ai: 'AI 接入',
  deployment: '部署',
  testing: '测试',
}

export function isArchitectureCandidateRequirement(requirement: Pick<RequirementItem, 'metadata'>) {
  return requirement.metadata.kind === ARCHITECTURE_CANDIDATE_KIND
}

export function isFormalRequirement(requirement: RequirementItem) {
  return !isArchitectureCandidateRequirement(requirement) && requirement.type !== 'MISSING_INFORMATION'
}

export function structuredRequirementFields(requirement: RequirementItem): StructuredRequirementField[] {
  const candidate = requirement.metadata.candidate as { stack?: Record<string, unknown> } | undefined
  if (candidate?.stack && typeof candidate.stack === 'object') {
    return Object.entries(candidate.stack)
      .filter(([, value]) => String(value ?? '').trim())
      .map(([key, value]) => ({ label: fieldLabels[key] ?? key, value: String(value) }))
  }

  const lines = requirement.content.split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
  if (lines.length < 2 || !lines.every(line => /^[A-Za-z][A-Za-z0-9]*\s*[:：]/.test(line))) {
    return []
  }
  return lines.map(line => {
    const [rawKey, ...rest] = line.split(/[:：]/)
    const key = rawKey.trim()
    return { label: fieldLabels[key] ?? key, value: rest.join(':').trim() }
  })
}
