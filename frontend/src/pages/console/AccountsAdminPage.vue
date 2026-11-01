<script setup lang="ts">
import { ref, watch } from 'vue'
import { useAuth } from '@/composables/useAuth'
import ScopeFilter, { type ScopeValue } from '@/components/ScopeFilter.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import AccountRow from '@/components/console/accounts/AccountRow.vue'
import AccountCreateModal from '@/components/console/accounts/AccountCreateModal.vue'
import AccountEditModal from '@/components/console/accounts/AccountEditModal.vue'
import AccountDeleteModal from '@/components/console/accounts/AccountDeleteModal.vue'
import UuidLabel from '@/components/UuidLabel.vue'
import type { AccountSummary } from '@/client'

const { state: authState } = useAuth()

// --- Scope ---
const scope = ref<ScopeValue>({ type: 'platform', tenantId: '' })
watch(scope, () => fetchAccounts(), { deep: true })

// --- Data ---
const accounts = ref<AccountSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// --- Modals ---
const showCreate = ref(false)
const editingAccount = ref<AccountSummary | null>(null)
const deletingAccount = ref<AccountSummary | null>(null)

function scopeLabel(): string {
  return scope.value.type === 'tenant' ? 'Tenant' : 'Platform'
}

function currentScopeId(): string | null {
  return scope.value.type === 'tenant' ? (scope.value.tenantId || null) : null
}

async function fetchAccounts() {
  loading.value = true; error.value = null
  try {
    if (scope.value.type === 'tenant' && scope.value.tenantId) {
      accounts.value = await authState.client.adminAccounts.list({ tenantId: scope.value.tenantId })
    } else {
      accounts.value = await authState.client.adminAccounts.list({})
    }
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to load accounts'
  } finally { loading.value = false }
}

// --- TOTP ---

async function disableTOTP(account: AccountSummary) {
  if (!account.membership_id) {
    error.value = 'Cannot disable TOTP: no membership ID available'
    return
  }
  if (!confirm(`Disable TOTP for ${account.email}? This removes their second-factor authentication.`)) return
  try {
    await authState.client.adminAccounts.disableTOTP(account.id, account.membership_id)
    fetchAccounts()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to disable TOTP'
  }
}

// Initial load
fetchAccounts()
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-2xl font-bold text-slate-900">Accounts</h2>
      <p class="text-sm text-slate-500 mt-1">View and manage accounts within the selected scope.</p>
    </div>

    <ErrorBanner v-model:error="error" />
    <ScopeFilter filter-bar v-model="scope" />

    <div class="bg-white border border-slate-200 rounded-xl shadow-sm">
      <div class="p-4 border-b border-slate-100 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wider">
          <span>{{ scopeLabel() }}</span>
          <UuidLabel v-if="currentScopeId()" :id="currentScopeId()!" class="ml-0.5 align-middle" />
          <span> accounts</span>
          <span v-if="!loading" class="text-slate-400 font-normal normal-case ml-2">({{ accounts.length }})</span>
        </h3>
        <button @click="showCreate = true" class="px-3 py-1.5 bg-indigo-600 text-white text-xs font-medium rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-1">
          <span class="material-symbols-outlined text-sm">person_add</span>New account
        </button>
      </div>

      <LoadingSpinner v-if="loading" />

      <div v-else-if="accounts.length === 0" class="text-sm text-slate-400 py-12 text-center">
        No accounts found.
      </div>

      <table v-else class="w-full">
        <thead>
          <tr class="border-b border-slate-100 text-left bg-slate-50/50">
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">ID</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Name</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Email</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">TOTP</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider w-28">Actions</th>
          </tr>
        </thead>
        <tbody>
          <AccountRow
            v-for="a in accounts" :key="a.id"
            :account="a"
            @edit="editingAccount = a"
            @disable-totp="disableTOTP(a)"
            @delete="deletingAccount = a"
          />
        </tbody>
      </table>
    </div>

    <AccountCreateModal
      v-if="showCreate"
      @created="showCreate = false; fetchAccounts()"
      @close="showCreate = false"
    />
    <AccountEditModal v-model="editingAccount" @saved="fetchAccounts()" />
    <AccountDeleteModal v-model="deletingAccount" @deleted="fetchAccounts()" />
  </div>
</template>
