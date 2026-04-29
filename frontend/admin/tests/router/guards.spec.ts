// Router 인증 가드 테스트 — SPEC-CMS-002
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'

// apiClient mock
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  registerAuthHooks: vi.fn(),
}))

import { useAuthStore } from '@/stores/auth'

// JWT mock token
const MOCK_TOKEN =
  'eyJhbGciOiJIUzI1NiJ9.' +
  btoa(JSON.stringify({ uid: 1, sub: 'admin', exp: 9999999999 })) +
  '.sig'

// 테스트용 라우터 (인증 가드 포함)
function buildRouter() {
  const testRouter = createRouter({
    history: createWebHashHistory(),
    routes: [
      {
        path: '/login',
        name: 'login',
        component: { template: '<div />' },
        meta: { public: true },
      },
      {
        path: '/',
        component: { template: '<router-view />' },
        meta: { requiresAuth: true },
        children: [
          { path: 'dashboard', name: 'dashboard', component: { template: '<div />' } },
        ],
      },
    ],
  })

  testRouter.beforeEach((to, _from, next) => {
    const auth = useAuthStore()
    if (to.meta.requiresAuth && !auth.isAuthenticated) {
      next({ name: 'login', query: { redirect: to.fullPath } })
      return
    }
    if (to.name === 'login' && auth.isAuthenticated) {
      next({ name: 'dashboard' })
      return
    }
    next()
  })

  return testRouter
}

describe('Router Guards', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('미인증 사용자가 보호 라우트 접근 시 /login으로 리다이렉트', async () => {
    const router = buildRouter()
    await router.push('/dashboard')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/dashboard')
  })

  it('인증된 사용자가 /login 접근 시 /dashboard로 리다이렉트', async () => {
    const auth = useAuthStore()
    auth._applyToken(MOCK_TOKEN, 900)

    const router = buildRouter()
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('인증된 사용자는 보호 라우트에 정상 접근', async () => {
    const auth = useAuthStore()
    auth._applyToken(MOCK_TOKEN, 900)

    const router = buildRouter()
    await router.push('/dashboard')
    expect(router.currentRoute.value.name).toBe('dashboard')
  })
})
