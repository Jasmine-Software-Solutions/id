<script setup lang="ts">
import { ref } from 'vue'
import { useFocus } from '@/composables/useFocus'

const { vAutoFocus } = useFocus()

defineProps<{ request?: string }>()

const emit = defineEmits<{
  submit: [response: Record<string, string>]
}>()

const firstName = ref('')
const lastName = ref('')

function submit() {
  emit('submit', { firstName: firstName.value, lastName: lastName.value })
}
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm p-8">
    <form @submit.prevent="submit" class="space-y-4">
      <p class="text-sm text-slate-500">Tell us who you are — this is how your name appears to others.</p>
      <div>
        <label class="block mb-2 text-sm font-medium text-slate-700">First name</label>
        <input v-model="firstName" type="text" required autocomplete="given-name" maxlength="40" v-auto-focus
          class="bg-slate-50 border border-slate-300 rounded-lg w-full p-3 text-sm" placeholder="Ada" />
      </div>
      <div>
        <label class="block mb-2 text-sm font-medium text-slate-700">Last name</label>
        <input v-model="lastName" type="text" required autocomplete="family-name" maxlength="40"
          class="bg-slate-50 border border-slate-300 rounded-lg w-full p-3 text-sm" placeholder="Lovelace" />
      </div>
      <button type="submit" class="w-full bg-indigo-600 text-white py-2.5 rounded-lg font-bold hover:bg-indigo-500 transition-colors">
        Continue
      </button>
    </form>
  </div>
</template>
