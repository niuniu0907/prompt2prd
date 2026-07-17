import {
  parseStreamEvent,
  type KnownStreamEvent,
} from './streamEvents'

export type FetchLike = (
  input: RequestInfo | URL,
  init?: RequestInit,
) => Promise<Response>

export interface PostSseOptions {
  url: string
  body: unknown
  signal?: AbortSignal
  fetcher?: FetchLike
  onEvent?: (event: KnownStreamEvent) => void
  onWarning?: (message: string) => void
}

export class AnalysisStreamError extends Error {
  constructor(
    message: string,
    readonly code = 'ANALYSIS_STREAM_ERROR',
    readonly retryable = true,
  ) {
    super(message)
    this.name = 'AnalysisStreamError'
  }
}

export async function consumePostSse(options: PostSseOptions): Promise<unknown> {
  const fetcher = options.fetcher ?? fetch
  const response = await fetcher(options.url, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(options.body),
    signal: options.signal,
  })

  if (!response.ok) {
    throw new AnalysisStreamError(`Analysis request failed with HTTP ${response.status}`)
  }
  if (!response.body) {
    throw new AnalysisStreamError('Analysis response does not contain a stream')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let boundRequestId: string | undefined
  let lastEventId = 0
  let terminalResult: unknown
  let terminalReceived = false

  const processFrame = (frame: string) => {
    const dataLines = frame
      .split('\n')
      .filter(line => line.startsWith('data:'))
      .map(line => line.slice(5).trimStart())
    if (dataLines.length === 0) return

    let payload: unknown
    try {
      payload = JSON.parse(dataLines.join('\n'))
    } catch (error) {
      throw new AnalysisStreamError(
        `Invalid SSE JSON: ${error instanceof Error ? error.message : String(error)}`,
        'MALFORMED_EVENT',
        false,
      )
    }
    const event = parseStreamEvent(payload)

    if (boundRequestId && event.requestId !== boundRequestId) {
      options.onWarning?.(`Ignored event for stale request ${event.requestId}`)
      return
    }
    boundRequestId ??= event.requestId

    if (event.eventId <= lastEventId) {
      options.onWarning?.(`Ignored duplicate or late event ${event.eventId}`)
      return
    }
    if (event.eventId !== lastEventId + 1) {
      throw new AnalysisStreamError(
        `Stream event ${event.eventId} is out of order; expected ${lastEventId + 1}`,
        'OUT_OF_ORDER_EVENT',
        false,
      )
    }
    lastEventId = event.eventId

    if (!event.known) {
      options.onWarning?.(`Ignored unknown stream event ${event.type}`)
      return
    }

    options.onEvent?.(event)
    if (event.type === 'generation_completed') {
      terminalReceived = true
      terminalResult = event.data.finalState
    } else if (event.type === 'generation_failed') {
      terminalReceived = true
      throw new AnalysisStreamError(
        `Analysis failed: ${String(event.data.errorCode)}`,
        String(event.data.errorCode),
        Boolean(event.data.retryable),
      )
    } else if (event.type === 'generation_aborted') {
      terminalReceived = true
      throw new AnalysisStreamError(
        `Analysis was aborted: ${String(event.data.reason)}`,
        'ANALYSIS_ABORTED',
        true,
      )
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      processFrame(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
    if (done) break
  }

  if (buffer.trim()) processFrame(buffer)
  if (!terminalReceived) {
    throw new AnalysisStreamError('Analysis stream disconnected before a terminal event')
  }
  return terminalResult
}
