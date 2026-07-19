import type { UtcIsoDateTime, Uuid } from '@/features/projects/types'

export type FlowchartType = 'MAIN' | 'EXCEPTION'
export type FlowchartStatus = 'VALID'

export interface FlowchartRecord {
  id: Uuid
  projectId: Uuid
  key: string
  type: FlowchartType
  title: string
  mermaid: string
  status: FlowchartStatus
  sourceRequirementIds: Uuid[]
  createdAt: UtcIsoDateTime
  updatedAt: UtcIsoDateTime
}

export interface FlowchartDraft {
  key: string
  type: FlowchartType
  title: string
  mermaid: string
  sourceRequirementIds: string[]
}

export interface FlowchartDiagramResult extends FlowchartDraft {
  status: 'GENERATED' | 'FAILED'
  errorCode: string | null
}

export interface FlowchartGenerationResult {
  mainFlow: FlowchartDiagramResult | null
  exceptionFlows: FlowchartDiagramResult[]
  missingInformation: string[]
}
