<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { createHandoffClient } from '@/client'

/**
 * The page the scanning device lands on after reading the QR code.
 *
 * Public by design: the device has no ID session. The QR token proves the scan,
 * and the resulting scan token — kept in sessionStorage so a reload resumes —
 * is the only credential the status and claim calls carry. This page never
 * renders a QR code.
 */

type ScanView =
  | 'loading'
  | 'waiting'
  | 'claiming'
  | 'rejected'
  | 'superseded'
  | 'expired'
  | 'already_scanned'
  | 'already_claimed'
  | 'forbidden'
  | 'invalid'
  | 'error'

const route = useRoute()
const handoffs = createHandoffClient()

const view = ref<ScanView>('loading')

const SCAN_KEY = 'id.handoff.scan'
const REDIRECT_KEY = 'id.handoff.redirect'
const POLL_INTERVAL_MS = 2000

let handoffId: string | null = null
let scanToken: string | null = null
let pollTimer: ReturnType<typeof setInterval> | undefined
let claiming = false

interface StoredScan {
  handoffId: string
  /** The QR token this scan came from, so a reload can tell resume from re-scan. */
  qrToken: string
  scanToken: string
}

function stringParam(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null
}

function statusOf(error: unknown): number {
  return (error as { status?: number } | null)?.status ?? 0
}

function storeScan(qrToken: string) {
  if (!handoffId || !scanToken) return
  try {
    sessionStorage.setItem(
      SCAN_KEY,
      JSON.stringify({ handoffId, qrToken, scanToken } satisfies StoredScan),
    )
  } catch {
    // Private-mode storage failures only cost the reload convenience.
  }
}

function restoreScan(): StoredScan | null {
  try {
    const raw = sessionStorage.getItem(SCAN_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<StoredScan>
    if (
      typeof parsed.handoffId !== 'string' ||
      typeof parsed.qrToken !== 'string' ||
      typeof parsed.scanToken !== 'string'
    ) return null
    return { handoffId: parsed.handoffId, qrToken: parsed.qrToken, scanToken: parsed.scanToken }
  } catch {
    return null
  }
}

function stopPolling() {
  if (pollTimer !== undefined) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => void poll(), POLL_INTERVAL_MS)
}

async function beginScan(id: string, token: string) {
  handoffId = id
  view.value = 'loading'

  try {
    const response = await handoffs.scan(id, token)
    scanToken = response.scan_token
    storeScan(token)
    view.value = 'waiting'
    startPolling()
  } catch (error) {
    const status = statusOf(error)
    if (status === 409) view.value = 'already_scanned'
    else if (status === 410) view.value = 'expired'
    else if (status === 404 || status === 400) view.value = 'invalid'
    else view.value = 'error'
  }
}

function resumeScan(stored: StoredScan) {
  handoffId = stored.handoffId
  scanToken = stored.scanToken
  view.value = 'waiting'
  startPolling()
  void poll()
}

async function poll() {
  if (!handoffId || !scanToken || claiming) return

  try {
    const response = await handoffs.scanStatus(handoffId, scanToken)

    switch (response.status) {
      case 'pending':
        view.value = 'waiting'
        break
      case 'confirmed':
        await claim()
        break
      case 'claimed':
        view.value = 'already_claimed'
        stopPolling()
        break
      case 'rejected':
        view.value = 'rejected'
        stopPolling()
        break
      case 'superseded':
        view.value = 'superseded'
        stopPolling()
        break
      case 'expired':
        view.value = 'expired'
        stopPolling()
        break
    }
  } catch (error) {
    const status = statusOf(error)
    if (status === 404 || status === 410) {
      view.value = status === 410 ? 'expired' : 'invalid'
      stopPolling()
    }
    // Other failures are transient; the next tick retries.
  }
}

async function claim() {
  if (!handoffId || !scanToken || claiming) return
  claiming = true
  stopPolling()
  view.value = 'claiming'

  try {
    const response = await handoffs.claim(handoffId, scanToken)
    try {
      sessionStorage.setItem(REDIRECT_KEY, response.redirect_url)
    } catch {
      // The navigation below still works without the reload copy.
    }
    window.location.assign(response.redirect_url)
  } catch (error) {
    const status = statusOf(error)
    if (status === 409) view.value = 'already_claimed'
    else if (status === 410) view.value = 'expired'
    else if (status === 403) view.value = 'forbidden'
    else view.value = 'error'
  } finally {
    claiming = false
  }
}

function retry() {
  if (claiming) return

  // Resume wherever the flow got stuck: a stored scan polls (and claims once
  // confirmed), otherwise the original QR code starts the scan over.
  const stored = restoreScan()
  if (stored) {
    resumeScan(stored)
    return
  }

  const queryHandoffId = stringParam(route.query.h)
  const queryToken = stringParam(route.query.t)
  if (queryHandoffId && queryToken) {
    void beginScan(queryHandoffId, queryToken)
    return
  }

  view.value = 'invalid'
}

