<script setup lang="ts">
import { ref, watch } from 'vue'
import EntitySelector from '@/components/EntitySelector.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import type { PlaceRestrictionPayload } from '@/client/types'

const emit = defineEmits<{
  submit: [payload: PlaceRestrictionPayload]
  close: []
}>()

defineProps<{
  error?: string | null
}>()

const formType = ref<'account_lock' | 'ip_block'>('account_lock')
const formTargetType = ref<'account' | 'ip'>('account')
const formTargetId = ref('')
const formEventId = ref('')
const formDuration = ref<number | null>(30)

watch(formTargetType, () => {
  formTargetId.value = ''
})

function handleSubmit() {
  const payload: PlaceRestrictionPayload = {
    type: formType.value,
    target_type: formTargetType.value,
    target_id: formTargetId.value,
    duration_minutes: formDuration.value ?? undefined,
  }
  if (formEventId.value.trim()) {
    payload.event_id = formEventId.value.trim()
  }
  emit('submit', payload)
}
</script>

<template>
  <div class="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
    <h2 class="text-lg font-bold text-slate-800 mb-4">Create Restriction</h2>

    <ErrorBanner v-if="error" :message="error" />

    <div class="space-y-4">
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1">Type</label>
        <select v-model="formType" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="account_lock">Account Lock</option>
          <option value="ip_block">IP Block</option>
        </select>
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1">Target Type</label>
        <select v-model="formTargetType" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="account">Account</option>
          <option value="ip">IP Address</option>
        </select>
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1">
          Target
          <span class="text-slate-400 font-normal ml-1">
            ({{ formTargetType === 'account' ? 'select an account' : 'enter an IP address' }})
          </span>
        </label>
        <EntitySelector
          v-if="formTargetType === 'account'"
          entity-type="account"
          v-model="formTargetId"
          placeholder="Select an account…"
        />
        <input
          v-else
          v-model="formTargetId"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono"
          placeholder="e.g. 192.168.1.1"
        />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1">
          Event ID
          <span class="text-slate-400 font-normal ml-1">(optional — the security event that triggered this restriction)</span>
        </label>
        <input
          v-model="formEventId"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono"
          placeholder="UUID of the security event (optional)"
        />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1">
          Duration
          <span class="text-slate-400 font-normal ml-1">(leave empty for permanent)</span>
        </label>
        <div class="flex items-center gap-2">
          <input
            v-model.number="formDuration"
            type="number"
            min="1"
            class="w-24 rounded-lg border border-slate-300 px-3 py-2 text-sm"
            placeholder="30"
          />
          <span class="text-sm text-slate-500">minutes</span>
        </div>
      </div>
    </div>

    <div class="flex justify-end gap-3 mt-6">
      <button
        @click="emit('close')"
        class="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors"
      >
        Cancel
      </button>
      <button
        @click="handleSubmit"
        class="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors"
      >
        Create
      </button>
    </div>
  </div>
</template>
