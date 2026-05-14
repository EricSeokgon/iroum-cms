// SPEC-CMS-PUBLIC-001 §5.4 — authStore
// 시민 사이트 선택적 인증: LocalStorage Bearer 토큰 (admin과 달리 HttpOnly cookie 미사용)

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type PublicUser } from '@/api/authApi'
import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY } from '@/api/client'

// @MX:ANCHOR: [AUTO] useAuthStore — router guard, LoginView, PublicHeader에서 참조
// @MX:REASON: fan_in >= 3: router/index.ts, LoginView.vue, PublicHeader.vue에서 사용
// @MX:SPEC: SPEC-CMS-PUBLIC-001 §5.4
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const user = ref<PublicUser | null>(null)

  const isAuthenticated = computed(() => token.value !== null)

  function _persist(): void {
    if (token.value) {
      localStorage.setItem(ACCESS_TOKEN_KEY, token.value)
    } else {
      localStorage.removeItem(ACCESS_TOKEN_KEY)
    }
    if (refreshToken.value) {
      localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken.value)
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
  }

  function initFromStorage(): void {
    token.value = localStorage.getItem(ACCESS_TOKEN_KEY)
    refreshToken.value = localStorage.getItem(REFRESH_TOKEN_KEY)
  }

  async function login(username: string, password: string): Promise<void> {
    const res = await authApi.login({ username, password })
    token.value = res.accessToken
    _persist()
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // 네트워크 오류여도 로컬 상태는 반드시 초기화
    }
    token.value = null
    refreshToken.value = null
    user.value = null
    _persist()
  }

  async function refresh(): Promise<void> {
    if (!refreshToken.value) return
    const res = await authApi.refresh(refreshToken.value)
    token.value = res.accessToken
    refreshToken.value = res.newRefreshToken
    _persist()
  }

  async function loadUser(): Promise<void> {
    if (!token.value) return
    user.value = await authApi.me()
  }

  return {
    token,
    refreshToken,
    user,
    isAuthenticated,
    initFromStorage,
    login,
    logout,
    refresh,
    loadUser,
  }
})
