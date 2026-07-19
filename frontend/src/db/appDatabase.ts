import Dexie, { type Table } from 'dexie'

import type { PrdSection } from '@/features/prd/types'
import type { FlowchartRecord } from '@/features/flowchart/types'
import type { Project } from '@/features/projects/types'
import type {
  ClarificationAnswer,
  ClarificationQuestion,
  ClarificationRound,
  RequirementChange,
  RequirementConflict,
  RequirementItem,
  RequirementVersion,
} from '@/features/requirements/types'
import {
  DATABASE_NAME,
  DATABASE_STORES,
  DATABASE_STORES_V1,
  DATABASE_VERSION,
  type AppSettingRecord,
} from './schema'

export { DATABASE_NAME, DATABASE_VERSION } from './schema'

export class AppDatabase extends Dexie {
  declare project: Table<Project, string>
  declare requirement_item: Table<RequirementItem, string>
  declare clarification_question: Table<ClarificationQuestion, string>
  declare clarification_answer: Table<ClarificationAnswer, string>
  declare clarification_round: Table<ClarificationRound, string>
  declare requirement_conflict: Table<RequirementConflict, string>
  declare requirement_version: Table<RequirementVersion, string>
  declare requirement_change: Table<RequirementChange, string>
  declare prd_section: Table<PrdSection, string>
  declare flowchart: Table<FlowchartRecord, string>
  declare app_setting: Table<AppSettingRecord, string>

  constructor(name = DATABASE_NAME) {
    super(name)
    this.version(1).stores(DATABASE_STORES_V1)
    this.version(2).stores({
      ...DATABASE_STORES_V1,
      flowchart: 'id, projectId, [projectId+key], [projectId+type], [projectId+status], updatedAt',
    })
    this.version(DATABASE_VERSION).stores(DATABASE_STORES).upgrade(async tx => {
      // Add roundNo=0 and coverageCategories=[] to all existing clarification_question records
      const questions = await tx.table('clarification_question').toCollection().toArray()
      for (const q of questions) {
        if (q.roundNo === undefined || q.roundNo === null) {
          await tx.table('clarification_question').update(q.id, { roundNo: 0, coverageCategories: [] })
        }
      }
    })
  }
}

export function createAppDatabase(name = DATABASE_NAME): AppDatabase {
  return new AppDatabase(name)
}

export const appDatabase = createAppDatabase()
