<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface EntityOption {
  id: string
  name: string
  fqdn?: string
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

const props = withDefaults(defineProps<{
  /** The type of entity to select. */
  entityType: 'account' | 'client' | 'role' | 'scope' | 'tenant'
  /** The model value (selected UUID string). */
  modelValue: string
  /** Placeholder text when nothing is selected. */
  placeholder?: string
  /** If true, show the full ID in the dropdown along with the name. */
  showFullId?: boolean
}>(), {
  placeholder: 'Select…',
  showFullId: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

const options = ref<EntityOption[]>([])
const search = ref('')
const open = ref(false)
const root = ref<HTMLElement | null>(null)

// Determine the endpoint
const endpoint = computed(() => {
  switch (props.entityType) {
    case 'account':    return '/api/v1/entities/accounts'
    case 'client':     return '/api/v1/entities/clients'
    case 'role':       return '/api/v1/entities/roles'
    case 'tenant':     return '/api/v1/tenants'
    case 'scope':      return null
  }
})

// ---------------------------------------------------------------------------
// Selected label
// ---------------------------------------------------------------------------

const label = computed(() => {
  const sel = options.value.find(o => o.id === props.modelValue)
  if (!sel) return ''
  const suffix = props.showFullId ? ` (${sel.id})` : ` (…${sel.id.slice(-8)})`
  return `${sel.name ?? sel.fqdn}${suffix}`
})

// ---------------------------------------------------------------------------
// Fetch
// ---------------------------------------------------------------------------

async function fetchOptions() {
  if (!endpoint.value) {
    options.value = []
    return
  }
  try {
    const r = await fetch(endpoint.value)
    if (!r.ok) return
    options.value = await r.json() as EntityOption[]
  } catch { /* ignore */ }
}

// Re-fetch when endpoint changes
watch(endpoint, fetchOptions)

onMounted(fetchOptions)

function onBlur(e: FocusEvent) {
  // Only close if focus has moved entirely outside this component.
  // This prevents the dropdown from closing when clicking the filter input
  // or an option button (focus moves from trigger -> input/option).
  const related = e.relatedTarget as HTMLElement | null
  if (related && root.value?.contains(related)) return
  open.value = false
}
</script>

<template>
  <div ref="root" class="relative">
    <!-- Trigger -->
    <button
      type="button"
      @click="open = !open"
      @blur="onBlur"
      class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm text-left flex items-center justify-between bg-white hover:border-slate-400 transition-colors"
      :class="{ 'ring-2 ring-indigo-200 border-indigo-400': open }"
    >
      <span v-if="modelValue" class="truncate pr-4 text-slate-700">{{ label }}</span>
      <span v-else class="truncate pr-4 text-slate-400">{{ placeholder }}</span>
      <span class="material-symbols-outlined text-slate-400 text-base shrink-0" :class="{ 'rotate-180': open }">expand_more</span>
    </button>

    <!-- Dropdown -->
    <div
      v-if="open"
      class="absolute z-30 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg max-h-64 overflow-hidden flex flex-col"
    >
      <!-- Search -->
      <div class="p-2 border-b border-slate-100">
        <input
          v-model="search"
          type="text"
          :placeholder="`Filter ${entityType}s…`"
          class="w-full px-2 py-1.5 text-sm border border-slate-200 rounded-md focus:outline-none focus:border-indigo-400"
          @keydown.escape="open = false"
        />
      </div>

      <!-- Options -->
      <div class="overflow-y-auto flex-1">
        <!-- Always show a clear/reset option matching the placeholder -->
        <button
          @mousedown.prevent="emit('update:modelValue', ''); open = false; search = ''"
          :class="[
            'w-full text-left px-3 py-2 text-sm transition-colors',
            !modelValue ? 'bg-indigo-50 text-indigo-500 font-medium' : 'text-slate-400 hover:bg-slate-50 italic'
          ]"
        >
          {{ placeholder }}
        </button>

        <button
          v-for="o in options.filter(t => !search || t.name.toLowerCase().includes(search.toLowerCase()) || t.id.includes(search))"
          :key="o.id"
          @mousedown.prevent="emit('update:modelValue', o.id); open = false; search = ''"
          :class="[
            'w-full text-left px-3 py-2 text-sm transition-colors',
            modelValue === o.id ? 'bg-indigo-50 text-indigo-700' : 'hover:bg-slate-50 text-slate-700'
          ]"
        >
          <span class="font-medium">{{ o.name ?? o.fqdn }}</span>
          <span class="text-xs text-slate-400 ml-2">…{{ o.id.slice(-8) }}</span>
        </button>

        <div
          v-if="options.filter(t => !search || t.name.toLowerCase().includes(search.toLowerCase()) || t.id.includes(search)).length === 0"
          class="px-3 py-4 text-sm text-slate-400 text-center"
        >
          No {{ entityType }}s found.
        </div>
      </div>
    </div>
  </div>
</template>
