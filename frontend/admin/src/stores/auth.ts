// 인증 스토어 — SPEC-CMS-002 REQ-AUTH-001 구현
// JWT 로그인·갱신·로그아웃 + Pinia 상태 관리

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiClient, registerAuthHooks } from '@iroum/shared/api/client'
import type { LoginResponse } from '@iroum/shared/types/api'

// ── JWT 경량 디코더 (jwt-decode 라이브러리 불필요) ─────────────────────────
interface JwtPayload {
  sub: string       // username
  uid: number       // user id
  roles?: string[]
  exp: number
  iat: number
}

// @MX:WARN: [AUTO] JWT 디코드 — 서명 검증 없이 페이로드만 파싱 (신뢰는 서버에 위임)
// @MX:REASON: 프런트엔드는 클레임 열람 전용. 민감한 결정은 서버 검증에 의존해야 함.
function decodeJwt(token: string): JwtPayload {
  const parts = token.split('.')
  if (parts.length !== 3) throw new Error('잘못된 JWT 형식')
  const payload = parts[1]
  // Base64url → Base64 → JSON
  const padded = payload.replace(/-/g, '+').replace(/_/g, '/')
  const json = atob(padded.padEnd(padded.length + ((4 - (padded.length % 4)) % 4), '='))
  return JSON.parse(decodeURIComponent(escape(json))) as JwtPayload
}

// @MX:ANCHOR: [AUTO] useAuthStore — router guard, LoginView, AdminLayout, apiClient 인터셉터에서 참조
// @MX:REASON: fan_in >= 3: router/index.ts, LoginView.vue, AdminLayout.vue, shared/api/client.ts에서 사용
export const useAuthStore = defineStore('auth', () => {
  // ── 상태 ──────────────────────────────────────────────────────────────────
  const accessToken = ref<string | null>(null)
  const expiresAt = ref<number | null>(null)    // Unix ms
  const user = ref<{ id: number; username: string } | null>(null)

  // ── 게터 ──────────────────────────────────────────────────────────────────
  const isAuthenticated = computed(
    () => !!accessToken.value && (expiresAt.value ?? 0) > Date.now(),
  )

  // ── 내부 헬퍼: 토큰 적용 ──────────────────────────────────────────────────
  function _applyToken(token: string, expiresInSeconds: number): void {
    accessToken.value = token
    expiresAt.value = Date.now() + expiresInSeconds * 1000
    try {
      const payload = decodeJwt(token)
      user.value = { id: payload.uid, username: payload.sub }
    } catch {
      user.value = null
    }
  }

  // ── apiClient 인터셉터 훅 등록 ─────────────────────────────────────────────
  // shared 패키지와의 순환 참조를 DI 패턴으로 해소
  registerAuthHooks({
    getToken: () => accessToken.value,
    setToken: (token: string, expiresInSeconds: number) => _applyToken(token, expiresInSeconds),
    onLogout: () => _clearState(),
  })

  // ── 액션 ──────────────────────────────────────────────────────────────────

  /** 로그인 — POST /api/v1/auth/login */
  async function login(username: string, password: string): Promise<void> {
    const res = await apiClient.post<LoginResponse>('/auth/login', { username, password })
    _applyToken(res.data.accessToken, res.data.expiresInSeconds)
  }

  /** 상태 초기화 (logout 내부용) */
  function _clearState(): void {
    accessToken.value = null
    expiresAt.value = null
    user.value = null
  }

  /** 로그아웃 — POST /api/v1/auth/logout 후 상태 초기화 */
  async function logout(): Promise<void> {
    try {
      await apiClient.post('/auth/logout', null)
    } catch {
      // 네트워크 오류여도 로컬 상태는 반드시 초기화
    }
    _clearState()
    // router import는 순환 참조 방지를 위해 동적으로
    const { default: router } = await import('@/router')
    router.push('/login')
  }

  return {
    accessToken,
    expiresAt,
    user,
    isAuthenticated,
    login,
    logout,
    _applyToken,  // 테스트용 노출
    _clearState,  // 테스트용 노출
  }
})
