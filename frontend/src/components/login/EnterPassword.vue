<script setup lang="ts">
import { ref } from 'vue'
import { useFocus } from '@/composables/useFocus'

const { vAutoFocus } = useFocus()

defineProps<{ request?: string }>()

const emit = defineEmits<{
  submit: [response: Record<string, string>]
}>()

const password = ref('')

function submit() {
  emit('submit', { password: password.value })
}
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm p-8">
    <form @submit.prevent="submit" class="space-y-4">
      <div>
        <label class="block mb-2 text-sm font-medium text-slate-700">Password</label>
        <input v-model="password" type="password" required v-auto-focus
          class="bg-slate-50 border border-slate-300 rounded-lg w-full p-3 text-sm" placeholder="Enter your password" />
      </div>
      <button type="submit" class="w-full bg-indigo-600 text-white py-2.5 rounded-lg font-bold hover:bg-indigo-500 transition-colors">
        Continue
      </button>
    </form>
  </div>
</template>
