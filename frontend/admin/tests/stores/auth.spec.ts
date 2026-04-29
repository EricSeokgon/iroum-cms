// auth store 단위 테스트 — SPEC-CMS-002 REQ-AUTH-001
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// apiClient mock
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  registerAuthHooks: vi.fn(),
}))

// router mock
vi.mock('@/router', () => ({
  default: { push: vi.fn() },
}))

import { useAuthStore } from '@/stores/auth'
import { apiClient } from '@iroum/shared/api/client'

// JWT 페이로드: { uid: 1, sub: "admin", exp: 9999999999 }
const MOCK_TOKEN =
  'eyJhbGciOiJIUzI1NiJ9.' +
  btoa(JSON.stringify({ uid: 1, sub: 'admin', exp: 9999999999 })) +
  '.signature'

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('login', () => {
    it('로그인 성공 시 accessToken과 user를 저장한다', async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        data: { accessToken: MOCK_TOKEN, expiresInSeconds: 900, tokenType: 'Bearer' },
      })

      const auth = useAuthStore()
      await auth.login('admin', 'password123')

      expect(auth.accessToken).toBe(MOCK_TOKEN)
      expect(auth.user?.username).toBe('admin')
      expect(auth.user?.id).toBe(1)
    })

    it('로그인 API 실패 시 예외를 던진다', async () => {
      vi.mocked(apiClient.post).mockRejectedValueOnce(new Error('401'))

      const auth = useAuthStore()
      await expect(auth.login('bad', 'bad')).rejects.toThrow()
      expect(auth.accessToken).toBeNull()
    })
  })

  describe('isAuthenticated', () => {
    it('accessToken이 있고 expiresAt이 미래이면 true', () => {
      const auth = useAuthStore()
      auth._applyToken(MOCK_TOKEN, 900)
      expect(auth.isAuthenticated).toBe(true)
    })

    it('accessToken이 null이면 false', () => {
      const auth = useAuthStore()
      expect(auth.isAuthenticated).toBe(false)
    })

    it('expiresAt이 과거이면 false', () => {
      const auth = useAuthStore()
      auth._applyToken(MOCK_TOKEN, 0)
      // expiresAt = Date.now() + 0 ≈ now → 미세 차이로 false 처리
      auth._applyToken(MOCK_TOKEN, -1)
      expect(auth.isAuthenticated).toBe(false)
    })
  })

  describe('logout', () => {
    it('로그아웃 시 상태가 초기화된다', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: {} })

      const auth = useAuthStore()
      auth._applyToken(MOCK_TOKEN, 900)

      await auth.logout()

      expect(auth.accessToken).toBeNull()
      expect(auth.user).toBeNull()
    })

    it('로그아웃 API 실패여도 상태가 초기화된다', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('network'))

      const auth = useAuthStore()
      auth._applyToken(MOCK_TOKEN, 900)

      await auth.logout()

      expect(auth.accessToken).toBeNull()
    })
  })

  describe('_applyToken', () => {
    it('expiresInSeconds 기반으로 expiresAt을 계산한다', () => {
      const auth = useAuthStore()
      const before = Date.now()
      auth._applyToken(MOCK_TOKEN, 900)
      const after = Date.now()

      expect(auth.expiresAt).toBeGreaterThanOrEqual(before + 900 * 1000)
      expect(auth.expiresAt).toBeLessThanOrEqual(after + 900 * 1000)
    })
  })
})
