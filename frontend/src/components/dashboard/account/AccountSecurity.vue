<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuth } from '@/composables/useAuth'
import type { TotpStatus } from '@/client/types'

const { state } = useAuth()

const totp = ref<TotpStatus>({ enabled: false, confirmed: false, qr_code_uri: null })
const totpCode = ref('')
const password = ref('')
const msg = ref('')

const qrCodeUrl = computed(() => {
  const uri = totp.value.qr_code_uri
  if (!uri) return null
  return `https://quickchart.io/chart?chs=256x256&cht=qr&chl=${encodeURIComponent(uri)}`
})

onMounted(async () => {
  if (!state.account) return
  try { totp.value = await state.client.accounts.getTotp('me') } catch { /* ignore */ }
})

async function enableTotp() {
  try { totp.value = await state.client.accounts.enableTotp('me') } catch { /* ignore */ }
}

async function disableTotp() {
  try { totp.value = await state.client.accounts.disableTotp('me') } catch { /* ignore */ }
}

async function confirmTotp() {
  const code = parseInt(totpCode.value, 10)
  if (isNaN(code)) { msg.value = 'Invalid code'; return }
  try {
    await state.client.accounts.confirmTotp('me', code)
    totp.value = { ...totp.value, confirmed: true }
  } catch { msg.value = 'Invalid code' }
  totpCode.value = ''
}

async function setPassword() {
  try {
    await state.client.accounts.setPassword('me', password.value)
    msg.value = 'Password updated!'
    password.value = ''
  } catch { msg.value = 'Error updating password' }
  setTimeout(() => msg.value = '', 3000)
}
</script>

<template>
  <section id="security" class="bg-white border border-slate-200 rounded-xl shadow-sm p-6">
    <div class="bg-indigo-600 text-white rounded-xl p-6 shadow-xl mb-6">
      <h3 class="text-lg font-bold mb-2">Account Security</h3>

      <template v-if="totp.enabled && totp.confirmed">
        <p class="text-sm opacity-90 mb-4">Your security score is looking great!</p>
        <div class="flex items-center gap-2">
          <span class="material-symbols-outlined" style="font-variation-settings: 'FILL' 1;">verified_user</span>
          <span class="text-sm font-bold">No Security Recommendations</span>
        </div>
      </template>

      <template v-else-if="totp.enabled && !totp.confirmed">
        <p class="text-sm opacity-90 mb-4">Scan and confirm Google Authenticator to finish setup.</p>
        <img v-if="qrCodeUrl" :src="qrCodeUrl" class="w-full rounded-lg bg-white p-2 mb-4" alt="QR code" />
        <form @submit.prevent="confirmTotp" class="mb-3">
          <input v-model="totpCode" placeholder="123456" class="w-full outline text-white rounded-lg px-3 py-2 mb-2 focus:outline-white" />
          <button type="submit" class="w-full bg-white text-indigo-600 py-2 rounded-lg font-bold text-sm hover:bg-slate-50">Confirm TOTP</button>
        </form>
        <button @click="disableTotp" class="w-full border border-white/30 text-white py-2 rounded-lg font-bold text-sm hover:bg-white/10">Cancel Setup</button>
      </template>

      <template v-else>
        <p class="text-sm opacity-90 mb-4">Enable two-factor authentication to protect your account.</p>
        <button @click="enableTotp" class="w-full bg-white text-indigo-600 py-2 rounded-lg font-bold text-sm hover:bg-slate-50">Setup Google Authenticator</button>
      </template>
    </div>

    <div class="space-y-4">
      <h3 class="text-lg font-bold flex items-center gap-2">
        <span class="material-symbols-outlined text-indigo-600">key</span>
        Sign-On Methods
      </h3>

      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-slate-400">mail</span>
          <span class="text-sm">Magic Link</span>
        </div>
        <div class="w-10 h-5 bg-indigo-600 rounded-full relative">
          <div class="absolute right-0.5 top-0.5 w-4 h-4 bg-white rounded-full"></div>
        </div>
      </div>

      <div class="flex items-center justify-between cursor-pointer" @click="totp.enabled ? disableTotp() : enableTotp()">
        <div class="flex items-center gap-3">
          <span class="material-symbols-outlined text-slate-400">smartphone</span>
          <span class="text-sm">Google Authenticator</span>
        </div>
        <div :class="['w-10 h-5 rounded-full relative', totp.enabled ? 'bg-indigo-600' : 'bg-slate-200']">
          <div :class="['absolute top-0.5 w-4 h-4 bg-white rounded-full', totp.enabled ? 'right-0.5' : 'left-0.5']"></div>
        </div>
      </div>

      <form @submit.prevent="setPassword" class="space-y-2 pt-4 border-t border-slate-100">
        <input v-model="password" type="password" placeholder="New password" class="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-sm" required />
        <button type="submit" class="w-full bg-indigo-600 text-white py-2 rounded-lg font-bold text-sm hover:bg-indigo-500">Update Password</button>
      </form>

      <p v-if="msg" class="text-green-600 text-sm">{{ msg }}</p>
    </div>
  </section>
</template>
