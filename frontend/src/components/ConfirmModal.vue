<script setup lang="ts">
defineProps<{
  title: string
  confirmText?: string
}>()

const error = defineModel<string | null>('error')
const emit = defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="$emit('cancel')">
    <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm mx-4">
      <div class="flex items-center gap-3 mb-4">
        <span class="p-2 bg-red-50 text-red-600 rounded-lg material-symbols-outlined">warning</span>
        <h3 class="text-lg font-bold">{{ title }}</h3>
      </div>
      <slot />
      <div v-if="error" class="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700 mb-4">{{ error }}</div>
      <div class="flex gap-2" :class="{ 'mt-6': !$slots.default, 'mt-4': !!$slots.default }">
        <button @click="$emit('confirm')" class="flex-1 px-4 py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700">{{ confirmText || 'Delete' }}</button>
        <button @click="$emit('cancel')" class="flex-1 px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Cancel</button>
      </div>
    </div>
  </div>
</template>
