<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  submit: [response: Record<string, string>]
}>()

const props = defineProps({
  disabled: Boolean
})

const email = ref('')

function submit() {
  emit('submit', { email: email.value })
}
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm p-8">
    <form @submit.prevent="submit" class="space-y-4">
      <!-- The email box is where a person with no account arrives, and this is the
           step that starts one — so the reassurance belongs here and not in the
           page header, which every later step also renders. Prose first is the
           shape the other steps use (`EnterName`). -->
      <p class="text-sm text-slate-500">
        New to Jasmine Software? Just enter your email address, and we'll get you started today.
      </p>
      <div>
        <label class="block mb-2 text-sm font-medium text-slate-700">Email address</label>
        <input v-model="email" type="email" required autofocus :disabled="disabled"
          class="bg-slate-50 border border-slate-300 rounded-lg w-full p-3 text-sm" placeholder="name@company.com" />
      </div>
      <button type="submit" class="w-full bg-indigo-600 text-white py-2.5 rounded-lg font-bold hover:bg-indigo-500 transition-colors">
        Continue
      </button>
    </form>
  </div>
</template>
