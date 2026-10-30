<script setup lang="ts">
import UuidLabel from '@/components/UuidLabel.vue'

defineProps<{
  tenant: { id: string; name: string; suspended: boolean }
}>()

const emit = defineEmits<{
  edit: []
  delete: []
}>()
</script>

<template>
  <tr class="border-b border-slate-50 hover:bg-slate-50/50">
    <td class="px-4 py-3"><UuidLabel :id="tenant.id" /></td>
    <td class="px-4 py-3 font-semibold text-sm text-slate-800">{{ tenant.name }}</td>
    <td class="px-4 py-3">
      <span v-if="tenant.suspended" class="text-xs px-2 py-0.5 bg-red-50 text-red-700 rounded-full font-medium">Suspended</span>
      <span v-else class="text-xs px-2 py-0.5 bg-emerald-50 text-emerald-700 rounded-full font-medium">Active</span>
    </td>
    <td class="px-4 py-3">
      <div class="flex items-center gap-1">
        <button @click="$emit('edit')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-indigo-600">
          <span class="material-symbols-outlined text-sm">edit</span>
        </button>
        <button @click="$emit('delete')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-red-600">
          <span class="material-symbols-outlined text-sm">delete</span>
        </button>
      </div>
    </td>
  </tr>
</template>
