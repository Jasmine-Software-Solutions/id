import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import './style.css'

import App from './App.vue'
import BlankLayout from './layouts/BlankLayout.vue'
import DashboardLayout from './layouts/DashboardLayout.vue'
import ConsoleLayout from './layouts/ConsoleLayout.vue'

import LoginPage from './pages/LoginPage.vue'
import MagicLinkPage from './pages/MagicLinkPage.vue'
import EmailChangeVerifyPage from './pages/EmailChangeVerifyPage.vue'
import SsoPage from './pages/dashboard/SsoPage.vue'
import AccountPage from './pages/dashboard/AccountPage.vue'
import RolesAdminPage from './pages/console/RolesAdminPage.vue'
import PoliciesAdminPage from './pages/console/PoliciesAdminPage.vue'
import MembershipsAdminPage from './pages/console/MembershipsAdminPage.vue'
import AccountsAdminPage from './pages/console/AccountsAdminPage.vue'
import TenantsAdminPage from './pages/console/TenantsAdminPage.vue'
import ClientsAdminPage from './pages/console/ClientsAdminPage.vue'
import RestrictionsAdminPage from './pages/console/RestrictionsAdminPage.vue'
import RestrictedPage from './pages/RestrictedPage.vue'

import InvitationPage from './pages/InvitationPage.vue'

import { useAuth } from './composables/useAuth'

const { state, fetchAccount } = useAuth()

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: BlankLayout, children: [{ path: '', component: LoginPage }] },
    { path: '/restricted', component: BlankLayout, children: [{ path: '', component: RestrictedPage }] },
    { path: '/magic-links/:id', component: BlankLayout, children: [{ path: '', component: MagicLinkPage }] },
    { path: '/email-change/verify', component: BlankLayout, children: [{ path: '', component: EmailChangeVerifyPage }] },
    { path: '/invitations/:id', component: BlankLayout, children: [{ path: '', component: InvitationPage }] },
    {
      path: '/',
      component: DashboardLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', component: SsoPage },
        { path: 'account', component: AccountPage },
      ],
    },
    {
      path: '/console',
      component: ConsoleLayout,
      meta: { requiresAuth: true },
      children: [
        { path: 'roles', component: RolesAdminPage },
        { path: 'policies', component: PoliciesAdminPage },
        { path: 'memberships', component: MembershipsAdminPage },
        { path: 'accounts', component: AccountsAdminPage },
        { path: 'tenants', component: TenantsAdminPage },
        { path: 'clients', component: ClientsAdminPage },
        { path: 'restrictions', component: RestrictionsAdminPage },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  // Don't redirect away from the restricted page
  if (to.path === '/restricted') return true

  if (!to.matched.some((r) => r.meta.requiresAuth)) return true

  const account = await fetchAccount()
  if (state.restricted) return '/restricted'
  if (!account) return '/login'
  return true
})

createApp(App).use(router).mount('#app')
