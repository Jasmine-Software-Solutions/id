<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useForm } from '@/composables/useForm'
import ErrorBanner from '@/components/ErrorBanner.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import ClientTableRow from '@/components/console/clients/ClientTableRow.vue'
import ClientFormModal from '@/components/console/clients/ClientFormModal.vue'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface ClientInfo {
  id: string
  fqdn: string
  confidential: boolean
  suspended: boolean
  redirect_uris: string[]
  created_at: number
  name: string | null
  displayName: string
  logoUri: string | null
  initiateLoginUri: string | null
}

interface ResetSecretResponse {
  secret: string
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

const loading = ref(false)
const error = ref<string | null>(null)
const clients = ref<ClientInfo[]>([])

// Create/edit form (backed by useForm composable)
const { open: formOpen, error: formError, show: showForm, close: closeForm, submit: submitForm } = useForm(() => fetchClients())
const editingClient = ref<ClientInfo | null>(null)

// Delete confirmation
const confirmDeleteClient = ref<ClientInfo | null>(null)

// Secret reveal
const revealedSecret = ref<string | null>(null)
const revealingClientId = ref<string | null>(null)

// ---------------------------------------------------------------------------
// API helpers
// ---------------------------------------------------------------------------

async function api<T>(url: string, init?: RequestInit): Promise<T> {
  const r = await fetch(url, init)
  if (!r.ok) {
    let msg = ''
    try { msg = await r.text() } catch { /* */ }
    throw { status: r.status, message: msg || r.statusText }
  }
  return r.json()
}

// ---------------------------------------------------------------------------
// Client CRUD
// ---------------------------------------------------------------------------

async function fetchClients() {
  loading.value = true; error.value = null
  try {
    clients.value = await api<ClientInfo[]>('/api/v1/admin/clients')
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to load clients'
  } finally { loading.value = false }
}

function openCreateForm() {
  editingClient.value = null
  showForm()
}

function openEditForm(client: ClientInfo) {
  editingClient.value = client
  showForm()
}

async function handleSave(fqdn: string, confidential: boolean, redirectUris: string[], displayName: string, logoUri: string, initiateLoginUri: string) {
  await submitForm(async () => {
    if (editingClient.value) {
      await fetch(`/api/v1/admin/clients/${editingClient.value.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          confidential,
          suspended: editingClient.value.suspended,
          redirect_uris: redirectUris,
          name: displayName || null,
          logo_uri: logoUri || null,
          initiate_login_uri: initiateLoginUri || null,
        }),
      })
    } else {
      await fetch('/api/v1/admin/clients', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fqdn,
          confidential,
          redirect_uris: redirectUris,
          name: displayName || null,
          logo_uri: logoUri || null,
          initiate_login_uri: initiateLoginUri || null,
        }),
      })
    }
  })
}

async function toggleSuspend(client: ClientInfo) {
  try {
    await fetch(`/api/v1/admin/clients/${client.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        suspended: !client.suspended,
      }),
    })
    fetchClients()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to update client'
  }
}

async function deleteClient() {
  if (!confirmDeleteClient.value) return
  try {
    await fetch(`/api/v1/admin/clients/${confirmDeleteClient.value.id}`, { method: 'DELETE' })
    confirmDeleteClient.value = null
    fetchClients()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to delete client'
  }
}

async function resetSecret(client: ClientInfo) {
  try {
    revealingClientId.value = client.id
    const r = await fetch(`/api/v1/admin/clients/${client.id}/reset-secret`, { method: 'POST' })
    if (!r.ok) throw new Error('Failed to reset secret')
    const res: ResetSecretResponse = await r.json()
    revealedSecret.value = res.secret
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to reset secret'
  }
}

// ---------------------------------------------------------------------------
// Init
// ---------------------------------------------------------------------------

onMounted(() => { fetchClients() })
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-slate-900">Clients</h2>
        <p class="text-sm text-slate-500 mt-1">Manage OAuth2 clients and their configurations.</p>
      </div>
      <button @click="openCreateForm()"
        class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
        <span class="material-symbols-outlined text-lg">add</span>
        New client
      </button>
    </div>

    <ErrorBanner v-model:error="error" />

    <!-- Table -->
    <div class="bg-white border border-slate-200 rounded-xl shadow-sm">
      <div class="p-4 border-b border-slate-100 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wider">
          All clients
          <span v-if="!loading" class="text-slate-400 font-normal normal-case ml-2">({{ clients.length }})</span>
        </h3>
      </div>

      <LoadingSpinner v-if="loading" />

      <!-- Empty -->
      <div v-else-if="clients.length === 0" class="text-sm text-slate-400 py-12 text-center">
        No clients found. Create your first OAuth2 client.
      </div>

      <!-- Table -->
      <table v-else class="w-full">
        <thead>
          <tr class="border-b border-slate-100 text-left bg-slate-50/50">
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">ID</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">FQDN</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Type</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Created</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider w-32">Actions</th>
          </tr>
        </thead>
        <tbody>
          <ClientTableRow
            v-for="c in clients"
            :key="c.id"
            :client="c"
            @edit="openEditForm(c)"
            @reset-secret="resetSecret(c)"
            @toggle-suspend="toggleSuspend(c)"
            @delete="confirmDeleteClient = c"
          />
        </tbody>
      </table>
    </div>

    <!-- Create/Edit modal -->
    <ClientFormModal
      v-if="formOpen"
      :client="editingClient"
      :error="formError"
      @save="handleSave"
      @cancel="closeForm()"
    />

    <ConfirmModal
      v-if="confirmDeleteClient"
      title="Delete client"
      @confirm="deleteClient()"
      @cancel="confirmDeleteClient = null"
    >
      <p class="text-sm text-slate-600 mb-2">{{ confirmDeleteClient.fqdn }}</p>
      <p class="text-xs text-slate-400">This action cannot be undone. All associated tokens and sessions will be invalidated.</p>
    </ConfirmModal>

    <!-- Revealed secret modal -->
    <div v-if="revealedSecret" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="revealedSecret = null; revealingClientId = null">
      <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-lg mx-4">
        <div class="flex items-center gap-3 mb-4">
          <span class="p-2 bg-amber-50 text-amber-600 rounded-lg material-symbols-outlined">key</span>
          <h3 class="text-lg font-bold">New client secret</h3>
        </div>
        <p class="text-sm text-slate-600 mb-3">Copy this secret now. <strong>It will not be shown again.</strong></p>
        <div class="bg-slate-900 text-emerald-400 font-mono text-sm p-3 rounded-lg mb-4 break-all">
          {{ revealedSecret }}
        </div>
        <button @click="revealedSecret = null; revealingClientId = null" class="w-full px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">
          I've copied it
        </button>
      </div>
    </div>
  </div>
</template>
