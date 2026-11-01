<script setup lang="ts">
import { ref, watch } from 'vue'
import { useAuth } from '@/composables/useAuth'
import { useForm } from '@/composables/useForm'
import ScopeFilter, { type ScopeValue } from '@/components/ScopeFilter.vue'
import EntitySelector from '@/components/EntitySelector.vue'
import UuidLabel from '@/components/UuidLabel.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import PolicyTableRow from '@/components/console/policies/PolicyTableRow.vue'
import type { PolicyDefinition, PolicyAssignment } from '@/client/types'

const { state: authState } = useAuth()

const scope = ref<ScopeValue>({ type: 'platform', tenantId: '' })
watch(scope, () => fetchPolicies(), { deep: true })

// --- Data ---
const policies = ref<PolicyDefinition[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// --- Assignments modal ---
const assignmentsModalPolicy = ref<PolicyDefinition | null>(null)
const assignments = ref<PolicyAssignment[]>([])
const assignmentsLoading = ref(false)

const showAssignForm = ref(false)
const assignTargetType = ref<'account' | 'client' | 'role'>('account')
const assignTargetId = ref('')
const assignExpiresAt = ref('')

// --- Document viewer ---
const showDocumentModal = ref(false)
const viewDocumentPolicy = ref<PolicyDefinition | null>(null)

// --- Create/Edit modal ---
const { open: formOpen, error: formError, show: showForm, close: closeForm, submit: submitForm } = useForm(() => fetchPolicies())
const editingPolicy = ref<PolicyDefinition | null>(null)
const formName = ref('')
const formDescription = ref('')
const formScope = ref<ScopeValue>({ type: 'platform', tenantId: '' })
const formDocumentText = ref('')

// --- Delete confirm ---
const confirmDeletePolicy = ref<PolicyDefinition | null>(null)
const deleteError = ref<string | null>(null)

function cancelDeletePolicy() {
  confirmDeletePolicy.value = null
  deleteError.value = null
}

// ---------------------------------------------------------------------------
// Watchers
function scopeLabel(): string {
  return scope.value.type === 'tenant' ? 'Tenant' : 'Platform'
}

// Initial load
fetchPolicies()

function targetLabel(a: PolicyAssignment): string {
  if (a.account_id) return a.account_email || 'Account'
  if (a.client_id) return a.client_name || 'Client'
  if (a.role_id) return a.role_name || 'Role'
  return '—'
}

// ---------------------------------------------------------------------------
// Fetch
// ---------------------------------------------------------------------------

async function fetchPolicies() {
  loading.value = true
  error.value = null
  try {
    if (scope.value.type === 'tenant' && scope.value.tenantId) {
      policies.value = await authState.client.policies.list({ tenantId: scope.value.tenantId })
    } else {
      policies.value = await authState.client.policies.list()
    }
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to load policies'
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------------------
// Assignments
// ---------------------------------------------------------------------------

async function openAssignments(policy: PolicyDefinition) {
  assignmentsModalPolicy.value = policy
  assignmentsLoading.value = true
  try {
    assignments.value = await authState.client.policies.listAssignments(policy.id)
  } catch {
    assignments.value = []
  } finally {
    assignmentsLoading.value = false
  }
}

function closeAssignments() {
  assignmentsModalPolicy.value = null
  assignments.value = []
  showAssignForm.value = false
  assignTargetId.value = ''
}

async function assignPolicy() {
  if (!assignmentsModalPolicy.value || !assignTargetId.value.trim()) return
  try {
    const payload: { account_id?: string; client_id?: string; role_id?: string; expires_at?: number } = {}
    if (assignTargetType.value === 'account') payload.account_id = assignTargetId.value.trim()
    else if (assignTargetType.value === 'client') payload.client_id = assignTargetId.value.trim()
    else payload.role_id = assignTargetId.value.trim()
    if (assignExpiresAt.value) payload.expires_at = Math.floor(new Date(assignExpiresAt.value).getTime() / 1000)

    await authState.client.policies.assign(assignmentsModalPolicy.value.id, payload)
    assignTargetId.value = ''
    assignExpiresAt.value = ''
    showAssignForm.value = false
    await openAssignments(assignmentsModalPolicy.value)
    fetchPolicies()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to assign policy'
  }
}

async function unassignPolicy(assignmentId: string) {
  if (!assignmentsModalPolicy.value) return
  try {
    await authState.client.policies.unassign(assignmentsModalPolicy.value.id, assignmentId)
    await openAssignments(assignmentsModalPolicy.value)
    fetchPolicies()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to remove assignment'
  }
}

// ---------------------------------------------------------------------------
// CRUD
// ---------------------------------------------------------------------------

function openCreateForm() {
  editingPolicy.value = null
  formName.value = ''
  formDescription.value = ''
  formScope.value = { type: 'platform', tenantId: '' }
  formDocumentText.value = JSON.stringify([{ effect: 'ALLOW', actions: ['*'], resources: ['*'] }], null, 2)
  showForm()
}

function openEditForm(policy: PolicyDefinition) {
  editingPolicy.value = policy
  formName.value = policy.name
  formDescription.value = policy.description ?? ''
  formScope.value = {
    type: policy.tenant_id ? 'tenant' : 'platform',
    tenantId: policy.tenant_id ?? '',
  }
  formDocumentText.value = JSON.stringify(policy.document, null, 2)
  showForm()
}

async function savePolicy() {
  if (!formName.value.trim()) return
  let document: unknown[]
  try {
    document = JSON.parse(formDocumentText.value)
    if (!Array.isArray(document)) throw new Error('Document must be a JSON array')
  } catch {
    formError.value = 'Invalid JSON document. Must be a valid JSON array.'
    return
  }

  await submitForm(async () => {
    if (editingPolicy.value) {
      await authState.client.policies.update(editingPolicy.value.id, {
        name: formName.value.trim(),
        description: formDescription.value.trim() || undefined,
        document,
      })
    } else {
      await authState.client.policies.create({
        name: formName.value.trim(),
        description: formDescription.value.trim() || undefined,
        tenant_id: formScope.value.type === 'tenant' && formScope.value.tenantId ? formScope.value.tenantId : undefined,
        document,
      })
    }
  })
}

async function deletePolicy() {
  if (!confirmDeletePolicy.value) return
  try {
    await authState.client.policies.delete(confirmDeletePolicy.value.id)
    confirmDeletePolicy.value = null
    fetchPolicies()
  } catch (e: unknown) {
    deleteError.value = (e as { message?: string })?.message ?? 'Failed to delete policy'
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-slate-900">Policies</h2>
        <p class="text-sm text-slate-500 mt-1">Fine-grained authorization policies evaluated by the Witchwood engine.</p>
      </div>
      <button @click="openCreateForm"
        class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
        <span class="material-symbols-outlined text-lg">add</span>
        Create policy
      </button>
    </div>

    <!-- Error -->
    <ErrorBanner v-model:error="error" />

    <ScopeFilter filter-bar v-model="scope" />

    <!-- Table -->
    <div class="bg-white border border-slate-200 rounded-xl shadow-sm">
      <div class="p-4 border-b border-slate-100 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wider">
          {{ scopeLabel() }} policies
          <span v-if="!loading" class="text-slate-400 font-normal normal-case ml-2">({{ policies.length }})</span>
        </h3>
      </div>

      <LoadingSpinner v-if="loading" />

      <div v-else-if="policies.length === 0" class="text-sm text-slate-400 py-16 text-center">
        <span class="material-symbols-outlined text-4xl mb-3 block text-slate-300">policy</span>
        No policies found in {{ scopeLabel().toLowerCase() }}.
      </div>

      <table v-else class="w-full">
        <thead>
          <tr class="border-b border-slate-100 text-left bg-slate-50/50">
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">ID</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Name</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Description</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Where</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Attached to</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Statements</th>
            <th class="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider w-20">Actions</th>
          </tr>
        </thead>
        <tbody>
          <PolicyTableRow
            v-for="p in policies"
            :key="p.id"
            :policy="p"
            @edit="openEditForm(p)"
            @delete="confirmDeletePolicy = p"
            @assignments="openAssignments(p)"
            @view-document="viewDocumentPolicy = p; showDocumentModal = true"
          />
        </tbody>
      </table>
    </div>

    <!-- Assignments modal -->
    <div v-if="assignmentsModalPolicy" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="closeAssignments">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 max-h-[80vh] flex flex-col">
        <div class="p-6 border-b border-slate-100 flex items-center justify-between">
          <div>
            <h3 class="text-lg font-bold">{{ assignmentsModalPolicy.name }}</h3>
            <p class="text-xs text-slate-400 mt-0.5">{{ assignments.length }} assignment{{ assignments.length !== 1 ? 's' : '' }}</p>
          </div>
          <button @click="closeAssignments" class="material-symbols-outlined text-slate-400 hover:text-slate-600">close</button>
        </div>

        <div class="p-6 overflow-y-auto flex-1">
          <!-- Assign form -->
          <div class="mb-6">
            <button v-if="!showAssignForm" @click="showAssignForm = true"
              class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
              <span class="material-symbols-outlined text-base">link</span>Attach
            </button>
            <div v-else class="p-4 bg-slate-50 rounded-lg border border-slate-200 space-y-3">
              <div>
                <label class="block text-xs font-medium text-slate-600 mb-1">Target type</label>
                <select v-model="assignTargetType" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm">
                  <option value="account">Account</option>
                  <option value="client">Client</option>
                  <option value="role">Role</option>
                </select>
              </div>
              <div>
                <label class="block text-xs font-medium text-slate-600 mb-1">{{ assignTargetType === 'account' ? 'Account' : assignTargetType === 'client' ? 'Client' : 'Role' }}</label>
                <EntitySelector
                  v-if="assignTargetType === 'account'"
                  entity-type="account"
                  v-model="assignTargetId"
                  placeholder="Select account…"
                />
                <EntitySelector
                  v-else-if="assignTargetType === 'client'"
                  entity-type="client"
                  v-model="assignTargetId"
                  placeholder="Select client…"
                />
                <EntitySelector
                  v-else
                  entity-type="role"
                  v-model="assignTargetId"
                  placeholder="Select role…"
                />
              </div>
              <div>
                <label class="block text-xs font-medium text-slate-600 mb-1">Expires (optional)</label>
                <input v-model="assignExpiresAt" type="datetime-local" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
              </div>
              <div class="flex gap-2">
                <button @click="assignPolicy" class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">Confirm</button>
                <button @click="showAssignForm = false" class="px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Cancel</button>
              </div>
            </div>
          </div>

          <!-- Assignments list -->
          <LoadingSpinner v-if="assignmentsLoading" />
          <div v-else-if="assignments.length === 0" class="text-sm text-slate-400 py-8 text-center">No assignments.</div>
          <table v-else class="w-full">
            <thead>
              <tr class="border-b border-slate-100 text-left">
                <th class="px-3 py-2 text-xs font-semibold text-slate-500 uppercase">Principal</th>
                <th class="px-3 py-2 text-xs font-semibold text-slate-500 uppercase">ID</th>
                <th class="px-3 py-2 text-xs font-semibold text-slate-500 uppercase">Expires</th>
                <th class="px-3 py-2 text-xs font-semibold text-slate-500 uppercase w-10"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in assignments" :key="a.id" class="border-b border-slate-50">
                <td class="px-3 py-2">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-slate-400 text-base">{{ a.account_id ? 'person' : a.client_id ? 'dns' : 'badge' }}</span>
                    <span class="text-sm font-medium">{{ targetLabel(a) }}</span>
                  </div>
                </td>
                <td class="px-3 py-2"><UuidLabel :id="(a.account_id ?? a.client_id ?? a.role_id ?? '')" /></td>
                <td class="px-3 py-2"><span class="text-xs text-slate-500">{{ a.expires_at ? new Date(a.expires_at * 1000).toLocaleDateString() : 'Never' }}</span></td>
                <td class="px-3 py-2">
                  <button @click="unassignPolicy(a.id)" class="material-symbols-outlined text-slate-400 hover:text-red-600 text-sm">close</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Document viewer modal -->
    <div v-if="showDocumentModal && viewDocumentPolicy" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="showDocumentModal = false">
      <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-2xl mx-4">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold">{{ viewDocumentPolicy.name }}</h3>
          <button @click="showDocumentModal = false" class="material-symbols-outlined text-slate-400 hover:text-slate-600">close</button>
        </div>
        <pre class="bg-slate-900 text-green-400 p-4 rounded-lg text-xs overflow-x-auto max-h-96"><code>{{ JSON.stringify(viewDocumentPolicy.document, null, 2) }}</code></pre>
      </div>
    </div>

    <!-- Create/Edit modal -->
    <div v-if="formOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 overflow-y-auto" @click.self="closeForm()">
      <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-2xl mx-4 my-8">
              <h3 class="text-lg font-bold mb-4">{{ editingPolicy ? 'Edit policy' : 'Create policy' }}</h3>
              <div v-if="formError" class="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700 mb-4">{{ formError }}</div>
              <div class="space-y-4">
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">Name</label>
            <input v-model="formName" type="text" placeholder="e.g. allow-read-access" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">Description</label>
            <input v-model="formDescription" type="text" placeholder="Optional" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          </div>
          <div>
            <ScopeFilter v-model="formScope" />
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">Policy document <span class="text-slate-400 font-normal">— JSON array of statements</span></label>
                        <p class="text-xs text-amber-600 mb-2" v-if="formScope.type === 'tenant'">Resources in tenant-scoped policies are automatically prefixed with the tenant path to prevent sandbox escapes.</p>
                        <textarea v-model="formDocumentText" rows="12" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm font-mono resize-y" spellcheck="false"></textarea>
          </div>
        </div>
        <div class="flex gap-2 mt-6">
          <button @click="savePolicy" class="flex-1 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">{{ editingPolicy ? 'Save changes' : 'Create policy' }}</button>
          <button @click="closeForm()" class="flex-1 px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Cancel</button>
        </div>
      </div>
    </div>

    <!-- Delete confirm modal -->
    <ConfirmModal
      v-if="confirmDeletePolicy"
      title="Delete policy"
      v-model:error="deleteError"
      @confirm="deletePolicy()"
      @cancel="cancelDeletePolicy()"
    >
      <p>"{{ confirmDeletePolicy.name }}"</p>
      <p>This action is permanent and cannot be undone. Any accounts, clients, or roles assigned this policy will lose its permissions.</p>
    </ConfirmModal>
  </div>
</template>
