<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuth } from '@/composables/useAuth'
import type { Membership } from '@/client/types'

const { state } = useAuth()

const memberships = ref<Membership[]>([])

onMounted(async () => {
  if (!state.account) return
  try { memberships.value = await state.client.accounts.listMemberships('me') } catch { /* ignore */ }
})
</script>

<template>
  <section id="tenants" class="bg-white border border-slate-200 rounded-xl shadow-sm p-6">
    <div class="flex items-center gap-3 mb-6">
      <span class="p-2 bg-slate-50 text-slate-600 rounded-lg material-symbols-outlined">corporate_fare</span>
      <h3 class="text-lg font-bold">Memberships</h3>
    </div>

    <div v-if="memberships.length === 0" class="text-sm text-slate-400 py-4">No memberships found.</div>

    <div v-else class="space-y-2">
      <div
        v-for="m in memberships"
        :key="m.id"
        class="flex items-center justify-between p-4 bg-slate-50 rounded-lg border border-slate-100"
      >
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-slate-400">apartment</span>
          <div>
            <p class="font-semibold text-sm">{{ m.tenant_name || 'Unknown' }}</p>
            <p class="text-xs text-slate-400 font-mono">{{ m.tenant_id }}</p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
