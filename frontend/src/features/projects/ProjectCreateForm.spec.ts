import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProjectCreateForm from './ProjectCreateForm.vue'

describe('ProjectCreateForm', () => {
  it('rejects text shorter than five Unicode characters', async () => {
    const wrapper = mount(ProjectCreateForm)

    await wrapper.get('textarea').setValue('四个字')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toContain('至少输入 5 个字符')
    expect(wrapper.emitted('create')).toBeUndefined()
  })

  it('emits normalized project input and a 20-character temporary name', async () => {
    const wrapper = mount(ProjectCreateForm)
    const prompt = '  一二三四五六七八九十一二三四五六七八九十甲乙产品需求  '

    await wrapper.get('textarea').setValue(prompt)
    expect(wrapper.get('[data-testid="temporary-name"]').text()).toContain(
      '一二三四五六七八九十一二三四五六七八九十',
    )
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('create')).toEqual([
      [
        expect.objectContaining({
          name: '一二三四五六七八九十一二三四五六七八九十',
          originalPrompt: prompt.trim(),
          uploadedFileName: null,
          uploadedFileContent: null,
          supplementalPrompt: null,
        }),
      ],
    ])
  })

  it('allows confirmed file content to bypass the text minimum and keeps supplemental text', async () => {
    const wrapper = mount(ProjectCreateForm, {
      props: {
        fileInput: {
          name: 'requirements.md',
          content: '# 文件需求\n建立本地优先产品',
          chunks: ['# 文件需求\n建立本地优先产品'],
        },
      },
    })

    await wrapper.get('textarea').setValue('补充')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('create')).toEqual([
      [
        expect.objectContaining({
          name: '补充',
          originalPrompt: '补充',
          uploadedFileName: 'requirements.md',
          uploadedFileContent: '# 文件需求\n建立本地优先产品',
          supplementalPrompt: '补充',
        }),
      ],
    ])
  })

  it('uses confirmed file content for the name when supplemental text is blank', async () => {
    const wrapper = mount(ProjectCreateForm, {
      props: {
        fileInput: {
          name: 'requirements.txt',
          content: '  文件中的有效产品需求  ',
          chunks: ['  文件中的有效产品需求  '],
        },
      },
    })

    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('create')?.[0]?.[0]).toMatchObject({
      name: '文件中的有效产品需求',
      originalPrompt: '',
      supplementalPrompt: null,
    })
  })

  it('disables editing and submission while creation is in progress', () => {
    const wrapper = mount(ProjectCreateForm, { props: { busy: true } })

    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').text()).toContain('正在创建')
  })
})
