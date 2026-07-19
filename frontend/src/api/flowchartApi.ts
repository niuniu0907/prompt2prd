import type { FlowchartGenerationResult } from '@/features/flowchart/types'

export interface FlowchartRequestBody {
  state: unknown
  targetKey: string | null
  modelSettings: unknown
}

export async function generateFlowcharts(
  body: FlowchartRequestBody,
  fetcher: typeof fetch = fetch,
): Promise<FlowchartGenerationResult> {
  const response = await fetcher('/api/generation/flowchart', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  })
  if (!response.ok) {
    let message = '生成流程图失败。'
    try {
      const error = await response.json() as { message?: unknown }
      if (typeof error.message === 'string' && error.message.trim()) message = error.message
    } catch {
      // Keep the safe local message for non-JSON responses.
    }
    throw new Error(message)
  }
  return response.json() as Promise<FlowchartGenerationResult>
}
