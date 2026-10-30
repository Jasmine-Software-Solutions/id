<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const router = useRouter()
const { state: authState, fetchAccount } = useAuth()

interface InvitationDetails {
  id: string
  email: string
  tenant_id: string
  tenant_name: string | null
  accepted_at: number | null
}

const loading = ref(true)
const error = ref<string | null>(null)
const invitation = ref<InvitationDetails | null>(null)
const accountExists = ref(false)
const creating = ref(false)

// Sign-up form
const firstName = ref('')
const lastName = ref('')
const password = ref('')

const invitationId = route.params.id as string
const token = (route.query.token as string) || ''

onMounted(async () => {
  if (!token) { error.value = 'Missing invitation token'; loading.value = false; return }

  // Fetch invitation details
  try {
    const r = await fetch(`/api/v1/invitations/${invitationId}?token=${encodeURIComponent(token)}`)
    if (!r.ok) throw new Error('Invitation not found or token invalid')
    invitation.value = await r.json()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to load invitation'
    loading.value = false
    return
  }

  // Check if the invited email matches an existing account
  if (invitation.value) {
    try {
      // Try to sign in / check if we have an active session that matches
      const account = await fetchAccount()
      accountExists.value = account != null
    } catch { accountExists.value = false }
  }

  loading.value = false
})

function scopeName(): string {
  const inv = invitation.value
  if (!inv) return ''
  return inv.tenant_name || 'this tenant'
}

async function signUpAndAccept() {
  if (!invitation.value || !firstName.value.trim() || !lastName.value.trim() || !password.value.trim()) return
  creating.value = true; error.value = null
  try {
    // Create account with invitation params
    const r = await fetch('/api/v1/accounts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: invitation.value.email,
        first_name: firstName.value.trim(),
        last_name: lastName.value.trim(),
        password: password.value.trim(),
        invitation_id: invitation.value.id,
        invitation_token: token,
      }),
    })
    if (!r.ok) {
      const msg = await r.text().catch(() => 'Failed to create account')
      throw new Error(msg)
    }
    // Set session cookies are auto-set by the browser — now redirect to home
    router.push('/')
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to create account'
  } finally { creating.value = false }
}

async function acceptInvitation() {
  if (!invitation.value) return
  creating.value = true; error.value = null
  try {
    const r = await fetch(`/api/v1/invitations/${invitation.value.id}/accept?token=${encodeURIComponent(token)}`, { method: 'POST' })
    if (!r.ok) { const msg = await r.text().catch(() => 'Failed'); throw new Error(msg) }
    router.push('/')
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to accept invitation'
  } finally { creating.value = false }
}

async function ignoreInvitation() {
  router.push('/')
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center px-4">
    <div v-if="loading" class="text-slate-400 text-sm flex items-center gap-2">
      <span class="material-symbols-outlined animate-spin">sync</span>Loading invitation…
    </div>

    <div v-else-if="error" class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md mx-4 text-center">
      <span class="material-symbols-outlined text-4xl text-red-400 mb-3">error</span>
      <p class="text-sm text-slate-600">{{ error }}</p>
      <router-link to="/" class="mt-4 inline-block text-indigo-600 hover:underline text-sm">Go home</router-link>
    </div>

    <!-- Account exists: show accept/ignore -->
    <div v-else-if="accountExists" class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md mx-4">
      <div class="flex items-center gap-3 mb-4">
        <span class="p-2 bg-indigo-50 text-indigo-600 rounded-lg material-symbols-outlined">mail</span>
        <div>
          <h2 class="text-lg font-bold">Invitation</h2>
          <p class="text-xs text-slate-400">You've been invited to join</p>
        </div>
      </div>
      <p class="text-2xl font-bold text-slate-800 mb-2">{{ scopeName() }}</p>
      <p class="text-sm text-slate-500 mb-6">You're signed in as <strong>{{ authState.account?.email }}</strong>. Accept the invitation to become a member of this tenant.</p>
      <div class="flex gap-2">
        <button @click="acceptInvitation" :disabled="creating" class="flex-1 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50">
          {{ creating ? 'Accepting…' : 'Accept' }}
        </button>
        <button @click="ignoreInvitation" class="flex-1 px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Ignore</button>
      </div>
    </div>

    <!-- No account: show sign-up -->
    <div v-else class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md mx-4">
      <div class="flex items-center gap-3 mb-4">
        <span class="p-2 bg-indigo-50 text-indigo-600 rounded-lg material-symbols-outlined">mail</span>
        <div>
          <h2 class="text-lg font-bold">You're invited</h2>
          <p class="text-xs text-slate-400">Create an account to join</p>
        </div>
      </div>
      <p class="text-2xl font-bold text-slate-800 mb-6">{{ scopeName() }}</p>

      <div v-if="error" class="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700 mb-4">{{ error }}</div>

      <div class="space-y-4">
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Email</label>
          <input :value="invitation?.email" disabled class="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm text-slate-400 bg-slate-50" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">First name</label>
            <input v-model="firstName" type="text" placeholder="Jane" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-600 mb-1">Last name</label>
            <input v-model="lastName" type="text" placeholder="Doe" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          </div>
        </div>
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Password</label>
          <input v-model="password" type="password" placeholder="Create a password" class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
        </div>
      </div>
      <div class="flex gap-2 mt-6">
        <button @click="signUpAndAccept" :disabled="creating" class="flex-1 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50">
          {{ creating ? 'Creating…' : 'Create account & accept' }}
        </button>
        <button @click="ignoreInvitation" class="flex-1 px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Ignore</button>
      </div>
    </div>
  </div>
</template>
