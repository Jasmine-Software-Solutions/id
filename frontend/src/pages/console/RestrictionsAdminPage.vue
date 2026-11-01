<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuth } from '@/composables/useAuth'
import { useForm } from '@/composables/useForm'
import RestrictionTableRow from '@/components/console/restrictions/RestrictionTableRow.vue'
import RestrictionCreateModal from '@/components/console/restrictions/RestrictionCreateModal.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import type { RestrictionResponse, PlaceRestrictionPayload } from '@/client/types'

const { state: authState } = useAuth()

const restrictions = ref<RestrictionResponse[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const { open: formOpen, error: formError, show: showForm, close: closeForm, submit: submitForm } = useForm(() => fetchRestrictions())

const confirmLift = ref<RestrictionResponse | null>(null)

onMounted(fetchRestrictions)

function targetLabel(r: RestrictionResponse): string {
  return r.account_email ?? r.target_id
}

async function fetchRestrictions() {
  loading.value = true
  error.value = null
  try {
    const client = authState.client
    if (!client) return
    restrictions.value = await client.restrictions.list()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load restrictions'
  } finally {
    loading.value = false
  }
}

async function handleCreate(payload: PlaceRestrictionPayload) {
  await submitForm(async () => {
    const client = authState.client
    if (!client) return
    await client.restrictions.place(payload)
    closeForm()
  })
}

async function handleLift(id: string) {
  const client = authState.client
  if (!client) return
  await client.restrictions.lift(id)
  confirmLift.value = null
  fetchRestrictions()
}

function typeLabel(type: string): string {
  return type === 'account_lock' ? 'Account Lock' : 'IP Block'
}
</script>

<template>
  <div class="max-w-6xl mx-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-800">Restrictions</h1>
        <p class="text-sm text-slate-500 mt-1">Manage account locks and IP blocks.</p>
      </div>
      <button
        @click="showForm"
        class="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 transition-colors"
      >
        <span class="material-symbols-outlined text-lg">block</span>
        Create Restriction
      </button>
    </div>

    <ErrorBanner v-if="error" :message="error" @retry="fetchRestrictions" />
    <LoadingSpinner v-if="loading" />

    <!-- Table -->
    <div
      v-if="!loading && restrictions.length > 0"
      class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden"
    >
      <table class="w-full text-sm">
        <thead>
          <tr class="bg-slate-50 border-b border-slate-200">
            <th class="text-left px-4 py-3 font-semibold text-slate-600">ID</th>
            <th class="text-left px-4 py-3 font-semibold text-slate-600">Type</th>
            <th class="text-left px-4 py-3 font-semibold text-slate-600">Target</th>
            <th class="text-left px-4 py-3 font-semibold text-slate-600">Event</th>
            <th class="text-left px-4 py-3 font-semibold text-slate-600">Expires</th>
            <th class="text-right px-4 py-3 font-semibold text-slate-600">Actions</th>
          </tr>
        </thead>
        <tbody>
          <RestrictionTableRow
            v-for="r in restrictions"
            :key="r.id"
            :restriction="r"
            @lift="confirmLift = r"
          />
        </tbody>
      </table>
    </div>

    <!-- Empty state -->
    <div
      v-if="!loading && restrictions.length === 0 && !error"
      class="text-center py-16 bg-white rounded-xl border border-slate-200"
    >
      <span class="material-symbols-outlined text-5xl text-slate-300">shield</span>
      <p class="text-slate-500 mt-4">No active restrictions.</p>
    </div>

    <!-- Create Modal -->
    <Teleport to="body">
      <div
        v-if="formOpen"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      >
        <RestrictionCreateModal
          :error="formError"
          @submit="handleCreate"
          @close="closeForm"
        />
      </div>
    </Teleport>

    <!-- Lift Confirmation -->
    <ConfirmModal
      v-if="confirmLift"
      title="Lift Restriction"
      :message="`Are you sure you want to lift this ${typeLabel(confirmLift.type).toLowerCase()} on ${confirmLift.target_type} ${targetLabel(confirmLift)}?`"
      @confirm="handleLift(confirmLift!.id)"
      @cancel="confirmLift = null"
    />
  </div>
</template>
