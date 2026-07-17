import { describe, expect, it, vi } from 'vitest'

import {
  MAX_REQUIREMENT_CHUNK_CHARACTERS,
  MAX_REQUIREMENT_FILE_BYTES,
  parseRequirementFile,
  processChunksSequentially,
  splitRequirementText,
} from './fileParser'

describe('parseRequirementFile', () => {
  it.each([
    ['requirements.md', '# 产品需求\r\n\r\n支持本地项目'],
    ['NOTES.TXT', '支持文件补充说明'],
  ])('parses a valid UTF-8 %s file', async (name, source) => {
    const result = await parseRequirementFile(new File([source], name))

    expect(result.name).toBe(name)
    expect(result.content).toBe(source.replace(/\r\n?/g, '\n'))
    expect(result.chunks.join('')).toBe(result.content)
  })

  it('removes a UTF-8 BOM without losing the remaining content', async () => {
    const result = await parseRequirementFile(
      new File([new Uint8Array([0xef, 0xbb, 0xbf]), '# 需求'], 'requirements.md'),
    )

    expect(result.content).toBe('# 需求')
  })

  it('accepts a non-empty file exactly at the 2 MB boundary', async () => {
    const source = 'a'.repeat(MAX_REQUIREMENT_FILE_BYTES)
    const result = await parseRequirementFile(new File([source], 'requirements.txt'))

    expect(result.content.length).toBe(MAX_REQUIREMENT_FILE_BYTES)
    expect(result.chunks.join('')).toBe(source)
  })

  it.each([
    [new File(['内容'], 'requirements.pdf'), 'TYPE', 'Markdown 或 TXT'],
    [new File(['   \n\t'], 'requirements.txt'), 'EMPTY', '有效文本'],
    [
      new File([new Uint8Array(MAX_REQUIREMENT_FILE_BYTES + 1)], 'requirements.md'),
      'SIZE',
      '2 MB',
    ],
    [
      new File([new Uint8Array([0xc3, 0x28])], 'requirements.txt'),
      'ENCODING',
      'UTF-8',
    ],
  ])('rejects invalid requirement files', async (file, code, message) => {
    await expect(parseRequirementFile(file)).rejects.toMatchObject({
      code,
      message: expect.stringContaining(message),
    })
  })
})

describe('splitRequirementText', () => {
  it('preserves all normalized text and prefers heading and paragraph boundaries', () => {
    const content = '# 标题\n第一段。\n\n## 子标题\n第二段。'
    const chunks = splitRequirementText(content, 10)

    expect(chunks.join('')).toBe(content)
    expect(chunks).toEqual(['# 标题\n', '第一段。\n\n', '## 子标题\n', '第二段。'])
  })

  it('hard-splits oversized blocks by Unicode code point without truncation', () => {
    const content = `# ${'😀'.repeat(MAX_REQUIREMENT_CHUNK_CHARACTERS + 5)}`
    const chunks = splitRequirementText(content)

    expect(chunks.join('')).toBe(content)
    expect(chunks.length).toBe(2)
    expect(chunks.every((chunk) => Array.from(chunk).length <= MAX_REQUIREMENT_CHUNK_CHARACTERS)).toBe(
      true,
    )
  })

  it('rejects an invalid maximum size', () => {
    expect(() => splitRequirementText('有效文本', 0)).toThrow('positive integer')
  })
})

describe('processChunksSequentially', () => {
  it('processes every chunk once and in order', async () => {
    const calls: Array<[string, number, number]> = []

    await processChunksSequentially(['一', '二', '三'], async (chunk, index, total) => {
      calls.push([chunk, index, total])
    })

    expect(calls).toEqual([
      ['一', 0, 3],
      ['二', 1, 3],
      ['三', 2, 3],
    ])
  })

  it('stops before a later chunk when cancellation is requested', async () => {
    const controller = new AbortController()
    const handler = vi.fn(async (_chunk: string, index: number) => {
      if (index === 0) controller.abort()
    })

    await expect(
      processChunksSequentially(['一', '二', '三'], handler, controller.signal),
    ).rejects.toMatchObject({ name: 'AbortError' })
    expect(handler).toHaveBeenCalledTimes(1)
  })
})