onMounted(() => {
  // A claim that completed but lost the navigation (the page died in between)
  // leaves the redirect here; finish the trip rather than scanning again.
  try {
    const redirect = sessionStorage.getItem(REDIRECT_KEY)
    if (redirect) {
      window.location.assign(redirect)
      return
    }
  } catch {
    // Fall through to the normal flow.
  }

  const queryHandoffId = stringParam(route.query.h)
  const queryToken = stringParam(route.query.t)
  const stored = restoreScan()

  if (queryHandoffId && queryToken) {
    // A reload keeps the same h/t in the URL. Resume the scan already made from
    // this exact QR code instead of re-scanning it (which would report "already
    // scanned"); a *different* t — a fresh QR after a decline — starts over.
    if (stored && stored.handoffId === queryHandoffId && stored.qrToken === queryToken) {
      resumeScan(stored)
      return
    }

    void beginScan(queryHandoffId, queryToken)
    return
  }

  if (stored) {
    resumeScan(stored)
    return
  }

  view.value = 'invalid'
})

onUnmounted(stopPolling)
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4">
    <div class="w-[340px] bg-white rounded-xl shadow-sm p-6 text-center">
      <div class="mb-4 flex items-center justify-center h-[64px]">
        <span
          v-if="view === 'loading' || view === 'claiming'"
          class="material-symbols-outlined animate-spin text-indigo-500 text-5xl"
        >progress_activity</span>
        <span
          v-else-if="view === 'waiting'"
          class="material-symbols-outlined text-indigo-500 text-5xl"
        >phonelink</span>
        <span
          v-else-if="view === 'already_claimed'"
          class="material-symbols-outlined text-green-500 text-5xl"
        >check_circle</span>
        <span
          v-else
          class="material-symbols-outlined text-slate-400 text-5xl"
        >error</span>
      </div>

      <template v-if="view === 'loading'">
        <p class="text-sm text-slate-500">Checking this QR code…</p>
      </template>

      <template v-else-if="view === 'waiting'">
        <h3 class="text-lg font-bold text-slate-800 mb-1">Waiting for confirmation</h3>
        <p class="text-sm text-slate-500">
          Confirm the sign-in on your other device to continue.
        </p>
      </template>

      <template v-else-if="view === 'claiming'">
        <h3 class="text-lg font-bold text-slate-800 mb-1">Signing you in…</h3>
        <p class="text-sm text-slate-500">This will only take a moment.</p>
      </template>

      <template v-else-if="view === 'rejected'">
        <h3 class="text-lg font-bold text-slate-800 mb-1">Sign-in declined</h3>
        <p class="text-sm text-slate-500">
          The request was declined. Scan the current QR code from your other device.
        </p>
      </template>

      <template v-else-if="view === 'superseded'">
        <h3 class="text-lg font-bold text-slate-800 mb-1">Request replaced</h3>
        <p class="text-sm text-slate-500">
          This request was replaced. Scan the current QR code from your other device.
        </p>
      </template>

      <template v-else-if="view === 'expired'">
        <h3 class="text-lg font-bold text-slate-800 mb-1">This request expired</h3>
        <p class="text-sm text-slate-500">
          Scan the current QR code from your other device to try again.
        </p>
      </template>

      <template v-else-if="view === 'already_scanned'">
        <h3 class="text-lg font-bold text-slate-800 mb-1">Already scanned</h3>
        <p class="text-sm text-slate-500">
          This code has already been scanned. Check the other tab where it was opened.
        </p>
      </template>

      <template v-else-if="view === 'already_claimed'">
        <h3 class="text-lg font-bold text-slate-800 mb-1">Already signed in</h3>
        <p class="text-sm text-slate-500">
          This sign-in was already completed. You can close this page.
        </p>
      </template>

      <template v-else-if="view === 'forbidden'">
        <h3 class="text-lg font-bold text-slate-800 mb-1">Handoff isn't available</h3>
        <p class="text-sm text-slate-500">
          Handoff isn't enabled for this app. Sign in on your other device instead.
        </p>
      </template>

      <template v-else-if="view === 'invalid'">
        <h3 class="text-lg font-bold text-slate-800 mb-1">Invalid QR code</h3>
        <p class="text-sm text-slate-500">
          This QR code is invalid or has expired. Scan the current code from your other device.
        </p>
      </template>

      <template v-else>
        <h3 class="text-lg font-bold text-slate-800 mb-1">Something went wrong</h3>
        <p class="text-sm text-slate-500 mb-3">
          The sign-in could not be completed. You can try again.
        </p>
        <button
          :disabled="claiming"
          class="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          @click="retry"
        >Try again</button>
      </template>
    </div>
  </div>
</template>
