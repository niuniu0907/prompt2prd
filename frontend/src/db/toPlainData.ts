/**
 * Strips Vue reactivity proxies, getters, and non-cloneable values from an
 * object so it can be safely written to IndexedDB via Dexie.
 *
 * Prefers `structuredClone` (handles Proxy unwrapping natively in modern
 * browsers) with a `JSON.parse(JSON.stringify(...))` fallback for runtimes
 * where structuredClone still chokes on Proxies (e.g. older jsdom / fake-
 * indexeddb test environments).
 */
export function toPlainData<T>(value: T): T {
  try {
    return structuredClone(value)
  } catch {
    return JSON.parse(JSON.stringify(value)) as T
  }
}
