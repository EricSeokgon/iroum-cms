// 인증 스토어 placeholder — 공공 사이트는 로그인 불필요하지만 구조 통일
// @MX:TODO: [AUTO] 공공 사이트 인증 요구사항 확인 필요 — SPEC-CMS-002에서 결정

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const isAuthenticated = computed(() => accessToken.value !== null)

  function clearAuth(): void {
    accessToken.value = null
  }

  return { accessToken, isAuthenticated, clearAuth }
})
