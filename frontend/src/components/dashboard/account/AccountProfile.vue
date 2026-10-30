<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuth } from '@/composables/useAuth'
import type { EmailChangeStatus } from '@/client/types'

const { state, fetchAccount } = useAuth()

const saved = ref(false)
const saving = ref(false)
const verifiedBanner = ref(false)
const saveError = ref('')

// Email change state
const emailChangeStatus = ref<EmailChangeStatus | null>(null)
const newEmail = ref('')

onMounted(async () => {
  if (!state.account) return

  if (window.location.hash.includes('verified=1')) {
    verifiedBanner.value = true
    const newHash = window.location.hash.replace(/[?&]verified=1/, '').replace(/\?$/, '')
    window.history.replaceState(null, '', window.location.pathname + window.location.search + (newHash || '#'))
    try { await fetchAccount() } catch { /* ignore */ }
  }

  try {
    emailChangeStatus.value = await state.client.accounts.getEmailChangeStatus('me')
  } catch { /* ignore */ }
})

const emailInputDisabled = computed(() => emailChangeStatus.value?.pending === true)

async function save() {
  if (!state.account) return
  saveError.value = ''
  saving.value = true

  try {
    // 1) Save name changes
    const updated = await state.client.accounts.updateProfile('me', {
      first_name: state.account.first_name,
      last_name: state.account.last_name,
    })
    state.account = updated

    // 2) If email changed, trigger verification
    const email = newEmail.value.trim()
    if (email && email !== state.account.email) {
      await state.client.accounts.startEmailChange('me', { new_email: email })
      emailChangeStatus.value = await state.client.accounts.getEmailChangeStatus('me')
      newEmail.value = ''
    }

    saved.value = true
    setTimeout(() => saved.value = false, 2000)
  } catch (e: unknown) {
    saveError.value = (e as { message?: string })?.message ?? 'Failed to save changes'
  } finally {
    saving.value = false
  }
}

async function resendVerification() {
  if (!state.account || !emailChangeStatus.value?.new_email) return
  saving.value = true
  try {
    await state.client.accounts.startEmailChange('me', { new_email: emailChangeStatus.value.new_email })
  } catch { /* ignore */ }
  finally { saving.value = false }
}

async function refreshStatus() {
  if (!state.account) return
  try {
    emailChangeStatus.value = await state.client.accounts.getEmailChangeStatus('me')
    if (!emailChangeStatus.value?.pending) await fetchAccount()
  } catch { /* ignore */ }
}
</script>

<template>
  <section id="profile" class="bg-white border border-slate-200 rounded-xl shadow-sm p-6">
    <template v-if="state.account">
      <div v-if="verifiedBanner" class="mb-6 p-4 bg-green-50 border border-green-200 rounded-lg flex items-center gap-2">
        <span class="material-symbols-outlined text-green-600">check_circle</span>
        <span class="text-sm text-green-700">Email verified successfully!</span>
        <button class="ml-auto text-green-500 hover:text-green-700" @click="verifiedBanner = false">&times;</button>
      </div>

      <div class="flex items-center gap-6 mb-8 pb-8 border-b border-slate-100">
        <div class="h-24 w-24 rounded-2xl bg-indigo-100 flex items-center justify-center shrink-0">
          <span class="material-symbols-outlined text-indigo-600 text-5xl">person</span>
        </div>
        <div class="min-w-0">
          <h3 class="text-xl font-bold truncate">{{ state.account.first_name }} {{ state.account.last_name }}</h3>
          <p class="text-slate-500 truncate">
            {{ state.account.email }}
            <span v-if="state.account.email_verified" class="ml-1 text-green-500 text-xs material-symbols-outlined align-middle">verified</span>
          </p>
        </div>
      </div>

      <form @submit.prevent="save" class="space-y-4">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="flex flex-col gap-2">
            <label class="text-xs text-slate-500 uppercase tracking-wider">First Name</label>
            <input v-model="state.account.first_name" class="bg-slate-50 border border-slate-200 rounded-lg px-4 py-3" />
          </div>
          <div class="flex flex-col gap-2">
            <label class="text-xs text-slate-500 uppercase tracking-wider">Last Name</label>
            <input v-model="state.account.last_name" class="bg-slate-50 border border-slate-200 rounded-lg px-4 py-3" />
          </div>
        </div>

        <div class="flex flex-col gap-2">
          <label class="text-xs text-slate-500 uppercase tracking-wider">Email</label>

          <!-- Pending verification -->
          <template v-if="emailInputDisabled">
            <div class="flex gap-2">
              <div class="flex-1 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 text-amber-700 flex items-center gap-2">
                <span class="material-symbols-outlined text-amber-500">hourglass_top</span>
                <span class="text-sm">Awaiting verification for <strong>{{ emailChangeStatus?.new_email }}</strong></span>
              </div>
              <button type="button" :disabled="saving" class="bg-indigo-500 text-white py-3 px-4 rounded-lg font-bold hover:bg-indigo-400 transition-colors disabled:opacity-50 shrink-0 text-sm" @click="resendVerification">Resend</button>
            </div>
            <p class="text-xs text-slate-400 mt-1">
              Check your inbox and click the verification link.
              <button type="button" class="text-indigo-500 underline hover:text-indigo-400" @click="refreshStatus">Refresh status</button>
            </p>
          </template>

          <!-- Normal state -->
          <template v-else>
            <input v-model="newEmail" type="email" :placeholder="state.account.email" class="bg-slate-50 border border-slate-200 rounded-lg px-4 py-3" />
          </template>
        </div>

        <p v-if="saveError" class="text-xs text-red-500">{{ saveError }}</p>

        <div class="flex justify-end">
          <span v-if="saved" class="text-green-600 text-sm mr-4 self-center">Saved!</span>
          <button type="submit" :disabled="saving" class="bg-indigo-600 text-white py-2 px-6 rounded-lg font-bold hover:bg-indigo-500 transition-colors disabled:opacity-50">
            <span v-if="saving" class="material-symbols-outlined animate-spin align-middle text-sm mr-1">progress_activity</span>
            Save Changes
          </button>
        </div>
      </form>
    </template>
  </section>
</template>
