<script setup lang="ts">
import { ref } from 'vue'
import { useAuth } from '@/composables/useAuth'

const { state: authState } = useAuth()

const createFirstName = ref('')
const createLastName = ref('')
const createEmail = ref('')
const error = ref<string | null>(null)

const emit = defineEmits<{
  created: []
  close: []
}>()

async function submit() {
  if (!createEmail.value.trim()) return
  error.value = null
  try {
    await authState.client.accounts.create({
      email: createEmail.value.trim(),
      first_name: createFirstName.value.trim(),
      last_name: createLastName.value.trim(),
    })
    emit('created')
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to create account'
  }
}

function close() {
  createFirstName.value = ''
  createLastName.value = ''
  createEmail.value = ''
  error.value = null
  emit('close')
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="close">
    <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md mx-4">
      <h3 class="text-lg font-bold mb-4">Create Account</h3>
      <div v-if="error" class="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700 mb-4">{{ error }}</div>
      <div class="space-y-4">
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Email</label>
          <input v-model="createEmail" type="email" placeholder="name@company.com" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">First name</label>
            <input v-model="createFirstName" type="text" placeholder="Jane" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">Last name</label>
            <input v-model="createLastName" type="text" placeholder="Doe" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          </div>
        </div>
      </div>
      <div class="flex gap-2 mt-6">
        <button @click="submit" class="flex-1 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">Create</button>
        <button @click="close" class="flex-1 px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Cancel</button>
      </div>
    </div>
  </div>
</template>
