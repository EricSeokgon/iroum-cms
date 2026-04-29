// 인증 스토어 — placeholder
// JWT 로그인·갱신 로직은 SPEC-CMS-002에서 구현 예정

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// @MX:TODO: [AUTO] JWT 인증 로직 미구현 — SPEC-CMS-002에서 구현 필요

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const userId = ref<string | null>(null)

  const isAuthenticated = computed(() => accessToken.value !== null)

  function setToken(token: string, id: string): void {
    accessToken.value = token
    userId.value = id
  }

  function clearAuth(): void {
    accessToken.value = null
    userId.value = null
  }

  return { accessToken, userId, isAuthenticated, setToken, clearAuth }
})
