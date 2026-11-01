<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const status = ref<'loading' | 'success' | 'already' | 'expired' | 'invalid' | 'error'>('loading')

onMounted(async () => {
  const token = route.query.token as string | undefined
  const changeId = route.query.changeId as string | undefined
  const accountId = route.query.accountId as string | undefined

  if (!token || !changeId || !accountId) {
    status.value = 'invalid'
    return
  }

  try {
    const url = `/api/v1/accounts/${accountId}/email-change/verify?changeId=${encodeURIComponent(changeId)}&token=${encodeURIComponent(token)}`
    // The API will 302 redirect to /#/?verified=1 on success
    window.location.href = url
  } catch {
    status.value = 'error'
  }
})
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center">
    <div class="w-full max-w-md bg-white rounded-xl shadow-sm p-8 text-center">
      <div v-if="status === 'loading'" class="text-slate-400">
        <span class="material-symbols-outlined animate-spin mb-3 block text-2xl">sync</span>
        Verifying your email address...
      </div>
      <div v-else class="text-red-600">
        <span class="material-symbols-outlined mb-3 block text-4xl">error</span>
        <p class="font-bold text-lg mb-1">Something went wrong</p>
        <p class="text-sm text-slate-500">Could not verify your email address.</p>
      </div>
    </div>
  </div>
</template>
