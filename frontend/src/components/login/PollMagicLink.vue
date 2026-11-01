<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const emit = defineEmits<{
  submit: [response: Record<string, string>]
  instead: [stepFqdn: string]
}>()

const clickedInstead = ref(false)

let interval: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  interval = setInterval(() => emit('submit', {}), 5000)
})
onUnmounted(() => clearInterval(interval))

function handleInstead() {
  if (clickedInstead.value) return
  clickedInstead.value = true
  clearInterval(interval)
  emit('instead', 'com.jasminesoftwaresolutions.id:enter_password')
}
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm p-8 text-center">
    <div class="mb-4">
      <span class="material-symbols-outlined text-indigo-600 text-5xl">mail</span>
    </div>
    <h3 class="text-lg font-bold mb-2">Check your email</h3>
    <p class="text-slate-500 text-sm">We sent a sign-in link to your email. Click it to sign in, then come back here — you'll be signed in automatically.</p>
    <button
      :disabled="clickedInstead"
      class="mt-4 text-sm text-indigo-600 hover:text-indigo-500 transition-colors cursor-pointer disabled:text-slate-400 disabled:cursor-not-allowed"
      @click="handleInstead"
    >Login with my password</button>
  </div>
</template>
