<script setup lang="ts">
import AppHeader from '../components/AppHeader.vue'
</script>

<template>
  <div class="min-h-screen">
    <AppHeader />
    <div class="flex">
      <aside class="fixed left-0 top-0 h-screen w-64 border-r bg-slate-50 border-slate-200 flex flex-col py-6">
        <div class="px-6 mb-8 flex items-center gap-3">
          <div class="h-8 w-8 bg-indigo-600 rounded-lg flex items-center justify-center">
            <span class="material-symbols-outlined text-white text-xl" style="font-variation-settings: 'FILL' 1;">terminal</span>
          </div>
          <span class="text-xl font-bold text-indigo-600">Jasmine Identity</span>
        </div>

        <nav class="flex-1 px-2 space-y-1 overflow-y-auto">
          <router-link to="/" class="flex items-center gap-3 px-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
            <span class="material-symbols-outlined">apps</span>
            <span>Applications</span>
          </router-link>
          <router-link to="/account" class="flex items-center gap-3 px-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-500 no-underline" active-class="bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 font-semibold">
            <span class="material-symbols-outlined">person</span>
            <span>Profile</span>
          </router-link>
        </nav>

        <div class="px-2 pb-2">
          <router-link to="/console" class="flex items-center gap-3 px-4 py-2.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-400 no-underline">
            <span class="material-symbols-outlined">admin_panel_settings</span>
            <span class="text-sm">Identity Console</span>
          </router-link>
        </div>
      </aside>
      <main class="ml-64 pt-24 pb-12 px-8 flex-1 min-h-screen bg-slate-50">
        <div class="max-w-[1120px] mx-auto">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>
