import { describe, expect, it } from 'vitest'

import { createProject, createTemporaryProjectName } from './types'

const projectId = '76deeeab-70cf-41af-92a2-24ff466ca1b1'
const now = '2026-07-17T07:00:00.000Z'

describe('createProject', () => {
  it('creates a local project with normalized persisted metadata', () => {
    const project = createProject({
      id: projectId,
      name: '本地需求项目',
      originalPrompt: '建立一个本地需求分析工具',
      now,
    })

    expect(project).toMatchObject({
      id: projectId,
      name: '本地需求项目',
      stage: 'CLARIFYING',
      status: 'ACTIVE',
      completeness: 0,
      archivedAt: null,
      deletedAt: null,
      createdAt: now,
      updatedAt: now,
    })
  })

  it('rejects malformed IDs, non-UTC timestamps, and invalid completeness', () => {
    expect(() =>
      createProject({ id: 'project-1', name: '项目', originalPrompt: '有效产品需求', now }),
    ).toThrow('UUID')
    expect(() =>
      createProject({
        id: projectId,
        name: '项目',
        originalPrompt: '有效产品需求',
        now: '2026-07-17T15:00:00+08:00',
      }),
    ).toThrow('UTC ISO-8601')
    expect(() =>
      createProject({
        id: projectId,
        name: '项目',
        originalPrompt: '有效产品需求',
        now,
        completeness: 101,
      }),
    ).toThrow('0 and 100')
  })

  it('creates a project from confirmed file content without text input', () => {
    const project = createProject({
      id: projectId,
      name: '文件需求项目',
      originalPrompt: '',
      uploadedFileName: 'requirements.md',
      uploadedFileContent: '# 有效需求\n实现本地需求分析工作台',
      now,
    })

    expect(project).toMatchObject({
      originalPrompt: '',
      uploadedFileName: 'requirements.md',
      uploadedFileContent: '# 有效需求\n实现本地需求分析工作台',
    })
  })

  it('rejects creation when both text and file content are blank', () => {
    expect(() =>
      createProject({
        id: projectId,
        name: '空项目',
        originalPrompt: '   ',
        uploadedFileContent: '\n ',
        now,
      }),
    ).toThrow('project source must not be blank')
  })

  it('rejects text-only creation with fewer than five Unicode characters', () => {
    expect(() =>
      createProject({
        id: projectId,
        name: '短输入项目',
        originalPrompt: '四个字',
        now,
      }),
    ).toThrow('at least 5 Unicode characters')
  })
})

describe('createTemporaryProjectName', () => {
  it('uses the first 20 Unicode characters of normalized source text', () => {
    expect(createTemporaryProjectName('  一二三四五六七八九十一二三四五六七八九十甲乙  ')).toBe(
      '一二三四五六七八九十一二三四五六七八九十',
    )
    expect(createTemporaryProjectName('😀😀😀😀😀产品想法')).toBe('😀😀😀😀😀产品想法')
  })

  it('rejects blank source text', () => {
    expect(() => createTemporaryProjectName('   ')).toThrow('temporary project name source')
  })
})
