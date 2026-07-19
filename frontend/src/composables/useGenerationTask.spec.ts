import { describe, expect, it } from 'vitest'
import { useGenerationTask } from './useGenerationTask'

describe('useGenerationTask', () => {
  it('begins with active=false and no error', () => {
    const task = useGenerationTask()
    expect(task.active.value).toBe(false)
    expect(task.error.value).toBeNull()
  })

  it('begin sets active and returns a signal with increasing version', () => {
    const task = useGenerationTask()

    const first = task.begin()
    expect(task.active.value).toBe(true)
    expect(first.version).toBe(1)
    expect(first.signal).toBeInstanceOf(AbortSignal)

    const second = task.begin()
    expect(second.version).toBe(2)
  })

  it('begin aborts the previous signal', () => {
    const task = useGenerationTask()

    const first = task.begin()
    expect(first.signal.aborted).toBe(false)

    task.begin()
    expect(first.signal.aborted).toBe(true)
  })

  it('isCurrentVersion returns true only for the latest version', () => {
    const task = useGenerationTask()

    const first = task.begin()
    const second = task.begin()

    expect(task.isCurrentVersion(first.version)).toBe(false)
    expect(task.isCurrentVersion(second.version)).toBe(true)
  })

  it('complete clears active state', () => {
    const task = useGenerationTask()
    task.begin()
    task.complete()
    expect(task.active.value).toBe(false)
  })

  it('fail records an error and clears active', () => {
    const task = useGenerationTask()
    task.begin()
    task.fail('连接超时')
    expect(task.active.value).toBe(false)
    expect(task.error.value).toBe('连接超时')
  })

  it('cancelAll invalidates all prior versions', () => {
    const task = useGenerationTask()
    const first = task.begin()
    task.cancelAll()

    expect(task.isCurrentVersion(first.version)).toBe(false)
    expect(task.active.value).toBe(false)
    expect(task.error.value).toBeNull()
  })

  it('cancelCurrent aborts the signal without bumping version', () => {
    const task = useGenerationTask()
    const first = task.begin()

    task.cancelCurrent()
    expect(first.signal.aborted).toBe(true)
    expect(task.active.value).toBe(false)
    // version stays the same; a new begin after cancelCurrent gives version+1
    const second = task.begin()
    expect(second.version).toBe(first.version + 1)
  })

  it('begin after error clears the error', () => {
    const task = useGenerationTask()
    task.begin()
    task.fail('失败了')
    expect(task.error.value).toBe('失败了')

    task.begin()
    expect(task.error.value).toBeNull()
  })
})
