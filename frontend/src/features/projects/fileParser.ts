export const MAX_REQUIREMENT_FILE_BYTES = 2 * 1024 * 1024
export const MAX_REQUIREMENT_CHUNK_CHARACTERS = 32_000

export type RequirementFileErrorCode = 'TYPE' | 'SIZE' | 'ENCODING' | 'EMPTY'

export interface ParsedRequirementFile {
  name: string
  content: string
  chunks: string[]
}

export class RequirementFileError extends Error {
  constructor(
    public readonly code: RequirementFileErrorCode,
    message: string,
  ) {
    super(message)
    this.name = 'RequirementFileError'
  }
}

function codePointLength(value: string): number {
  return Array.from(value).length
}

function semanticBlocks(content: string): string[] {
  const lines = content.match(/[^\n]*(?:\n|$)/g)?.filter(Boolean) ?? []
  const blocks: string[] = []
  let paragraph = ''

  const flushParagraph = () => {
    if (!paragraph) return
    blocks.push(paragraph)
    paragraph = ''
  }

  for (const line of lines) {
    const lineContent = line.endsWith('\n') ? line.slice(0, -1) : line
    const isHeading = /^ {0,3}#{1,6}(?:[\t ]+|$)/.test(lineContent)
    const isBlank = lineContent.trim() === ''

    if (isHeading) flushParagraph()
    paragraph += line
    if (isHeading || isBlank) flushParagraph()
  }
  flushParagraph()
  return blocks
}

export function splitRequirementText(
  content: string,
  maxCharacters = MAX_REQUIREMENT_CHUNK_CHARACTERS,
): string[] {
  if (!Number.isInteger(maxCharacters) || maxCharacters <= 0) {
    throw new TypeError('maximum chunk size must be a positive integer')
  }
  if (!content) return []

  const chunks: string[] = []
  let current = ''

  const flushCurrent = () => {
    if (!current) return
    chunks.push(current)
    current = ''
  }

  for (const block of semanticBlocks(content)) {
    let remaining = Array.from(block)

    if (remaining.length > maxCharacters) {
      flushCurrent()
      while (remaining.length > maxCharacters) {
        chunks.push(remaining.slice(0, maxCharacters).join(''))
        remaining = remaining.slice(maxCharacters)
      }
    }

    const tail = remaining.join('')
    if (!tail) continue
    if (codePointLength(current) + remaining.length > maxCharacters) {
      flushCurrent()
    }
    current += tail
  }

  flushCurrent()
  return chunks
}

export async function parseRequirementFile(file: File): Promise<ParsedRequirementFile> {
  if (!/\.(?:md|txt)$/i.test(file.name)) {
    throw new RequirementFileError('TYPE', '只支持 Markdown 或 TXT 文件（.md、.txt）。')
  }
  if (file.size > MAX_REQUIREMENT_FILE_BYTES) {
    throw new RequirementFileError('SIZE', '文件不能超过 2 MB，且不会被截断后继续处理。')
  }

  let content: string
  try {
    const bytes = await file.arrayBuffer()
    content = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    throw new RequirementFileError('ENCODING', '文件无法按 UTF-8 解码，请转换编码后重试。')
  }

  content = content.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n')
  if (!content.trim()) {
    throw new RequirementFileError('EMPTY', '文件中没有可分析的有效文本。')
  }

  return {
    name: file.name,
    content,
    chunks: splitRequirementText(content),
  }
}

function abortError(): DOMException {
  return new DOMException('The operation was aborted.', 'AbortError')
}

export async function processChunksSequentially(
  chunks: readonly string[],
  handler: (chunk: string, index: number, total: number) => void | Promise<void>,
  signal?: AbortSignal,
): Promise<void> {
  for (const [index, chunk] of chunks.entries()) {
    if (signal?.aborted) throw abortError()
    await handler(chunk, index, chunks.length)
  }
  if (signal?.aborted) throw abortError()
}
