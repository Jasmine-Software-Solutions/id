import { ref } from 'vue'
import { createAuthClient } from '@/client'

const authClient = createAuthClient()

export function useFlow() {
  const flowId = ref<string | null>(null)
  const step = ref<string | null>(null)
  const request = ref<string | null>(null)
  const loading = ref(false)
  const error = ref(false)

  async function start() {
    loading.value = true
    const data = await authClient.start()
    flowId.value = data.flow_id
    step.value = data.step
    request.value = data.request
    loading.value = false
  }

  async function next(response: Record<string, string>) {
    loading.value = true
    const data = await authClient.next(flowId.value!, request.value!, response)
    loading.value = false

    if (data.done && !data.error) return { success: true as const }
    if (data.done && data.error) { error.value = true; step.value = null; return { success: false as const } }

    step.value = data.step
    request.value = data.request
    error.value = false
    return { success: false as const, next: true as const }
  }

  async function instead(stepFqdn: string) {
    loading.value = true
    const data = await authClient.instead(flowId.value!, stepFqdn)
    step.value = data.step
    request.value = data.request
    loading.value = false
  }

  function reset() {
    flowId.value = null
    step.value = null
    request.value = null
    error.value = false
    loading.value = false
  }

  return { flowId, step, request, loading, error, start, next, instead, reset }
}
