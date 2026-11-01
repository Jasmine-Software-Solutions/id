<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuth } from '@/composables/useAuth'
import type { Session } from '@/client/types'

const { state } = useAuth()

const sessions = ref<Session[]>([])

onMounted(async () => {
  if (!state.account) return
  try { sessions.value = await state.client.accounts.listSessions('me') } catch { /* ignore */ }
})

async function revoke(id: string) {
  try {
    await state.client.accounts.revokeSession('me', id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
  } catch { /* ignore */ }
}

async function revokeAll() {
  try {
    await state.client.accounts.revokeAllSessions('me')
    sessions.value = sessions.value.filter((s) => s.is_current)
  } catch { /* ignore */ }
}
</script>

<template>
  <section id="sessions" class="bg-white border border-slate-200 rounded-xl shadow-sm p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h3 class="text-lg font-bold">Active Sessions</h3>
        <p class="text-sm text-slate-500">Logged in devices and browser sessions</p>
      </div>
      <button @click="revokeAll" class="text-red-600 text-sm font-bold border border-red-200 px-4 py-2 rounded-lg hover:bg-red-50 transition-colors">Log out all sessions</button>
    </div>
    <div class="overflow-x-auto">
      <table class="w-full text-left">
        <thead class="border-b border-slate-100">
          <tr>
            <th class="pb-4 text-xs text-slate-500 uppercase tracking-wider px-2">Device</th>
            <th class="pb-4 text-xs text-slate-500 uppercase tracking-wider px-2">IP Address</th>
            <th class="pb-4 text-xs text-slate-500 uppercase tracking-wider px-2">Created</th>
            <th class="pb-4 text-xs text-slate-500 uppercase tracking-wider px-2">Expires</th>
            <th class="pb-4 text-xs text-slate-500 uppercase tracking-wider px-2">Status</th>
            <th class="pb-4 text-xs text-slate-500 uppercase tracking-wider px-2 text-right">Action</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-50">
          <tr v-for="s in sessions" :key="s.id">
            <td class="py-3 px-2 text-sm">{{ s.user_agent?.substring(0, 40) || 'Unknown' }}</td>
            <td class="py-3 px-2 text-sm font-mono text-slate-500">{{ s.ip_address || '-' }}</td>
            <td class="py-3 px-2 text-sm text-slate-500">{{ new Date(s.created_at * 1000).toLocaleString() }}</td>
            <td class="py-3 px-2 text-sm text-slate-500">{{ new Date(s.expires_at * 1000).toLocaleString() }}</td>
            <td class="py-3 px-2 text-sm">
              <span v-if="s.is_current" class="text-green-600 font-semibold">Current</span>
              <span v-else class="text-slate-400">Active</span>
            </td>
            <td class="py-3 px-2 text-right">
              <button v-if="!s.is_current" @click="revoke(s.id)" class="text-red-500 text-sm hover:text-red-700">Revoke</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
