// KPI 대시보드 API 래퍼 — SPEC-CMS-KPI-001 Phase 4
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] kpiApi — KpiDashboardView, kpiStore, 단위 테스트에서 참조
// @MX:REASON: fan_in >= 3: KPI 대시보드 위젯/스토어/테스트에서 공통 호출하는 단일 진입점

const BASE = '/admin/kpi'

// ── 타입 정의 ──────────────────────────────────────────────────────────────────

/** 집계 단위 (백엔드 granularity 파라미터). */
export type KpiGranularity = 'daily' | 'weekly' | 'monthly' | 'quarterly' | 'yearly'

/** 데이터 상태 — PREPARING 은 집계 미완료(값 없음)를 의미한다. */
export type KpiDataState = 'READY' | 'PREPARING'

/** GET /api/v1/admin/kpi/values 쿼리 파라미터. */
export interface KpiQueryParams {
  kpiCode?: string
  fromDate: string          // YYYY-MM-DD
  toDate: string            // YYYY-MM-DD
  dimensionJson?: string    // JSONB 필터를 JSON 문자열로 직렬화
  granularity?: KpiGranularity
  page?: number             // 기본 0
  size?: number             // 기본 100, 최대 1000
}

/** KPI 값 단건. value 는 PREPARING 상태에서 null 일 수 있다. */
export interface KpiValueItem {
  kpiCode: string
  kpiName: string
  dimensionJson: string     // 차원 JSONB 의 JSON 문자열
  value: number | null
  aggregatedAt: string      // ISO datetime
  dataState: KpiDataState
}

/** KPI 조회 응답. */
export interface KpiQueryResponse {
  items: KpiValueItem[]
  hasMore: boolean
  totalCount: number
  filters: Record<string, unknown>
}

/** 비동기 내보내기 작업 응답 (HTTP 202). */
export interface ExportJobResponse {
  jobId: string
  status: 'PROCESSING'
}

/** 내보내기 작업 상태 폴링 응답. */
export interface ExportStatus {
  jobId: string
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED'
  downloadToken?: string
  message?: string
}

/** 알려진 KPI 코드 상수 — 위젯 매핑에 사용. */
export const KPI_CODES = {
  // SPEC-CMS-KPI-001 (콘텐츠·정책 성과 3종)
  FEATURE_USAGE_RATE: 'FEATURE_USAGE_RATE',
  FILE_DOWNLOAD_COUNT: 'FILE_DOWNLOAD_COUNT',
  POLICY_APPLY_CONVERSION_RATE: 'POLICY_APPLY_CONVERSION_RATE',
  // SPEC-CMS-KPI-002 (운영 활동 지표 4종 / 코드 5개)
  DAU: 'DAU',
  MAU: 'MAU',
  CONTENT_VIEW: 'CONTENT_VIEW',
  AVG_SESSION_DURATION: 'AVG_SESSION_DURATION',
  API_ERROR_RATE: 'API_ERROR_RATE',
} as const

const EXCEL_CONTENT_TYPE = 'application/vnd.ms-excel'

// ── API 함수 ──────────────────────────────────────────────────────────────────

/** GET /api/v1/admin/kpi/values — KPI 값 조회. */
function fetchKpiValues(params: KpiQueryParams): Promise<KpiQueryResponse> {
  return apiClient
    .get<KpiQueryResponse>(`${BASE}/values`, { params })
    .then((res) => res.data)
}

/**
 * POST /api/v1/admin/kpi/export — Excel 내보내기.
 * 동기 응답(200, Excel) → Blob, 비동기 응답(202) → ExportJobResponse.
 * Content-Type 으로 분기한다.
 */
async function exportKpi(params: KpiQueryParams): Promise<Blob | ExportJobResponse> {
  const res = await apiClient.post<Blob>(`${BASE}/export`, params, {
    responseType: 'blob',
  })

  const contentType = String(res.headers?.['content-type'] ?? '')

  // HTTP 202 또는 JSON 응답 → 비동기 작업
  if (res.status === 202 || (!contentType.includes(EXCEL_CONTENT_TYPE) && contentType.includes('application/json'))) {
    // blob 으로 받았으므로 JSON 으로 역직렬화
    const text = await (res.data as Blob).text()
    return JSON.parse(text) as ExportJobResponse
  }

  return res.data as Blob
}

/** GET /api/v1/admin/kpi/export/status — 비동기 작업 상태 폴링. */
function pollExportStatus(jobId: string): Promise<ExportStatus> {
  return apiClient
    .get<ExportStatus>(`${BASE}/export/status`, { params: { jobId } })
    .then((res) => res.data)
}

/**
 * GET /api/v1/admin/kpi/export/download?token=... — 서명 토큰으로 파일 다운로드.
 * 브라우저 앵커 트리거 방식 (인증 헤더 불필요한 서명 URL).
 */
function downloadExport(token: string): void {
  const a = document.createElement('a')
  a.href = `/api/v1${BASE}/export/download?token=${encodeURIComponent(token)}`
  a.click()
}

/** Blob 을 파일명으로 즉시 다운로드. */
function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export const kpiApi = {
  fetchKpiValues,
  exportKpi,
  pollExportStatus,
  downloadExport,
  saveBlob,
}
