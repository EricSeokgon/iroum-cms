// SPEC-CMS-RBAC-001 REQ-RBAC-006 — usePermission 컴포저블 + permissionStore 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// apiClient mock — permissionsApi 가 의존하는 shared client
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  registerAuthHooks: vi.fn(),
}))

import { apiClient } from '@iroum/shared/api/client'
import { usePermissionStore } from '@/stores/permissionStore'
import { usePermission } from '@/composables/usePermission'
import type { AccessibleMenu, MePermissionsResponse } from '@/api/permissions'

const mockGet = apiClient.get as unknown as ReturnType<typeof vi.fn>

const ADMIN_PERMS: MePermissionsResponse = {
  roles: ['ADMIN'],
  permissions: ['USER:READ', 'USER:WRITE', 'ROLE:READ', 'AUDIT:READ'],
}

const ADMIN_MENUS: AccessibleMenu[] = [
  { menuKey: 'users', name: '사용자 관리', routePath: '/users', icon: null, sortOrder: 20, children: [] },
  {
    menuKey: 'system.roles',
    name: '역할/권한 관리',
    routePath: '/roles',
    icon: null,
    sortOrder: 40,
    children: [],
  },
  {
    menuKey: 'audit',
    name: '감사',
    routePath: null,
    icon: null,
    sortOrder: 50,
    children: [
      {
        menuKey: 'audit.permission_changes',
        name: '권한 변경 이력',
        routePath: '/audit/permission-changes',
        icon: null,
        sortOrder: 51,
        children: [],
      },
    ],
  },
]

/** me/permissions + admin/menus/accessible 응답을 순서대로 mock */
function mockApiResponses(perms: MePermissionsResponse, menus: AccessibleMenu[]): void {
  mockGet.mockImplementation((url: string) => {
    if (url === '/me/permissions') return Promise.resolve({ data: perms })
    if (url === '/admin/menus/accessible') return Promise.resolve({ data: menus })
    return Promise.reject(new Error(`unexpected url ${url}`))
  })
}

describe('usePermission / permissionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('AC-006-3: loadPermissions 후 hasPermission/hasRole/canAccessMenu boolean 반환', async () => {
    mockApiResponses(ADMIN_PERMS, ADMIN_MENUS)
    const store = usePermissionStore()
    await store.loadPermissions()

    const { hasPermission, hasRole, canAccessMenu } = usePermission()

    expect(hasPermission('USER:READ')).toBe(true)
    expect(hasPermission('SYSTEM:ADMIN')).toBe(false)
    expect(hasRole('ADMIN')).toBe(true)
    expect(hasRole('SUPER_ADMIN')).toBe(false)
    expect(canAccessMenu('system.roles')).toBe(true)
    // 트리 자식도 탐색
    expect(canAccessMenu('audit.permission_changes')).toBe(true)
    expect(canAccessMenu('governance')).toBe(false)
  })

  it('로드 전에는 모든 판정이 false (캐시 비어 있음)', () => {
    const { hasPermission, hasRole, canAccessMenu } = usePermission()
    expect(hasPermission('USER:READ')).toBe(false)
    expect(hasRole('ADMIN')).toBe(false)
    expect(canAccessMenu('system.roles')).toBe(false)
  })

  it('loadPermissions 는 멱등 — 이미 로드되면 API 재호출 안 함', async () => {
    mockApiResponses(ADMIN_PERMS, ADMIN_MENUS)
    const store = usePermissionStore()
    await store.loadPermissions()
    await store.loadPermissions()
    // me/permissions + accessible = 2회만 (재호출 없음)
    expect(mockGet).toHaveBeenCalledTimes(2)
  })

  it('동시 호출 시 in-flight Promise 공유 — API 중복 호출 방지', async () => {
    mockApiResponses(ADMIN_PERMS, ADMIN_MENUS)
    const store = usePermissionStore()
    await Promise.all([store.loadPermissions(), store.loadPermissions()])
    expect(mockGet).toHaveBeenCalledTimes(2)
  })

  it('reset 후 캐시 비워지고 재로드 가능', async () => {
    mockApiResponses(ADMIN_PERMS, ADMIN_MENUS)
    const store = usePermissionStore()
    await store.loadPermissions()
    expect(store.loaded).toBe(true)

    store.reset()
    expect(store.loaded).toBe(false)
    expect(store.permissions).toEqual([])
    expect(store.roles).toEqual([])
    expect(store.menus).toEqual([])

    await store.loadPermissions()
    expect(store.loaded).toBe(true)
  })
})
