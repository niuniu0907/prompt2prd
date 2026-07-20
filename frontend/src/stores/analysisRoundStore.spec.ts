import 'fake-indexeddb/auto'

import Dexie from 'dexie'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { createAppDatabase } from '@/db/appDatabase'
import type { ClarificationQuestion } from '@/features/requirements/types'
import { useAnalysisRoundStore } from './analysisRoundStore'

function question(overrides: Partial<ClarificationQuestion> = {}): ClarificationQuestion {
  return {
    id: crypto.randomUUID(),
    projectId: '10000000-0000-4000-8000-000000000000',
    batchId: '20000000-0000-4000-8000-000000000000',
    roundNo: 1,
    text: '测试问题？',
    reason: '测试原因',
    dimension: 'FEATURES',
    targetField: 'test.field',
    semanticKey: `test-${crypto.randomUUID()}`,
    inputType: 'SINGLE_SELECT',
    options: [
      { id: crypto.randomUUID(), label: '选项A', impact: '影响A', recommended: true },
    ],
    coverageCategories: [],
    priority: 5,
    status: 'PENDING',
    createdAt: '2026-07-17T12:00:00.000Z',
    updatedAt: '2026-07-17T12:00:00.000Z',
    ...overrides,
  }
}

describe('analysisRoundStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  // Test 1: pendingAreas is Vue ref array → persist doesn't throw DataCloneError
  it('persists successfully when pendingAreas is a Vue reactive array', async () => {
    const database = createAppDatabase(`round-store-${crypto.randomUUID()}`)
    try {
      const store = useAnalysisRoundStore()
      // Pinia auto-unwraps refs: setting store.pendingAreas actually sets
      // the internal ref's .value, which becomes a reactive proxy — exactly
      // the scenario that caused DataCloneError before toPlainData was added.
      store.pendingAreas = ['area-1', 'area-2'] as unknown as string[]
      store.coveredAreas = new Set(['existing-area']) as unknown as Set<string>

      // Must not throw DataCloneError
      await expect(
        store.persist('10000000-0000-4000-8000-000000000000', database),
      ).resolves.toBeUndefined()

      // Verify the persisted data is clean (no proxies)
      const record = await database.app_setting.get(
        'roundState:10000000-0000-4000-8000-000000000000',
      )
      expect(record).toBeDefined()
      expect(record!.value).toBeDefined()
      const saved = record!.value as Record<string, unknown>
      expect(saved.pendingAreas).toEqual(['area-1', 'area-2'])
      expect(Array.isArray(saved.pendingAreas)).toBe(true)
    } finally {
      await database.close()
      await Dexie.delete(database.name)
    }
  })

  // Test 2: allQuestions values are plain arrays (not proxies) after toPlainData
  it('writes plain data when questions come from the Pinia store', async () => {
    const database = createAppDatabase(`round-store-${crypto.randomUUID()}`)
    try {
      const store = useAnalysisRoundStore()
      const q = question({ roundNo: 2, status: 'PENDING' })
      store.allQuestions.set(2, [q])

      // Simulate what repersistFutureRoundQuestions does: extract questions
      // from the store and write them via bulkPut with toPlainData.
      const future: ClarificationQuestion[] = []
      for (const [roundNo, questions] of store.allQuestions) {
        if (roundNo > store.currentRoundNo) {
          future.push(...questions)
        }
      }

      // The questions array from the store may contain reactive proxies.
      // toPlainData must strip them before bulkPut.
      const { toPlainData } = await import('@/db/toPlainData')
      await database.clarification_question.bulkPut(toPlainData(future))

      const saved = await database.clarification_question.toArray()
      expect(saved).toHaveLength(1)
      expect(saved[0]!.id).toBe(q.id)
      expect(saved[0]!.text).toBe('测试问题？')
    } finally {
      await database.close()
      await Dexie.delete(database.name)
    }
  })

  // Test 4: currentRoundNo changes from 1 to 2 via activateNextRound
  it('advances currentRoundNo from 1 to 2 when activateNextRound is called', async () => {
    const database = createAppDatabase(`round-store-${crypto.randomUUID()}`)
    try {
      const store = useAnalysisRoundStore()
      expect(store.currentRoundNo).toBe(1)

      // Set up: round 2 is READY
      store.readyNextRoundNo = 2

      // Write a clarification_round record so activateNextRound can update it
      await database.clarification_round.put({
        id: crypto.randomUUID(),
        projectId: '10000000-0000-4000-8000-000000000000',
        roundNo: 2,
        requestId: 'req-1',
        contextVersion: '1',
        questionIds: [],
        coverageCategories: [],
        status: 'READY',
        createdAt: '2026-07-17T12:00:00.000Z',
        generatedAt: '2026-07-17T12:00:00.000Z',
      })

      const result = await store.activateNextRound(
        '10000000-0000-4000-8000-000000000000',
        database,
      )

      expect(result).toBe(true)
      expect(store.currentRoundNo).toBe(2)
      expect(store.readyNextRoundNo).toBeNull()
      expect(store.contextVersion).toBeGreaterThan(0)
    } finally {
      await database.close()
      await Dexie.delete(database.name)
    }
  })

  // Test: shouldStartGeneration returns false when readyNextRoundNo matches
  it('prevents generation when the next round is already READY', () => {
    const store = useAnalysisRoundStore()
    store.readyNextRoundNo = 2

    const shouldStart = store.shouldStartGeneration(2, 'req-1')
    expect(shouldStart).toBe(false)
  })

  // Test: shouldStartGeneration returns true when readyNextRoundNo is different
  it('allows generation when readyNextRoundNo does not match', () => {
    const store = useAnalysisRoundStore()
    store.readyNextRoundNo = 3 // round 3 is ready, not round 2

    const shouldStart = store.shouldStartGeneration(2, 'req-1')
    expect(shouldStart).toBe(true)
  })

  // Test 6: deduplicateAndLimitQuestions caps at 10 even with many answered questions
  it('limits display to 10 questions even when more answered questions exist', () => {
    const store = useAnalysisRoundStore()

    // Create 15 already-answered questions with distinct keys
    const answeredQuestions = Array.from({ length: 15 }, (_, i) =>
      question({
        id: `ans-${i}`,
        semanticKey: `answered-${i}`,
        targetField: `field.answered.${i}`,
        text: `已回答问题 ${i + 1}`,
        status: 'ANSWERED',
        updatedAt: new Date(Date.UTC(2026, 6, 17, 12, 0, i)).toISOString(),
      }),
    )

    // Create 5 pending questions
    const pendingQuestions = Array.from({ length: 5 }, (_, i) =>
      question({
        id: `pend-${i}`,
        semanticKey: `pending-${i}`,
        targetField: `field.pending.${i}`,
        text: `待回答 ${i + 1}`,
        status: 'PENDING',
        priority: 10 - i,
      }),
    )

    const all = [...answeredQuestions, ...pendingQuestions]
    store.setCurrentRoundQuestions(1, all)

    const display = store.currentRoundQuestions
    expect(display.length).toBeLessThanOrEqual(10)
    // All displayed questions should be from the answered set (most recent first)
    // or pending if there's room — but with 15 answered, only the 10 most recent
    // answered should show.
    for (const q of display) {
      expect(q.status === 'ANSWERED' || q.status === 'SKIPPED').toBe(true)
    }
  })

  // Test 7: IndexedDB retains all historical answers after deduplication
  it('retains all historical answers in the database after UI-side deduplication', async () => {
    const database = createAppDatabase(`round-store-${crypto.randomUUID()}`)
    try {
      // Write 15 questions (all answered) directly to DB
      const allQuestions = Array.from({ length: 15 }, (_, i) =>
        question({
          id: `db-q-${i}`,
          semanticKey: `db-sem-${i}`,
          targetField: `db.field.${i}`,
          text: `数据库问题 ${i + 1}`,
          status: 'ANSWERED',
          updatedAt: new Date(Date.UTC(2026, 6, 17, 12, 0, i)).toISOString(),
        }),
      )
      await database.clarification_question.bulkPut(allQuestions)

      // Now load them via recover (which calls deduplicateAndLimitQuestions)
      const store = useAnalysisRoundStore()
      // Write a round state entry so recover works
      await database.app_setting.put({
        key: 'roundState:10000000-0000-4000-8000-000000000000',
        value: { currentRoundNo: 1, readyNextRoundNo: null, coveredAreas: [], pendingAreas: [], contextVersion: 0 },
        updatedAt: '2026-07-17T12:00:00.000Z',
      })

      await store.recover('10000000-0000-4000-8000-000000000000', database)

      // UI display should be capped at 10
      expect(store.currentRoundQuestions.length).toBeLessThanOrEqual(10)

      // But DB should still have all 15
      const dbQuestions = await database.clarification_question.toArray()
      expect(dbQuestions).toHaveLength(15)
    } finally {
      await database.close()
      await Dexie.delete(database.name)
    }
  })

  // Edge case: empty pending array works fine
  it('persists with empty pendingAreas array', async () => {
    const database = createAppDatabase(`round-store-${crypto.randomUUID()}`)
    try {
      const store = useAnalysisRoundStore()
      store.pendingAreas = []

      await expect(
        store.persist('10000000-0000-4000-8000-000000000000', database),
      ).resolves.toBeUndefined()

      const record = await database.app_setting.get(
        'roundState:10000000-0000-4000-8000-000000000000',
      )
      expect(record!.value).toBeDefined()
      const saved = record!.value as Record<string, unknown>
      expect(saved.pendingAreas).toEqual([])
    } finally {
      await database.close()
      await Dexie.delete(database.name)
    }
  })
})
