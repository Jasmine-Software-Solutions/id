<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  client?: { id: string; fqdn: string; confidential: boolean; redirect_uris: string[]; name?: string | null; logo_uri?: string | null; initiate_login_uri?: string | null } | null
  error?: string | null
}>()

const fqdn = ref('')
const confidential = ref(true)
const redirectUris = ref('')
const displayName = ref('')
const logoUri = ref('')
const initiateLoginUri = ref('')

watch(() => props.client, (c) => {
  if (c) {
    fqdn.value = c.fqdn
    confidential.value = c.confidential
    redirectUris.value = (c.redirect_uris || []).join('\n')
    displayName.value = c.name || ''
    logoUri.value = c.logo_uri || ''
    initiateLoginUri.value = c.initiate_login_uri || ''
  } else {
    fqdn.value = ''
    confidential.value = true
    redirectUris.value = ''
    displayName.value = ''
    logoUri.value = ''
    initiateLoginUri.value = ''
  }
}, { immediate: true })

const emit = defineEmits<{
  save: [fqdn: string, confidential: boolean, redirectUris: string[], displayName: string, logoUri: string, initiateLoginUri: string]
  cancel: []
}>()

function handleSave() {
  if (!fqdn.value.trim()) return
  const uris = redirectUris.value
    .split('\n')
    .map(s => s.trim())
    .filter(s => s.length > 0)
  emit('save', fqdn.value.trim(), confidential.value, uris, displayName.value.trim(), logoUri.value.trim(), initiateLoginUri.value.trim())
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="emit('cancel')">
    <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
      <h3 class="text-lg font-bold mb-4">{{ client ? 'Edit client' : 'Create client' }}</h3>
      <div class="space-y-4">
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">FQDN</label>
          <input v-model="fqdn" type="text" placeholder="e.g. my-service.jasmine.software"
            class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm"
            :disabled="!!client"
            :class="{ 'bg-slate-50 text-slate-500': !!client }" />
          <p v-if="client" class="text-xs text-slate-400 mt-1">Fully qualified domain name cannot be changed after creation.</p>
        </div>
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Display Name <span class="text-slate-400">(optional, human-readable)</span></label>
          <input v-model="displayName" type="text" placeholder="e.g. My Service"
            class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          <p class="text-xs text-slate-400 mt-1">Shown on the SSO page. Defaults to FQDN if left blank.</p>
        </div>
        <div v-if="!client" class="flex items-center gap-2">
          <input v-model="confidential" type="checkbox" id="conf" class="rounded" />
          <label for="conf" class="text-sm text-slate-600">Confidential client (server-side, can keep a secret)</label>
        </div>
        <div v-if="client" class="text-sm text-slate-500">
          Type: <strong>{{ client.confidential ? 'Confidential' : 'Public' }}</strong>
        </div>
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Logo URI <span class="text-slate-400">(optional)</span></label>
          <input v-model="logoUri" type="text" placeholder="https://example.com/logo.png"
            class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
        </div>
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Initiate Login URI <span class="text-slate-400">(optional, enables SSO tile)</span></label>
          <input v-model="initiateLoginUri" type="text" placeholder="https://example.com/oauth/authorize?..."
            class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm" />
          <p class="text-xs text-slate-400 mt-1">When set, this client appears on the account SSO page.</p>
        </div>
        <div>
          <label class="block text-xs font-medium text-slate-600 mb-1">Redirect URIs (one per line)</label>
          <textarea v-model="redirectUris" rows="3"
            placeholder="https://example.com/oauth/callback"
            class="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm font-mono"></textarea>
        </div>
      </div>
      <div v-if="error" class="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{{ error }}</div>
      <div class="flex gap-2 mt-6">
        <button @click="handleSave" class="flex-1 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">
          {{ client ? 'Save' : 'Create' }}
        </button>
        <button @click="emit('cancel')" class="flex-1 px-4 py-2 border border-slate-300 text-slate-600 text-sm rounded-lg hover:bg-slate-100">Cancel</button>
      </div>
    </div>
  </div>
</template>
