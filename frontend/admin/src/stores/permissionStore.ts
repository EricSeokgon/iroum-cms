// SPEC-CMS-RBAC-001 REQ-RBAC-006 — 권한 스토어
// GET /api/v1/me/permissions + GET /api/v1/admin/menus/accessible 캐시 + 권한 판정 단일 진실 소스

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { permissionsApi, type AccessibleMenu } from '@/api/permissions'

// @MX:ANCHOR: [AUTO] usePermissionStore — usePermission 컴포저블·라우터 가드·AdminLayout 사이드바에서 참조
// @MX:REASON: fan_in >= 3: composables/usePermission.ts, router/index.ts, layouts/AdminLayout.vue
export const usePermissionStore = defineStore('permission', () => {
  // ── 상태 ──────────────────────────────────────────────────────────────────
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const menus = ref<AccessibleMenu[]>([])
  const loaded = ref(false)
  // 동시 로드 중복 요청 방지용 in-flight Promise
  let inflight: Promise<void> | null = null

  // ── 게터 ──────────────────────────────────────────────────────────────────

  /** 권한 코드 보유 여부 */
  const hasPermission = computed(() => (code: string): boolean => permissions.value.includes(code))

  /** 역할 코드 보유 여부 */
  const hasRole = computed(() => (code: string): boolean => roles.value.includes(code))

  /** 메뉴 트리에서 menuKey 접근 가능 여부 (재귀 탐색) */
  const canAccessMenu = computed(() => (menuKey: string): boolean => containsMenu(menus.value, menuKey))

  function containsMenu(tree: AccessibleMenu[], menuKey: string): boolean {
    for (const node of tree) {
      if (node.menuKey === menuKey) return true
      if (node.children.length > 0 && containsMenu(node.children, menuKey)) return true
    }
    return false
  }

  // ── 액션 ──────────────────────────────────────────────────────────────────

  /**
   * 권한·역할·접근 가능 메뉴 로드 (멱등 — 이미 로드되었으면 재요청 안 함).
   * 동시 호출 시 단일 in-flight Promise 를 공유한다.
   */
  async function loadPermissions(): Promise<void> {
    if (loaded.value) return
    if (inflight) return inflight

    inflight = (async () => {
      const [permRes, menuRes] = await Promise.all([
        permissionsApi.myPermissions(),
        permissionsApi.accessibleMenus(),
      ])
      roles.value = permRes.data.roles
      permissions.value = permRes.data.permissions
      menus.value = menuRes.data
      loaded.value = true
    })()

    try {
      await inflight
    } finally {
      inflight = null
    }
  }

  /** 권한 상태 초기화 (로그아웃 시 호출) */
  function reset(): void {
    roles.value = []
    permissions.value = []
    menus.value = []
    loaded.value = false
    inflight = null
  }

  return {
    roles,
    permissions,
    menus,
    loaded,
    hasPermission,
    hasRole,
    canAccessMenu,
    loadPermissions,
    reset,
  }
})
