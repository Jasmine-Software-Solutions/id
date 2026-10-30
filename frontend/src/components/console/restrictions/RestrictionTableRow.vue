<script setup lang="ts">
import { computed } from 'vue'
import UuidLabel from '@/components/UuidLabel.vue'
import type { RestrictionResponse } from '@/client/types'

const props = defineProps<{
  restriction: RestrictionResponse
}>()

const emit = defineEmits<{
  lift: []
}>()

function targetLabel(): string {
  return props.restriction.account_email ?? props.restriction.target_id
}

const typeBadge = computed(() =>
  props.restriction.type === 'account_lock'
    ? 'bg-red-50 text-red-700'
    : 'bg-amber-50 text-amber-700',
)

const typeLabel = computed(() =>
  props.restriction.type === 'account_lock' ? 'Account Lock' : 'IP Block',
)

function expiresLabel(expiresAt: number | null): string {
  if (expiresAt === null) return 'Permanent'
  const remaining = expiresAt * 1000 - Date.now()
  if (remaining <= 0) return 'Expired'
  const mins = Math.ceil(remaining / 60000)
  if (mins < 60) return `${mins}m remaining`
  const hours = Math.ceil(mins / 60)
  if (hours < 24) return `${hours}h remaining`
  return `${Math.ceil(hours / 24)}d remaining`
}

function expiresClass(expiresAt: number | null): string {
  if (expiresAt === null) return 'text-red-600 bg-red-50'
  if (expiresAt * 1000 < Date.now()) return 'text-slate-400 bg-slate-50'
  return 'text-amber-600 bg-amber-50'
}
</script>

<template>
  <tr class="border-b border-slate-100 hover:bg-slate-50 transition-colors">
    <td class="px-4 py-3">
      <UuidLabel :id="restriction.id" />
    </td>
    <td class="px-4 py-3">
      <span class="inline-block px-2.5 py-1 rounded-full text-xs font-semibold" :class="typeBadge">
        {{ typeLabel }}
      </span>
    </td>
    <td class="px-4 py-3">
      <div class="flex flex-col">
        <span class="text-sm text-slate-700">{{ targetLabel() }}</span>
        <UuidLabel :id="restriction.target_id" />
      </div>
    </td>
    <td class="px-4 py-3">
      <UuidLabel v-if="restriction.event_id" :id="restriction.event_id" />
      <span v-else class="text-xs text-slate-400">&mdash;</span>
    </td>
    <td class="px-4 py-3">
      <span
        class="inline-block px-2.5 py-1 rounded-full text-xs font-semibold"
        :class="expiresClass(restriction.expires_at)"
      >
        {{ expiresLabel(restriction.expires_at) }}
      </span>
    </td>
    <td class="px-4 py-3 text-right">
      <button
        @click="emit('lift')"
        class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors"
      >
        <span class="material-symbols-outlined text-sm">lock_open</span>
        Lift
      </button>
    </td>
  </tr>
</template>
