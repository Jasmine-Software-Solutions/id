<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import QrcodeVue from 'qrcode.vue'
import { createHandoffClient } from '@/client'
import type { HandoffQrResponse } from '@/client/types'

/**
 * The embeddable handoff card.
 *
 * Every instance attaches to one shared handoff per ID session and polls its
 * status, while holding its own short-lived QR code. A scan in any instance
 * moves the shared status to `pending_confirmation`; a decision in any instance
 * moves it back or forward for all of them.
 */

type HandoffView =
  | 'loading'
  | 'signed_out'
  | 'awaiting_scan'
  | 'pending_confirmation'
  | 'confirmed'
  | 'superseded'
  | 'ended'
  | 'forbidden'
  | 'invalid'
  | 'error'

const route = useRoute()
const handoffs = createHandoffClient()

const view = ref<HandoffView>('loading')
const handoffId = ref<string | null>(null)
const qrUrl = ref<string | null>(null)
const qrExpiresAt = ref<number | null>(null)
const busy = ref(false)
const qrLoading = ref(false)

const POLL_INTERVAL_MS = 2000
const ROTATION_LEAD_SECONDS = 30

let pollTimer: ReturnType<typeof setInterval> | undefined
let retryTimer: ReturnType<typeof setInterval> | undefined
let retryInFlight = false

/** Only a plain string is a parameter; repeated params parse to arrays. */
function stringParam(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null
}

const clientId = stringParam(route.query.client_id)
const redirectUri = stringParam(route.query.redirect_uri)
const state = stringParam(route.query.state)
const scope = stringParam(route.query.scope)
const nonce = stringParam(route.query.nonce)

/**
 * Signing in happens top-level in a popup, which comes back to this exact
 * handoff URL. The hash names the page and carries its parameters, and the
 * pathname stays `/embed` (inside the iframe) so the return trip keeps the
 * framing permission.
 */
const signInUrl = computed(() => {
  const returnUrl = window.location.href
  return `${window.location.origin}/#/login?return_url=${encodeURIComponent(returnUrl)}`
})

function statusOf(error: unknown): number {
  return (error as { status?: number } | null)?.status ?? 0
}

function nowSeconds(): number {
  return Date.now() / 1000
}

