<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { createAuthClient } from '@/client'

const route = useRoute()
const authClient = createAuthClient()
const status = ref<'loading' | 'confirm' | 'approving' | 'success' | 'already' | 'expired' | 'invalid' | 'error'>('loading')

onMounted(() => {
  const token = route.query.token as string | undefined
  if (!token) { status.value = 'invalid'; return }
  status.value = 'confirm'
})

async function approve() {
  const token = route.query.token as string
  const id = route.params.id as string
  status.value = 'approving'
  try {
    const ok = await authClient.resolveMagicLink(id, token)
    if (ok) {
      status.value = 'success'
    } else {
      status.value = 'invalid'
    }
  } catch {
    status.value = 'error'
  }
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center">
    <div class="w-full max-w-md bg-white rounded-xl shadow-sm p-8 text-center">

      <div v-if="status === 'loading'" class="text-slate-400">
        <span class="material-symbols-outlined animate-spin mb-3 block text-2xl">sync</span>
        Loading...
      </div>

      <div v-else-if="status === 'confirm'" class="space-y-4">
        <span class="material-symbols-outlined text-indigo-600 text-5xl block">login</span>
        <h3 class="text-lg font-bold text-slate-800">Approve sign-on</h3>
        <p class="text-sm text-slate-500">Someone is attempting to sign in to your account. If this was you, click below to approve. If this was not you, you do not need to do anything; your account is safe.</p>
        <button @click="approve" class="w-full px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">Approve sign-on</button>
      </div>

      <div v-else-if="status === 'approving'" class="text-slate-400">
        <span class="material-symbols-outlined animate-spin mb-3 block text-2xl">sync</span>
        Approving...
      </div>

      <div v-else-if="status === 'success'" class="text-green-600">
        <span class="material-symbols-outlined mb-3 block text-4xl">check_circle</span>
        <p class="font-bold text-lg mb-1">Sign-in approved!</p>
        <p class="text-sm text-slate-500">You may close this page and return to the sign-in tab.</p>
      </div>

      <div v-else class="text-red-600">
        <span class="material-symbols-outlined mb-3 block text-4xl">error</span>
        <p class="font-bold text-lg mb-1">Invalid sign-in link</p>
        <p class="text-sm text-slate-500">This link is invalid or has already been used.</p>
      </div>

    </div>
  </div>
</template>
