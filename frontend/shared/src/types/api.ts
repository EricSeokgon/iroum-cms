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
  meta: PageMeta
}
