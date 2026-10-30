<script setup lang="ts">
import AccountProfile from '@/components/dashboard/account/AccountProfile.vue'
import AccountSecurity from '@/components/dashboard/account/AccountSecurity.vue'
import AccountSessions from '@/components/dashboard/account/AccountSessions.vue'
import AccountTenants from '@/components/dashboard/account/AccountTenants.vue'
</script>

<template>
  <div class="space-y-6">
    <!-- Profile and Security side-by-side -->
    <div class="grid grid-cols-12 gap-6">
      <AccountProfile class="col-span-12 lg:col-span-8" />
      <AccountSecurity class="col-span-12 lg:col-span-4" />
    </div>

    <!-- Full-width sections below -->
    <AccountSessions />
    <AccountTenants />
  </div>
</template>
