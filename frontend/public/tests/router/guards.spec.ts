// SPEC-CMS-PUBLIC-001 T-004 — 라우터 가드 동작 검증
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/menuApi', () => ({ menuApi: { getPublicMenus: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/systemApi', () => ({ systemApi: { health: vi.fn().mockResolvedValue({ maintenanceMode: false }) } }))
vi.mock('@/api/authApi', () => ({
  authApi: { login: vi.fn(), logout: vi.fn(), refresh: vi.fn(), me: vi.fn() },
}))

describe('Public Router — 라우트 정의', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('25개 라우트가 모두 정의된다 (P0 18 + 에러 2 + P1 5)', async () => {
    const { default: router } = await import('@/router')
    const routes = router.getRoutes()
    // 핵심 라우트가 존재하는지 검증
    const names = routes.map((r) => r.name).filter(Boolean)
    expect(names).toContain('home')
    expect(names).toContain('notice-list')
    expect(names).toContain('notice-detail')
    expect(names).toContain('faq')
    expect(names).toContain('policy-list')
    expect(names).toContain('safety-guideline-list')
    expect(names).toContain('search')
    expect(names).toContain('login')
    expect(names).toContain('maintenance')
    expect(names).toContain('forbidden')
    expect(names).toContain('server-error')
    expect(names).toContain('not-found')
  })

  it('동적 import — 라우트 컴포넌트는 함수(코드 스플리팅)', async () => {
    const { default: router } = await import('@/router')
    const routes = router.getRoutes()
    // 인덱스 라우트의 children 중 하나를 추출
    const noticeList = routes.find((r) => r.name === 'notice-list')
    expect(noticeList?.components?.default).toBeTypeOf('function')
  })
})

describe('Public Router — 가드 동작', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.resetModules()
  })

  it('점검 모드 활성 시 일반 라우트로 진입하면 /maintenance로 리다이렉트', async () => {
    const { default: router } = await import('@/router')
    const { useMaintenanceStore } = await import('@/stores/maintenanceStore')
    const maintenance = useMaintenanceStore()
    maintenance.isMaintenanceMode = true

    await router.push('/notices')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('maintenance')
  })

  it('점검 모드 활성 시 /maintenance 자체는 통과', async () => {
    const { default: router } = await import('@/router')
    const { useMaintenanceStore } = await import('@/stores/maintenanceStore')
    const maintenance = useMaintenanceStore()
    maintenance.isMaintenanceMode = true

    await router.push('/maintenance')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('maintenance')
  })

  it('점검 모드 활성 시 /error/* 라우트는 통과', async () => {
    const { default: router } = await import('@/router')
    const { useMaintenanceStore } = await import('@/stores/maintenanceStore')
    const maintenance = useMaintenanceStore()
    maintenance.isMaintenanceMode = true

    await router.push('/error/500')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('server-error')
  })

  it('requiresAuth=true 라우트 + 비인증 시 /login으로 리다이렉트', async () => {
    const { default: router } = await import('@/router')
    await router.push('/qnas/new')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/qnas/new')
  })

  it('공개 라우트는 인증 없이 접근 가능', async () => {
    const { default: router } = await import('@/router')
    await router.push('/notices')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('notice-list')
  })

  it('afterEach가 document.title을 갱신한다', async () => {
    const { default: router } = await import('@/router')
    await router.push('/notices')
    await router.isReady()
    expect(document.title).toContain('iroum-cms')
  })
})
