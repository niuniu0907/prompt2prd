import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { ProjectSummary } from '@/db/repositories/projectRepository'
import ProjectCard from './ProjectCard.vue'

const summary: ProjectSummary = {
  project: {
    id: '76deeeab-70cf-41af-92a2-24ff466ca1b1',
    name: '离线需求工作台',
    originalPrompt: '建立一个离线需求工作台',
    uploadedFileName: null,
    uploadedFileContent: null,
    supplementalPrompt: null,
    language: 'zh-CN',
    stage: 'CLARIFYING',
    status: 'ACTIVE',
    completeness: 35,
    userRenamed: false,
    archivedAt: null,
    deletedAt: null,
    createdAt: '2026-07-17T08:00:00.000Z',
    updatedAt: '2026-07-17T08:10:00.000Z',
  },
  pendingCount: 3,
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ProjectCard', () => {
  it('shows project metadata and keeps active actions inside a menu', async () => {
    const wrapper = mount(ProjectCard, { props: { summary, view: 'ACTIVE' } })

    expect(wrapper.text()).toContain('离线需求工作台')
    expect(wrapper.text()).toContain('需求澄清')
    expect(wrapper.text()).toContain('35%')
    expect(wrapper.text()).toContain('3 项待确认')
    expect(wrapper.get('progress').attributes('value')).toBe('35')

    const menu = wrapper.get('details')
    expect(menu.text()).toContain('重命名')
    expect(menu.text()).toContain('复制项目')
    expect(menu.text()).toContain('归档项目')
    expect(menu.text()).toContain('移入回收站')

    await menu.find('[data-action="rename"]').trigger('click')
    await wrapper.get('input').setValue('新的项目名称')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('rename')).toEqual([[summary.project.id, '新的项目名称']])
  })

  it('offers restore and confirmed permanent deletion in the recycle bin', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const deletedSummary: ProjectSummary = {
      ...summary,
      project: { ...summary.project, status: 'DELETED', deletedAt: summary.project.updatedAt },
    }
    const wrapper = mount(ProjectCard, { props: { summary: deletedSummary, view: 'DELETED' } })

    expect(wrapper.get('details').text()).toContain('恢复项目')
    expect(wrapper.get('details').text()).toContain('永久删除')
    await wrapper.get('[data-action="permanent-delete"]').trigger('click')

    expect(confirm).toHaveBeenCalledWith('永久删除“离线需求工作台”？此操作无法撤销。')
    expect(wrapper.emitted('permanentDelete')).toEqual([[summary.project.id]])
  })
})
