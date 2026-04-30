// 콘텐츠 관리 스토어 — SPEC-CMS-004 Bundle C
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { sites, menus } from '@/api/content'
import type { SiteResponse, MenuTreeNode } from '@/api/content'

// @MX:ANCHOR: [AUTO] useSiteStore — SiteView, AdminLayout, 페이지 에디터에서 현재 사이트 참조
// @MX:REASON: fan_in >= 3: SiteView.vue, MenuTreeView.vue, 여러 content 뷰에서 currentSite 사용

export const useSiteStore = defineStore('site', () => {
  const currentSite = ref<SiteResponse | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchCurrent(): Promise<void> {
    if (currentSite.value) return // 캐시 히트
    loading.value = true
    error.value = null
    try {
      const res = await sites.getCurrent()
      currentSite.value = res.data
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load site'
    } finally {
      loading.value = false
    }
  }

  function invalidate(): void {
    currentSite.value = null
  }

  return { currentSite, loading, error, fetchCurrent, invalidate }
})

// @MX:ANCHOR: [AUTO] useMenuTreeStore — MenuTreeView, 사이드바 메뉴 렌더링에서 참조
// @MX:REASON: fan_in >= 3: MenuTreeView.vue, AdminLayout 메뉴, 권한 필터링 로직에서 호출

export const useMenuTreeStore = defineStore('menuTree', () => {
  const tree = ref<MenuTreeNode[]>([])
  const loading = ref(false)
  const errors = ref<string | null>(null)

  async function fetchTree(params: { siteId?: number; context?: 'ADMIN' | 'USER' } = {}): Promise<void> {
    loading.value = true
    errors.value = null
    try {
      const res = await menus.tree(params)
      tree.value = res.data
    } catch (e) {
      errors.value = e instanceof Error ? e.message : 'Failed to load menu tree'
    } finally {
      loading.value = false
    }
  }

  function invalidate(): void {
    tree.value = []
  }

  return { tree, loading, errors, fetchTree, invalidate }
})
