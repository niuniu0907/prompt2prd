import { describe, expect, it, vi } from 'vitest'
import { createPrdClient } from './prdApi'

function fakeResponse(body: string, ok = true): Response {
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(body))
      controller.close()
    },
  })
  return { ok, body: stream, status: ok ? 200 : 500 } as Response
}

const sectionStarted = (requestId: string, eventId: number, sectionId: string, title: string) =>
  `data:${JSON.stringify({ requestId, eventId, type: 'section_started', data: { sectionId, title }, timestamp: '2026-07-17T00:00:00Z' })}\n\n`

const sectionDelta = (requestId: string, eventId: number, sectionId: string, delta: string) =>
  `data:${JSON.stringify({ requestId, eventId, type: 'section_delta', data: { sectionId, delta }, timestamp: '2026-07-17T00:00:00Z' })}\n\n`

const sectionCompleted = (requestId: string, eventId: number, sectionId: string) =>
  `data:${JSON.stringify({ requestId, eventId, type: 'section_completed', data: { sectionId, status: 'COMPLETED' }, timestamp: '2026-07-17T00:00:00Z' })}\n\n`

const generationCompleted = (requestId: string, eventId: number) =>
  `data:${JSON.stringify({ requestId, eventId, type: 'generation_completed', data: { nextStage: 'PRD_EDITING', finalState: { mode: 'FINAL', missingItems: [] } }, timestamp: '2026-07-17T00:00:00Z' })}\n\n`

describe('createPrdClient', () => {
  it('streams full PRD generation events', async () => {
    const requestId = '123e4567-e89b-42d3-a456-426614174000'
    const events: string[] = []
    const fetcher = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(fakeResponse(
        sectionStarted(requestId, 1, 'apis', '接口契约') +
        sectionDelta(requestId, 2, 'apis', '第一段') +
        sectionDelta(requestId, 3, 'apis', '第二段') +
        sectionCompleted(requestId, 4, 'apis') +
        generationCompleted(requestId, 5),
      )))

    const client = createPrdClient(fetcher)
    const result = await client.generateAll(
      { state: {}, missingInformation: [], modelSettings: {} },
      { onEvent: e => events.push(e.type) },
    )

    expect(result).toEqual({ mode: 'FINAL', missingItems: [] })
    expect(events).toEqual([
      'section_started', 'section_delta', 'section_delta',
      'section_completed', 'generation_completed',
    ])
  })

  it('cancels previous request on new generation', async () => {
    const requestId = '123e4567-e89b-42d3-a456-426614174000'
    let abortCount = 0
    const fetcher = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.signal) {
        const signal = init.signal as AbortSignal
        signal.addEventListener('abort', () => { abortCount++ })
      }
      return Promise.resolve(fakeResponse(
        sectionStarted(requestId, 1, 'apis', '接口') +
        generationCompleted(requestId, 2),
      ))
    })

    const client = createPrdClient(fetcher)
    const first = client.generateAll({ state: {}, missingInformation: [], modelSettings: {} })
    const second = client.generateAll({ state: {}, missingInformation: [], modelSettings: {} })
    await Promise.allSettled([first, second])
    expect(abortCount).toBeGreaterThanOrEqual(1)
  })

  it('generates single section by key', async () => {
    const requestId = '123e4567-e89b-42d3-a456-426614174000'
    const fetcher = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(fakeResponse(
        sectionStarted(requestId, 1, 'architecture', '架构') +
        sectionDelta(requestId, 2, 'architecture', 'Vue 3 + Spring Boot') +
        sectionCompleted(requestId, 3, 'architecture') +
        generationCompleted(requestId, 4),
      )))

    const client = createPrdClient(fetcher)
    const result = await client.generateSection('architecture',
      { state: {}, missingInformation: [], modelSettings: {} })

    expect(result).toBeDefined()
  })
})
