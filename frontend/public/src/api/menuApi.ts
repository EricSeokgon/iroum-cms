// SPEC-CMS-010 메뉴 트리 — 공공 사이트 전용 엔드포인트
import { apiClient } from './client'

export interface MenuNode {
  id: number
  code: string
  name: string
  path: string
  parentId: number | null
  depth: number
  sortOrder: number
  children?: MenuNode[]
}

export const menuApi = {
  /** 공공 사이트 메뉴 트리 조회 (siteCode=public) */
  getPublicMenus(lang?: string): Promise<MenuNode[]> {
    return apiClient
      .get<MenuNode[]>('/menus/public', { params: lang ? { lang } : undefined })
      .then((res) => res.data)
  },
}
