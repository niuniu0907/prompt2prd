import type { ModelKeySource, ModelProvider } from '@/stores/modelConfigStore'

export type { ModelProvider } from '@/stores/modelConfigStore'

export interface ModelCapabilities {
  systemKeyAvailable: boolean
}

export interface ModelConnectionInput {
  keySource: ModelKeySource
  provider: ModelProvider
  baseUrl?: string
  model: string
  apiKey?: string
  parameters?: Record<string, unknown>
}

export interface ModelConnectionResult {
  success: true
  keySource: ModelKeySource
  model: string
  latencyMs: number
}

export interface ModelConfigClient {
  getCapabilities(): Promise<ModelCapabilities>
  testConnection(input: ModelConnectionInput): Promise<ModelConnectionResult>
}

interface ApiErrorBody {
  code?: string
  message?: string
}

type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export class ModelConfigApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
  ) {
    super(message)
    this.name = 'ModelConfigApiError'
  }
}

export function createModelConfigClient(fetcher: FetchLike = fetch): ModelConfigClient {
  return {
    async getCapabilities() {
      const response = await fetcher('/api/model-config')
      return readJson<ModelCapabilities>(response)
    },
    async testConnection(input) {
      const response = await fetcher('/api/model-config/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      })
      return readJson<ModelConnectionResult>(response)
    },
  }
}

async function readJson<T>(response: Response): Promise<T> {
  const body = await response.json() as T | ApiErrorBody
  if (!response.ok) {
    const error = body as ApiErrorBody
    throw new ModelConfigApiError(
      error.code ?? 'INTERNAL_ERROR',
      error.message ?? '模型配置请求失败',
      response.status,
    )
  }
  return body as T
}

export const modelConfigClient = createModelConfigClient()
