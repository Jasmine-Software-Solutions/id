<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  title: string
  initialName?: string
  placeholder?: string
  showSuspended?: boolean
  initialSuspended?: boolean
  error?: string | null
}>()

const name = ref(props.initialName ?? '')
const suspended = ref(props.initialSuspended ?? false)

watch(() => props.initialName, (v) => { name.value = v ?? '' })
watch(() => props.initialSuspended, (v) => { suspended.value = v ?? false })

const emit = defineEmits<{
  save: [name: string, suspended: boolean]
  cancel: []
}>()
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="$emit('cancel')">
    <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md mx-4">
      <h3 class="text-lg font-bold mb-4">{{ title }}</h3>
      <div class="space-y-4">
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Name</label>
          <input v-model="name" type="text" :placeholder="placeholder" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
        </div>
        <div v-if="showSuspended" class="flex items-center gap-2">
          <input v-model="suspended" type="checkbox" id="nmf-sus" class="rounded" />
          <label for="nmf-sus" class="text-sm text-slate-600">Suspended</label>
        </div>
        <p v-if="error" class="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{{ error }}</p>
      </div>
      <div class="flex gap-2 mt-6">
        <button @click="$emit('save', name.trim(), suspended)" class="flex-1 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">Save</button>
        <button @click="$emit('cancel')" class="flex-1 px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Cancel</button>
      </div>
    </div>
  </div>
</template>
