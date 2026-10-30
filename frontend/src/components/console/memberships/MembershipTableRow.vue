<script setup lang="ts">
import type { Membership } from '@/client/types'
import UuidLabel from '@/components/UuidLabel.vue'

const props = defineProps<{
  membership: Membership
}>()

const emit = defineEmits<{
  remove: []
}>()
</script>

<template>
  <tr class="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
    <td class="px-4 py-3"><UuidLabel :id="membership.id" /></td>
    <td class="px-4 py-3">
      <div class="flex items-center gap-2">
        <span class="material-symbols-outlined text-slate-400">person</span>
        <div>
          <p class="text-sm font-semibold text-slate-800">{{ membership.account_email || 'Account' }}</p>
          <UuidLabel v-if="membership.account_id" :id="membership.account_id" />
        </div>
      </div>
    </td>
    <td class="px-4 py-3">
      <div class="flex items-center gap-2">
        <span class="material-symbols-outlined text-slate-400 text-base">apartment</span>
        <div>
          <p class="text-sm font-semibold text-slate-800">{{ membership.tenant_name || 'Unknown' }}</p>
        </div>
      </div>
    </td>
    <td class="px-4 py-3">
      <button
        @click="emit('remove')"
        class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-red-600 transition-colors"
      >
        <span class="material-symbols-outlined text-sm">person_remove</span>
      </button>
    </td>
  </tr>
</template>
