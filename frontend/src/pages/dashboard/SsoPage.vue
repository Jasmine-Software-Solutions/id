<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuth } from '@/composables/useAuth'
import type { SsoClientInfo, SsoPreferences } from '@/client/types'
import ErrorBanner from '@/components/ErrorBanner.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const { state } = useAuth()

const clients = ref<SsoClientInfo[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const favorites = ref<Set<string>>(new Set())
const clientOrder = ref<string[]>([])
const savingError = ref<string | null>(null)

// ---------------------------------------------------------------------------
// Data fetching
// ---------------------------------------------------------------------------

async function fetchSsoClients() {
  loading.value = true
  error.value = null
  try {
    const r = await fetch('/api/v1/accounts/me/sso-clients')
    if (!r.ok) throw new Error('Failed to load SSO clients')
    clients.value = await r.json()
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? 'Failed to load applications'
  } finally {
    loading.value = false
  }
}

async function fetchPreferences() {
  try {
    const r = await fetch('/api/v1/accounts/me/sso-preferences')
    if (r.ok) {
      const prefs: SsoPreferences = await r.json()
      favorites.value = new Set(prefs.favorites || [])
      clientOrder.value = prefs.order || []
    }
  } catch {
    // Preferences not critical — use defaults
  }
}

// ---------------------------------------------------------------------------
// Preferences persistence
// ---------------------------------------------------------------------------

async function savePreferences() {
  savingError.value = null
  try {
    await fetch('/api/v1/accounts/me/sso-preferences', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        favorites: Array.from(favorites.value),
        order: clientOrder.value,
      }),
    })
  } catch (e: unknown) {
    savingError.value = (e as { message?: string })?.message ?? 'Failed to save preferences'
  }
}

// ---------------------------------------------------------------------------
// Favorites
// ---------------------------------------------------------------------------

function toggleFavorite(clientId: string) {
  const f = new Set(favorites.value)
  if (f.has(clientId)) {
    f.delete(clientId)
  } else {
    f.add(clientId)
  }
  favorites.value = f
  savePreferences()
}

// ---------------------------------------------------------------------------
// Drag-and-drop reordering
// ---------------------------------------------------------------------------

const dragOverClientId = ref<string | null>(null)

function onDragStart(e: DragEvent, clientId: string) {
  e.dataTransfer!.effectAllowed = 'move'
  e.dataTransfer!.setData('text/plain', clientId)
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  e.dataTransfer!.dropEffect = 'move'
}

function onDragEnter(e: DragEvent, clientId: string) {
  e.preventDefault()
  dragOverClientId.value = clientId
}

function onDragLeave() {
  dragOverClientId.value = null
}

function onDrop(e: DragEvent, targetClientId: string) {
  e.preventDefault()
  dragOverClientId.value = null
  const draggedId = e.dataTransfer!.getData('text/plain')
  if (!draggedId || draggedId === targetClientId) return

  const ids = sortedClients.value.map(c => c.client_id)
  const fromIdx = ids.indexOf(draggedId)
  const toIdx = ids.indexOf(targetClientId)
  if (fromIdx === -1) return

  const reordered = [...ids]
  reordered.splice(fromIdx, 1)
  reordered.splice(toIdx, 0, draggedId)
  clientOrder.value = reordered
  savePreferences()
}

// ---------------------------------------------------------------------------
// Sorted clients (favorites first, then custom order, then default)
// ---------------------------------------------------------------------------

const sortedClients = computed(() => {
  const all = [...clients.value]

  // Build order maps
  const favSet = new Set(favorites.value)
  const orderMap = new Map<string, number>()
  clientOrder.value.forEach((id, i) => orderMap.set(id, i))

  return all.sort((a, b) => {
    // Favorites first
    const aFav = favSet.has(a.client_id)
    const bFav = favSet.has(b.client_id)
    if (aFav && !bFav) return -1
    if (!aFav && bFav) return 1

    // Within same fav group, use custom order if available
    const aOrder = orderMap.get(a.client_id)
    const bOrder = orderMap.get(b.client_id)
    if (aOrder != null && bOrder != null) return aOrder - bOrder
    if (aOrder != null) return -1
    if (bOrder != null) return 1

    // Default: alphabetical by displayName
    return (a.name ?? a.fqdn).localeCompare(b.name ?? b.fqdn)
  })
})

// ---------------------------------------------------------------------------
// Color generation for initial avatars
// ---------------------------------------------------------------------------

