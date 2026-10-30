<script setup lang="ts">
import { ref } from 'vue'

const identityOpen = ref(true)
const accessOpen = ref(false)

function toggleIdentity() { identityOpen.value = !identityOpen.value }
function toggleAccess() { accessOpen.value = !accessOpen.value }
</script>

<template>
  <aside class="fixed left-0 top-0 h-screen w-64 border-r bg-slate-50 border-slate-200 flex flex-col py-6">
    <!-- Brand -->
    <div class="px-6 mb-8 flex items-center gap-3">
      <div class="h-8 w-8 bg-indigo-600 rounded-lg flex items-center justify-center">
        <span class="material-symbols-outlined text-white text-xl" style="font-variation-settings: 'FILL' 1;">terminal</span>
      </div>
      <span class="text-xl font-bold text-indigo-600">Jasmine Identity</span>
    </div>

    <nav class="flex-1 px-2 space-y-1 overflow-y-auto">
      <!-- Personal -->
      <div class="px-4 pt-1 pb-2">
        <p class="text-[11px] text-slate-400 uppercase tracking-widest font-semibold">Personal</p>
      </div>
      <router-link to="/" class="flex items-center gap-3 px-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
        <span class="material-symbols-outlined">home</span>
        <span>Home</span>
      </router-link>
      <router-link to="/account" class="flex items-center gap-3 px-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
        <span class="material-symbols-outlined">person</span>
        <span>Profile</span>
      </router-link>

      <!-- Identity (collapsible) -->
      <button @click="toggleIdentity" class="w-full flex items-center justify-between px-4 pt-5 pb-2 hover:bg-slate-100/50 rounded-lg transition-colors">
        <p class="text-[11px] text-slate-400 uppercase tracking-widest font-semibold">Identity</p>
        <span class="material-symbols-outlined text-slate-400 text-sm transition-transform" :class="identityOpen ? 'rotate-0' : '-rotate-90'">expand_more</span>
      </button>
      <div v-show="identityOpen" class="space-y-0.5">
        <router-link to="/accounts" class="flex items-center gap-3 pl-8 pr-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
          <span class="material-symbols-outlined">group</span>
          <span>Accounts</span>
        </router-link>
        <router-link to="/organizations" class="flex items-center gap-3 pl-8 pr-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
          <span class="material-symbols-outlined">apartment</span>
          <span>Organizations</span>
        </router-link>
        <router-link to="/memberships" class="flex items-center gap-3 pl-8 pr-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
          <span class="material-symbols-outlined">corporate_fare</span>
          <span>Memberships</span>
        </router-link>
        <router-link to="/clients" class="flex items-center gap-3 pl-8 pr-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
          <span class="material-symbols-outlined">dns</span>
          <span>Clients</span>
        </router-link>
      </div>

      <!-- Access (collapsible) -->
      <button @click="toggleAccess" class="w-full flex items-center justify-between px-4 pt-5 pb-2 hover:bg-slate-100/50 rounded-lg transition-colors">
        <p class="text-[11px] text-slate-400 uppercase tracking-widest font-semibold">Access</p>
        <span class="material-symbols-outlined text-slate-400 text-sm transition-transform" :class="accessOpen ? 'rotate-0' : '-rotate-90'">expand_more</span>
      </button>
      <div v-show="accessOpen" class="space-y-0.5">
        <router-link to="/roles" class="flex items-center gap-3 pl-8 pr-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
          <span class="material-symbols-outlined">badge</span>
          <span>Roles</span>
        </router-link>
        <router-link to="/console/policies" class="flex items-center gap-3 pl-8 pr-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
          <span class="material-symbols-outlined">policy</span>
          <span>Policies</span>
        </router-link>
        <router-link to="/console/restrictions" class="flex items-center gap-3 pl-8 pr-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
          <span class="material-symbols-outlined">shield</span>
          <span>Restrictions</span>
        </router-link>
      </div>
    </nav>
  </aside>
</template>
