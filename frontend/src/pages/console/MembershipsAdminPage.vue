<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAuth } from '@/composables/useAuth'
import EntitySelector from '@/components/EntitySelector.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import MembershipTableRow from '@/components/console/memberships/MembershipTableRow.vue'
import type { Membership } from '@/client/types'

const { state: authState } = useAuth()

const tenantId = ref('')
watch(tenantId, () => { if (tenantId.value.trim()) fetchMemberships() })

const memberships = ref<Membership[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// --- Force add form ---
const showAddForm = ref(false)
const addAccountId = ref('')

// --- Invite form ---
const showInviteForm = ref(false)
const inviteEmail = ref('')

const confirmRemoveMembership = ref<Membership | null>(null)

async function fetchMemberships() {
  if (!tenantId.value.trim()) return
  loading.value = true; error.value = null
  try {
    memberships.value = await authState.client.memberships.list(tenantId.value.trim())
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to load memberships'
  } finally { loading.value = false }
}

async function forceMembership() {
  if (!addAccountId.value.trim() || !tenantId.value.trim()) return
  try {
    await authState.client.memberships.create(tenantId.value.trim(), {
      account_id: addAccountId.value.trim(),
    })
    addAccountId.value = ''
    showAddForm.value = false
    fetchMemberships()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to add membership'
  }
}

async function inviteAccount() {
  if (!inviteEmail.value.trim() || !tenantId.value.trim()) return
  try {
    await fetch(`/api/v1/tenants/${tenantId.value.trim()}/invitations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: inviteEmail.value.trim() }),
    })
    inviteEmail.value = ''
    showInviteForm.value = false
    error.value = null
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to send invitation'
  }
}

async function removeMembership() {
  if (!confirmRemoveMembership.value) return
  try {
    await authState.client.memberships.delete(confirmRemoveMembership.value.id)
    confirmRemoveMembership.value = null
    fetchMemberships()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to remove membership'
  }
}

</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-2xl font-bold text-slate-900">Memberships</h2>
      <p class="text-sm text-slate-500 mt-1">Manage tenant memberships.</p>
    </div>

    <ErrorBanner v-model:error="error" />

    <!-- Tenant selector -->
    <div class="flex items-center gap-3 bg-white border border-slate-200 rounded-xl shadow-sm px-4 py-3">
      <label class="text-sm font-medium text-slate-600">Tenant</label>
      <EntitySelector entity-type="tenant" v-model="tenantId" placeholder="Select a tenant…" class="flex-1" />
    </div>

    <!-- Results -->
    <div v-if="tenantId.trim()" class="bg-white border border-slate-200 rounded-xl shadow-sm">
      <div class="p-4 border-b border-slate-100 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wider">
          Members
          <span v-if="!loading" class="text-slate-400 font-normal normal-case ml-2">({{ memberships.length }})</span>
        </h3>
        <div class="flex items-center gap-2">
          <button @click="showInviteForm = !showInviteForm; showAddForm = false"
            class="px-3 py-1.5 border border-indigo-200 text-indigo-600 text-xs font-medium rounded-lg hover:bg-indigo-50 transition-colors flex items-center gap-1">
            <span class="material-symbols-outlined text-sm">mail</span>Invite
          </button>
          <button @click="showAddForm = !showAddForm; showInviteForm = false"
            class="px-3 py-1.5 bg-indigo-600 text-white text-xs font-medium rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-1">
            <span class="material-symbols-outlined text-sm">person_add</span>Create
          </button>
        </div>
      </div>

      <!-- Invite form -->
      <div v-if="showInviteForm" class="m-4 p-4 bg-slate-50 rounded-lg border border-slate-200 space-y-3">
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Email address</label>
          <input v-model="inviteEmail" type="email" placeholder="name@company.com" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
        </div>
        <div class="flex gap-2">
          <button @click="inviteAccount" class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">Send invitation</button>
          <button @click="showInviteForm = false" class="px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Cancel</button>
        </div>
      </div>

      <!-- Force add form -->
      <div v-if="showAddForm" class="m-4 p-4 bg-slate-50 rounded-lg border border-slate-200 space-y-3">
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Account</label>
          <EntitySelector entity-type="account" v-model="addAccountId" placeholder="Select account…" />
        </div>
        <div class="flex gap-2">
          <button @click="forceMembership" class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">Add</button>
          <button @click="showAddForm = false" class="px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Cancel</button>
        </div>
      </div>

      <!-- Table -->
      <LoadingSpinner v-if="loading" />

      <div v-else-if="memberships.length === 0" class="text-sm text-slate-400 py-12 text-center">
        No memberships found.
      </div>

      <table v-else class="w-full">
        <thead>
          <tr class="border-b border-slate-100 text-left bg-slate-50/50">
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">ID</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Account</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Tenant</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider w-10"></th>
          </tr>
        </thead>
        <tbody>
          <MembershipTableRow
            v-for="m in memberships"
            :key="m.id"
            :membership="m"
            @remove="confirmRemoveMembership = m"
          />
        </tbody>
      </table>
    </div>

    <!-- Empty state -->
    <div v-else class="bg-white border border-slate-200 rounded-xl shadow-sm flex items-center justify-center h-64">
      <div class="text-center text-slate-400">
        <span class="material-symbols-outlined text-4xl mb-3">corporate_fare</span>
        <p class="text-sm">Select a tenant to view its members.</p>
      </div>
    </div>

    <!-- Remove confirm modal -->
    <ConfirmModal
      v-if="confirmRemoveMembership"
      title="Remove member"
      confirm-text="Remove"
      @confirm="removeMembership()"
      @cancel="confirmRemoveMembership = null"
    >
      <p class="text-sm text-slate-600 mb-2">{{ confirmRemoveMembership.account_email || 'Account' }}</p>
      <p class="text-xs text-slate-400 mb-6">This will remove the account from the tenant.</p>
    </ConfirmModal>
  </div>
</template>
