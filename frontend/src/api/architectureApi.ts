import type { ArchitectureRecommendationResponse, TechnicalConstraints } from '@/features/architecture/types'

export type ArchitectureFetch = typeof fetch

export async function recommendArchitecture(
  constraints: TechnicalConstraints,
  fetcher: ArchitectureFetch = fetch,
): Promise<ArchitectureRecommendationResponse> {
  const response = await fetcher('/api/architecture/recommend', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(constraints),
  })
  if (!response.ok) {
    let message = '生成架构候选失败。'
    try {
      const body = await response.json() as { message?: unknown }
      if (typeof body.message === 'string' && body.message.trim()) message = body.message
    } catch {
      // Keep the safe local fallback when the server response is not JSON.
    }
    throw new Error(message)
  }
  return response.json() as Promise<ArchitectureRecommendationResponse>
}
