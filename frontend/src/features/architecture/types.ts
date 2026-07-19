export type TargetPlatform = 'WEB' | 'DESKTOP' | 'MOBILE' | 'CROSS_PLATFORM' | 'API'
export type TeamSize = 'SOLO' | 'SMALL_TEAM' | 'LARGE_TEAM'
export type UserScale = 'PROTOTYPE' | 'SMALL' | 'MEDIUM' | 'LARGE'
export type DataSensitivity = 'PUBLIC' | 'INTERNAL' | 'PERSONAL' | 'SENSITIVE' | 'HIGHLY_SENSITIVE'
export type Deployment = 'LOCAL' | 'MONOLITHIC_DOCKER' | 'CLOUD' | 'MULTI_INSTANCE'
export type Budget = 'MINIMAL' | 'LIMITED' | 'FLEXIBLE'
export type Timeline = 'RAPID' | 'STANDARD' | 'LONG_TERM'
export type MaintenanceCapacity = 'LOW' | 'MEDIUM' | 'HIGH'

export interface CriticalCapabilities {
  login: boolean
  realtime: boolean
  payments: boolean
  fileUpload: boolean
  ai: boolean
}

export interface TechnicalConstraints {
  projectId: string
  knownTechnologies: string[]
  customTechnology: string | null
  targetPlatform: TargetPlatform | null
  teamSize: TeamSize | null
  userScale: UserScale | null
  criticalCapabilities: CriticalCapabilities | null
  dataSensitivity: DataSensitivity | null
  deployment: Deployment | null
  budget: Budget | null
  timeline: Timeline | null
  maintenanceCapacity: MaintenanceCapacity | null
}

export const SCORE_DIMENSIONS = [
  'LEARNING_COST',
  'DEVELOPMENT_SPEED',
  'DEPLOYMENT_SIMPLICITY',
  'RUNNING_COST',
  'MAINTAINABILITY',
  'SCALABILITY',
  'AI_SUPPORT',
] as const

export type ScoreDimension = (typeof SCORE_DIMENSIONS)[number]

export interface ArchitectureCandidate {
  id: string
  name: string
  stack: Record<string, string>
  responsibilities: string[]
  advantages: string[]
  disadvantages: string[]
  limitations: string[]
  unselectedReasons: string[]
  scores: Record<ScoreDimension, number>
  totalScore: number
  recommended: boolean
}

export interface ArchitectureRecommendationResponse {
  candidates: ArchitectureCandidate[]
  pendingFields: string[]
}

export interface ArchitectureConfirmedEvent {
  requestId: string
  eventId: 1
  type: 'architecture_confirmed'
  data: { architectureId: string }
  timestamp: string
}
