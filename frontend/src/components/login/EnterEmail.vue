<script setup lang="ts">
import { computed, ref } from 'vue'

const emit = defineEmits<{
  submit: [response: Record<string, string>]
}>()

const props = defineProps<{
  disabled?: boolean
  /**
   * Why Identity is asking for an email address, when the product that sent
   * the person here supplied its own sentence — shown in place of the generic
   * one below.
   */
  hint?: string | null
}>()

const DEFAULT_PROSE =
  "New to Jasmine Software? Just enter your email address, and we'll get you started today."

/**
 * The reassurance above the field.
 *
 * A hint replaces the generic sentence rather than sitting above it: two
 * reassurances in a row is one too many, and the referring product knows which
 * tenant the person is applying to while this page does not. A hint that is
 * blank after trimming is no hint, so the generic copy stays.
 */
const prose = computed(() => props.hint?.trim() || DEFAULT_PROSE)

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
           shape the other steps use (`EnterName`). A referring product's hint
           (see `prose`) takes this sentence's place. -->
      <p class="text-sm text-slate-500">{{ prose }}</p>
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
