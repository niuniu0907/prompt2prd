import { assertUuid } from '@/features/projects/types'
import type { ClarificationRound, ClarificationRoundStatus } from '@/features/requirements/types'
import { appDatabase, type AppDatabase } from '../appDatabase'

export class ClarificationRoundRepository {
  constructor(
    private readonly database: AppDatabase = appDatabase,
    private readonly createId = () => crypto.randomUUID(),
  ) {}

  async saveRound(round: ClarificationRound): Promise<ClarificationRound> {
    assertUuid(round.id, 'round id')
    assertUuid(round.projectId, 'project id')
    const existing = await this.database.clarification_round.get(round.id)
    if (existing) {
      await this.database.clarification_round.update(round.id, { ...round })
      return round
    }
    await this.database.clarification_round.add(round)
    return round
  }

  async loadByProject(projectId: string): Promise<ClarificationRound[]> {
    assertUuid(projectId, 'project id')
    return this.database.clarification_round
      .where('projectId').equals(projectId)
      .sortBy('roundNo')
  }

  async findByRoundNo(projectId: string, roundNo: number): Promise<ClarificationRound | undefined> {
    assertUuid(projectId, 'project id')
    return this.database.clarification_round
      .where('[projectId+roundNo]').equals([projectId, roundNo])
      .first()
  }

  async updateStatus(
    projectId: string,
    roundNo: number,
    status: ClarificationRoundStatus,
  ): Promise<void> {
    assertUuid(projectId, 'project id')
    const existing = await this.findByRoundNo(projectId, roundNo)
    if (existing) {
      await this.database.clarification_round.update(existing.id, {
        status,
        generatedAt: new Date().toISOString(),
      })
    }
  }

  async deleteStaleFrom(projectId: string, fromRoundNo: number): Promise<void> {
    assertUuid(projectId, 'project id')
    const stale = await this.database.clarification_round
      .where('projectId').equals(projectId)
      .filter(r => r.roundNo >= fromRoundNo && (r.status === 'STALE' || r.status === 'GENERATING'))
      .toArray()
    for (const r of stale) {
      await this.database.clarification_round.delete(r.id)
    }
  }

  async deleteAllForProject(projectId: string): Promise<void> {
    assertUuid(projectId, 'project id')
    await this.database.clarification_round
      .where('projectId').equals(projectId)
      .delete()
  }

  createRound(
    projectId: string,
    roundNo: number,
    requestId: string,
    contextVersion: string,
    questionIds: string[],
    coverageCategories: string[],
    status: ClarificationRoundStatus,
  ): ClarificationRound {
    const now = new Date().toISOString()
    return {
      id: this.createId(),
      projectId,
      roundNo,
      requestId,
      contextVersion,
      questionIds,
      coverageCategories,
      status,
      createdAt: now,
      generatedAt: now,
    }
  }
}

export const clarificationRoundRepository = new ClarificationRoundRepository()
