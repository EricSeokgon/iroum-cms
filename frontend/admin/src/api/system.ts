// 시스템 관리 API 래퍼 — SPEC-CMS-005 Bundle D
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] systemApi — SystemDashboardView, AccessLogView, CodeManagerView, SystemSettingView, MaintenanceManagerView, AuditLogView에서 참조
// @MX:REASON: fan_in >= 3: Bundle D 6개 뷰 + stores/system.ts에서 공통 호출

const BASE = '/system'

// ── 공통 페이지 응답 ──────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 대시보드 ──────────────────────────────────────────────────────────────────
export interface DashboardKpiResponse {
  today_visits: number
  today_unique: number
  today_page_views: number
  today_signups: number
  error_rate_24h: number
  avg_response_ms_24h: number
  locked_accounts: number
  audit_log_24h_count: number
  audit_log_critical_24h_count: number
  health_status: 'HEALTHY' | 'DEGRADED' | 'DOWN'
  // 전일 대비 변화량 (선택)
  visits_change_pct?: number
  page_views_change_pct?: number
}

export interface TrendItemResponse {
  date: string          // YYYY-MM-DD
  visits: number
  page_views: number
  errors: number
}

export interface TopPageResponse {
  rank: number
  page_url: string
  views: number
  avg_response_ms: number
  error_rate: number
}

// ── 방문 통계 ──────────────────────────────────────────────────────────────────
export interface VisitorStatsResponse {
  date: string
  count: number
  unique: number
  page_views: number
}

// ── 접속 로그 ──────────────────────────────────────────────────────────────────
// 백엔드 AccessLogResponse record는 @JsonNaming 없음 → camelCase 직렬화
export interface AccessLogResponse {
  id: number
  createdAt: string
  pageUrl: string
  statusCode: number
  responseTimeMs: number
  userAgent: string
  referrer?: string
  ipHash: string
  userId?: number
  siteId?: number
}

export interface AccessLogFilter {
  from?: string
  to?: string
  statusCode?: number   // 백엔드 파라미터 이름: statusCode
  pageUrl?: string
  page?: number         // 백엔드는 0-based
  size?: number
}

// 백엔드 접속 로그 응답 형식: { items, total, page, size }
export interface AccessLogPageResponse {
  items: AccessLogResponse[]
  total: number
  page: number
  size: number
}

// ── 공통 코드 ──────────────────────────────────────────────────────────────────
export interface CodeGroupResponse {
  code: string
  name: string
  sort_order: number
  status: 'ACTIVE' | 'INACTIVE'
  code_count?: number
}

export interface CodeGroupRequest {
  code: string
  name: string
  sort_order?: number
  status?: 'ACTIVE' | 'INACTIVE'
}

export interface CodeResponse {
  id: number
  group_code: string
  code: string
  name: string
  value?: string
  sort_order: number
  status: 'ACTIVE' | 'INACTIVE'
}

export interface CodeRequest {
  group_code: string
  code: string
  name: string
  value?: string
  sort_order?: number
  status?: 'ACTIVE' | 'INACTIVE'
}

// ── 시스템 설정 ────────────────────────────────────────────────────────────────
export interface SystemSettingResponse {
  key: string
  value: string
  value_type: 'STRING' | 'INT' | 'BOOL' | 'JSON'
  category: string
  description?: string
  updated_at?: string
  updated_by?: string
}

// ── 점검 모드 ──────────────────────────────────────────────────────────────────
export type MaintenanceStatus = 'SCHEDULED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

export interface MaintenanceResponse {
  id: number
  start_at: string
  end_at: string
  status: MaintenanceStatus
  message_ko: string
  message_en: string
  allow_admin_access: boolean
  created_at: string
}

export interface MaintenanceRequest {
  start_at: string
  end_at: string
  message_ko: string
  message_en: string
  allow_admin_access?: boolean
}

// ── 통합 감사 로그 ────────────────────────────────────────────────────────────
export type AuditAction =
  | 'CREATE' | 'UPDATE' | 'DELETE'
  | 'LOGIN' | 'LOGOUT'
  | 'PERMISSION_CHANGE' | 'EXPORT' | 'VIEW_SENSITIVE'

export type AuditSeverity = 'INFO' | 'WARN' | 'CRITICAL'
export type AuditResult = 'SUCCESS' | 'FAILURE'

export interface AuditLogResponse {
  id: number
  event_time: string
  actor_id?: number
  actor_username?: string
  action: AuditAction
  entity_type?: string
  entity_id?: string
  severity: AuditSeverity
  result: AuditResult
  before?: Record<string, unknown>
  after?: Record<string, unknown>
  ip_address?: string
  user_agent?: string
  detail?: string
}

export interface AuditLogFilter {
  fromTime?: string   // 백엔드 파라미터 이름: fromTime
  toTime?: string     // 백엔드 파라미터 이름: toTime
  actor_id?: number
  entity_type?: string
  action?: AuditAction
  severity?: AuditSeverity
  result?: AuditResult
  page?: number       // 백엔드는 1-based
  size?: number
}

// 백엔드 감사 로그 응답 형식: { items, total, page, size }
export interface AuditLogPageResponse {
  items: AuditLogResponse[]
  total: number
  page: number
  size: number
}

// ── API 함수 ──────────────────────────────────────────────────────────────────

