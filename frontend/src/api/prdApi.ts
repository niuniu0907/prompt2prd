import {
  consumePostSse,
  type FetchLike,
} from './sseClient'
import type { KnownStreamEvent } from './streamEvents'

export interface PrdGenerateRequestBody {
  state: unknown
  missingInformation: string[]
  modelSettings: unknown
}

export interface PrdCallbacks {
  onEvent?: (event: KnownStreamEvent) => void
  onWarning?: (message: string) => void
}

export class StalePrdError extends Error {
  constructor() {
    super('PRD generation was superseded by a newer request')
    this.name = 'StalePrdError'
  }
}

export function createPrdClient(fetcher: FetchLike = fetch) {
  let requestVersion = 0
  let activeController: AbortController | undefined

  const execute = async (
    url: string,
    body: PrdGenerateRequestBody,
    callbacks: PrdCallbacks = {},
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
      if (version !== requestVersion) throw new StalePrdError()
      return result
    } catch (error) {
      if (version !== requestVersion) throw new StalePrdError()
      throw error
    } finally {
      if (version === requestVersion) activeController = undefined
    }
  }

  return {
    generateAll: (body: PrdGenerateRequestBody, callbacks?: PrdCallbacks) =>
      execute('/api/generation/prd', body, callbacks),
    generateSection: (sectionId: string, body: PrdGenerateRequestBody, callbacks?: PrdCallbacks) =>
      execute(`/api/generation/prd/sections/${sectionId}`, body, callbacks),
    cancel: () => {
      requestVersion += 1
      activeController?.abort()
      activeController = undefined
    },
  }
}

export type PrdClient = ReturnType<typeof createPrdClient>
