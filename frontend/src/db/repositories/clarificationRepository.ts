import { assertUuid, assertUtcIsoDateTime } from '@/features/projects/types'
import type { ClarificationAnswer, ClarificationQuestion } from '@/features/requirements/types'
import type { QuestionAnswerDraft } from '@/features/analysis/answerTypes'
import { appDatabase, type AppDatabase } from '../appDatabase'

export interface SubmitBatchResult { answers: ClarificationAnswer[]; questions: ClarificationQuestion[] }
export interface ClarificationSubmitter {
  submitBatch(projectId: string, drafts: QuestionAnswerDraft[], now?: string): Promise<SubmitBatchResult>
}

export class ClarificationRepository implements ClarificationSubmitter {
  constructor(private readonly database: AppDatabase = appDatabase, private readonly createId = () => crypto.randomUUID()) {}

  async submitBatch(projectId: string, drafts: QuestionAnswerDraft[], now = new Date().toISOString()): Promise<SubmitBatchResult> {
    assertUuid(projectId, 'project id'); assertUtcIsoDateTime(now, 'answer timestamp')
    if (!drafts.length) throw new TypeError('answer batch must not be empty')
    if (new Set(drafts.map(item => item.questionId)).size !== drafts.length) throw new TypeError('answer batch contains duplicate questions')
    return this.database.transaction('rw', [this.database.clarification_question, this.database.clarification_answer], async () => {
      const answers: ClarificationAnswer[] = []
      const questions: ClarificationQuestion[] = []
      for (const draft of drafts) {
        assertUuid(draft.questionId, 'question id')
        const question = await this.database.clarification_question.get(draft.questionId)
        if (!question || question.projectId !== projectId) throw new Error(`Question ${draft.questionId} not found`)
        const allowed = new Set(question.options.map(option => option.id))
        if (draft.selectedOptionIds.some(id => !allowed.has(id))) throw new TypeError('answer contains an option from another question')
        if (!draft.skipped && draft.selectedOptionIds.length === 0 && !draft.customAnswer?.trim()) throw new TypeError('question must be answered or skipped')
        const existing = await this.database.clarification_answer.where('[projectId+questionId]').equals([projectId, question.id]).first()
        const answer: ClarificationAnswer = {
          id: existing?.id ?? this.createId(), projectId, questionId: question.id,
          selectedOptionIds: [...draft.selectedOptionIds], customAnswer: draft.customAnswer?.trim() || null,
          note: draft.note?.trim() || null, skipped: draft.skipped,
          createdAt: existing?.createdAt ?? now, updatedAt: now,
        }
        assertUuid(answer.id, 'answer id')
        const updatedQuestion = { ...question, status: draft.skipped ? 'SKIPPED' as const : 'ANSWERED' as const, updatedAt: now }
        await this.database.clarification_answer.put(answer)
        await this.database.clarification_question.put(updatedQuestion)
        answers.push(answer); questions.push(updatedQuestion)
      }
      return { answers, questions }
    })
  }
}

export const clarificationRepository = new ClarificationRepository()
