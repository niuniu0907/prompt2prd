import { describe, expect, it, vi } from 'vitest'
import type { PrdSection } from './types'
import type { PrdSectionStatus } from './types'
import { exportPrdMarkdown, sanitizeFileName, downloadPrdFile } from './prdExporter'

const projectId = '123e4567-e89b-42d3-a456-426614174000'
const now = '2026-07-17T00:00:00.000Z'

function section(key: string, order: number, content: string, status: PrdSectionStatus = 'COMPLETED'): PrdSection {
  return {
    id: crypto.randomUUID(), projectId, sectionKey: key as PrdSection['sectionKey'],
    title: `Section ${order}`, content, order, status, locked: false, errorCode: null,
    createdAt: now, updatedAt: now,
  }
}

describe('exportPrdMarkdown', () => {
  it('produces valid markdown with project name', () => {
    const sections = [
      section('coding-agent-guide', 1, '## 说明\n\nCoding agent 使用说明。'),
      section('product-context', 2, '产品定位：测试项目。'),
    ]
    const result = exportPrdMarkdown(sections, '测试项目')
    expect(result).toContain('# 测试项目 - PRD')
    expect(result).toContain('## 1. Section 1')
    expect(result).toContain('Coding agent 使用说明')
  })

  it('skips sections without content', () => {
    const sections = [
      section('coding-agent-guide', 1, ''),
      section('product-context', 2, '有内容'),
      section('roles-permissions', 3, '', 'DRAFT'),
    ]
    const result = exportPrdMarkdown(sections, 'Test')
    expect(result).toContain('有内容')
    expect(result).not.toContain('Section 1')
    expect(result).not.toContain('Section 3')
  })

  it('skips failed and generating sections', () => {
    const sections = [
      section('coding-agent-guide', 1, '章节1', 'COMPLETED'),
      section('product-context', 2, '章节2', 'FAILED'),
      section('roles-permissions', 3, '章节3', 'GENERATING'),
      section('features-priorities', 4, '章节4', 'DRAFT'),
    ]
    const result = exportPrdMarkdown(sections, 'Test')
    expect(result).toContain('章节1')
    expect(result).toContain('章节4')
    expect(result).not.toContain('章节2')
    expect(result).not.toContain('章节3')
  })
})

describe('sanitizeFileName', () => {
  it('removes illegal characters', () => {
    expect(sanitizeFileName('test:file<name>')).toBe('testfilename')
  })

  it('replaces spaces with hyphens', () => {
    expect(sanitizeFileName('my project name')).toBe('my-project-name')
  })

  it('trims leading and trailing hyphens', () => {
    expect(sanitizeFileName('-test-')).toBe('test')
  })

  it('returns untitled for empty result', () => {
    expect(sanitizeFileName('<>:"/\\|?*')).toBe('untitled')
  })

  it('truncates to 200 characters', () => {
    const long = 'a'.repeat(250)
    expect(sanitizeFileName(long).length).toBe(200)
  })
})

describe('downloadPrdFile', () => {
  it('creates and revokes download link', () => {
    const createObjectUrl = vi.fn(() => 'blob:test')
    const revokeObjectUrl = vi.fn()
    const appendChild = vi.fn()
    const removeChild = vi.fn()
    const click = vi.fn()

    vi.stubGlobal('URL', {
      createObjectURL: createObjectUrl,
      revokeObjectURL: revokeObjectUrl,
    })
    const origCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = origCreateElement(tag)
      if (tag === 'a') {
        Object.defineProperty(el, 'click', { value: click })
      }
      return el
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation(appendChild)
    vi.spyOn(document.body, 'removeChild').mockImplementation(removeChild)

    downloadPrdFile('# Test PRD', '我的项目')

    expect(createObjectUrl).toHaveBeenCalled()
    expect(appendChild).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
    expect(removeChild).toHaveBeenCalled()
    expect(revokeObjectUrl).toHaveBeenCalled()

    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })
})
