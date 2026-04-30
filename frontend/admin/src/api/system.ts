// 시스템 관리 API 래퍼 — SPEC-CMS-005 Bundle D
import axios from 'axios'

// @MX:ANCHOR: [AUTO] systemApi — SystemDashboardView, AccessLogView, CodeManagerView, SystemSettingView, MaintenanceManagerView, AuditLogView에서 참조
// @MX:REASON: fan_in >= 3: Bundle D 6개 뷰 + stores/system.ts에서 공통 호출

const BASE = '/api/v1/system'

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
export interface AccessLogResponse {
  id: number
  created_at: string
  page_url: string
  status: number
  response_time_ms: number
  user_agent: string
  referrer?: string
  ip_hash: string
  user_id?: number
}

export interface AccessLogFilter {
  from?: string
  to?: string
  status?: number
  ip_hash?: string
  user_id?: number
  page?: number
  size?: number
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
  from?: string
  to?: string
  actor_id?: number
  entity_type?: string
  action?: AuditAction
  severity?: AuditSeverity
  result?: AuditResult
  page?: number
  size?: number
}

// ── API 함수 ──────────────────────────────────────────────────────────────────

/** 대시보드 KPI */
export const dashboard = {
  kpi(params?: { noCache?: boolean }) {
    const headers = params?.noCache ? { 'X-No-Cache': 'true' } : {}
    return axios.get<DashboardKpiResponse>(`${BASE}/dashboard/kpi`, { headers })
  },
  trends(days: 7 | 30 | 90 = 30) {
    return axios.get<TrendItemResponse[]>(`${BASE}/dashboard/trends`, { params: { days } })
  },
  topPages(period: '7d' | '30d' = '7d') {
    return axios.get<TopPageResponse[]>(`${BASE}/dashboard/top-pages`, { params: { period } })
  },
}

/** 방문 통계 */
export const stats = {
  visitors(params: { period: 'DAILY' | 'MONTHLY'; from: string; to: string }) {
    return axios.get<VisitorStatsResponse[]>(`${BASE}/stats/visitors`, { params })
  },
  recompute(params: { from: string; to: string }) {
    return axios.post(`${BASE}/stats/recompute`, params)
  },
}

/** 접속 로그 */
export const accessLogs = {
  list(params: AccessLogFilter) {
    return axios.get<PageResponse<AccessLogResponse>>(`${BASE}/access-logs`, { params })
  },
  exportCsv(params: Omit<AccessLogFilter, 'page' | 'size'>) {
    return axios.get(`${BASE}/access-logs/export`, {
      params,
      responseType: 'blob',
    })
  },
}

/** 공통 코드 그룹 */
export const codeGroups = {
  list() {
    return axios.get<CodeGroupResponse[]>(`${BASE}/codes/groups`)
  },
  create(req: CodeGroupRequest) {
    return axios.post<CodeGroupResponse>(`${BASE}/codes/groups`, req)
  },
  update(code: string, req: Partial<CodeGroupRequest>) {
    return axios.put<CodeGroupResponse>(`${BASE}/codes/groups/${code}`, req)
  },
  delete(code: string) {
    return axios.delete(`${BASE}/codes/groups/${code}`)
  },
}

/** 공통 코드 */
export const codes = {
  list(groupCode: string) {
    return axios.get<CodeResponse[]>(`${BASE}/codes`, { params: { group_code: groupCode } })
  },
  bulk(groupCodes: string[]) {
    return axios.get<Record<string, CodeResponse[]>>(`${BASE}/codes/bulk`, {
      params: { groups: groupCodes.join(',') },
    })
  },
  create(req: CodeRequest) {
    return axios.post<CodeResponse>(`${BASE}/codes`, req)
  },
  update(id: number, req: Partial<CodeRequest>) {
    return axios.put<CodeResponse>(`${BASE}/codes/${id}`, req)
  },
  delete(id: number) {
    return axios.delete(`${BASE}/codes/${id}`)
  },
}

/** 시스템 설정 */
export const settings = {
  list(category?: string) {
    return axios.get<SystemSettingResponse[]>(`${BASE}/settings`, {
      params: category ? { category } : undefined,
    })
  },
  get(key: string) {
    return axios.get<SystemSettingResponse>(`${BASE}/settings/${key}`)
  },
  update(key: string, value: string, valueType: SystemSettingResponse['value_type']) {
    return axios.put<SystemSettingResponse>(`${BASE}/settings/${key}`, { value, value_type: valueType })
  },
}

/** 점검 모드 */
export const maintenance = {
  list() {
    return axios.get<MaintenanceResponse[]>(`${BASE}/maintenance`)
  },
  create(req: MaintenanceRequest) {
    return axios.post<MaintenanceResponse>(`${BASE}/maintenance`, req)
  },
  update(id: number, req: Partial<MaintenanceRequest>) {
    return axios.put<MaintenanceResponse>(`${BASE}/maintenance/${id}`, req)
  },
  activate(id: number) {
    return axios.post(`${BASE}/maintenance/${id}/activate`)
  },
  cancel(id: number) {
    return axios.post(`${BASE}/maintenance/${id}/cancel`)
  },
}

/** 통합 감사 로그 */
export const auditLogs = {
  search(params: AuditLogFilter) {
    return axios.get<PageResponse<AuditLogResponse>>(`${BASE}/audit-logs`, { params })
  },
  detail(id: number) {
    return axios.get<AuditLogResponse>(`${BASE}/audit-logs/${id}`)
  },
  exportCsv(params: Omit<AuditLogFilter, 'page' | 'size'>) {
    return axios.get(`${BASE}/audit-logs/export`, {
      params,
      responseType: 'blob',
    })
  },
  critical() {
    return axios.get<AuditLogResponse[]>(`${BASE}/audit-logs/critical`)
  },
}
