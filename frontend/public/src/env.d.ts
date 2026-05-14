/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE: string
  readonly VITE_API_BASE_URL: string
  readonly VITE_APP_TITLE_KO: string
  readonly VITE_APP_TITLE_EN: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
