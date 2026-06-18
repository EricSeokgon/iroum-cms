// 정책사업 매칭 + 발송 API 래퍼 — SPEC-CMS-007
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] policyApi — PolicyListView, PolicyDetailView, PolicyMatchView, PolicySubscriptionView, PolicyDispatchView 및 policyStore에서 참조
// @MX:REASON: fan_in >= 3: SPEC-CMS-007 5개 뷰 + policyStore에서 공통 호출

const BASE = '/policy'
const ME_BASE = '/api/v1/me'

// ── 공통 페이지 응답 ──────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 정책사업 (Programs) ───────────────────────────────────────────────────────
export type PolicyStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'EXPIRED'

export interface PolicyProgramSummary {
  id: number
  uuid?: string
  title: string
  ministry?: string
  agency?: string
  status: PolicyStatus
  target_industries?: string[]
  target_regions?: string[]
  application_start_at?: string
  application_end_at?: string
  budget_amount?: number
  source?: string
}

export interface PolicyProgramDetail extends PolicyProgramSummary {
  description?: string
  external_url?: string
  external_id?: string
  employee_count_min?: number
  employee_count_max?: number
  revenue_min?: number
  revenue_max?: number
  business_age_min?: number
  business_age_max?: number
  required_certifications?: string[]
  excluded_industries?: string[]
  application_method?: string
  contact_dept?: string
  contact_phone?: string
  contact_email?: string
  attachments?: Array<{ url: string; name: string }>
  created_at?: string
  updated_at?: string
}

export interface PolicyFilter {
  status?: PolicyStatus
  industry?: string
  region?: string
  ministry?: string
  search?: string
  page?: number
  size?: number
}

export interface PolicyProgramRequest {
  title: string
  ministry?: string
  agency?: string
  status: PolicyStatus
  description?: string
  external_url?: string
  target_industries?: string[]
  target_regions?: string[]
  application_start_at?: string
  application_end_at?: string
  budget_amount?: number
  employee_count_min?: number
  employee_count_max?: number
  revenue_min?: number
  revenue_max?: number
  business_age_min?: number
  business_age_max?: number
  required_certifications?: string[]
  excluded_industries?: string[]
  application_method?: string
  contact_dept?: string
  contact_phone?: string
  contact_email?: string
}

// ── 매칭 (Matching) ───────────────────────────────────────────────────────────
export type PolicyGrade = 'A' | 'B' | 'C' | 'D'

export interface CompanyProfile {
  industry_code: string
  region_code: string
  employee_count: number
  annual_revenue: number       // 원 단위
  business_age_years: number
  certifications?: string[]
  keywords?: string[]
  user_id?: number
}

export interface ScoreBreakdown {
  industry: number    // 0~100
  region: number
  size: number
  age: number
  revenue: number
  certification_bonus?: number
  keyword_bonus?: number
}

export interface PolicyMatchResult {
  match_id: number
  policy_id: number
  policy: PolicyProgramSummary
  total_score: number       // 0.0 ~ 100.0
  grade: PolicyGrade
  score_breakdown: ScoreBreakdown
  matched_at: string
  expires_at: string
  cached: boolean
  rank: number
}

export interface PolicyMatchResponse {
  results: PolicyMatchResult[]
  generated_at: string
  expires_at: string
  cached: boolean
  cache_ttl_days: number      // 일반 7일
  total_evaluated: number
}

// ── 발송 예약 (Dispatch) ──────────────────────────────────────────────────────
export type DispatchStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED' | 'FAILED'
export type DispatchType = 'POLICY_MATCH' | 'ANNOUNCEMENT' | 'REMINDER' | 'MARKETING'
export type Channel = 'KAKAO' | 'EMAIL' | 'INAPP'

export interface DispatchScheduleSummary {
  uuid: string
  policy_id?: number
  policy_title?: string
  dispatch_type: DispatchType
  channels: Channel[]
  status: DispatchStatus
  scheduled_at: string
  total_targets?: number
  sent_count?: number
  failed_count?: number
  created_at: string
}

export interface DispatchScheduleDetail extends DispatchScheduleSummary {
  template_id: number
  template_name?: string
  target_filter?: Record<string, unknown>
  cost_estimate?: number       // 원 단위
  triggered_at?: string
  completed_at?: string
  error_message?: string
  created_by?: string
}

export interface DispatchScheduleRequest {
  policy_id?: number
  template_id: number
  dispatch_type: DispatchType
  channels: Channel[]
  scheduled_at: string         // ISO-8601
  target_filter?: Record<string, unknown>
  // SPEC-CMS-NOTI-EXT-001 — EMAIL 채널 발송 시 사용할 알림 템플릿(선택)
  notification_template_id?: number
}

export interface DispatchSimulateRequest {
  target_filter: Record<string, unknown>
  channels: Channel[]
}

export interface DispatchSimulateResponse {
  matched_user_count: number
  estimated_cost: number       // 원 단위
  channel_breakdown?: Record<Channel, number>
}

