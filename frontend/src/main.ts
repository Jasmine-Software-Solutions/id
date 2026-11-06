import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
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
import HandoffPage from './pages/HandoffPage.vue'
import HandoffScanPage from './pages/HandoffScanPage.vue'
import EmbedInvalidPage from './pages/EmbedInvalidPage.vue'

import { useAuth } from './composables/useAuth'

const { state, fetchAccount } = useAuth()

// The embed URL is `/embed/#/<page>?...`. The edge only sees the pathname, so
// `/embed` is the one prefix that may be framed; the hash names the embedded
// page (today only `/handoff`) and carries its parameters:
//
//   https://id.jasmine.software/embed/#/handoff?client_id=…&redirect_uri=…
//
// The pathname is deliberately left alone: a reload inside the iframe must
// request `/embed` again, or it would land on `/` and lose both the framing
// permission and the parameters.
const requestedPath = window.location.pathname
const isEmbedPath = requestedPath === '/embed' || requestedPath.startsWith('/embed/')

// The full application. `/handoff` remains a top-level route (its hash form is
// useful for links); it is not frameable.
const appRoutes: RouteRecordRaw[] = [
  { path: '/login', component: BlankLayout, children: [{ path: '', component: LoginPage }] },
  { path: '/restricted', component: BlankLayout, children: [{ path: '', component: RestrictedPage }] },
  { path: '/magic-links/:id', component: BlankLayout, children: [{ path: '', component: MagicLinkPage }] },
  { path: '/email-change/verify', component: BlankLayout, children: [{ path: '', component: EmailChangeVerifyPage }] },
  { path: '/invitations/:id', component: BlankLayout, children: [{ path: '', component: InvitationPage }] },
  {
    path: '/handoff',
    component: BlankLayout,
    children: [
      { path: '', component: HandoffPage },
      { path: 'scan', component: HandoffScanPage },
    ],
  },
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
]

// Everything served under the frameable `/embed` prefix can only render embed
// UI. A framed `/embed#/console/clients` therefore cannot reach the console:
// those routes are not registered here, and the catch-all shows an invalid
// embed instead. The scan page is deliberately absent — it runs top-level on
// the scanning device and must not be frameable.
const embedRoutes: RouteRecordRaw[] = [
  {
    path: '/handoff',
    component: BlankLayout,
    children: [{ path: '', component: HandoffPage }],
  },
  { path: '/:pathMatch(.*)*', component: EmbedInvalidPage },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes: isEmbedPath ? embedRoutes : appRoutes,
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
