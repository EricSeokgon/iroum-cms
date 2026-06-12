// SPEC-CMS-RBAC-001 — 현재 사용자 권한·접근 가능 메뉴 API 래퍼
// GET /api/v1/me/permissions, GET /api/v1/admin/menus/accessible 엔드포인트 캡슐화

import { apiClient } from '@iroum/shared/api/client'

/** GET /api/v1/me/permissions 응답 — 현재 사용자 유효 권한·역할 집합 */
export interface MePermissionsResponse {
  roles: string[]
  permissions: string[]
}

/** 접근 가능 어드민 메뉴 트리 노드 */
export interface AccessibleMenu {
  menuKey: string
  name: string
  routePath: string | null
  icon: string | null
  sortOrder: number
  children: AccessibleMenu[]
}

// @MX:ANCHOR: [AUTO] permissionsApi — permissionStore 가 권한 판정 단일 진실 소스로 사용
// @MX:REASON: usePermission 컴포저블·라우터 가드·AdminLayout 사이드바가 store 를 통해 간접 의존(fan_in >= 3)
export const permissionsApi = {
  /**
   * 현재 사용자 유효 권한·역할 집합 조회
   * GET /api/v1/me/permissions
   * 권한: 인증된 모든 사용자 (자기 권한)
   */
  myPermissions() {
    return apiClient.get<MePermissionsResponse>('/me/permissions')
  },

  /**
   * 현재 사용자 접근 가능 어드민 메뉴 트리 조회
   * GET /api/v1/admin/menus/accessible
   * 권한: 인증된 모든 사용자 (자기 접근 가능 메뉴)
   */
  accessibleMenus() {
    return apiClient.get<AccessibleMenu[]>('/admin/menus/accessible')
  },
}
