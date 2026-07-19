import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import VersionHistory from './VersionHistory.vue'
import type { RequirementVersion } from '@/features/requirements/types'

describe('VersionHistory', () => {
  it('shows versions and requests a confirmed restore', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(VersionHistory, { props: { versions: [version()] } })
    expect(wrapper.text()).toContain('手动编辑：退款规则')
    await wrapper.get('[data-testid="restore-version"]').trigger('click')
    expect(wrapper.emitted('restore')?.[0]).toEqual([version().id])
  })
})

function version(): RequirementVersion {
  const project = { id: '10000000-0000-4000-8000-000000000000', name: '测试', originalPrompt: '创建测试项目', uploadedFileName: null, uploadedFileContent: null, supplementalPrompt: null, language: 'zh-CN', stage: 'CLARIFYING' as const, status: 'ACTIVE' as const, completeness: 40, userRenamed: false, archivedAt: null, deletedAt: null, createdAt: '2026-07-17T12:00:00.000Z', updatedAt: '2026-07-17T12:00:00.000Z' }
  return { id: '90000000-0000-4000-8000-000000000001', projectId: project.id, changeType: 'UPDATE', summary: '手动编辑：退款规则', snapshot: { project, requirements: [], questions: [], answers: [], conflicts: [] }, createdAt: '2026-07-17T12:01:00.000Z' }
}
