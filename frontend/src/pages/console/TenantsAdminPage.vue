<script setup lang="ts">
import { ref } from 'vue'
import { useForm } from '@/composables/useForm'
import { createTenantsClient, type ClientTenantInfo } from '@/client'
import ErrorBanner from '@/components/ErrorBanner.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import NameFormModal from '@/components/console/tenants/NameFormModal.vue'
import TenantTableRow from '@/components/console/tenants/TenantTableRow.vue'

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

const tenants = createTenantsClient()

const loading = ref(false)
const error = ref<string | null>(null)

const rows = ref<ClientTenantInfo[]>([])

const editingId = ref<string | null>(null)
const formInitialName = ref('')
const formInitialSuspended = ref(false)
const confirmDeleteId = ref<string | null>(null)

const { open: formOpen, error: formError, show: showForm, close: closeForm, submit: submitForm } = useForm(fetchTenants)

// ---------------------------------------------------------------------------
// Data fetching
// ---------------------------------------------------------------------------

async function fetchTenants() {
  loading.value = true; error.value = null
  try {
    rows.value = await tenants.list()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to load tenants'
  } finally { loading.value = false }
}

// ---------------------------------------------------------------------------
// Form helpers
// ---------------------------------------------------------------------------

function openForm(entity?: ClientTenantInfo) {
  editingId.value = entity?.id ?? null
  formInitialName.value = entity?.name ?? ''
  formInitialSuspended.value = entity?.suspended ?? false
  showForm()
}

async function saveForm(name: string, suspended: boolean) {
  if (!name) return
  await submitForm(async () => {
    if (editingId.value) {
      await tenants.update(editingId.value, { name, suspended })
    } else {
      await tenants.create({ name, suspended })
    }
  })
}

async function confirmDelete() {
  if (!confirmDeleteId.value) return
  try {
    await tenants.delete(confirmDeleteId.value)
    confirmDeleteId.value = null
    await fetchTenants()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to delete tenant'
  }
}

// Initial load
fetchTenants()
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-2xl font-bold text-slate-900">Tenants</h2>
      <p class="text-sm text-slate-500 mt-1">Manage the tenants this deployment serves.</p>
    </div>

    <ErrorBanner v-model:error="error" />

    <div class="bg-white border border-slate-200 rounded-xl shadow-sm">
      <div class="p-4 border-b border-slate-100 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wider">
          All tenants
          <span v-if="!loading" class="text-slate-400 font-normal normal-case ml-2">({{ rows.length }})</span>
        </h3>
        <button @click="openForm()" class="px-3 py-1.5 bg-indigo-600 text-white text-xs font-medium rounded-lg hover:bg-indigo-700 flex items-center gap-1">
          <span class="material-symbols-outlined text-sm">add</span>New tenant
        </button>
      </div>
      <LoadingSpinner v-if="loading" />
      <div v-else-if="rows.length === 0" class="text-sm text-slate-400 py-12 text-center">No tenants found.</div>
      <table v-else class="w-full">
        <thead>
          <tr class="border-b border-slate-100 text-left bg-slate-50/50">
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase">ID</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase">Name</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase">Status</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase w-16"></th>
          </tr>
        </thead>
        <tbody>
          <TenantTableRow v-for="t in rows" :key="t.id" :tenant="t"
            @edit="openForm(t)"
            @delete="confirmDeleteId = t.id" />
        </tbody>
      </table>
    </div>

    <!-- Form modal -->
    <NameFormModal
      v-if="formOpen"
      :title="editingId ? 'Edit tenant' : 'Create tenant'"
      :initial-name="formInitialName"
      :initial-suspended="formInitialSuspended"
      show-suspended
      placeholder="e.g. Acme Corp"
      :error="formError"
      @save="saveForm"
      @cancel="closeForm()"
    />

    <!-- Delete confirm modal -->
    <ConfirmModal
      v-if="confirmDeleteId"
      title="Delete tenant"
      @confirm="confirmDelete"
      @cancel="confirmDeleteId = null"
    >
      <p class="text-sm text-slate-600 mb-2">{{ rows.find(t => t.id === confirmDeleteId)?.name }}</p>
      <p class="text-xs text-slate-400 mb-6">This will permanently delete the tenant and all its memberships and invitations.</p>
    </ConfirmModal>
  </div>
</template>
