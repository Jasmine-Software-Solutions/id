<script setup lang="ts">
import { ref } from 'vue'
import { useAuth } from '@/composables/useAuth'
import ConfirmModal from '../../ConfirmModal.vue'
import type { AccountSummary } from '@/client'

const { state: authState } = useAuth()

const account = defineModel<AccountSummary | null>()
const error = ref<string | null>(null)

const emit = defineEmits<{ deleted: [] }>()

async function confirm() {
  if (!account.value) return
  error.value = null
  try {
    await authState.client.accounts.delete(account.value.id)
    account.value = null
    emit('deleted')
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to delete account'
  }
}
</script>

<template>
  <ConfirmModal
    v-if="account"
    title="Delete Account"
    v-model:error="error"
    @confirm="confirm"
    @cancel="account = null"
  >
    <p class="text-sm text-slate-600 mb-2">{{ account.first_name }} {{ account.last_name }}</p>
    <p class="text-sm text-slate-600 mb-2">{{ account.email }}</p>
    <p class="text-xs text-slate-400 mb-6">This action cannot be undone. All sessions, memberships, roles, and policy assignments for this account will be permanently removed.</p>
  </ConfirmModal>
</template>
