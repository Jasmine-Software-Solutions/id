<script setup lang="ts">
import { ref } from 'vue'
import { useFocus } from '@/composables/useFocus'

const { vAutoFocus } = useFocus()

defineProps<{ request?: string }>()

const emit = defineEmits<{
  submit: [response: Record<string, string>]
}>()

const totp = ref('')

function submit() {
  emit('submit', { totp: totp.value })
}
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm p-8">
    <form @submit.prevent="submit" class="space-y-4">
      <div>
        <label class="block mb-2 text-sm font-medium text-slate-700">Authenticator code</label>
        <input v-model="totp" type="text" inputmode="numeric" pattern="[0-9]*" required v-auto-focus
          class="bg-slate-50 border border-slate-300 rounded-lg w-full p-3 text-sm tracking-widest text-center text-lg"
          placeholder="000000" maxlength="6" />
      </div>
      <button type="submit" class="w-full bg-indigo-600 text-white py-2.5 rounded-lg font-bold hover:bg-indigo-500 transition-colors">
        Verify
      </button>
    </form>
  </div>
</template>
