// SPEC-CMS-010 메뉴 트리 — 실제 백엔드 경로: /content/menus/tree?siteId=1
import { apiClient } from './client'

export interface MenuNode {
  id: number
  code: string
  name: string
  url?: string
  path: string
  parentId: number | null
  depth: number
  sortOrder: number
  isVisible: boolean
  accessible: boolean
  children?: MenuNode[]
}

function filterPublicMenus(menus: MenuNode[]): MenuNode[] {
  return menus
    .filter((m) => m.isVisible !== false && !m.url?.startsWith('/admin'))
    .map((m) => ({ ...m, children: m.children ? filterPublicMenus(m.children) : [] }))
}

export const menuApi = {
  /** 공공 사이트 메뉴 트리 조회 (siteId=1, /admin 제외) */
  getPublicMenus(_lang?: string): Promise<MenuNode[]> {
    return apiClient
      .get<MenuNode[]>('/content/menus/tree', { params: { siteId: 1 } })
      .then((res) => filterPublicMenus(res.data))
  },
}
