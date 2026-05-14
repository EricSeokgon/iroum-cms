// SPEC-CMS-PUBLIC-001 T-003 — authStore 테스트
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/authApi', () => ({
  authApi: {
    login: vi.fn().mockResolvedValue({
      accessToken: 'new-token',
      expiresInSeconds: 3600,
      tokenType: 'Bearer',
    }),
    logout: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn().mockResolvedValue({
      accessToken: 'refreshed-token',
      newRefreshToken: 'new-refresh',
      accessExpiresInSeconds: 3600,
      refreshExpiresInSeconds: 86400,
    }),
    me: vi.fn().mockResolvedValue({
      id: 1,
      username: 'citizen',
      roleCodes: ['CITIZEN'],
    }),
  },
}))

describe('authStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('초기 상태는 비인증 + 토큰 null', async () => {
    const { useAuthStore } = await import('@/stores/authStore')
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.token).toBeNull()
    expect(store.user).toBeNull()
  })

  it('login 호출 시 토큰을 localStorage에 영속화한다', async () => {
    const { useAuthStore } = await import('@/stores/authStore')
    const store = useAuthStore()
    await store.login('citizen', 'pw')
    expect(store.token).toBe('new-token')
    expect(store.isAuthenticated).toBe(true)
    expect(localStorage.getItem('public.accessToken')).toBe('new-token')
  })

  it('logout 호출 시 토큰을 클리어한다', async () => {
    localStorage.setItem('public.accessToken', 'existing')
    const { useAuthStore } = await import('@/stores/authStore')
    const store = useAuthStore()
    store.initFromStorage()
    await store.logout()
    expect(store.token).toBeNull()
    expect(localStorage.getItem('public.accessToken')).toBeNull()
  })

  it('initFromStorage 호출 시 LocalStorage 토큰을 복원한다', async () => {
    localStorage.setItem('public.accessToken', 'stored-token')
    const { useAuthStore } = await import('@/stores/authStore')
    const store = useAuthStore()
    store.initFromStorage()
    expect(store.token).toBe('stored-token')
    expect(store.isAuthenticated).toBe(true)
  })
})
