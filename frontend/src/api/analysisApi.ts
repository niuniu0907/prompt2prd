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

export interface GenerateRoundRequestBody {
  state: unknown
  targetRoundNo: number
  coveredAreas: string[]
  currentVisibleQuestions: { text: string; targetField: string; semanticKey: string }[]
  missingInformation: string[]
  modelSettings: unknown
}

export interface GenerateRoundResponseBody {
  success: boolean
  errorCode?: string
  errorMessage?: string
  roundNo: number
  questions: unknown[]
  coverageCategories: string[]
  requestId: string
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

export class RoundGenerationError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly retryable = true,
  ) {
    super(message)
    this.name = 'RoundGenerationError'
  }
}

export function createAnalysisClient(fetcher: FetchLike = fetch) {
  let requestVersion = 0
  let activeController: AbortController | undefined
  let generationController: AbortController | undefined
  let generationRequestId: string | null = null

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

  const jsonPost = async (
    url: string,
    body: unknown,
    signal?: AbortSignal,
  ): Promise<unknown> => {
    const response = await fetcher(url, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      signal,
    })

    if (!response.ok) {
      let errorMessage = `生成请求失败（HTTP ${response.status}）`
      try {
        const payload = await response.json() as Partial<{ code: string; message: string }>
        if (payload.message) errorMessage = payload.message
      } catch { /* ignore parse failure */ }
      throw new RoundGenerationError(errorMessage, `HTTP_${response.status}`, response.status >= 500)
    }

    return response.json()
  }

  return {
    analyze: (body: AnalysisRequestBody, callbacks?: AnalysisCallbacks) =>
      execute('/api/analysis', body, callbacks),

    submitAnswers: (body: AnalysisAnswersRequestBody, callbacks?: AnalysisCallbacks) =>
      execute('/api/analysis/answers', body, callbacks),

    generateRound: async (
      body: GenerateRoundRequestBody,
      signal?: AbortSignal,
    ): Promise<GenerateRoundResponseBody> => {
      const id = crypto.randomUUID()
      generationRequestId = id

      // Use a dedicated AbortController so cancel() can abort in-flight generateRound
      generationController?.abort()
      const controller = new AbortController()
      generationController = controller

      // Compose external signal (if provided) with the internal abort controller
      const compositeSignal = signal
        ? combineSignals(signal, controller.signal)
        : controller.signal

      const startTime = performance.now()
      try {
        const result = await jsonPost('/api/analysis/generate-round', body, compositeSignal) as GenerateRoundResponseBody
        const elapsed = Math.round(performance.now() - startTime)
        console.debug(`[analysisApi] generateRound roundNo=${body.targetRoundNo} latencyMs=${elapsed}`)

        // Validate this is still the latest request
        if (generationRequestId !== id) {
          throw new StaleAnalysisError()
        }

        if (!result.success) {
          throw new RoundGenerationError(
            result.errorMessage ?? 'Round generation failed',
            result.errorCode ?? 'GENERATION_FAILED',
            true,
          )
        }

        return result
      } catch (error) {
        if (error instanceof StaleAnalysisError) throw error
        if (error instanceof RoundGenerationError) throw error
        const elapsed = Math.round(performance.now() - startTime)
        console.warn(`[analysisApi] generateRound failed roundNo=${body.targetRoundNo} latencyMs=${elapsed}`, error)
        throw new RoundGenerationError(
          error instanceof Error ? error.message : 'Round generation failed',
          'GENERATION_FAILED',
          true,
        )
      } finally {
        if (generationRequestId === id) generationController = undefined
      }
    },

    cancel: () => {
      requestVersion += 1
      generationRequestId = null
      generationController?.abort()
      generationController = undefined
      activeController?.abort()
      activeController = undefined
    },
  }
}

export type AnalysisClient = ReturnType<typeof createAnalysisClient>

function combineSignals(a: AbortSignal, b: AbortSignal): AbortSignal {
  const controller = new AbortController()
  const onAbort = () => controller.abort()
  a.addEventListener('abort', onAbort, { once: true })
  b.addEventListener('abort', onAbort, { once: true })
  if (a.aborted || b.aborted) controller.abort()
  return controller.signal
}