const avatarColors = [
  'bg-indigo-500', 'bg-emerald-500', 'bg-amber-500', 'bg-rose-500',
  'bg-cyan-500', 'bg-violet-500', 'bg-teal-500', 'bg-orange-500',
]

function avatarColor(clientId: string): string {
  let hash = 0
  for (let i = 0; i < clientId.length; i++) {
    hash = clientId.charCodeAt(i) + ((hash << 5) - hash)
  }
  return avatarColors[Math.abs(hash) % avatarColors.length]
}

function initial(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) return '?'
  return trimmed.charAt(0).toUpperCase()
}

// ---------------------------------------------------------------------------
// Init
// ---------------------------------------------------------------------------

onMounted(async () => {
  await Promise.all([fetchSsoClients(), fetchPreferences()])
})
</script>

<template>
  <div>
    <div class="mb-8">
      <h1 class="text-2xl font-bold text-slate-900">Applications</h1>
      <p class="text-sm text-slate-500 mt-1">Single sign-on to your connected services.</p>
    </div>

    <ErrorBanner v-model:error="error" />

    <LoadingSpinner v-if="loading" />

    <!-- Empty state -->
    <div v-else-if="clients.length === 0" class="text-center py-16">
      <div class="h-16 w-16 mx-auto mb-4 bg-slate-100 rounded-2xl flex items-center justify-center">
        <span class="material-symbols-outlined text-slate-400 text-3xl">apps</span>
      </div>
      <h3 class="text-lg font-semibold text-slate-700 mb-1">No applications available</h3>
      <p class="text-sm text-slate-500">
        You don't have access to any SSO-enabled applications yet.
        Contact your administrator if you believe this is an error.
      </p>
    </div>

    <!-- Saving error banner -->
    <div v-if="savingError" class="mb-4 p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-700">
      {{ savingError }}
    </div>

    <!-- Tile grid -->
    <div v-else class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
      <a
        v-for="client in sortedClients"
        :key="client.client_id"
        :href="client.initiate_login_uri"
        class="group relative flex flex-col items-center p-5 bg-white border border-slate-200 rounded-xl shadow-sm hover:shadow-md hover:border-indigo-300 transition-all duration-150 no-underline"
        :class="{ 'ring-2 ring-indigo-300': dragOverClientId === client.client_id }"
        draggable="true"
        @dragstart="onDragStart($event, client.client_id)"
        @dragover="onDragOver"
        @dragenter="onDragEnter($event, client.client_id)"
        @dragleave="onDragLeave"
        @drop="onDrop($event, client.client_id)"
      >
        <!-- Favorite star -->
        <button
          class="absolute top-2 right-2 p-1 rounded-full hover:bg-slate-100 transition-colors"
          :class="favorites.has(client.client_id) ? 'text-amber-400' : 'text-slate-300'"
          :title="favorites.has(client.client_id) ? 'Remove from favorites' : 'Add to favorites'"
          @click.prevent="toggleFavorite(client.client_id)"
        >
          <span class="material-symbols-outlined text-xl" :style="favorites.has(client.client_id) ? { fontVariationSettings: '\'FILL\' 1' } : {}">
            star
          </span>
        </button>

        <!-- Logo / avatar -->
        <div
          v-if="client.logo_uri"
          class="h-14 w-14 mb-3 rounded-xl overflow-hidden bg-slate-100 flex items-center justify-center"
        >
          <img :src="client.logo_uri" :alt="client.name ?? client.fqdn" class="h-full w-full object-cover" @error="(e: Event) => { (e.target as HTMLImageElement).style.display = 'none'; (e.target as HTMLImageElement).parentElement!.classList.add('hidden') }" />
        </div>
        <div
          v-else
          class="h-14 w-14 mb-3 rounded-xl flex items-center justify-center text-white text-xl font-bold"
          :class="avatarColor(client.client_id)"
        >
          {{ initial(client.name ?? client.fqdn) }}
        </div>

        <!-- Name -->
        <span class="text-sm font-medium text-slate-800 text-center leading-tight">
          {{ client.name ?? client.fqdn }}
        </span>
        <span class="text-xs text-slate-400 mt-1 text-center truncate max-w-full">
          {{ client.fqdn }}
        </span>

        <!-- Drag handle hint -->
        <div class="absolute bottom-2 left-2 opacity-0 group-hover:opacity-100 transition-opacity">
          <span class="material-symbols-outlined text-slate-300 text-sm">drag_indicator</span>
        </div>
      </a>
    </div>
  </div>
</template>
