// Axios 인스턴스 — 공통 API 클라이언트
// JWT 인터셉터·refresh 로직은 SPEC-CMS-002 구현 시 추가 예정

import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiError } from '../types/api'

// @MX:ANCHOR: [AUTO] apiClient — admin, public, 테스트에서 공통 사용하는 Axios 인스턴스
// @MX:REASON: fan_in >= 3: admin/composables, public/composables, HealthView에서 직접 사용

const apiClient: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
})

// 요청 인터셉터 — Access Token 주입 (SPEC-CMS-002에서 Pinia auth store 연동)
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig): InternalAxiosRequestConfig => {
    // TODO(SPEC-CMS-002): Pinia authStore에서 accessToken 읽어 헤더에 주입
    return config
  },
  (error: unknown) => Promise.reject(error),
)

// 응답 인터셉터 — 401 처리 및 토큰 갱신 stub
// @MX:WARN: [AUTO] 401 응답 시 토큰 갱신 로직 미구현 — 무한 재시도 위험
// @MX:REASON: refresh 엔드포인트 연동 전 401 루프 발생 가능. SPEC-CMS-002에서 해결 필요.
apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      // TODO(SPEC-CMS-002): POST /api/v1/auth/refresh 호출 후 재시도
      const apiError: ApiError = {
        code: 'UNAUTHORIZED',
        message: '인증이 필요합니다. 다시 로그인해 주세요.',
      }
      return Promise.reject(apiError)
    }
    return Promise.reject(error)
  },
)

export { apiClient }
