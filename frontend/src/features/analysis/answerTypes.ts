export interface QuestionAnswerDraft {
  questionId: string
  selectedOptionIds: string[]
  customAnswer: string | null
  note: string | null
  skipped: boolean
}

export function emptyAnswer(questionId: string): QuestionAnswerDraft {
  return { questionId, selectedOptionIds: [], customAnswer: null, note: null, skipped: false }
}
