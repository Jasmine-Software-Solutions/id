<script setup lang="ts">
const error = defineModel<string | null>('error')
</script>

<template>
  <div v-if="error" class="p-4 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700 flex items-center gap-2">
    <span class="material-symbols-outlined">error</span>{{ error }}
    <button @click="error = null" class="ml-auto material-symbols-outlined text-red-400 hover:text-red-600">close</button>
  </div>
</template>
