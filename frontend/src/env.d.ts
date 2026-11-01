declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SUPPORT_EMAIL?: string
  readonly VITE_SUPPORT_PHONE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
