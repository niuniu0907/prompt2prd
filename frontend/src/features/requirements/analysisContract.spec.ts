import { describe, expect, it } from 'vitest'

import state from '../../../../contracts/analysis-state.sample.json'

import {
  QUESTION_INPUT_TYPES,
  REQUIREMENT_STATUSES,
  REQUIREMENT_TYPES,
  SOURCE_TYPES,
  createRequirementItem,
} from './types'

describe('shared analysis-state contract', () => {
  it('parses the same fixture with the frontend domain vocabulary', () => {
    const typedState = state as {
      project: { id: string; language: string; completeness: number }
      requirements: Array<Record<string, unknown>>
      questions: Array<{ inputType: string; status: string }>
      completeness: { total: number }
    }
    const rawRequirement = typedState.requirements[0] as Record<string, unknown>

    const requirement = createRequirementItem({
      id: String(rawRequirement.id),
      projectId: String(rawRequirement.projectId),
      type: String(rawRequirement.type) as (typeof REQUIREMENT_TYPES)[number],
      title: String(rawRequirement.title),
      content: String(rawRequirement.content),
      status: String(rawRequirement.status) as (typeof REQUIREMENT_STATUSES)[number],
      sourceType: String(rawRequirement.sourceType) as (typeof SOURCE_TYPES)[number],
      sourceId: null,
      locked: Boolean(rawRequirement.locked),
      metadata: rawRequirement.metadata as Record<string, unknown>,
      now: String(rawRequirement.createdAt),
    })

    expect(typedState.project.language).toBe('zh-CN')
    expect(typedState.project.completeness).toBe(40)
    expect(REQUIREMENT_TYPES).toContain(requirement.type)
    expect(REQUIREMENT_STATUSES).toContain(requirement.status)
    expect(SOURCE_TYPES).toContain(requirement.sourceType)
    expect(QUESTION_INPUT_TYPES).toContain(typedState.questions[0]?.inputType)
    expect(typedState.questions[0]?.status).toBe('PENDING')
    expect(typedState.completeness.total).toBe(40)
  })
})
