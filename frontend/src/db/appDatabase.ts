import Dexie, { type Table } from 'dexie'

import type { PrdSection } from '@/features/prd/types'
import type { Project } from '@/features/projects/types'
import type {
  ClarificationAnswer,
  ClarificationQuestion,
  RequirementChange,
  RequirementConflict,
  RequirementItem,
  RequirementVersion,
} from '@/features/requirements/types'
import {
  DATABASE_NAME,
  DATABASE_STORES,
  DATABASE_VERSION,
  type AppSettingRecord,
} from './schema'

export { DATABASE_NAME, DATABASE_VERSION } from './schema'

export class AppDatabase extends Dexie {
  declare project: Table<Project, string>
  declare requirement_item: Table<RequirementItem, string>
  declare clarification_question: Table<ClarificationQuestion, string>
  declare clarification_answer: Table<ClarificationAnswer, string>
  declare requirement_conflict: Table<RequirementConflict, string>
  declare requirement_version: Table<RequirementVersion, string>
  declare requirement_change: Table<RequirementChange, string>
  declare prd_section: Table<PrdSection, string>
  declare app_setting: Table<AppSettingRecord, string>

  constructor(name = DATABASE_NAME) {
    super(name)
    this.version(DATABASE_VERSION).stores(DATABASE_STORES)
  }
}

export function createAppDatabase(name = DATABASE_NAME): AppDatabase {
  return new AppDatabase(name)
}

export const appDatabase = createAppDatabase()
