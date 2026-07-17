import type { PrdSection } from './types'

export function exportPrdMarkdown(sections: PrdSection[], projectName: string): string {
  const lines: string[] = []

  lines.push(`# ${projectName} - PRD`)
  lines.push('')
  lines.push('> 本文档由 Prompt2PRD 自动生成。')
  lines.push('')

  for (const section of sections) {
    if (section.status !== 'COMPLETED' && section.status !== 'DRAFT') continue
    if (!section.content.trim()) continue

    lines.push('---')
    lines.push('')
    lines.push(`## ${section.order}. ${section.title}`)
    lines.push('')
    lines.push(section.content.trim())
    lines.push('')
  }

  return lines.join('\n')
}

export function sanitizeFileName(name: string): string {
  return name
    .replace(/[<>:"/\\|?*\x00-\x1f]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 200)
    || 'untitled'
}

export function downloadPrdFile(content: string, fileName: string) {
  const safeName = sanitizeFileName(fileName)
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${safeName}-PRD.md`
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

export async function validatePrd(
  sections: PrdSection[],
  confirmedArchitectureId: string | null,
  fetcher: typeof fetch = fetch,
): Promise<{ valid: boolean; errors: string[]; warnings: string[] }> {
  const sectionsMap: Record<string, string> = {}
  for (const section of sections) {
    sectionsMap[section.sectionKey] = section.content
  }
  const response = await fetcher('/api/generation/prd/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sections: sectionsMap, confirmedArchitectureId }),
  })
  if (!response.ok) {
    let message = 'PRD 校验请求失败。'
    try {
      const body = await response.json() as { message?: unknown }
      if (typeof body.message === 'string' && body.message.trim()) message = body.message
    } catch {
      // Keep the safe local fallback.
    }
    throw new Error(message)
  }
  return response.json() as Promise<{ valid: boolean; errors: string[]; warnings: string[] }>
}

export interface PrdChangeAnalysisRequest {
  sectionKey: string
  oldContent: string
  newContent: string
  currentRequirements: unknown[]
}

export interface SyncedChange {
  requirementId: string
  requirementTitle: string
  field: string
  oldValue: string
  newValue: string
}

export interface PendingChange {
  field: string
  oldValue: string
  newValue: string
  reason: string
}

export interface ConflictWarning {
  requirementId: string
  requirementTitle: string
  field: string
  oldValue: string
  newValue: string
  reason: string
}

export interface PrdChangeReport {
  syncedChanges: SyncedChange[]
  pendingChanges: PendingChange[]
  conflictWarnings: ConflictWarning[]
  hasChanges: boolean
}

export async function analyzePrdChanges(
  body: PrdChangeAnalysisRequest,
  fetcher: typeof fetch = fetch,
): Promise<PrdChangeReport> {
  const response = await fetcher('/api/generation/prd/analyze-changes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    let message = 'PRD 变更分析失败。'
    try {
      const error = await response.json() as { message?: unknown }
      if (typeof error.message === 'string' && error.message.trim()) message = error.message
    } catch {
      // Keep the safe local fallback.
    }
    throw new Error(message)
  }
  return response.json() as Promise<PrdChangeReport>
}
