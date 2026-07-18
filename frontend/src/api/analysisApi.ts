import {
  consumePostSse,
  type FetchLike,
} from './sseClient'
import type { KnownStreamEvent } from './streamEvents'

export interface AnalysisRequestBody {
  state: unknown
  input: string
  missingInformation: string[]
  modelSettings: unknown
}

export interface AnalysisAnswersRequestBody {
  state: unknown
  answers: unknown[]
  originalInput?: string
  supplementalInput?: string
  missingInformation: string[]
  modelSettings: unknown
}

export interface AnalysisCallbacks {
  onEvent?: (event: KnownStreamEvent) => void
  onWarning?: (message: string) => void
}

export class StaleAnalysisError extends Error {
  constructor() {
    super('Analysis result was superseded by a newer request')
    this.name = 'StaleAnalysisError'
  }
}

export function createAnalysisClient(fetcher: FetchLike = fetch) {
  let requestVersion = 0
  let activeController: AbortController | undefined

  const execute = async (
    url: string,
    body: AnalysisRequestBody | AnalysisAnswersRequestBody,
    callbacks: AnalysisCallbacks = {},
  ) => {
    const version = ++requestVersion
    activeController?.abort()
    const controller = new AbortController()
    activeController = controller

    try {
      const result = await consumePostSse({
        url,
        body,
        fetcher,
        signal: controller.signal,
        onEvent: event => {
          if (version === requestVersion) callbacks.onEvent?.(event)
        },
        onWarning: message => {
          if (version === requestVersion) callbacks.onWarning?.(message)
        },
      })
      if (version !== requestVersion) throw new StaleAnalysisError()
      return result
    } catch (error) {
      if (version !== requestVersion) throw new StaleAnalysisError()
      throw error
    } finally {
      if (version === requestVersion) activeController = undefined
    }
  }

  return {
    analyze: (body: AnalysisRequestBody, callbacks?: AnalysisCallbacks) =>
      execute('/api/analysis', body, callbacks),
    submitAnswers: (body: AnalysisAnswersRequestBody, callbacks?: AnalysisCallbacks) =>
      execute('/api/analysis/answers', body, callbacks),
    cancel: () => {
      requestVersion += 1
      activeController?.abort()
      activeController = undefined
    },
  }
}

export type AnalysisClient = ReturnType<typeof createAnalysisClient>
