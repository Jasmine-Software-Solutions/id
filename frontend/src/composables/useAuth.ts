import { reactive } from 'vue'
import { createIdClient } from '@/client'
import type { Account } from '@/client/types'
import { RestrictedError } from '@/client/types'

const state = reactive({
  account: null as Account | null,
  client: createIdClient({ accountId: '' }),
  loading: false,
  error: null as number | null,
  restricted: false,
})

async function fetchAccount(): Promise<Account | null> {
  state.loading = true
  try {
    const r = await fetch('/api/v1/accounts/me')
    if (!r.ok) {
      state.account = null
      state.error = r.status
      state.restricted = r.headers.get('X-ID-Restricted') === 'true'
      return null
    }
    const account: Account = await r.json()
    state.account = account
    state.client = createIdClient({ accountId: account.id })
    state.error = null
    state.restricted = false
    return account
  } catch (e: unknown) {
    state.account = null
    state.error = (e as { status?: number })?.status ?? 0
    state.restricted = e instanceof RestrictedError
    return null
  } finally {
    state.loading = false
  }
}

async function logout() {
  // Revoke the current session on the server
  const sessionId = getCookie('session_id')
  const sessionToken = getCookie('session_token')
  if (sessionId && sessionToken) {
    try {
      await fetch(`/api/v1/accounts/${state.account?.id ?? 'me'}/sessions/${sessionId}/revoke`, { method: 'POST' })
    } catch { /* best effort */ }
  }

  document.cookie = 'session_id=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
  document.cookie = 'session_token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
  state.account = null
  state.client = createIdClient({ accountId: '' })
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? match[1] : null
}

/** Shared reactive auth store. Components should use `toRefs(useAuth())` or access properties directly. */
export function useAuth() {
  return { state, fetchAccount, logout }
}
