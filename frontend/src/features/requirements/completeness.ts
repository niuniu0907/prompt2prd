import type { ClarificationQuestion, CompletenessDimension, CompletenessScore, RequirementConflict, RequirementItem, RequirementType } from './types'

const weights: Record<string, number> = { PRODUCT_SCOPE:10, ROLES_PERMISSIONS:8, CORE_FLOW:15, FEATURES:12, BUSINESS_RULES:10, EXCEPTIONS:8, DATA_MODEL:10, ARCHITECTURE_CONSTRAINTS:12, PAGES_APIS:8, ACCEPTANCE:7 }
const typeDimensions: Partial<Record<RequirementType, string>> = { PRODUCT_GOAL:'PRODUCT_SCOPE', ROLE:'ROLES_PERMISSIONS', USER_STORY:'CORE_FLOW', FEATURE:'FEATURES', BUSINESS_RULE:'BUSINESS_RULES', EXCEPTION_SCENARIO:'EXCEPTIONS', DATA_MODEL:'DATA_MODEL', TECHNICAL_CONSTRAINT:'ARCHITECTURE_CONSTRAINTS', PAGE:'PAGES_APIS', API:'PAGES_APIS', ACCEPTANCE_CRITERION:'ACCEPTANCE' }

export function calculateCompleteness(requirements: RequirementItem[], questions: ClarificationQuestion[], conflicts: RequirementConflict[]): CompletenessScore {
  const effectiveRequirements = requirements.filter(item =>
    item.metadata.kind !== 'ARCHITECTURE_CANDIDATE' || item.status === 'CONFIRMED',
  )
  const dimensions: CompletenessDimension[] = Object.keys(weights).map(dimension => {
    const items = effectiveRequirements.filter(item => dimensionOf(item) === dimension)
    const unanswered = questions.filter(question => question.dimension === dimension && question.status !== 'ANSWERED').length
    const denominator = items.length + unanswered
    const confirmed = items.filter(item => item.status === 'CONFIRMED').length
    const inferred = items.filter(item => item.status === 'INFERRED').length
    let score = denominator ? Math.round((confirmed * 100 + inferred * 40) / denominator) : 0
    const hasCore = conflicts.some(conflict => conflict.core && conflict.status === 'OPEN' && [conflict.leftRequirementId, conflict.rightRequirementId].some(id => items.some(item => item.id === id)))
    if (hasCore) score = Math.min(score, 50)
    return { dimension, applicable: true, score, reasons: [`${confirmed} confirmed item(s)`, `${inferred} inferred item(s)`, `${unanswered} unanswered question(s)`] }
  })
  const total = Math.round(dimensions.reduce((sum, item) => sum + item.score * weights[item.dimension]!, 0) / 100)
  return { total, dimensions, pendingCount: effectiveRequirements.filter(item => item.status === 'PENDING' || item.status === 'CONFLICTED').length + questions.filter(item => item.status !== 'ANSWERED').length, hasCoreConflict: conflicts.some(item => item.core && item.status === 'OPEN') }
}

function dimensionOf(item: RequirementItem) {
  const override = item.metadata.dimension
  return typeof override === 'string' && override in weights ? override : typeDimensions[item.type]
}
