import type { RequirementItem, RequirementType } from './types'

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

// --- Requirement grouping ---

export const REQUIREMENT_GROUPS: { label: string; types: RequirementType[]; order: number; defaultOpen: boolean }[] = [
  { label: '产品与用户',    types: ['PRODUCT_GOAL', 'ROLE'],                                          order: 1, defaultOpen: true },
  { label: '功能需求',      types: ['FEATURE', 'USER_STORY'],                                         order: 2, defaultOpen: true },
  { label: '业务规则',      types: ['BUSINESS_RULE', 'EXCEPTION_SCENARIO'],                           order: 3, defaultOpen: true },
  { label: '页面需求',      types: ['PAGE', 'API'],                                                    order: 4, defaultOpen: true },
  { label: '数据需求',      types: ['DATA_MODEL'],                                                     order: 5, defaultOpen: true },
  { label: '验收标准',      types: ['ACCEPTANCE_CRITERION'],                                           order: 6, defaultOpen: true },
  { label: '非功能需求',    types: ['NON_FUNCTIONAL_REQUIREMENT', 'TECHNICAL_CONSTRAINT',
                                    'CODING_AGENT_CONSTRAINT', 'IMPLEMENTATION_PHASE'],                order: 7, defaultOpen: true },
  { label: 'AI 推断与风险', types: ['ASSUMPTION', 'RISK_OPEN_ITEM'],                                  order: 8, defaultOpen: false },
]

export function requirementToGroup(type: RequirementType): string {
  for (const group of REQUIREMENT_GROUPS) {
    if (group.types.includes(type)) return group.label
  }
  return '其他'
}

export function requirementGroupOrder(label: string): number {
  return REQUIREMENT_GROUPS.find(g => g.label === label)?.order ?? 99
}

export function requirementGroupDefaultOpen(label: string): boolean {
  return REQUIREMENT_GROUPS.find(g => g.label === label)?.defaultOpen ?? true
}

// --- Summary truncation ---

export function requirementSummary(content: string, maxLength = 120): string {
  const cleaned = content.replace(/\n/g, ' ').replace(/\s+/g, ' ').trim()
  if (cleaned.length <= maxLength) return cleaned
  return cleaned.slice(0, maxLength).replace(/\s+\S*$/, '') + '…'
}

// --- Flowchart eligibility ---

const FLOW_KEYWORDS = ['订单', '支付', '审核', '退款', '退货', '状态变更', '审批', '流程', '申请', '流转']
const FLOW_REJECT_KEYWORDS = ['静态页面', '列表展示', '只读', '查看']

export function canGenerateFlowchart(requirement: RequirementItem): boolean {
  const text = `${requirement.title} ${requirement.content}`
  const hasFlow = FLOW_KEYWORDS.some(kw => text.includes(kw))
  const isRejected = FLOW_REJECT_KEYWORDS.some(kw => text.includes(kw))
  return hasFlow && !isRejected
}
