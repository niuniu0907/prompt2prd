import { describe, expect, it, vi } from 'vitest'

import {
  ModelConfigApiError,
  createModelConfigClient,
  type ModelConnectionInput,
} from './modelConfigApi'

describe('modelConfigApi', () => {
  it('sends the exact selected configuration and reads the safe success result', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({
      success: true,
      keySource: 'USER',
      model: 'qwen-plus',
      latencyMs: 24,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    const client = createModelConfigClient(fetcher)
    const input: ModelConnectionInput = {
      keySource: 'USER',
      provider: 'QWEN',
      model: 'qwen-plus',
      apiKey: 'sk-runtime-request',
      parameters: { temperature: 0.4 },
    }

    await expect(client.testConnection(input)).resolves.toEqual({
      success: true,
      keySource: 'USER',
      model: 'qwen-plus',
      latencyMs: 24,
    })
    expect(fetcher).toHaveBeenCalledWith('/api/model-config/test', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(input),
    }))
  })

  it('preserves the backend error category without exposing request credentials', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({
      code: 'UNAUTHORIZED',
      message: 'Model service authentication failed. Verify the selected API key.',
      requestId: '9a72a2d4-b54e-4814-8706-0329964e967c',
      timestamp: '2026-07-17T12:00:00Z',
    }), { status: 502, headers: { 'Content-Type': 'application/json' } }))
    const client = createModelConfigClient(fetcher)

    const failure = await client.testConnection({
      keySource: 'USER',
      provider: 'OPENAI',
      model: 'gpt-test',
      apiKey: 'sk-never-in-error',
    }).catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(ModelConfigApiError)
    expect(failure).toMatchObject({ code: 'UNAUTHORIZED' })
    expect(String(failure)).not.toContain('sk-never-in-error')
  })
})
