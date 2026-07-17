import mermaid from 'mermaid'

export interface MermaidValidationResult {
  valid: boolean
  diagramType: string | null
  message: string | null
}

export async function validateMermaid(source: string): Promise<MermaidValidationResult> {
  if (!source.trim()) return { valid: false, diagramType: null, message: 'Mermaid 源码不能为空。' }
  try {
    const result = await mermaid.parse(source, { suppressErrors: true })
    if (result === false) return { valid: false, diagramType: null, message: 'Mermaid 语法无效。' }
    return { valid: true, diagramType: result.diagramType, message: null }
  } catch {
    return { valid: false, diagramType: null, message: 'Mermaid 语法无效。' }
  }
}
