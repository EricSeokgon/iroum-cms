// SPEC-CMS-PUBLIC-001 §5.4 — menuStore (공공 사이트 메뉴 트리)
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { menuApi, type MenuNode } from '@/api/menuApi'

export const useMenuStore = defineStore('menu', () => {
  const menus = ref<MenuNode[]>([])
  const isLoaded = ref(false)
  const currentMenuPath = ref<string>('')

  async function fetchMenus(lang?: string): Promise<void> {
    if (isLoaded.value) return
    try {
      menus.value = await menuApi.getPublicMenus(lang)
      isLoaded.value = true
    } catch {
      // 메뉴 로드 실패 시에도 사이트는 동작해야 함 (RP-05 fallback)
      isLoaded.value = false
    }
  }

  async function reload(lang?: string): Promise<void> {
    isLoaded.value = false
    await fetchMenus(lang)
  }

  function setCurrentPath(path: string): void {
    currentMenuPath.value = path
  }

  /** 라우트 path에서 메뉴 트리상의 breadcrumb 추출 — 단순 매칭, 깊은 매칭은 후속 */
  function findBreadcrumb(routePath: string): Array<{ label: string; path: string }> {
    const result: Array<{ label: string; path: string }> = []
    function walk(nodes: MenuNode[], trail: Array<{ label: string; path: string }>): boolean {
      for (const node of nodes) {
        const newTrail = [...trail, { label: node.name, path: node.path }]
        if (node.path === routePath) {
          result.push(...newTrail)
          return true
        }
        if (node.children && walk(node.children, newTrail)) return true
      }
      return false
    }
    walk(menus.value, [])
    return result
  }

  return {
    menus,
    isLoaded,
    currentMenuPath,
    fetchMenus,
    reload,
    setCurrentPath,
    findBreadcrumb,
  }
})
