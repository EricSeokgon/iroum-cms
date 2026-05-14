// SPEC-CMS-PUBLIC-001 §5.3 — 공공 사이트 axios 클라이언트
// 관리자 SPA(`@iroum/shared/api/client`)와 별도 인스턴스 — 시민 사이트는 LocalStorage Bearer 토큰 + 선택적 인증

import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse, AxiosError } from 'axios'
import type { ApiError } from '@iroum/shared/types/api'

// LocalStorage 키 (auth store와 공유)
export const ACCESS_TOKEN_KEY = 'public.accessToken'
export const REFRESH_TOKEN_KEY = 'public.refreshToken'

// @MX:ANCHOR: [AUTO] apiClient — public SPA 전 도메인 모듈(menu/notice/board/...)에서 공통 사용
// @MX:REASON: fan_in >= 3: menuApi, noticeApi, policyApi 등 11개 도메인 모듈이 직접 참조
// @MX:SPEC: SPEC-CMS-PUBLIC-001 §5.3
const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

// ── 요청 인터셉터: LocalStorage 토큰을 Authorization Bearer 헤더로 부착 ──
// 익명 호출이 다수이므로 토큰 미존재 시 헤더 미부착 (정상 시나리오)
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig): InternalAxiosRequestConfig => {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: unknown) => Promise.reject(error),
)

// ── 응답 인터셉터: 401(인증 만료) + 403(권한 없음) + 5xx(서버 오류) + 503(점검 모드) 처리 ──
// @MX:WARN: [AUTO] 401 처리는 현재 라우트의 requiresAuth 메타에 따라 분기
// @MX:REASON: 익명 라우트의 401은 정상(예: /policies/match anonymous 호출)이므로 강제 리다이렉트 시 UX 파괴
apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError<ApiError>) => {
    if (!axios.isAxiosError(error)) return Promise.reject(error)

    const status = error.response?.status
    const code = error.response?.data?.code ?? ''
    const method = (error.config?.method ?? 'get').toLowerCase()
    // F-03: 5xx 자동 리다이렉트는 GET/HEAD 등 멱등(read) 메서드에 한정
    // 변경(쓰기) 메서드의 5xx는 컴포넌트가 직접 처리 — 사용자 작성 데이터 손실 방지
    const isReadMethod = method === 'get' || method === 'head'

    // 503 + MAINTENANCE_MODE_ACTIVE → /maintenance 강제 리다이렉트 (SPEC-CMS-009)
    if (status === 503 && code === 'MAINTENANCE_MODE_ACTIVE') {
      const router = (await import('@/router')).default
      router.push({ name: 'maintenance' })
      return Promise.reject(error)
    }

    // 401 처리 — 현재 라우트의 requiresAuth 메타로 분기
    if (status === 401) {
      const router = (await import('@/router')).default
      const requiresAuth = router.currentRoute.value?.meta?.requiresAuth === true
      if (requiresAuth) {
        // 인증 필수 라우트의 401 → 토큰 클리어 + /login 리다이렉트
        localStorage.removeItem(ACCESS_TOKEN_KEY)
        localStorage.removeItem(REFRESH_TOKEN_KEY)
        router.push({
          name: 'login',
          query: { redirect: router.currentRoute.value.fullPath },
        })
      }
      // 익명 가능 라우트의 401은 단순 reject (UX 보존)
      return Promise.reject(error)
    }

    // F-02: 403 → /error/403 강제 리다이렉트 (권한 없음 안내)
    if (status === 403) {
      const router = (await import('@/router')).default
      router.push({ name: 'forbidden' })
      return Promise.reject(error)
    }

    // F-03: 5xx (>=500, 503 제외 — 위에서 처리) + GET/HEAD → /error/500 리다이렉트
    // 변경 메서드(POST/PUT/PATCH/DELETE)는 리다이렉트하지 않고 컴포넌트에 reject 전달
    if (status !== undefined && status >= 500 && status !== 503 && isReadMethod) {
      const router = (await import('@/router')).default
      router.push({ name: 'server-error' })
      return Promise.reject(error)
    }

    return Promise.reject(error)
  },
)

export { apiClient }
