import { describe, expect, it, vi } from 'vitest'

import {
  AnalysisStreamError,
  consumePostSse,
} from './sseClient'

const requestId = '53be8d9c-8949-4476-8835-8f12080257ac'

describe('POST SSE client', () => {
  it('parses fragmented frames and returns final state only after completion', async () => {
    const first = frame(1, 'analysis_progress', { progress: 25, message: '分析中' })
    const final = frame(2, 'generation_completed', {
      nextStage: 'questions',
      finalState: { project: { id: 'project-1' } },
    })
    const events: string[] = []

    const result = await consumePostSse({
      url: '/api/analysis',
      body: { input: 'test' },
      fetcher: async () => streamResponse([
        first.slice(0, 13),
        first.slice(13) + final.slice(0, 7),
        final.slice(7),
      ]),
      onEvent: event => events.push(event.type),
    })

    expect(events).toEqual(['analysis_progress', 'generation_completed'])
    expect(result).toEqual({ project: { id: 'project-1' } })
  })

  it('ignores duplicate and late events for the bound request', async () => {
    const warning = vi.fn()
    const events: number[] = []

    await consumePostSse({
      url: '/api/analysis',
      body: {},
      fetcher: async () => streamResponse([
        frame(1, 'analysis_started', { phase: 'analysis' }),
        frame(1, 'analysis_started', { phase: 'analysis' }),
        frame(2, 'analysis_progress', { progress: 20, message: '继续' }, crypto.randomUUID()),
        frame(2, 'generation_completed', { nextStage: 'questions', finalState: {} }),
      ]),
      onEvent: event => events.push(event.eventId),
      onWarning: warning,
    })

    expect(events).toEqual([1, 2])
    expect(warning).toHaveBeenCalledTimes(2)
  })

  it('rejects gaps, malformed known events, and disconnects without a terminal event', async () => {
    await expect(consumePostSse({
      url: '/api/analysis',
      body: {},
      fetcher: async () => streamResponse([
        frame(1, 'analysis_started', { phase: 'analysis' }),
        frame(3, 'analysis_progress', { progress: 50, message: 'gap' }),
      ]),
    })).rejects.toThrow(/out of order/)

    await expect(consumePostSse({
      url: '/api/analysis',
      body: {},
      fetcher: async () => streamResponse([
        frame(1, 'analysis_progress', { progress: 20 }),
      ]),
    })).rejects.toThrow(/message/)

    await expect(consumePostSse({
      url: '/api/analysis',
      body: {},
      fetcher: async () => streamResponse([
        frame(1, 'analysis_started', { phase: 'analysis' }),
      ]),
    })).rejects.toBeInstanceOf(AnalysisStreamError)
  })

  it('warns and ignores unknown events while preserving event ordering', async () => {
    const warning = vi.fn()
    await expect(consumePostSse({
      url: '/api/analysis',
      body: {},
      fetcher: async () => streamResponse([
        frame(1, 'future_event', { value: true }),
        frame(2, 'generation_completed', { nextStage: 'questions', finalState: { ok: true } }),
      ]),
      onWarning: warning,
    })).resolves.toEqual({ ok: true })
    expect(warning).toHaveBeenCalledWith(expect.stringContaining('future_event'))
  })

  it('maps generation failure codes to actionable Chinese messages', async () => {
    await expect(consumePostSse({
      url: '/api/analysis',
      body: {},
      fetcher: async () => streamResponse([
        frame(1, 'generation_failed', {
          errorCode: 'MODEL_AUTHENTICATION_FAILED',
          retryable: false,
        }),
      ]),
    })).rejects.toMatchObject({
      code: 'MODEL_AUTHENTICATION_FAILED',
      message: '模型 API Key 验证失败，请检查 Key 是否正确或是否已过期。',
      retryable: false,
    })
  })

  it('reads structured non-stream API errors instead of hiding them behind HTTP status', async () => {
    await expect(consumePostSse({
      url: '/api/analysis/answers',
      body: {},
      fetcher: async () => new Response(JSON.stringify({
        code: 'BAD_REQUEST',
        message: 'Request parameters are invalid. Check required fields and value formats.',
        requestId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        timestamp: '2026-07-18T07:40:00Z',
      }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    })).rejects.toMatchObject({
      code: 'BAD_REQUEST',
      message: expect.stringContaining('请求参数无效'),
      retryable: false,
    })
  })
})

function frame(
  eventId: number,
  type: string,
  data: Record<string, unknown>,
  id = requestId,
): string {
  return `event: ${type}\nid: ${eventId}\ndata: ${JSON.stringify({
    requestId: id,
    eventId,
    type,
    data,
    timestamp: '2026-07-17T10:00:00Z',
  })}\n\n`
}

function streamResponse(chunks: string[]): Response {
  const encoder = new TextEncoder()
  return new Response(new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  }), {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}