// ── 수신 동의 (Subscription) ──────────────────────────────────────────────────
export interface NotificationPreferences {
  user_id?: number
  preferences: NotificationPreference[]
  updated_at?: string
}

export interface NotificationPreference {
  channel: Channel
  category: DispatchType
  opted_in: boolean
  consent_at?: string
}

export interface NotificationPreferencesUpdate {
  preferences: NotificationPreference[]
}

// ── 추적 (Tracking) ───────────────────────────────────────────────────────────
export type TrackAction = 'VIEW' | 'CLICK_APPLY' | 'EXTERNAL_REDIRECT'

export interface TrackEventRequest {
  action: TrackAction
  metadata?: Record<string, unknown>
}

// ── 동기화 (Data Sources) ─────────────────────────────────────────────────────
export interface SyncTriggerResponse {
  triggered_at: string
  job_id?: string
  source_count?: number
}

// ── API 그룹 ──────────────────────────────────────────────────────────────────
export const policyApi = {
  // 정책사업
  programs: {
    list(params: PolicyFilter): Promise<{ data: PageResponse<PolicyProgramSummary> }> {
      return apiClient.get(`${BASE}/programs`, { params })
    },
    get(id: number): Promise<{ data: PolicyProgramDetail }> {
      return apiClient.get(`${BASE}/programs/${id}`)
    },
    create(req: PolicyProgramRequest): Promise<{ data: PolicyProgramDetail }> {
      return apiClient.post(`${BASE}/programs`, req)
    },
    update(id: number, req: Partial<PolicyProgramRequest>): Promise<{ data: PolicyProgramDetail }> {
      return apiClient.put(`${BASE}/programs/${id}`, req)
    },
    delete(id: number): Promise<void> {
      return apiClient.delete(`${BASE}/programs/${id}`)
    },
    sync(): Promise<{ data: SyncTriggerResponse }> {
      // K-Startup 등 외부 데이터 소스 일괄 동기화
      return apiClient.post(`${BASE}/data-sources/sync-all`)
    },
  },

  // 매칭
  matching: {
    upsertProfile(req: CompanyProfile): Promise<{ data: CompanyProfile }> {
      return apiClient.put(`${ME_BASE}/policy/profile`, req)
    },
    fetchProfile(): Promise<{ data: CompanyProfile }> {
      return apiClient.get(`${ME_BASE}/policy/profile`)
    },
    run(): Promise<{ data: PolicyMatchResponse }> {
      return apiClient.post(`${BASE}/match`)
    },
    fetchMine(): Promise<{ data: PolicyMatchResponse }> {
      return apiClient.get(`${BASE}/match/me`)
    },
    fetchReason(matchId: number): Promise<{ data: ScoreBreakdown & { explanation?: string } }> {
      return apiClient.get(`${BASE}/match/${matchId}/reason`)
    },
  },

  // 발송 예약
  dispatch: {
    list(params?: { page?: number; size?: number; status?: DispatchStatus }): Promise<{ data: PageResponse<DispatchScheduleSummary> }> {
      return apiClient.get(`${BASE}/dispatch-schedules`, { params })
    },
    get(uuid: string): Promise<{ data: DispatchScheduleDetail }> {
      return apiClient.get(`${BASE}/dispatch-schedules/${uuid}`)
    },
    create(req: DispatchScheduleRequest): Promise<{ data: DispatchScheduleDetail }> {
      return apiClient.post(`${BASE}/dispatch-schedules`, req)
    },
    cancel(uuid: string): Promise<void> {
      return apiClient.delete(`${BASE}/dispatch-schedules/${uuid}`)
    },
    trigger(uuid: string): Promise<{ data: DispatchScheduleDetail }> {
      // 즉시 발송 트리거 (PENDING -> PROCESSING)
      return apiClient.post(`${BASE}/dispatch-schedules/${uuid}/trigger`)
    },
    simulate(req: DispatchSimulateRequest): Promise<{ data: DispatchSimulateResponse }> {
      return apiClient.post(`${BASE}/dispatch-schedules/simulate`, req)
    },
    stats(uuid: string): Promise<{ data: { sent: number; failed: number; pending: number; total: number } }> {
      return apiClient.get(`${BASE}/dispatch-schedules/${uuid}/stats`)
    },
  },

  // 수신 동의
  subscription: {
    fetchMine(): Promise<{ data: NotificationPreferences }> {
      return apiClient.get(`${ME_BASE}/notifications/preferences`)
    },
    updateMine(req: NotificationPreferencesUpdate): Promise<{ data: NotificationPreferences }> {
      return apiClient.put(`${ME_BASE}/notifications/preferences`, req)
    },
  },

  // 추적 (사용자 행동 로그)
  tracking: {
    track(policyId: number, req: TrackEventRequest): Promise<void> {
      return apiClient.post(`${BASE}/${policyId}/track`, req)
    },
  },
}
