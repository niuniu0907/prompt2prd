import type { ClarificationQuestion } from '@/features/requirements/types'

export interface PrdCoverageArea {
  key: string
  label: string
  dimension: string
}

export const prdCoverageAreas: PrdCoverageArea[] = [
  { key: 'productContext', label: '产品背景与目标', dimension: 'PRODUCT_SCOPE' },
  { key: 'rolesScenarios', label: '用户角色与使用场景', dimension: 'ROLES_PERMISSIONS' },
  { key: 'featureScopePriorities', label: '功能范围与优先级', dimension: 'FEATURES' },
  { key: 'coreBusinessFlow', label: '核心业务流程', dimension: 'CORE_FLOW' },
  { key: 'userStories', label: '用户故事', dimension: 'CORE_FLOW' },
  { key: 'rulesExceptions', label: '业务规则与异常场景', dimension: 'BUSINESS_RULES' },
  { key: 'pagesStates', label: '页面清单', dimension: 'PAGES_APIS' },
  { key: 'dataEntitiesFields', label: '数据实体与字段', dimension: 'DATA_MODEL' },
  { key: 'acceptanceCriteria', label: '验收标准', dimension: 'ACCEPTANCE' },
  { key: 'nonFunctional', label: '非功能需求', dimension: 'ARCHITECTURE_CONSTRAINTS' },
  { key: 'assumptionsRisksOpenItems', label: '假设、风险与待确认事项', dimension: 'PRODUCT_SCOPE' },
]

const dimensionFallback: Record<string, string[]> = {
  PRODUCT_SCOPE: ['productContext', 'assumptionsRisksOpenItems'],
  ROLES_PERMISSIONS: ['rolesScenarios'],
  FEATURES: ['featureScopePriorities'],
  CORE_FLOW: ['coreBusinessFlow', 'userStories'],
  BUSINESS_RULES: ['rulesExceptions'],
  EXCEPTIONS: ['rulesExceptions'],
  PAGES_APIS: ['pagesStates'],
  DATA_MODEL: ['dataEntitiesFields'],
  ACCEPTANCE: ['acceptanceCriteria'],
  ARCHITECTURE_CONSTRAINTS: ['nonFunctional'],
}

export function activeCoverageKeys(questions: ClarificationQuestion[]) {
  const keys = new Set<string>()
  for (const question of questions) {
    const prefix = question.targetField.split('.')[0]
    if (prdCoverageAreas.some(area => area.key === prefix)) {
      keys.add(prefix)
      continue
    }
    for (const key of dimensionFallback[question.dimension] ?? []) keys.add(key)
  }
  return keys
}
