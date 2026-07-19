import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { ClarificationQuestion, ClarificationRound, ClarificationRoundStatus } from '@/features/requirements/types'
import { appDatabase, type AppDatabase } from '@/db/appDatabase'

interface GenerationRequest {
  roundNo: number
  requestId: string
  contextVersion: number
}

interface RoundPersistenceState {
  currentRoundNo: number
  readyNextRoundNo: number | null
  coveredAreas: string[]
  pendingAreas: string[]
  contextVersion: number
}

export const useAnalysisRoundStore = defineStore('analysisRound', () => {
  // --- Core state ---
  const currentRoundNo = ref(1)
  const readyNextRoundNo = ref<number | null>(null)
  const generatingRoundNo = ref<number | null>(null)
  const generationRequestId = ref<string | null>(null)
  const contextVersion = ref(0)
  const generationError = ref<string | null>(null)

  // Round questions: roundNo -> questions
  const allQuestions = ref<Map<number, ClarificationQuestion[]>>(new Map())

  // Coverage tracking
  const coveredAreas = ref<Set<string>>(new Set())
  const pendingAreas = ref<string[]>([])
  const staleRounds = ref<Set<number>>(new Set())

  // Internal dedup tracker
  const inflightRequests = new Map<number, GenerationRequest>()

  // --- Computed ---
  const currentRoundQuestions = computed(() =>
    allQuestions.value.get(currentRoundNo.value) ?? []
  )

  const hasReadyNextRound = computed(() => readyNextRoundNo.value !== null)

  const isLoadingNextRound = computed(() =>
    generatingRoundNo.value !== null && !hasReadyNextRound.value
  )

  const currentRoundInfo = computed(() => {
    const questions = currentRoundQuestions.value
    const coverage = questions.length > 0
      ? [...new Set(questions.flatMap(q => q.coverageCategories ?? []))]
      : []

    return {
      roundNo: currentRoundNo.value,
      questionCount: questions.length,
      coverageCategories: coverage,
    }
  })

  // --- Actions ---

  function setCurrentRoundQuestions(roundNo: number, questions: ClarificationQuestion[]) {
    const existing = allQuestions.value.get(roundNo) ?? []
    const merged = new Map<string, ClarificationQuestion>()
    for (const q of existing) merged.set(q.id, q)
    for (const q of questions) merged.set(q.id, q)
    allQuestions.value.set(roundNo, [...merged.values()])

    // Track covered areas
    for (const q of questions) {
      for (const cat of (q.coverageCategories ?? [])) {
        coveredAreas.value.add(cat)
      }
    }
  }

  async function activateNextRound(projectId: string, database: AppDatabase = appDatabase): Promise<boolean> {
    if (readyNextRoundNo.value === null) return false
    const nextRound = readyNextRoundNo.value
    currentRoundNo.value = nextRound
    readyNextRoundNo.value = null
    contextVersion.value++
    generationError.value = null

    // Persist the round status change (READY → ACTIVE) and the updated state
    await database.clarification_round
      .where('[projectId+roundNo]').equals([projectId, nextRound])
      .modify({ status: 'ACTIVE' as ClarificationRoundStatus, generatedAt: new Date().toISOString() })
    await persist(projectId, database)
    return true
  }

  function shouldStartGeneration(roundNo: number, requestId: string): boolean {
    if (generatingRoundNo.value === roundNo) return false
    if (readyNextRoundNo.value === roundNo) return false
    // Dedup: same roundNo with same or newer contextVersion
    const existing = inflightRequests.get(roundNo)
    if (existing && existing.contextVersion >= contextVersion.value) return false

    inflightRequests.set(roundNo, {
      roundNo,
      requestId,
      contextVersion: contextVersion.value,
    })
    return true
  }

  function startGeneration(roundNo: number, requestId: string) {
    generatingRoundNo.value = roundNo
    generationRequestId.value = requestId
    generationError.value = null
  }

  function completeGeneration(roundNo: number, questions: ClarificationQuestion[], requestId: string) {
    // Verify this is still the expected generation
    if (generationRequestId.value !== requestId) {
      console.debug('Ignoring stale generation result for round', roundNo)
      return
    }

    inflightRequests.delete(roundNo)
    setCurrentRoundQuestions(roundNo, questions)
    readyNextRoundNo.value = roundNo
    generatingRoundNo.value = null
    generationRequestId.value = null
    contextVersion.value++
  }

  function markGenerationFailed(roundNo: number, error?: string) {
    inflightRequests.delete(roundNo)
    if (generatingRoundNo.value === roundNo) {
      generatingRoundNo.value = null
      generationRequestId.value = null
      generationError.value = error ?? 'Background generation failed'
    }
  }

  function markDownstreamStale(fromRoundNo: number) {
    // Mark all rounds > fromRoundNo as STALE
    for (const [roundNo] of allQuestions.value) {
      if (roundNo > fromRoundNo) {
        staleRounds.value.add(roundNo)
      }
    }
    // Clear ready next round if it's stale
    if (readyNextRoundNo.value !== null && readyNextRoundNo.value > fromRoundNo) {
      readyNextRoundNo.value = null
    }
    // Stop any in-progress generation for stale rounds
    if (generatingRoundNo.value !== null && generatingRoundNo.value > fromRoundNo) {
      generatingRoundNo.value = null
      generationRequestId.value = null
    }
  }

  function clearStaleMarker(roundNo: number) {
    staleRounds.value.delete(roundNo)
  }

  function isRoundStale(roundNo: number): boolean {
    return staleRounds.value.has(roundNo)
  }

  // --- Persistence ---

  async function persist(projectId: string, database: AppDatabase = appDatabase) {
    const state: RoundPersistenceState = {
      currentRoundNo: currentRoundNo.value,
      readyNextRoundNo: readyNextRoundNo.value,
      coveredAreas: [...coveredAreas.value],
      pendingAreas: pendingAreas.value,
      contextVersion: contextVersion.value,
    }
    await database.app_setting.put({
      key: roundStateKey(projectId),
      value: state,
      updatedAt: new Date().toISOString(),
    })
  }

  async function recover(projectId: string, database: AppDatabase = appDatabase) {
    // Load round state from app_setting
    const record = await database.app_setting.get(roundStateKey(projectId))
    if (record?.value) {
      const saved = record.value as RoundPersistenceState
      if (saved.currentRoundNo) currentRoundNo.value = saved.currentRoundNo
      if (saved.readyNextRoundNo !== undefined) readyNextRoundNo.value = saved.readyNextRoundNo
      if (saved.contextVersion) contextVersion.value = saved.contextVersion
      if (saved.coveredAreas) coveredAreas.value = new Set(saved.coveredAreas)
      if (saved.pendingAreas) pendingAreas.value = saved.pendingAreas
    }

    // Load questions grouped by roundNo
    const questions = await database.clarification_question
      .where('projectId').equals(projectId)
      .toArray()

    const byRound = new Map<number, ClarificationQuestion[]>()
    for (const q of questions) {
      // Treat roundNo=0 (pre-round-system legacy) as round 1
      const rn = q.roundNo && q.roundNo > 0 ? q.roundNo : 1
      if (!byRound.has(rn)) byRound.set(rn, [])
      byRound.get(rn)!.push(q)
    }
    allQuestions.value = byRound

    // Load rounds from clarification_round table
    const rounds = await database.clarification_round
      .where('projectId').equals(projectId)
      .toArray()

    // Restore ready next round: prefer the nearest READY round after the current round
    const readyRounds = rounds.filter(r => r.status === 'READY').sort((a, b) => a.roundNo - b.roundNo)
    if (readyRounds.length && !readyNextRoundNo.value) {
      // Pick the smallest READY roundNo > currentRoundNo; otherwise pick the largest overall
      const current = currentRoundNo.value
      const upcoming = readyRounds.filter(r => r.roundNo > current)
      readyNextRoundNo.value = upcoming.length > 0 ? upcoming[0].roundNo : readyRounds[readyRounds.length - 1].roundNo
    }

    // Restore stale rounds
    for (const r of rounds) {
      if (r.status === 'STALE') staleRounds.value.add(r.roundNo)
    }
  }

  async function persistRound(
    round: ClarificationRound,
    database: AppDatabase = appDatabase,
  ) {
    await database.clarification_round.put(round)
  }

  async function updateRoundStatus(
    roundNo: number,
    projectId: string,
    status: ClarificationRoundStatus,
    database: AppDatabase = appDatabase,
  ) {
    const existing = await database.clarification_round
      .where('[projectId+roundNo]').equals([projectId, roundNo])
      .first()
    if (existing) {
      await database.clarification_round.update(existing.id, { status, generatedAt: new Date().toISOString() })
    }
  }

  async function deleteStaleRounds(
    projectId: string,
    fromRoundNo: number,
    database: AppDatabase = appDatabase,
  ) {
    const stale = await database.clarification_round
      .where('projectId').equals(projectId)
      .filter(r => r.roundNo >= fromRoundNo && r.status === 'STALE')
      .toArray()
    for (const r of stale) {
      await database.clarification_round.delete(r.id)
    }
  }

  function reset() {
    currentRoundNo.value = 1
    readyNextRoundNo.value = null
    generatingRoundNo.value = null
    generationRequestId.value = null
    contextVersion.value = 0
    generationError.value = null
    allQuestions.value = new Map()
    coveredAreas.value = new Set()
    pendingAreas.value = []
    staleRounds.value = new Set()
    inflightRequests.clear()
  }

  return {
    // State
    currentRoundNo,
    readyNextRoundNo,
    generatingRoundNo,
    generationRequestId,
    contextVersion,
    generationError,
    allQuestions,
    coveredAreas,
    pendingAreas,
    staleRounds,
    // Computed
    currentRoundQuestions,
    hasReadyNextRound,
    isLoadingNextRound,
    currentRoundInfo,
    // Actions
    setCurrentRoundQuestions,
    activateNextRound,
    shouldStartGeneration,
    startGeneration,
    completeGeneration,
    markGenerationFailed,
    markDownstreamStale,
    clearStaleMarker,
    isRoundStale,
    persist,
    recover,
    persistRound,
    updateRoundStatus,
    deleteStaleRounds,
    reset,
  }
})

function roundStateKey(projectId: string): `roundState:${string}` {
  return `roundState:${projectId}`
}
