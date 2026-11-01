<script setup lang="ts">
import UuidLabel from '@/components/UuidLabel.vue'

defineProps<{
  	client: { id: string; fqdn: string; confidential: boolean; suspended: boolean; created_at: number; redirect_uris: string[] }
}>()

const emit = defineEmits<{
  edit: []
  resetSecret: []
  toggleSuspend: []
  delete: []
}>()

function formatDate(epoch: number): string {
  return new Date(epoch * 1000).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
  })
}
</script>

<template>
  <tr class="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
    <td class="px-4 py-3">
      <UuidLabel :id="client.id" />
    </td>
    <td class="px-4 py-3">
      <div class="flex items-center gap-2">
        <span class="material-symbols-outlined text-slate-400">dns</span>
        <span class="font-semibold text-sm text-slate-800">{{ client.fqdn }}</span>
      </div>
    </td>
    <td class="px-4 py-3">
      <span v-if="client.confidential" class="text-xs px-2 py-0.5 bg-blue-50 text-blue-700 rounded-full font-medium">Confidential</span>
      <span v-else class="text-xs px-2 py-0.5 bg-amber-50 text-amber-700 rounded-full font-medium">Public</span>
    </td>
    <td class="px-4 py-3">
      <span v-if="client.suspended" class="text-xs px-2 py-0.5 bg-red-50 text-red-700 rounded-full font-medium">Suspended</span>
      <span v-else class="text-xs px-2 py-0.5 bg-emerald-50 text-emerald-700 rounded-full font-medium">Active</span>
    </td>
    <td class="px-4 py-3">
      <span class="text-sm text-slate-500">{{ formatDate(client.created_at) }}</span>
    </td>
    <td class="px-4 py-3">
      <div class="flex items-center gap-1">
        <button @click="emit('edit')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-indigo-600 transition-colors" title="Edit">
          <span class="material-symbols-outlined text-sm">edit</span>
        </button>
        <button @click="emit('resetSecret')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-amber-600 transition-colors disabled:opacity-50" title="Reset secret">
          <span class="material-symbols-outlined text-sm">key</span>
        </button>
        <button @click="emit('toggleSuspend')"
          class="p-1.5 rounded hover:bg-slate-100 transition-colors" :title="client.suspended ? 'Unsuspend' : 'Suspend'"
          :class="client.suspended ? 'text-amber-400 hover:text-amber-600' : 'text-slate-400 hover:text-amber-600'">
          <span class="material-symbols-outlined text-sm">{{ client.suspended ? 'play_arrow' : 'pause' }}</span>
        </button>
        <button @click="emit('delete')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-red-600 transition-colors" title="Delete">
          <span class="material-symbols-outlined text-sm">delete</span>
        </button>
      </div>
    </td>
  </tr>
</template>