/** 대시보드 KPI */
export const dashboard = {
  kpi(params?: { noCache?: boolean }) {
    const headers = params?.noCache ? { 'X-No-Cache': 'true' } : {}
    return apiClient.get<DashboardKpiResponse>(`${BASE}/dashboard/kpi`, { headers })
  },
  trends(days: 7 | 30 | 90 = 30) {
    return apiClient.get<TrendItemResponse[]>(`${BASE}/dashboard/trends`, { params: { days } })
  },
  topPages(period: '7d' | '30d' = '7d') {
    return apiClient.get<TopPageResponse[]>(`${BASE}/dashboard/top-pages`, { params: { period } })
  },
}

export interface MenuPageStatsResponse {
  page_url: string
  menu_name?: string
  visit_count: number
  unique_visitors: number
  avg_response_ms: number
  error_rate: number
}

export interface MenuPageStatsPageResponse {
  items: MenuPageStatsResponse[]
  total: number
  page: number
  size: number
}

/** 방문 통계 */
export const stats = {
  visitors(params: { period: 'DAILY' | 'MONTHLY'; from: string; to: string }) {
    return apiClient.get<VisitorStatsResponse[]>(`${BASE}/stats/visitors`, { params })
  },
  menuPages(params: { from: string; to: string; page?: number; size?: number }) {
    return apiClient.get<MenuPageStatsPageResponse>(`${BASE}/stats/menu-pages`, { params })
  },
  recompute(params: { from: string; to: string }) {
    return apiClient.post(`${BASE}/stats/recompute`, params)
  },
}

/** 접속 로그 */
export const accessLogs = {
  list(params: AccessLogFilter) {
    // 백엔드: { items, total, page, size } 형식 반환
    return apiClient.get<AccessLogPageResponse>(`${BASE}/access-logs`, { params })
  },
  exportCsv(params: Omit<AccessLogFilter, 'page' | 'size'>) {
    return apiClient.get(`${BASE}/access-logs/export`, {
      params,
      responseType: 'blob',
    })
  },
}

/** 공통 코드 그룹 */
export const codeGroups = {
  list() {
    return apiClient.get<CodeGroupResponse[]>(`${BASE}/codes/groups`)
  },
  create(req: CodeGroupRequest) {
    return apiClient.post<CodeGroupResponse>(`${BASE}/codes/groups`, req)
  },
  update(code: string, req: Partial<CodeGroupRequest>) {
    return apiClient.put<CodeGroupResponse>(`${BASE}/codes/groups/${code}`, req)
  },
  delete(code: string) {
    return apiClient.delete(`${BASE}/codes/groups/${code}`)
  },
}

/** 공통 코드 */
export const codes = {
  list(groupCode: string) {
    return apiClient.get<CodeResponse[]>(`${BASE}/codes`, { params: { groupCode } })
  },
  bulk(groupCodes: string[]) {
    return apiClient.get<Record<string, CodeResponse[]>>(`${BASE}/codes/bulk`, {
      params: { groups: groupCodes.join(',') },
    })
  },
  create(req: CodeRequest) {
    return apiClient.post<CodeResponse>(`${BASE}/codes`, req)
  },
  update(id: number, req: Partial<CodeRequest>) {
    return apiClient.put<CodeResponse>(`${BASE}/codes/${id}`, req)
  },
  delete(id: number) {
    return apiClient.delete(`${BASE}/codes/${id}`)
  },
}

/** 시스템 설정 */
export const settings = {
  list(category?: string) {
    return apiClient.get<SystemSettingResponse[]>(`${BASE}/settings`, {
      params: category ? { category } : undefined,
    })
  },
  get(key: string) {
    return apiClient.get<SystemSettingResponse>(`${BASE}/settings/${key}`)
  },
  update(key: string, value: string, valueType: SystemSettingResponse['value_type']) {
    return apiClient.put<SystemSettingResponse>(`${BASE}/settings/${key}`, { value, value_type: valueType })
  },
}

/** 점검 모드 */
export const maintenance = {
  list() {
    return apiClient.get<MaintenanceResponse[]>(`${BASE}/maintenance`)
  },
  create(req: MaintenanceRequest) {
    return apiClient.post<MaintenanceResponse>(`${BASE}/maintenance`, req)
  },
  update(id: number, req: Partial<MaintenanceRequest>) {
    return apiClient.put<MaintenanceResponse>(`${BASE}/maintenance/${id}`, req)
  },
  activate(id: number) {
    return apiClient.post(`${BASE}/maintenance/${id}/activate`)
  },
  cancel(id: number) {
    return apiClient.post(`${BASE}/maintenance/${id}/cancel`)
  },
}

/** 통합 감사 로그 */
export const auditLogs = {
  search(params: AuditLogFilter) {
    // 백엔드: { items, total, page, size } 형식 반환
    return apiClient.get<AuditLogPageResponse>(`${BASE}/audit-logs`, { params })
  },
  detail(id: number) {
    return apiClient.get<AuditLogResponse>(`${BASE}/audit-logs/${id}`)
  },
  exportCsv(params: Omit<AuditLogFilter, 'page' | 'size'>) {
    return apiClient.get(`${BASE}/audit-logs/export`, {
      params,
      responseType: 'blob',
    })
  },
  critical() {
    return apiClient.get<AuditLogResponse[]>(`${BASE}/audit-logs/critical`)
  },
}
