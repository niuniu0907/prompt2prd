export interface QuotaSnapshot {
  systemKeyAvailable: boolean
  analysisRemaining: number
  fullPrdRemaining: number
  globalCallsRemaining: number
}

interface ApiErrorBody {
  code?: string
  message?: string
}

type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export class QuotaApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
  ) {
    super(message)
    this.name = 'QuotaApiError'
  }
}

export interface QuotaClient {
  getQuota(): Promise<QuotaSnapshot>
}

export function createQuotaClient(fetcher: FetchLike = fetch): QuotaClient {
  return {
    async getQuota() {
      const response = await fetcher('/api/quota')
      if (!response.ok) {
        let body: ApiErrorBody = {}
        try { body = await response.json() as ApiErrorBody } catch { /* keep fallback */ }
        throw new QuotaApiError(
          body.code ?? 'INTERNAL_ERROR',
          body.message ?? '无法读取当前额度信息',
          response.status,
        )
      }
      return response.json() as Promise<QuotaSnapshot>
    },
  }
}

export const quotaClient = createQuotaClient()
