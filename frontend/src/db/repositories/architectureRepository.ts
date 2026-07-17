import type { AppDatabase } from '@/db/appDatabase'
import { appDatabase } from '@/db/appDatabase'
import type { ArchitectureCandidate, ArchitectureConfirmedEvent } from '@/features/architecture/types'
import { SCORE_DIMENSIONS } from '@/features/architecture/types'
import { assertUuid, assertUtcIsoDateTime } from '@/features/projects/types'
import type { RequirementItem, RequirementStateSnapshot } from '@/features/requirements/types'
import { calculateCompleteness } from '@/features/requirements/completeness'

const CANDIDATE_KIND = 'ARCHITECTURE_CANDIDATE'

export interface ArchitectureConfirmationResult {
  requirement: RequirementItem
  event: ArchitectureConfirmedEvent
}

export class ArchitectureRepository {
  constructor(
    private readonly database: AppDatabase = appDatabase,
    private readonly createId: () => string = () => crypto.randomUUID(),
  ) {}

  async listCandidates(projectId: string): Promise<ArchitectureCandidate[]> {
    assertUuid(projectId, 'project id')
    const requirements = await this.architectureRequirements(projectId)
    return requirements.map(item => this.candidateFrom(item)).filter((item): item is ArchitectureCandidate => item !== null)
  }

  async selected(projectId: string): Promise<ArchitectureCandidate | null> {
    assertUuid(projectId, 'project id')
    const requirements = await this.architectureRequirements(projectId)
    const selected = requirements.find(item => item.status === 'CONFIRMED')
    return selected ? this.candidateFrom(selected) : null
  }

  async saveCandidates(projectId: string, candidates: ArchitectureCandidate[], now = new Date().toISOString()): Promise<void> {
    assertUuid(projectId, 'project id'); assertUtcIsoDateTime(now, 'candidate timestamp')
    if (candidates.length < 2 || candidates.length > 3) throw new TypeError('architecture recommendation must contain 2-3 candidates')
    candidates.forEach(candidate => this.validateCandidate(candidate))
    await this.database.transaction('rw', this.database.requirement_item, async () => {
      const existing = await this.architectureRequirements(projectId)
      const incomingIds = new Set(candidates.map(candidate => candidate.id))
      for (const stale of existing.filter(item => item.status !== 'CONFIRMED' && !incomingIds.has(item.id))) {
        await this.database.requirement_item.delete(stale.id)
      }
      for (const candidate of candidates) {
        const current = existing.find(item => item.id === candidate.id)
        await this.database.requirement_item.put(this.toRequirement(projectId, candidate, current?.status === 'CONFIRMED', current?.createdAt ?? now, now))
      }
    })
  }

