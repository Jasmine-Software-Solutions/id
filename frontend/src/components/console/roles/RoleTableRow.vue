<script setup lang="ts">
import type { RoleDefinition } from '@/client/types'
import UuidLabel from '@/components/UuidLabel.vue'

defineProps<{
  role: RoleDefinition
}>()

const emit = defineEmits<{
  edit: []
  delete: []
  assignments: []
}>()

/** The scope a role is attached at — `null` tenant means platform scope. */
function scopeType(role: RoleDefinition): 'platform' | 'tenant' {
  return role.tenant_id ? 'tenant' : 'platform'
}

function scopeBadge(type: 'platform' | 'tenant') {
  return type === 'tenant' ? 'bg-amber-50 text-amber-700' : 'bg-blue-50 text-blue-700'
}
</script>

<template>
  <tr class="border-b border-slate-100 hover:bg-slate-50 transition-colors">
    <!-- ID -->
    <td class="px-4 py-3">
      <UuidLabel :id="role.id" />
    </td>
    <!-- Name -->
    <td class="px-4 py-3">
      <p class="font-semibold text-sm text-slate-800">{{ role.name }}</p>
    </td>
    <!-- Description -->
    <td class="px-4 py-3">
      <p class="text-sm text-slate-500">
        {{ role.description || '' }}
      </p>
    </td>
    <!-- Scope badge -->
    <td class="px-4 py-3">
      <span class="text-xs px-2 py-0.5 rounded-full font-medium" :class="scopeBadge(scopeType(role))">
        {{ scopeType(role) }}
      </span>
    </td>
    <!-- Attached to count (clickable) -->
    <td class="px-4 py-3">
      <button
        @click="emit('assignments')"
        class="text-sm font-medium text-indigo-600 hover:text-indigo-800 hover:underline transition-colors"
      >
        View assignments
      </button>
    </td>
    <!-- Created -->
    <td class="px-4 py-3">
      <span class="text-sm text-slate-400">—</span>
    </td>
    <!-- Actions -->
    <td class="px-4 py-3">
      <div class="flex items-center gap-1">
        <button @click="emit('edit')" class="p-1.5 rounded-lg text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 transition-colors" title="Edit">
          <span class="material-symbols-outlined text-sm">edit</span>
        </button>
        <button @click="emit('delete')" class="p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors" title="Delete">
          <span class="material-symbols-outlined text-sm">delete</span>
        </button>
      </div>
    </td>
  </tr>
</template>
