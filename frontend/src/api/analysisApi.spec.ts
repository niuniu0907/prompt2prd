import { describe, expect, it, vi } from 'vitest'

import { createAnalysisClient, StaleAnalysisError } from './analysisApi'

const requestId = '53be8d9c-8949-4476-8835-8f12080257ac'

describe('analysis API', () => {
  it('uses the initial and answer POST endpoints', async () => {
    const urls: string[] = []
    const fetcher = vi.fn(async (input: RequestInfo | URL) => {
      urls.push(String(input))
      return completedResponse()
    })
    const client = createAnalysisClient(fetcher)

    await client.analyze({ state: {}, input: '创建工作台', missingInformation: [], modelSettings: {} })
    await client.submitAnswers({ state: {}, answers: [{}], missingInformation: [], modelSettings: {} })

    expect(urls).toEqual(['/api/analysis', '/api/analysis/answers'])
  })

  it('does not deliver a superseded request result or late events', async () => {
    let finishOld!: (response: Response) => void
    const oldResponse = new Promise<Response>((resolve) => { finishOld = resolve })
    const fetcher = vi.fn()
      .mockImplementationOnce(() => oldResponse)
      .mockImplementationOnce(async () => completedResponse({ request: 'new' }))
    const event = vi.fn()
    const client = createAnalysisClient(fetcher)

    const old = client.analyze({ state: {}, input: 'old', missingInformation: [], modelSettings: {} }, { onEvent: event })
    await client.analyze({ state: {}, input: 'new', missingInformation: [], modelSettings: {} }, { onEvent: event })
    finishOld(completedResponse({ request: 'old' }))

    await expect(old).rejects.toBeInstanceOf(StaleAnalysisError)
    expect(event).toHaveBeenCalledTimes(1)
  })
})

function completedResponse(finalState: unknown = {}): Response {
  const data = JSON.stringify({
    requestId,
    eventId: 1,
    type: 'generation_completed',
    data: { nextStage: 'questions', finalState },
    timestamp: '2026-07-17T10:00:00Z',
  })
  return new Response(`event: generation_completed\nid: 1\ndata: ${data}\n\n`, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}
