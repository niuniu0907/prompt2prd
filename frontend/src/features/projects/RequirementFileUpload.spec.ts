import 'fake-indexeddb/auto'

import { flushPromises, mount } from '@vue/test-utils'
import Dexie from 'dexie'
import { describe, expect, it, vi } from 'vitest'

import { createAppDatabase } from '@/db/appDatabase'
import RequirementFileUpload from './RequirementFileUpload.vue'
import {
  createUploadPrivacySetting,
  type UploadPrivacySetting,
} from './requirementFileUpload'

function privacySetting(accepted: boolean): UploadPrivacySetting {
  return {
    isAccepted: vi.fn(async () => accepted),
    accept: vi.fn(async () => undefined),
  }
}

async function selectFile(wrapper: ReturnType<typeof mount>, file: File) {
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
  await flushPromises()
}

describe('RequirementFileUpload', () => {
  it('stores only the accepted privacy flag in the existing app_setting table', async () => {
    const database = createAppDatabase(`upload-privacy-${crypto.randomUUID()}`)
    const setting = createUploadPrivacySetting(database)

    await expect(setting.isAccepted()).resolves.toBe(false)
    await setting.accept()

    await expect(database.app_setting.toArray()).resolves.toEqual([
      expect.objectContaining({
        key: 'uploadPrivacyNoticeAccepted',
        value: true,
        updatedAt: expect.stringMatching(/Z$/),
      }),
    ])
    await expect(setting.isAccepted()).resolves.toBe(true)

    database.close()
    await Dexie.delete(database.name)
  })

  it('requires and persists the privacy acknowledgement before the first preview', async () => {
    const setting = privacySetting(false)
    const wrapper = mount(RequirementFileUpload, { props: { privacySetting: setting } })

    await selectFile(wrapper, new File(['# 本地需求'], 'requirements.md'))

    expect(wrapper.get('[role="dialog"]').text()).toContain(
      '文件内容将发送给用户选择的大模型服务商进行分析，请勿上传密码、密钥等敏感信息。',
    )
    expect(wrapper.find('[data-testid="file-preview"]').exists()).toBe(false)

    await wrapper.get('[data-testid="accept-privacy"]').trigger('click')
    await flushPromises()

    expect(setting.accept).toHaveBeenCalledOnce()
    expect(wrapper.get('textarea[readonly]').element).toHaveProperty('value', '# 本地需求')
  })

  it('does not repeat an accepted privacy dialog and always shows the short reminder', async () => {
    const wrapper = mount(RequirementFileUpload, {
      props: { privacySetting: privacySetting(true) },
    })

    expect(wrapper.get('[data-testid="privacy-reminder"]').text()).toContain('请勿上传密码')
    await selectFile(wrapper, new File(['已有需求正文'], 'requirements.txt'))

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.get('textarea[readonly]').element).toHaveProperty('value', '已有需求正文')
  })

  it('shows a clear parser error and does not preview an invalid file', async () => {
    const wrapper = mount(RequirementFileUpload, {
      props: { privacySetting: privacySetting(true) },
    })

    await selectFile(wrapper, new File(['不是文本扩展名'], 'requirements.pdf'))

    expect(wrapper.get('[role="alert"]').text()).toContain('Markdown 或 TXT')
    expect(wrapper.find('[data-testid="file-preview"]').exists()).toBe(false)
  })

  it('keeps script-like Markdown as inert textarea text', async () => {
    const wrapper = mount(RequirementFileUpload, {
      props: { privacySetting: privacySetting(true) },
    })
    const source = '<script>window.compromised = true</script>'

    await selectFile(wrapper, new File([source], 'requirements.md'))

    expect(wrapper.get('textarea[readonly]').element).toHaveProperty('value', source)
    expect(wrapper.find('.file-preview script').exists()).toBe(false)
  })

  it('processes all chunks in sequence, reports progress, and emits the full confirmed file', async () => {
    const processed: number[] = []
    const processChunk = vi.fn(async (_chunk: string, index: number) => {
      processed.push(index)
    })
    const source = '😀'.repeat(32_001)
    const wrapper = mount(RequirementFileUpload, {
      props: { privacySetting: privacySetting(true), processChunk },
    })
    await selectFile(wrapper, new File([source], 'requirements.md'))

    await wrapper.get('[data-testid="confirm-file"]').trigger('click')
    await flushPromises()

    expect(processed).toEqual([0, 1])
    expect(wrapper.get('[data-testid="chunk-progress"]').text()).toContain('2 / 2')
    expect(wrapper.emitted('confirmed')?.[0]?.[0]).toMatchObject({
      name: 'requirements.md',
      content: source,
      chunks: expect.arrayContaining([expect.any(String)]),
    })
  })

  it('cancels between chunks without emitting a confirmed file', async () => {
    let releaseFirstChunk!: () => void
    const firstChunk = new Promise<void>((resolve) => {
      releaseFirstChunk = resolve
    })
    const processChunk = vi.fn(async (_chunk: string, index: number) => {
      if (index === 0) await firstChunk
    })
    const wrapper = mount(RequirementFileUpload, {
      props: { privacySetting: privacySetting(true), processChunk },
    })
    await selectFile(wrapper, new File(['字'.repeat(32_001)], 'requirements.txt'))

    await wrapper.get('[data-testid="confirm-file"]').trigger('click')
    expect(wrapper.find('[data-testid="cancel-processing"]').exists()).toBe(true)
    await wrapper.get('[data-testid="cancel-processing"]').trigger('click')
    releaseFirstChunk()
    await flushPromises()

    expect(processChunk).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('confirmed')).toBeUndefined()
    expect(wrapper.get('[data-testid="chunk-progress"]').text()).toContain('已取消')
    expect(wrapper.get('[data-testid="confirm-file"]').attributes('disabled')).toBeUndefined()
  })

  it('clears the selected file and emits a cleared event', async () => {
    const wrapper = mount(RequirementFileUpload, {
      props: { privacySetting: privacySetting(true) },
    })
    await selectFile(wrapper, new File(['有效需求'], 'requirements.txt'))

    await wrapper.get('[data-testid="clear-file"]').trigger('click')

    expect(wrapper.find('[data-testid="file-preview"]').exists()).toBe(false)
    expect(wrapper.emitted('cleared')).toEqual([[]])
  })

  it('clears a previously confirmed parent value when replacement selection begins', async () => {
    const wrapper = mount(RequirementFileUpload, {
      props: { privacySetting: privacySetting(true) },
    })
    await selectFile(wrapper, new File(['第一份需求'], 'first.txt'))
    await wrapper.get('[data-testid="confirm-file"]').trigger('click')
    await flushPromises()

    await selectFile(wrapper, new File(['第二份需求'], 'second.txt'))

    expect(wrapper.emitted('cleared')).toEqual([[]])
    expect(wrapper.get('textarea[readonly]').element).toHaveProperty('value', '第二份需求')
  })
})
