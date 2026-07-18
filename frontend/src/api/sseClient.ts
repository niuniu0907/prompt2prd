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
    const error = await readHttpError(response)
    throw new AnalysisStreamError(error.message, error.code, false)
  }
  if (!response.body) {
    throw new AnalysisStreamError('生成响应不包含流式内容。')
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
      const code = String(event.data.errorCode)
      throw new AnalysisStreamError(
        analysisFailureMessage(code),
        code,
        Boolean(event.data.retryable),
      )
    } else if (event.type === 'generation_aborted') {
      terminalReceived = true
      throw new AnalysisStreamError(
        `本次生成已停止：${String(event.data.reason)}`,
        'GENERATION_ABORTED',
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
    throw new AnalysisStreamError('生成流在完成前中断，请重试。')
  }
  return terminalResult
}

async function readHttpError(response: Response): Promise<{ code: string; message: string }> {
  const fallback = {
    code: `HTTP_${response.status}`,
    message: `生成请求失败（HTTP ${response.status}），请检查模型设置或稍后重试。`,
  }
  try {
    const contentType = response.headers.get('Content-Type') ?? ''
    if (!contentType.includes('application/json')) return fallback
    const payload = await response.json() as Partial<{ code: unknown; message: unknown; requestId: unknown }>
    const code = typeof payload.code === 'string' && payload.code ? payload.code : fallback.code
    const requestId = typeof payload.requestId === 'string' && payload.requestId ? ` 请求 ID：${payload.requestId}` : ''
    if (code === 'BAD_REQUEST') {
      return {
        code,
        message: `请求参数无效，请检查模型设置和当前项目数据后重试。${requestId}`,
      }
    }
    const upstreamMessage = typeof payload.message === 'string' && payload.message ? payload.message : fallback.message
    return {
      code,
      message: `${upstreamMessage}${requestId}`,
    }
  } catch {
    return fallback
  }
}

function analysisFailureMessage(code: string): string {
  const messages: Record<string, string> = {
    INVALID_CONFIGURATION: '模型配置无效，请先检查服务商、Base URL、模型名称和 Key 来源。',
    MODEL_UNREACHABLE: '无法连接到当前模型服务，请检查 Base URL、网络或本地模型服务是否启动。',
    MODEL_AUTHENTICATION_FAILED: '模型 API Key 验证失败，请检查 Key 是否正确或是否已过期。',
    MODEL_NOT_FOUND: '当前模型不存在或该 Key 无权使用，请在模型设置中重新选择模型。',
    MODEL_RATE_LIMITED: '模型服务暂时限流，请稍后重试或切换到自己的 Key。',
    MODEL_FORMAT_INCOMPATIBLE: '模型返回格式不符合当前任务要求，请重试或换一个兼容性更稳定的模型。',
    MODEL_TIMEOUT: '模型生成超时，请稍后重试，或减少输入内容后重新生成。',
    MODEL_CANCELLED: '本次生成已停止。',
    MODEL_INTERNAL_ERROR: '模型服务内部错误，请稍后重试或切换模型服务。',
    ANALYSIS_OUTPUT_INVALID: '模型返回的需求分析结果不完整，系统没有保存这次结果，请重试或切换模型。',
    ANALYSIS_FAILED: 'AI 初始需求分析失败，问题向导还不能开始。请检查模型设置后重新分析。',
    PRD_GENERATION_FAILED: 'PRD 生成失败，已保存内容未改变。请检查模型设置后重试。',
    SECTION_GENERATION_FAILED: '当前 PRD 章节生成失败，可稍后重新生成该章节。',
  }
  return messages[code] ?? `AI 生成失败（${code}），请检查模型设置后重试。`
}
