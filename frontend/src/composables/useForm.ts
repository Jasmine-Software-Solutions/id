import { ref } from 'vue'

export function useForm(onSuccess: () => Promise<void> | void) {
  const open = ref(false)
  const error = ref<string | null>(null)
  const submitting = ref(false)

  function show() {
    error.value = null
    submitting.value = false
    open.value = true
  }

  function close() {
    open.value = false
    error.value = null
  }

  async function submit(fn: () => Promise<unknown>): Promise<unknown> {
    error.value = null
    submitting.value = true
    try {
      const result = await fn()
      open.value = false
      await onSuccess()
      return result
    } catch (e: unknown) {
      // Handle various error shapes thrown by different API clients
      const err = e as Record<string, unknown>
      const msg =
        (typeof err?.message === 'string' && err.message) ||
        (typeof err?.statusText === 'string' && err.statusText) ||
        (err?.status ? `Request failed (${err.status})` : null) ||
        'Operation failed'
      error.value = msg
      return undefined
    } finally {
      submitting.value = false
    }
  }

  return { open, error, submitting, show, close, submit }
}
