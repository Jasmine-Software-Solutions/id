<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center">
    <div class="w-full max-w-md">
      <div class="mb-8 text-center">
        <h1 class="text-2xl font-bold text-slate-800">Jasmine Identity</h1>
      </div>

      <div class="bg-white rounded-xl shadow-sm p-8 text-center">
        <div class="text-red-500 mb-4">
          <svg class="w-12 h-12 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M12 15v2m0 0v2m0-2h2m-2 0H10m9.364-10.364A9 9 0 1112 3a9 9 0 017.364 4.636z" />
          </svg>
        </div>

        <h2 class="text-xl font-semibold text-slate-800 mb-3">Account Restricted</h2>
        <p class="text-slate-600 mb-6">
          Your account has been temporarily restricted due to suspicious activity.
          Please contact support to resolve this.
        </p>

        <div v-if="supportEmail || supportPhone" class="bg-slate-50 rounded-lg p-4 space-y-2 text-left">
          <p v-if="supportEmail" class="text-slate-700">
            <span class="font-medium">Email:</span>
            <a :href="`mailto:${supportEmail}`" class="text-indigo-600 hover:underline ml-1">{{ supportEmail }}</a>
          </p>
          <p v-if="supportPhone" class="text-slate-700">
            <span class="font-medium">Phone:</span>
            <a :href="`tel:${supportPhone}`" class="text-indigo-600 hover:underline ml-1">{{ supportPhone }}</a>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const supportEmail = computed(() => import.meta.env.VITE_SUPPORT_EMAIL ?? null)
const supportPhone = computed(() => import.meta.env.VITE_SUPPORT_PHONE ?? null)
</script>
