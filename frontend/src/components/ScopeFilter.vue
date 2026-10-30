<script setup lang="ts">
import { ref, watch } from 'vue'
import EntitySelector from './EntitySelector.vue'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface ScopeValue {
  type: 'platform' | 'tenant'
  tenantId: string
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

const props = withDefaults(defineProps<{
  /** v-model value (for form usage) */
  modelValue?: ScopeValue
  /** Show the filter bar wrapper (for filter bar usage at top of list pages) */
  filterBar?: boolean
  /** Label for the select / filter heading */
  label?: string
  /** Hide the Platform option from the type dropdown (use when only showing Tenant) */
  hidePlatform?: boolean
}>(), {
  modelValue: () => ({ type: 'platform', tenantId: '' }),
  filterBar: false,
  label: 'Where',
  hidePlatform: false,
})

// ---------------------------------------------------------------------------
// Emits
// ---------------------------------------------------------------------------

const emit = defineEmits<{
  'update:modelValue': [value: ScopeValue]
  /** Convenience emit for filter bar usage */
  change: [value: ScopeValue]
}>()

// ---------------------------------------------------------------------------
// State derived from v-model
// ---------------------------------------------------------------------------

const type = ref<'platform' | 'tenant'>(props.modelValue.type)
const tenantId = ref(props.modelValue.tenantId)

// ---------------------------------------------------------------------------
// Emit helpers
// ---------------------------------------------------------------------------

function buildValue(): ScopeValue {
  // In filter-bar mode the type is implied by whether a tenant is picked.
  return {
    type: props.filterBar ? (tenantId.value ? 'tenant' : 'platform') : type.value,
    tenantId: tenantId.value,
  }
}

function emitValue() {
  const v = buildValue()
  emit('update:modelValue', v)
  emit('change', v)
}

// ---------------------------------------------------------------------------
// Cascade clearing
// ---------------------------------------------------------------------------

function onTypeChange() {
  if (type.value === 'platform') {
    tenantId.value = ''
  }
  emitValue()
}

watch(tenantId, () => {
  emitValue()
})

// Sync from external v-model changes
watch(() => props.modelValue, (v) => {
  type.value = v.type
  tenantId.value = v.tenantId
})

// Initial emit
emitValue()
</script>

<template>
  <!-- Filter bar mode: horizontal layout with a label and Scope display -->
  <div v-if="filterBar" class="bg-white border border-slate-200 rounded-xl shadow-sm p-4">
    <div class="flex items-end gap-4 flex-wrap">
      <div class="flex-1 min-w-48">
        <label class="block text-xs font-medium text-slate-600 mb-1">Tenant</label>
        <EntitySelector entity-type="tenant" v-model="tenantId" placeholder="All tenants…" />
      </div>
    </div>
  </div>

  <!-- Form mode: vertical layout with a type dropdown -->
  <div v-else class="space-y-3">
    <div>
      <label class="block text-xs font-medium text-slate-600 mb-1">{{ label }}</label>
      <select v-model="type" @change="onTypeChange" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm">
        <option v-if="!hidePlatform" value="platform">Platform</option>
        <option value="tenant">Tenant</option>
      </select>
    </div>
    <div v-if="type !== 'platform'">
      <label class="block text-xs font-medium text-slate-600 mb-1">Tenant</label>
      <EntitySelector entity-type="tenant" v-model="tenantId" placeholder="Select tenant…" />
    </div>
  </div>
</template>