function stopPolling() {
  if (pollTimer !== undefined) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

function stopRetry() {
  if (retryTimer !== undefined) {
    clearInterval(retryTimer)
    retryTimer = undefined
  }
}

function stopAll() {
  stopPolling()
  stopRetry()
}

function applyStatus(status: string) {
  switch (status) {
    case 'awaiting_scan':
      view.value = 'awaiting_scan'
      break
    case 'pending_confirmation':
      view.value = 'pending_confirmation'
      break
    case 'confirmed':
    case 'claimed':
      view.value = 'confirmed'
      break
    case 'superseded':
      view.value = 'superseded'
      stopAll()
      break
    case 'expired':
      view.value = 'ended'
      stopAll()
      break
    default:
      view.value = 'error'
  }
}

function applyQrResponse(response: HandoffQrResponse) {
  qrUrl.value = response.qr_url
  qrExpiresAt.value = response.qr_expires_at
  applyStatus(response.status)
}

function applyCreatedQr(qrUrlValue: string | null, qrExpires: number | null) {
  if (qrUrlValue) {
    qrUrl.value = qrUrlValue
    qrExpiresAt.value = qrExpires
  }
}

async function connect() {
  if (retryInFlight || !clientId || !redirectUri) return
  retryInFlight = true

  try {
    const response = await handoffs.createOrAttach({
      client_id: clientId,
      redirect_uri: redirectUri,
      ...(state ? { state } : {}),
      ...(scope ? { scope } : {}),
      ...(nonce ? { nonce } : {}),
    })

    stopRetry()
    handoffId.value = response.handoff_id
    applyCreatedQr(response.qr_url, response.qr_expires_at)
    applyStatus(response.status)
    startPolling()
  } catch (error) {
    const status = statusOf(error)
    if (status === 401) {
      // No ID cookies reach this frame yet. Keep retrying: the Log in popup
      // sets them first-party, and a same-site embed then picks them up.
      view.value = 'signed_out'
      startRetry()
    } else if (status === 403) {
      view.value = 'forbidden'
      stopAll()
    } else if (status === 400 || status === 404) {
      view.value = 'invalid'
      stopAll()
    } else {
      view.value = 'error'
    }
  } finally {
    retryInFlight = false
  }
}

function startRetry() {
  stopRetry()
  retryTimer = setInterval(() => {
    if (view.value === 'signed_out') void connect()
  }, POLL_INTERVAL_MS)
}

function startPolling() {
  if (pollTimer !== undefined) return
  pollTimer = setInterval(() => void poll(), POLL_INTERVAL_MS)
}

async function poll() {
  if (!handoffId.value) return

  const previous = view.value

  try {
    const response = await handoffs.status(handoffId.value)

    applyStatus(response.status)

    if (response.status !== 'awaiting_scan') return

    // Returning to the QR state — after a decline in another tab or Change
    // devices — means this instance's old code is stale. While already
    // awaiting, rotate before expiry so a scan never meets a dead code.
    const needsFresh = previous !== 'awaiting_scan' || qrUrl.value === null
    const expiring =
      qrExpiresAt.value !== null &&
      qrExpiresAt.value - nowSeconds() <= ROTATION_LEAD_SECONDS

    if (needsFresh || expiring) await refreshQr()
  } catch (error) {
    const status = statusOf(error)
    if (status === 401 || status === 404 || status === 410) {
      view.value = 'ended'
      stopAll()
    }
    // Other failures are transient; the next tick retries.
  }
}

async function refreshQr() {
  if (!handoffId.value || qrLoading.value) return
  qrLoading.value = true

  try {
    const response = await handoffs.issueQr(handoffId.value)
    qrUrl.value = response.qr_url
    qrExpiresAt.value = response.qr_expires_at
    if (view.value !== 'awaiting_scan') applyStatus(response.status)
  } catch (error) {
    const status = statusOf(error)
    if (status === 409) {
      // A scan won the race with rotation; pick the shared state up.
      await poll()
    } else if (status === 410) {
      view.value = 'ended'
      stopAll()
    } else if (status === 401) {
      view.value = 'ended'
      stopAll()
    }
  } finally {
    qrLoading.value = false
  }
}

async function handleActionError(error: unknown) {
  const status = statusOf(error)
  if (status === 409) {
    await poll()
  } else if (status === 410 || status === 401) {
    view.value = 'ended'
    stopAll()
  } else if (status === 403) {
    view.value = 'forbidden'
    stopAll()
  } else {
    view.value = 'error'
  }
}

async function rejectScan() {
  if (busy.value || !handoffId.value) return
  busy.value = true
  try {
    applyQrResponse(await handoffs.reject(handoffId.value))
  } catch (error) {
    await handleActionError(error)
  } finally {
    busy.value = false
  }
}

async function confirmScan() {
  if (busy.value || !handoffId.value) return
  busy.value = true
  try {
    const response = await handoffs.confirm(handoffId.value)
    applyStatus(response.status)
  } catch (error) {
    await handleActionError(error)
  } finally {
    busy.value = false
  }
}

async function changeDevices() {
  if (busy.value || !handoffId.value) return
  busy.value = true
  try {
    applyQrResponse(await handoffs.changeDevices(handoffId.value))
  } catch (error) {
    await handleActionError(error)
  } finally {
    busy.value = false
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') void poll()
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)

  if (!clientId || !redirectUri) {
    view.value = 'invalid'
    return
  }

  void connect()
})

