// SPEC-CMS-PUBLIC-001 §5.4 — breadcrumbStore (동적 브레드크럼 트레일)
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface BreadcrumbItem {
  label: string
  path: string
}

export const useBreadcrumbStore = defineStore('breadcrumb', () => {
  const items = ref<BreadcrumbItem[]>([])

  function set(next: BreadcrumbItem[]): void {
    items.value = next
  }

  function clear(): void {
    items.value = []
  }

  return { items, set, clear }
})