  async confirm(
    projectId: string,
    candidate: ArchitectureCandidate,
    manual = false,
    now = new Date().toISOString(),
  ): Promise<ArchitectureConfirmationResult> {
    assertUuid(projectId, 'project id'); assertUtcIsoDateTime(now, 'confirmation timestamp'); this.validateCandidate(candidate)
    return this.database.transaction('rw', this.tables(), async () => {
      const project = await this.database.project.get(projectId)
      if (!project) throw new Error(`Project ${projectId} not found`)
      const current = await this.architectureRequirements(projectId)
      const previous = current.find(item => item.status === 'CONFIRMED') ?? null
      for (const item of current) {
        await this.database.requirement_item.put({ ...item, status: 'PENDING', locked: false, updatedAt: now })
      }
      const selected = this.toRequirement(projectId, candidate, true,
        current.find(item => item.id === candidate.id)?.createdAt ?? now, now, manual)
      await this.database.requirement_item.put(selected)
      const [requirements, questions, answers, conflicts, flowcharts] = await Promise.all([
        this.database.requirement_item.where('projectId').equals(projectId).toArray(),
        this.database.clarification_question.where('projectId').equals(projectId).toArray(),
        this.database.clarification_answer.where('projectId').equals(projectId).toArray(),
        this.database.requirement_conflict.where('projectId').equals(projectId).toArray(),
        this.database.flowchart.where('projectId').equals(projectId).toArray(),
      ])
      const completeness = calculateCompleteness(requirements, questions, conflicts)
      const updatedProject = { ...project, stage: 'ARCHITECTURE' as const, completeness: completeness.total, updatedAt: now }
      await this.database.project.put(updatedProject)
      await this.database.app_setting.put({ key: `analysisCompleteness:${projectId}`, value: completeness, updatedAt: now })
      const versionId = this.createId(); const changeId = this.createId()
      assertUuid(versionId, 'version id'); assertUuid(changeId, 'change id')
      const snapshot: RequirementStateSnapshot = {
        project: structuredClone(updatedProject), requirements: structuredClone(requirements),
        questions: structuredClone(questions), answers: structuredClone(answers), conflicts: structuredClone(conflicts), flowcharts: structuredClone(flowcharts),
      }
      await this.database.requirement_version.add({ id: versionId, projectId, changeType: 'UPDATE', summary: `确认主架构：${candidate.name}`, snapshot, createdAt: now })
      await this.database.requirement_change.add({ id: changeId, projectId, versionId, requirementId: candidate.id, changeType: 'UPDATE', field: 'selectedArchitecture', oldValue: previous ? this.candidateFrom(previous) : null, newValue: candidate, createdAt: now })
      const event: ArchitectureConfirmedEvent = { requestId: this.createId(), eventId: 1, type: 'architecture_confirmed', data: { architectureId: candidate.id }, timestamp: now }
      assertUuid(event.requestId, 'event request id')
      return { requirement: selected, event }
    })
  }

  private toRequirement(projectId: string, candidate: ArchitectureCandidate, confirmed: boolean,
    createdAt: string, updatedAt: string, manual = false): RequirementItem {
    return {
      id: candidate.id, projectId, type: 'TECHNICAL_CONSTRAINT', title: candidate.name,
      content: Object.entries(candidate.stack).map(([key, value]) => `${key}: ${value}`).join('\n'),
      status: confirmed ? 'CONFIRMED' : 'PENDING', sourceType: manual ? 'USER_EDIT' : confirmed ? 'USER_ANSWER' : 'AI_RECOMMENDATION',
      sourceId: null, locked: false, metadata: { kind: CANDIDATE_KIND, candidate: structuredClone(candidate), draft: !confirmed },
      createdAt, updatedAt,
    }
  }

  private async architectureRequirements(projectId: string) {
    const requirements = await this.database.requirement_item.where('projectId').equals(projectId).toArray()
    return requirements.filter(item => item.type === 'TECHNICAL_CONSTRAINT' && item.metadata.kind === CANDIDATE_KIND)
  }

  private candidateFrom(item: RequirementItem): ArchitectureCandidate | null {
    const candidate = item.metadata.candidate
    if (!candidate || typeof candidate !== 'object') return null
    try { this.validateCandidate(candidate as ArchitectureCandidate); return structuredClone(candidate as ArchitectureCandidate) } catch { return null }
  }

  private validateCandidate(candidate: ArchitectureCandidate) {
    assertUuid(candidate.id, 'architecture candidate id')
    if (!candidate.name.trim()) throw new TypeError('architecture name must not be blank')
    for (const dimension of SCORE_DIMENSIONS) {
      const score = candidate.scores[dimension]
      if (!Number.isInteger(score) || score < 1 || score > 5) throw new TypeError(`invalid ${dimension} score`)
    }
  }

  private tables() {
    return [this.database.project, this.database.requirement_item, this.database.clarification_question,
      this.database.clarification_answer, this.database.requirement_conflict,
      this.database.requirement_version, this.database.requirement_change, this.database.app_setting, this.database.flowchart]
  }
}

export const architectureRepository = new ArchitectureRepository()
