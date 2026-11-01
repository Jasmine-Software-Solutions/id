<script setup lang="ts">
import UuidLabel from '@/components/UuidLabel.vue'
import type { AccountSummary } from '@/client'

defineProps<{ account: AccountSummary }>()
const emit = defineEmits<{
  edit: []
  disableTotp: []
  delete: []
}>()
</script>

<template>
  <tr class="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
    <td class="px-4 py-3">
      <UuidLabel :id="account.id" />
    </td>
    <td class="px-4 py-3">
      <p class="font-semibold text-sm text-slate-800">{{ account.first_name }} {{ account.last_name }}</p>
    </td>
    <td class="px-4 py-3">
      <p class="text-sm text-slate-600">{{ account.email }}</p>
    </td>
    <td class="px-4 py-3">
      <span v-if="account.totp_enabled" class="inline-flex items-center gap-1 text-xs font-medium text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full">
        <span class="material-symbols-outlined text-sm">shield_lock</span>Enabled
      </span>
      <span v-else class="text-xs text-slate-400">Disabled</span>
    </td>
    <td class="px-4 py-3">
      <div class="flex items-center gap-1">
        <button @click="$emit('edit')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-indigo-600 transition-colors" title="Edit profile">
          <span class="material-symbols-outlined text-sm">edit</span>
        </button>
        <button v-if="account.totp_enabled && account.membership_id" @click="$emit('disableTotp')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-red-600 transition-colors" title="Disable TOTP">
          <span class="material-symbols-outlined text-sm">shield_off</span>
        </button>
        <button @click="$emit('delete')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-red-600 transition-colors" title="Delete account">
          <span class="material-symbols-outlined text-sm">person_remove</span>
        </button>
      </div>
    </td>
  </tr>
</template>
