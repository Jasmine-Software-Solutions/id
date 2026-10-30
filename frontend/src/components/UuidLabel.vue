<script setup lang="ts">
defineProps<{ id: string }>()

function copyToClipboard(id: string) {
  navigator.clipboard.writeText(id)
}
</script>

<template>
  <button
    @click="copyToClipboard(id)"
    class="inline-flex items-center gap-1 text-xs text-slate-400 font-mono hover:text-indigo-600 transition-colors bg-transparent border-0 cursor-pointer p-0"
    title="Click to copy full ID"
  >
    <span>{{ id.slice(-8) }}</span>
    <span class="material-symbols-outlined text-[14px]">content_copy</span>
  </button>
</template>
