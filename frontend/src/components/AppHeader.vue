<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const { state, logout: doLogout } = useAuth()

onMounted(async () => {
  // Ensure account is loaded (router guard may have already done this,
  // but if user navigates directly we need a safety net).
  if (!state.account) {
    const { fetchAccount } = useAuth()
    const account = await fetchAccount()
    if (!account) { router.push('/login'); return }
  }
})

function logout() {
  doLogout()
  router.push('/login')
}
</script>

<template>
  <header class="fixed top-0 right-0 left-64 border-b z-10 bg-white/80 backdrop-blur-md border-slate-200 shadow-sm flex justify-between items-center h-16 px-8">
    <div class="font-semibold">
      {{ state.account ? state.account.first_name + ' ' + state.account.last_name : 'My Account' }}
    </div>
    <div class="flex items-center gap-3">
      <button @click="logout" class="text-sm text-slate-600 hover:text-indigo-600">Logout</button>
    </div>
  </header>
</template>
