// 공통 API 타입 정의
// 백엔드 ApiResponse<T> 포맷에 대응합니다

/** 서버 헬스 체크 응답 */
export interface HealthResponse {
  status: string
  service: string
  version: string
}

/** 공통 API 에러 응답 */
export interface ApiError {
  code: string
  message: string
  traceId?: string
}

/** 공통 API 응답 래퍼 */
export interface ApiResponse<T> {
  success: boolean
  data: T
  error?: ApiError
  timestamp: string
}

/** 페이지네이션 메타 */
export interface PageMeta {
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** 페이지네이션 응답 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── Auth 타입 (SPEC-CMS-002) ──────────────────────────────────────────────────

/** POST /api/v1/auth/login 요청 */
export interface LoginRequest {
  username: string
  password: string
}

/** POST /api/v1/auth/login 200 응답 본문 */
export interface LoginResponse {
  accessToken: string
  expiresInSeconds: number
  tokenType: 'Bearer'
}

/** POST /api/v1/auth/refresh 200 응답 본문 */
export interface RefreshResult {
  accessToken: string
  newRefreshToken: string
  accessExpiresInSeconds: number
  refreshExpiresInSeconds: number
}

// ── 사용자 타입 ──────────────────────────────────────────────────────────────

/** 사용자 상태 코드 */
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'DELETED'

/** 사용자 목록용 요약 DTO */
export interface UserSummary {
  id: number
  uuid: string
  username: string
  email: string
  name: string
  status: UserStatus
  lastLoginAt?: string
}
