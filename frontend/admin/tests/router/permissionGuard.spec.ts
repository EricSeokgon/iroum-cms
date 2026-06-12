// SPEC-CMS-RBAC-001 REQ-RBAC-006/007 — 라우터 권한 가드 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'

// apiClient mock
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  registerAuthHooks: vi.fn(),
}))

import { apiClient } from '@iroum/shared/api/client'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permissionStore'
import type { MePermissionsResponse, AccessibleMenu } from '@/api/permissions'

const mockGet = apiClient.get as unknown as ReturnType<typeof vi.fn>

// JWT mock token (인증 통과용)
const MOCK_TOKEN =
  'eyJhbGciOiJIUzI1NiJ9.' +
  btoa(JSON.stringify({ uid: 1, sub: 'viewer', exp: 9999999999 })) +
  '.sig'

const VIEWER_PERMS: MePermissionsResponse = {
  roles: ['VIEWER'],
  permissions: ['USER:READ', 'ORGANIZATION:READ'],
}
const NO_MENUS: AccessibleMenu[] = []

function mockApiResponses(perms: MePermissionsResponse): void {
  mockGet.mockImplementation((url: string) => {
    if (url === '/me/permissions') return Promise.resolve({ data: perms })
    if (url === '/admin/menus/accessible') return Promise.resolve({ data: NO_MENUS })
    return Promise.reject(new Error(`unexpected url ${url}`))
  })
}

// 테스트용 라우터 — 운영 가드 로직을 그대로 재현 (router/index.ts beforeEach 와 동일 의미)
function buildRouter() {
  const testRouter = createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/login', name: 'login', component: { template: '<div />' }, meta: { public: true } },
      { path: '/forbidden', name: 'forbidden', component: { template: '<div />' }, meta: { requiresAuth: true } },
      {
        path: '/',
        component: { template: '<router-view />' },
        meta: { requiresAuth: true },
        children: [
          { path: 'dashboard', name: 'dashboard', component: { template: '<div />' } },
          // ADMIN 보호 라우트
          {
            path: 'admin-only',
            name: 'admin-only',
            component: { template: '<div />' },
            meta: { permissions: ['ADMIN'] },
          },
          // 권한 코드 보호 라우트
          {
            path: 'audit',
            name: 'audit',
            component: { template: '<div />' },
            meta: { permissions: ['AUDIT:READ'] },
          },
          // VIEWER 도 보유한 권한 라우트
          {
            path: 'user-read',
            name: 'user-read',
            component: { template: '<div />' },
            meta: { permissions: ['USER:READ'] },
          },
        ],
      },
    ],
  })

  testRouter.beforeEach(async (to, _from, next) => {
    const auth = useAuthStore()
    if (to.meta.requiresAuth && !auth.isAuthenticated) {
      next({ name: 'login', query: { redirect: to.fullPath } })
      return
    }
    if (to.name === 'login' && auth.isAuthenticated) {
      next({ name: 'dashboard' })
      return
    }
    const requiredPermissions = to.meta.permissions as string[] | undefined
    const requiredRoles = to.meta.roles as string[] | undefined
    if (auth.isAuthenticated && (requiredPermissions?.length || requiredRoles?.length)) {
      const permStore = usePermissionStore()
      if (!permStore.loaded) {
        try {
          await permStore.loadPermissions()
        } catch {
          next({ name: 'forbidden' })
          return
        }
      }
      const passesPermissions =
        !requiredPermissions?.length ||
        requiredPermissions.some((c) => permStore.hasPermission(c) || permStore.hasRole(c))
      const passesRoles =
        !requiredRoles?.length || requiredRoles.some((r) => permStore.hasRole(r))
      if (!passesPermissions || !passesRoles) {
        next({ name: 'forbidden' })
        return
      }
    }
    next()
  })

  return testRouter
}

describe('Router Permission Guard (SPEC-CMS-RBAC-001)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockApiResponses(VIEWER_PERMS)
  })

  it('AC-006-1: VIEWER 가 meta.permissions:[ADMIN] 라우트 진입 시 /forbidden 전환', async () => {
    const auth = useAuthStore()
    auth._applyToken(MOCK_TOKEN, 900)

    const router = buildRouter()
    await router.push('/admin-only')
    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('AC-006-1-B: VIEWER 가 AUDIT:READ 미보유 → /forbidden', async () => {
    const auth = useAuthStore()
    auth._applyToken(MOCK_TOKEN, 900)

    const router = buildRouter()
    await router.push('/audit')
    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('VIEWER 가 보유 권한(USER:READ) 라우트 진입 → 정상 통과', async () => {
    const auth = useAuthStore()
    auth._applyToken(MOCK_TOKEN, 900)

    const router = buildRouter()
    await router.push('/user-read')
    expect(router.currentRoute.value.name).toBe('user-read')
  })

  it('AC-006-4: 권한 미로드 상태에서 보호 라우트 진입 시 로드 대기 후 평가(오판 방지)', async () => {
    const auth = useAuthStore()
    auth._applyToken(MOCK_TOKEN, 900)

    const router = buildRouter()
    const permStore = usePermissionStore()
    expect(permStore.loaded).toBe(false) // 진입 전 미로드

    // USER:READ 는 VIEWER 보유 → 로드 대기 후 통과해야 함(미달 오판 금지)
    await router.push('/user-read')
    expect(permStore.loaded).toBe(true) // 가드가 로드 완료시킴
    expect(router.currentRoute.value.name).toBe('user-read')
  })

  it('미인증 사용자가 보호 라우트 접근 시 /login (권한 평가 이전 인증 우선)', async () => {
    const router = buildRouter()
    await router.push('/admin-only')
    expect(router.currentRoute.value.name).toBe('login')
    // 권한 API 는 호출되지 않아야 함
    expect(mockGet).not.toHaveBeenCalled()
  })
})
