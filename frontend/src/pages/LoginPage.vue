<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFlow } from '../composables/useFlow'
import { useAuth } from '../composables/useAuth'
import EnterEmail from '../components/login/EnterEmail.vue'
import EnterName from '../components/login/EnterName.vue'
import EnterPassword from '../components/login/EnterPassword.vue'
import EnterTotp from '../components/login/EnterTotp.vue'
import PollMagicLink from '../components/login/PollMagicLink.vue'

const route = useRoute()
const router = useRouter()
const { step, loading, error, start, next, instead } = useFlow()
const { fetchAccount } = useAuth()

// Capture return_url from query params before starting the flow.
// This preserves OAuth authorize redirects and other post-login destinations.
const returnUrl = (route.query.return_url as string) || null

// The referring product's own sentence for why the email address is being
// asked for, carried here by the authorize endpoint. Only the string form is
// accepted: a repeated parameter (`?referrer_hint=a&referrer_hint=b`) parses to
// an array, and joining it would invent a sentence nobody sent. Capped here as
// well as server-side because this route can be opened directly, without the
// authorize endpoint in between.
const rawHint = route.query.referrer_hint
const referrerHint =
  typeof rawHint === 'string' && rawHint.trim() ? rawHint.trim().slice(0, 500) : null

// Silent SSO.
//
// A product reaches this page by way of the OAuth authorize endpoint, which
// redirects here whenever it cannot see a session — and on this one hop it
// cannot. `session_id` is SameSite=Strict, so the browser withholds it when the
// navigation to Identity was started by *another* product's page, which is
// exactly what a route guard's `location.href` is. This page is same-origin, so
// its own request does carry the cookie: the check the authorize endpoint could
// not make gets made here, and somebody who is already signed in goes straight
// back out rather than being asked to sign in again.
//
// Only with a return_url — without somewhere to go, showing the form is right.
// It is how a signed-in person switches accounts.
const checking = ref(returnUrl !== null)

onMounted(async () => {
  if (returnUrl && (await fetchAccount())) {
    // The same session lookup the authorize endpoint performs, so the two agree;
    // if the forward does not take, it is because no session authorize accepts.
    window.location.href = returnUrl
    return
  }
  checking.value = false
  await start()
})

async function handleNext(response: Record<string, string>) {
  const result = await next(response)
  if (result.success) {
    if (returnUrl) {
      // Redirect back to the original URL (e.g. OAuth authorize endpoint)
      window.location.href = returnUrl
    } else {
      router.push('/')
    }
  }
}

async function handleInstead(stepFqdn: string) {
  await instead(stepFqdn)
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center">
    <div class="w-full max-w-md">
      <!-- No subtitle. "Sign in to your account" described the old page only: this
           one is where an account starts as much as where one is signed into, and
           the welcome that says so lives on the email step, where it is true for
           the person reading it (`EnterEmail`). -->
      <div class="mb-8 text-center">
        <h1 class="text-2xl font-bold text-slate-800">Jasmine Identity</h1>
      </div>

      <div v-if="checking || (loading && !step)">
        <EnterEmail :disabled="true" :hint="referrerHint" />
      </div>

      <div v-else-if="error" class="bg-white rounded-xl shadow-sm p-8">
        <p class="text-red-600 text-center mb-4">The credentials you entered are incorrect.</p>
        <button @click="start" class="w-full bg-indigo-600 text-white py-2 rounded-lg font-bold hover:bg-indigo-500 transition-colors">Try again</button>
      </div>

      <div v-else-if="step">
        <EnterEmail v-if="step.includes('enter_email_address')"
          :hint="referrerHint"
          @submit="handleNext" />
        <EnterPassword v-else-if="step.includes('enter_password')"
          @submit="handleNext" />
        <EnterName v-else-if="step.includes('enter_name')"
          @submit="handleNext" />
        <EnterTotp v-else-if="step.includes('enter_totp')"
          @submit="handleNext" />
        <PollMagicLink v-else-if="step.includes('poll_magic_link')"
          @submit="handleNext"
          @instead="handleInstead" />
      </div>
    </div>
  </div>
</template>
