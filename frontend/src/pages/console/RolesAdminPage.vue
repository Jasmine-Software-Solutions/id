<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useAuth } from '@/composables/useAuth'
import { useForm } from '@/composables/useForm'
import ScopeFilter, { type ScopeValue } from '@/components/ScopeFilter.vue'
import EntitySelector from '@/components/EntitySelector.vue'
import UuidLabel from '@/components/UuidLabel.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import RoleTableRow from '@/components/console/roles/RoleTableRow.vue'
import type { RoleDefinition, RoleAssignment } from '@/client/types'

const { state: authState } = useAuth()

const scope = ref<ScopeValue>({ type: 'platform', tenantId: '' })
watch(scope, () => fetchRoles(), { deep: true })

// --- Data ---
const roles = ref<RoleDefinition[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// --- Assignments modal ---
const assignmentsModalRole = ref<RoleDefinition | null>(null)
const assignments = ref<RoleAssignment[]>([])
const assignmentsLoading = ref(false)

// --- Assign inline form (inside assignments modal) ---
const showAssignForm = ref(false)
const assignTargetType = ref<'account' | 'client'>('account')
const assignTargetId = ref('')
const assignExpiresAt = ref('')

// --- Create/Edit modal ---
const { open: roleFormOpen, error: roleFormError, show: showRoleForm, close: closeRoleForm, submit: submitRoleForm } = useForm(() => fetchRoles())
const editingRole = ref<RoleDefinition | null>(null)
const formName = ref('')
const formDescription = ref<string | null>(null)
const formScope = ref<ScopeValue>({ type: 'platform', tenantId: '' })

// --- Delete confirm ---
const confirmDeleteRole = ref<RoleDefinition | null>(null)

// Initial load
fetchRoles()

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function scopeLabel(): string {
  return scope.value.type === 'tenant' ? 'Tenant' : 'Platform'
}

// ---------------------------------------------------------------------------
// Fetch
// ---------------------------------------------------------------------------

async function fetchRoles() {
  loading.value = true
  error.value = null
  try {
    if (scope.value.type === 'tenant' && scope.value.tenantId) {
      roles.value = await authState.client.roles.list({ tenantId: scope.value.tenantId })
    } else {
      roles.value = await authState.client.roles.list()
    }
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to load roles'
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------------------
// Assignments modal
// ---------------------------------------------------------------------------

async function openAssignmentsModal(role: RoleDefinition) {
  assignmentsModalRole.value = role
  assignments.value = []
  showAssignForm.value = false
  assignTargetType.value = 'account'
  assignTargetId.value = ''
  assignExpiresAt.value = ''
  assignmentsLoading.value = true
  try {
    assignments.value = await authState.client.roles.listAssignments(role.id)
  } catch {
    assignments.value = []
  } finally {
    assignmentsLoading.value = false
  }
}

function closeAssignmentsModal() {
  assignmentsModalRole.value = null
  assignments.value = []
  showAssignForm.value = false
}

async function assignRole() {
  if (!assignmentsModalRole.value || !assignTargetId.value.trim()) return
  try {
    const payload: { account_id?: string; client_id?: string; expires_at?: number } = {}
    if (assignTargetType.value === 'account') payload.account_id = assignTargetId.value.trim()
    else payload.client_id = assignTargetId.value.trim()
    if (assignExpiresAt.value) payload.expires_at = Math.floor(new Date(assignExpiresAt.value).getTime() / 1000)
    await authState.client.roles.assign(assignmentsModalRole.value.id, payload)
    assignTargetId.value = ''
    assignExpiresAt.value = ''
    showAssignForm.value = false
    // Re-fetch assignments
    assignmentsLoading.value = true
    try {
      assignments.value = await authState.client.roles.listAssignments(assignmentsModalRole.value.id)
    } catch {
      assignments.value = []
    } finally {
      assignmentsLoading.value = false
    }
    // Re-fetch roles to update counts
    await fetchRoles()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to assign role'
  }
}

async function unassignRole(assignmentId: string) {
  if (!assignmentsModalRole.value) return
  try {
    await authState.client.roles.unassign(assignmentsModalRole.value.id, assignmentId)
    assignmentsLoading.value = true
    try {
      assignments.value = await authState.client.roles.listAssignments(assignmentsModalRole.value.id)
    } catch {
      assignments.value = []
    } finally {
      assignmentsLoading.value = false
    }
    await fetchRoles()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to remove assignment'
  }
}

function onAssignTargetTypeChange() {
  assignTargetId.value = ''
}

// ---------------------------------------------------------------------------
// Create/Edit modal
// ---------------------------------------------------------------------------

function openCreateForm() {
  editingRole.value = null
  formName.value = ''
  formDescription.value = ''
  formScope.value = { type: 'platform', tenantId: '' }
  showRoleForm()
}

function openEditForm(role: RoleDefinition) {
  editingRole.value = role
  formName.value = role.name
  formDescription.value = role.description ?? null
  formScope.value = {
    type: role.tenant_id ? 'tenant' : 'platform',
    tenantId: role.tenant_id ?? '',
  }
  showRoleForm()
}

async function saveRole() {
  if (!formName.value.trim()) return
  await submitRoleForm(async () => {
    if (editingRole.value) {
      await authState.client.roles.update(editingRole.value.id, {
        name: formName.value.trim(),
        description: formDescription.value?.trim(),
      })
    } else {
      await authState.client.roles.create({
        name: formName.value.trim(),
        description: formDescription.value?.trim(),
        tenant_id: formScope.value.type === 'tenant' && formScope.value.tenantId ? formScope.value.tenantId : undefined,
      })
    }
  })
}

// ---------------------------------------------------------------------------
// Delete
// ---------------------------------------------------------------------------

function requestDelete(role: RoleDefinition) {
  confirmDeleteRole.value = role
}

async function confirmDelete() {
  if (!confirmDeleteRole.value) return
  try {
    await authState.client.roles.delete(confirmDeleteRole.value.id)
    confirmDeleteRole.value = null
    await fetchRoles()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to delete role'
    confirmDeleteRole.value = null
  }
}

function formatDate(epoch: number | null): string {
  if (!epoch) return 'Never'
  return new Date(epoch * 1000).toLocaleString()
}
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold">Roles</h2>
        <p class="text-sm text-slate-500 mt-1">Define roles and assign them to accounts or clients.</p>
      </div>
      <button @click="openCreateForm"
        class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
        <span class="material-symbols-outlined text-lg">add</span>
        Create Role
      </button>
    </div>

    <!-- Error banner -->
    <ErrorBanner v-model:error="error" />

    <ScopeFilter filter-bar v-model="scope" />

    <!-- Full-width role table -->
    <div class="bg-white border border-slate-200 rounded-xl shadow-sm">
      <div class="p-4 border-b border-slate-100 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wider">
          {{ scopeLabel() }} Roles
          <span v-if="!loading" class="text-slate-400 font-normal normal-case ml-2">({{ roles.length }})</span>
        </h3>
      </div>

      <!-- Loading -->
      <LoadingSpinner v-if="loading" />

      <!-- Empty -->
      <div v-else-if="roles.length === 0" class="text-sm text-slate-400 py-12 text-center">
        No roles found.
      </div>

      <!-- Table -->
      <table v-else class="w-full">
        <thead>
          <tr class="border-b border-slate-100 text-left">
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">ID</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Name</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Description</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Where</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Attached to</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Created</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider w-10"></th>
          </tr>
        </thead>
        <tbody>
          <RoleTableRow
            v-for="role in roles"
            :key="role.id"
            :role="role"
            @edit="openEditForm(role)"
            @delete="requestDelete(role)"
            @assignments="openAssignmentsModal(role)"
          />
        </tbody>
      </table>
    </div>

    <!-- =================================================================== -->
    <!-- Assignments modal -->
    <!-- =================================================================== -->
    <div v-if="assignmentsModalRole" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="closeAssignmentsModal">
      <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-2xl mx-4 max-h-[85vh] overflow-y-auto">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h3 class="text-lg font-bold">{{ assignmentsModalRole.name }} — Assignments</h3>
            <p class="text-xs text-slate-400 font-mono mt-0.5">ID: <UuidLabel :id="assignmentsModalRole.id" /></p>
          </div>
          <button @click="closeAssignmentsModal" class="material-symbols-outlined text-slate-400 hover:text-slate-600">close</button>
        </div>

        <!-- Assign inline form toggle -->
        <div class="mb-4">
          <button
            v-if="!showAssignForm"
            @click="showAssignForm = true"
            class="px-3 py-1.5 bg-indigo-600 text-white text-xs font-medium rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-1">
            <span class="material-symbols-outlined text-sm">person_add</span>
            Assign
          </button>
        </div>

        <!-- Inline assign form -->
        <div v-if="showAssignForm" class="mb-4 p-4 bg-slate-50 rounded-lg border border-slate-200 space-y-3">
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">Target Type</label>
            <select v-model="assignTargetType" @change="onAssignTargetTypeChange" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm">
              <option value="account">Account</option>
              <option value="client">Client</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">{{ assignTargetType === 'account' ? 'Account' : 'Client' }}</label>
            <EntitySelector :entity-type="assignTargetType" v-model="assignTargetId" :placeholder="`Select ${assignTargetType}…`" />
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">Expires (optional)</label>
            <input v-model="assignExpiresAt" type="datetime-local"
              class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          </div>
          <div class="flex gap-2">
            <button @click="assignRole"
              class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 transition-colors">
              Confirm
            </button>
            <button @click="showAssignForm = false"
              class="px-4 py-2 border border-slate-300 text-slate-600 text-sm font-medium rounded-lg hover:bg-slate-100 transition-colors">
              Cancel
            </button>
          </div>
        </div>

        <!-- Assignments table -->
        <LoadingSpinner v-if="assignmentsLoading" />
        <div v-else-if="assignments.length === 0" class="text-sm text-slate-400 py-8 text-center">
          No assignments yet.
        </div>
        <table v-else class="w-full">
          <thead>
            <tr class="border-b border-slate-100 text-left">
              <th class="px-3 py-2 text-xs font-semibold text-slate-500 uppercase tracking-wider">Principal</th>
              <th class="px-3 py-2 text-xs font-semibold text-slate-500 uppercase tracking-wider">Principal ID</th>
              <th class="px-3 py-2 text-xs font-semibold text-slate-500 uppercase tracking-wider">Expires</th>
              <th class="px-3 py-2 text-xs font-semibold text-slate-500 uppercase tracking-wider w-10"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in assignments" :key="a.id" class="border-b border-slate-100 hover:bg-slate-50 transition-colors">
              <td class="px-3 py-2">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-slate-400 text-sm">
                    {{ a.account_id ? 'person' : 'dns' }}
                  </span>
                  <span class="text-sm font-medium text-slate-700">{{ a.account_email || a.client_name || (a.account_id ? 'Account' : 'Client') }}</span>
                </div>
              </td>
              <td class="px-3 py-2">
                <UuidLabel :id="a.account_id ?? a.client_id ?? ''" />
              </td>
              <td class="px-3 py-2">
                <span class="text-sm text-slate-500">{{ formatDate(a.expires_at) }}</span>
              </td>
              <td class="px-3 py-2">
                <button @click="unassignRole(a.id)" class="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors" title="Revoke">
                  <span class="material-symbols-outlined text-sm">close</span>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- =================================================================== -->
    <!-- Create/Edit modal -->
    <!-- =================================================================== -->
    <div v-if="roleFormOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="closeRoleForm()">
      <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md mx-4">
        <h3 class="text-lg font-bold mb-4">{{ editingRole ? 'Edit Role' : 'Create Role' }}</h3>
        <div v-if="roleFormError" class="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700 mb-4">{{ roleFormError }}</div>
        <div class="space-y-4">
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">Name</label>
            <input v-model="formName" type="text" placeholder="e.g. SuperAdmin"
              class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">Description</label>
            <input v-model="formDescription" type="text" placeholder="e.g. Allows Access to Documents"
                   class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          </div>
          <div>
            <ScopeFilter v-model="formScope" />
          </div>
        </div>
        <div class="flex gap-2 mt-6">
          <button @click="saveRole"
            class="flex-1 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 transition-colors">
            {{ editingRole ? 'Save Changes' : 'Create' }}
          </button>
          <button @click="closeRoleForm()"
            class="flex-1 px-4 py-2 border border-slate-300 text-slate-600 text-sm font-medium rounded-lg hover:bg-slate-100 transition-colors">
            Cancel
          </button>
        </div>
      </div>
    </div>

    <!-- =================================================================== -->
    <!-- Delete confirmation modal -->
    <!-- =================================================================== -->
    <ConfirmModal
      v-if="confirmDeleteRole"
      title="Delete Role"
      @confirm="confirmDelete()"
      @cancel="confirmDeleteRole = null"
    >
      <p>Are you sure you want to delete <strong>{{ confirmDeleteRole.name }}</strong>? This cannot be undone.</p>
    </ConfirmModal>
  </div>
</template>
