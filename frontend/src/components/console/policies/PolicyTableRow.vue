<script setup lang="ts">
import UuidLabel from '@/components/UuidLabel.vue'
import type { PolicyDefinition } from '@/client/types'

defineProps<{ policy: PolicyDefinition }>()
const emit = defineEmits<{
  edit: []
  delete: []
  assignments: []
  viewDocument: []
}>()

/** The scope a policy is attached at — `null` tenant means platform scope. */
function scopeType(policy: PolicyDefinition): 'platform' | 'tenant' {
  return policy.tenant_id ? 'tenant' : 'platform'
}

function scopeBadge(type: 'platform' | 'tenant') {
  return type === 'tenant' ? 'bg-amber-50 text-amber-700' : 'bg-blue-50 text-blue-700'
}

function scopeLabel(policy: PolicyDefinition): string {
  return policy.tenant_id ? '(Tenant policy)' : '(Platform policy)'
}
</script>

<template>
  <tr class="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
    <td class="px-4 py-3">
      <UuidLabel :id="policy.id" />
    </td>
    <td class="px-4 py-3">
      <p class="font-semibold text-sm text-slate-800">{{ policy.name }}</p>
      <p class="text-xs text-slate-400">
        {{ scopeLabel(policy) }}
        <UuidLabel v-if="policy.tenant_id" :id="policy.tenant_id" />
      </p>
    </td>
    <td class="px-4 py-3">
      <p class="text-sm text-slate-500 max-w-xs truncate">{{ policy.description || '—' }}</p>
    </td>
    <td class="px-4 py-3">
      <span class="text-xs px-2 py-0.5 rounded-full font-medium" :class="scopeBadge(scopeType(policy))">{{ scopeType(policy) }}</span>
    </td>
    <td class="px-4 py-3">
      <button @click="$emit('assignments')" class="text-indigo-600 hover:text-indigo-800 text-sm font-medium hover:underline">
        View assignments
      </button>
    </td>
    <td class="px-4 py-3">
      <button @click="$emit('viewDocument')" class="text-xs px-2 py-0.5 bg-slate-100 text-slate-600 rounded hover:bg-slate-200 transition-colors">
        {{ policy.document?.length ?? 0 }} statement{{ policy.document?.length !== 1 ? 's' : '' }}
      </button>
    </td>
    <td class="px-4 py-3">
      <div class="flex items-center gap-1">
        <button @click="$emit('edit')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-indigo-600 transition-colors" title="Edit">
          <span class="material-symbols-outlined text-sm">edit</span>
        </button>
        <button @click="$emit('delete')" class="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-red-600 transition-colors" title="Delete">
          <span class="material-symbols-outlined text-sm">delete</span>
        </button>
      </div>
    </td>
  </tr>
</template>
