<script setup lang="ts">
import { ref, watch } from 'vue'
import { useAuth } from '@/composables/useAuth'
import type { AccountSummary } from '@/client'

const { state: authState } = useAuth()

const account = defineModel<AccountSummary | null>()

const editFirstName = ref('')
const editLastName = ref('')
const editEmail = ref('')
const error = ref<string | null>(null)

const emit = defineEmits<{ saved: [] }>()

watch(account, (val) => {
  if (val) {
    editFirstName.value = val.first_name
    editLastName.value = val.last_name
    editEmail.value = val.email
    error.value = null
  }
})

async function submit() {
  if (!account.value) return
  error.value = null
  try {
    await authState.client.accounts.updateProfile(account.value.id, {
      first_name: editFirstName.value.trim() || undefined,
      last_name: editLastName.value.trim() || undefined,
    })
    account.value = null
    emit('saved')
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to update profile'
  }
}
</script>

<template>
  <div v-if="account" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="account = null">
    <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md mx-4">
      <h3 class="text-lg font-bold mb-4">Edit Account</h3>
      <div v-if="error" class="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700 mb-4">{{ error }}</div>
      <div class="space-y-4">
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">First name</label>
          <input v-model="editFirstName" type="text" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
        </div>
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Last name</label>
          <input v-model="editLastName" type="text" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
        </div>
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Email</label>
          <input v-model="editEmail" type="email" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
        </div>
      </div>
      <div class="flex gap-2 mt-6">
        <button @click="submit" class="flex-1 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">Save</button>
        <button @click="account = null" class="flex-1 px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Cancel</button>
      </div>
    </div>
  </div>
</template>
