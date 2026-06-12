// SPEC-CMS-RBAC-001 REQ-RBAC-006 — 권한 판정 컴포저블
// permissionStore 캐시 기반 hasPermission/hasRole/canAccessMenu 제공

import { usePermissionStore } from '@/stores/permissionStore'

/**
 * 권한 판정 컴포저블.
 *
 * <p>GET /api/v1/me/permissions 결과(permissionStore 캐시)를 단일 진실 소스로
 * 권한·역할·메뉴 접근 가능 여부를 판정한다.
 */
// @MX:NOTE: [AUTO] usePermission — 컴포넌트/가드에서 권한 판정 진입점
// @MX:SPEC: SPEC-CMS-RBAC-001
export function usePermission() {
  const store = usePermissionStore()

  /** 권한 코드 보유 여부 */
  function hasPermission(code: string): boolean {
    return store.hasPermission(code)
  }

  /** 역할 코드 보유 여부 */
  function hasRole(code: string): boolean {
    return store.hasRole(code)
  }

  /** 메뉴 접근 가능 여부 (accessible 메뉴 트리 기반) */
  function canAccessMenu(menuKey: string): boolean {
    return store.canAccessMenu(menuKey)
  }

  return { hasPermission, hasRole, canAccessMenu }
}
