// Axios 인스턴스 — 공통 API 클라이언트
// SPEC-CMS-002: JWT 인터셉터 + 401 자동 갱신 구현

import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiError, RefreshResult } from '../types/api'

// @MX:ANCHOR: [AUTO] apiClient — admin/composables, auth store, useApi 등 전 패키지에서 공통 사용
// @MX:REASON: fan_in >= 3: useApi, authStore, LoginView, DashboardView에서 직접 참조

const apiClient: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,   // Refresh Token HttpOnly Cookie 자동 전송
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

// ── 401 동시 재시도 큐 ────────────────────────────────────────────────────────
// @MX:WARN: [AUTO] 동시 다발 401 처리 — 단일 refresh 진행 보장, 큐 관리 필수
// @MX:REASON: 여러 뷰에서 동시 요청 시 refresh 중복 호출 방지. 실패 시 큐 전체 reject 필요.
let isRefreshing = false
let pendingQueue: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

function processQueue(error: unknown, token: string | null): void {
  for (const p of pendingQueue) {
    if (error) {
      p.reject(error)
    } else {
      p.resolve(token as string)
    }
  }
  pendingQueue = []
}

// ── 요청 인터셉터: Access Token 주입 ─────────────────────────────────────────
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig): InternalAxiosRequestConfig => {
    // 로그인·갱신 엔드포인트는 토큰 헤더 제외
    const skipPaths = ['/auth/login', '/auth/refresh', '/auth/logout']
    const url = config.url ?? ''
    if (skipPaths.some((p) => url.includes(p))) {
      return config
    }

    // Pinia authStore 순환 참조 방지를 위해 런타임에 동적 import
    // (shared 패키지가 admin 패키지를 직접 참조할 수 없으므로 전역 훅 방식 사용)
    const token = _tokenGetter?.()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: unknown) => Promise.reject(error),
)

// ── 응답 인터셉터: 401 자동 갱신 ─────────────────────────────────────────────
// @MX:WARN: [AUTO] 401 응답 루프 위험 — refresh 실패 시 반드시 /login 리다이렉트 수행
// @MX:REASON: refresh 엔드포인트 자체가 401을 반환하면 무한 루프 발생. skipPaths 처리 필수.
apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) return Promise.reject(error)

    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const status = error.response?.status
    const code = (error.response?.data as ApiError | undefined)?.code ?? ''

    // 로그인 자격증명 오류(401)는 재시도 안 함
    if (originalRequest.url?.includes('/auth/login')) {
      return Promise.reject(error)
    }

    // TOKEN_REUSE_DETECTED → 즉시 로그아웃
    if (status === 401 && code === 'TOKEN_REUSE_DETECTED') {
      _logoutCallback?.()
      return Promise.reject(error)
    }

    // 401 + 재시도 미표시인 경우 → refresh 시도
    if (status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // 이미 갱신 중 → 큐에 등록하고 대기
        return new Promise((resolve, reject) => {
          pendingQueue.push({
            resolve: (token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(apiClient(originalRequest))
            },
            reject,
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const res = await apiClient.post<RefreshResult>('/auth/refresh', null)
        const newToken = res.data.accessToken
        _tokenSetter?.(newToken, res.data.accessExpiresInSeconds)
        processQueue(null, newToken)
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return apiClient(originalRequest)
      } catch (refreshErr) {
        processQueue(refreshErr, null)
        _logoutCallback?.()
        return Promise.reject(refreshErr)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  },
)

// ── 외부 훅 등록 (순환 참조 없는 DI 패턴) ────────────────────────────────────
// admin 패키지의 authStore가 이 함수들을 등록하여 shared 패키지와 결합도를 없앰

let _tokenGetter: (() => string | null) | null = null
let _tokenSetter: ((token: string, expiresInSeconds: number) => void) | null = null
let _logoutCallback: (() => void) | null = null

// @MX:ANCHOR: [AUTO] registerAuthHooks — authStore에서 반드시 호출, 인터셉터 동작의 유일한 진입점
// @MX:REASON: 등록 없으면 모든 인증 요청에 Authorization 헤더가 누락되고 401 refresh가 무기능
export function registerAuthHooks(hooks: {
  getToken: () => string | null
  setToken: (token: string, expiresInSeconds: number) => void
  onLogout: () => void
}): void {
  _tokenGetter = hooks.getToken
  _tokenSetter = hooks.setToken
  _logoutCallback = hooks.onLogout
}

export { apiClient }
