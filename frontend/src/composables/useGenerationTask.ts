import { ref, onUnmounted } from 'vue'

/**
 * Composable providing unified abort controller and version-based stale
 * result protection for analysis, architecture, flowchart, and PRD generation.
 *
 * Each new operation increments an internal version counter and aborts the
 * previous AbortController. Callers inspect {@link isCurrentVersion} before
 * persisting results so late arrivals from cancelled predecessors are discarded.
 */
export function useGenerationTask() {
  const active = ref(false)
  const error = ref<string | null>(null)

  let version = 0
  let controller: AbortController | undefined

  /** Start a new task: bump version, abort predecessor, return fresh signal. */
  function begin(): { signal: AbortSignal; version: number } {
    cancelCurrent()
    version += 1
    controller = new AbortController()
    active.value = true
    error.value = null
    return { signal: controller.signal, version }
  }

  /** Returns true only for the most recently begun task. */
  function isCurrentVersion(taskVersion: number): boolean {
    return taskVersion === version
  }

  /** Mark the current task as finished (successfully). */
  function complete() {
    active.value = false
    controller = undefined
  }

  /** Record a non-abort error for the current task. */
  function fail(message: string) {
    error.value = message
    active.value = false
    controller = undefined
  }

  /** Abort the current request without bumping the version. */
  function cancelCurrent() {
    if (controller) {
      controller.abort()
      controller = undefined
    }
    active.value = false
  }

  /** Cancel the current task and invalidate all prior versions. */
  function cancelAll() {
    cancelCurrent()
    version += 1
    error.value = null
  }

  onUnmounted(() => {
    cancelCurrent()
  })

  return {
    active,
    error,
    begin,
    isCurrentVersion,
    complete,
    fail,
    cancelCurrent,
    cancelAll,
  }
}
