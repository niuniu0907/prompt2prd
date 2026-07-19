import { ref } from 'vue'

export interface Toast {
  id: number
  message: string
  type: 'success' | 'error' | 'info'
}

const toasts = ref<Toast[]>([])
let nextId = 0

export function useToast() {
  function show(message: string, type: Toast['type'] = 'success', duration = 2000) {
    const id = nextId++
    toasts.value = [...toasts.value, { id, message, type }]
    setTimeout(() => {
      toasts.value = toasts.value.filter(t => t.id !== id)
    }, duration)
  }

  function success(msg: string) { show(msg, 'success') }
  function error(msg: string) { show(msg, 'error', 3000) }
  function info(msg: string) { show(msg, 'info') }

  return { toasts, success, error, info }
}