onUnmounted(() => {
  stopAll()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4">
    <!-- Fixed geometry: the square and the text area never change size, so the
         iframe never reflows as the state changes. -->
    <div class="w-[340px] bg-white rounded-xl shadow-sm p-6">
      <div class="h-[220px] flex items-center justify-center">
        <QrcodeVue
          v-if="view === 'awaiting_scan' && qrUrl"
          :value="qrUrl"
          :size="200"
          level="M"
        />
        <span
          v-else-if="view === 'loading' || (view === 'awaiting_scan' && !qrUrl)"
          class="material-symbols-outlined animate-spin text-slate-300 text-5xl"
        >progress_activity</span>
        <span
          v-else-if="view === 'signed_out' || view === 'ended'"
          class="material-symbols-outlined text-slate-400 text-6xl"
        >lock</span>
        <span
          v-else-if="view === 'pending_confirmation'"
          class="material-symbols-outlined text-amber-500 text-6xl"
        >warning</span>
        <span
          v-else-if="view === 'confirmed'"
          class="material-symbols-outlined text-green-500 text-6xl"
        >check_circle</span>
        <span
          v-else-if="view === 'superseded'"
          class="material-symbols-outlined text-slate-400 text-6xl"
        >swap_horiz</span>
        <span
          v-else
          class="material-symbols-outlined text-slate-400 text-6xl"
        >error</span>
      </div>

      <div class="min-h-[120px] flex flex-col items-center justify-center text-center gap-3">
        <p v-if="view === 'loading'" class="text-sm text-slate-500">Loading…</p>

        <template v-else-if="view === 'signed_out'">
          <h3 class="text-lg font-bold text-slate-800">Open on your phone</h3>
          <p class="text-sm text-slate-500">
            To open this page on your phone, click the button below and scan the QR code.
          </p>
          <a
            :href="signInUrl"
            target="_blank"
            rel="noopener"
            class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-500 transition-colors"
          >Show QR</a>
        </template>

        <template v-else-if="view === 'awaiting_scan'">
          <h3 class="text-lg font-bold text-slate-800">Open on your phone</h3>
          <p class="text-sm text-slate-500">
            Scan the QR code above to open this page on your phone.
          </p>
        </template>

        <template v-else-if="view === 'pending_confirmation'">
          <h3 class="text-lg font-bold text-slate-800">Did you just scan the QR code?</h3>
          <div class="flex items-center gap-3">
            <button
              :disabled="busy"
              class="px-5 py-2 bg-slate-100 text-slate-700 text-sm font-medium rounded-lg hover:bg-slate-200 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              @click="rejectScan"
            >No</button>
            <button
              :disabled="busy"
              class="px-5 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              @click="confirmScan"
            >Yes</button>
          </div>
        </template>

        <template v-else-if="view === 'confirmed'">
          <h3 class="text-lg font-bold text-slate-800">Signed in on your other device</h3>
          <button
            :disabled="busy"
            class="px-4 py-2 bg-slate-100 text-slate-700 text-sm font-medium rounded-lg hover:bg-slate-200 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            @click="changeDevices"
          >Change devices</button>
        </template>

        <p v-else-if="view === 'superseded'" class="text-sm text-slate-500">
          This handoff was replaced in another tab.
        </p>

        <template v-else-if="view === 'ended'">
          <p class="text-sm font-bold text-slate-800">Your ID session ended</p>
          <p class="text-sm text-slate-500">Sign in again to hand this sign-in to another device.</p>
          <a
            :href="signInUrl"
            target="_blank"
            rel="noopener"
            class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-500 transition-colors"
          >Log in</a>
        </template>

        <p v-else-if="view === 'forbidden'" class="text-sm text-slate-500">
          Handoff isn't enabled for this app.
        </p>

        <p v-else-if="view === 'invalid'" class="text-sm text-slate-500">
          This handoff link is invalid or has expired.
        </p>

        <p v-else class="text-sm text-slate-500">
          Something went wrong. Please reload the page.
        </p>
      </div>
    </div>
  </div>
</template>
