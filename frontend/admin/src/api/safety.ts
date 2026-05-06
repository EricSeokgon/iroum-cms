// 안전관리 API 래퍼 — SPEC-CMS-006
import axios from 'axios'

// @MX:ANCHOR: [AUTO] safetyApi — IncidentListView, IncidentDetailView, SafetyProfileView, MatchResultView, GuidelineReportView, TemplateManageView에서 참조
// @MX:REASON: fan_in >= 3: SPEC-CMS-006 6개 뷰 + safetyStore에서 공통 호출

const BASE = '/api/v1/safety'

// ── 공통 페이지 응답 ──────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 사고사례 ──────────────────────────────────────────────────────────────────
export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface IncidentSummary {
  id: number
  uuid?: string
  incident_type: string
  industry_code: string
  severity: IncidentSeverity
  occurred_at: string
  summary: string
  source?: string
}

export interface IncidentDetail extends IncidentSummary {
  description?: string
  cause?: string
  countermeasure?: string
  process_type?: string
  hazard_factors?: string[]
  keywords?: string[]
  attachments?: Array<{ url: string; name: string }>
  external_id?: string
  external_url?: string
  created_at?: string
  updated_at?: string
}

export interface IncidentFilter {
  industry_code?: string
  incident_type?: string
  severity?: IncidentSeverity
  from?: string
  to?: string
  search?: string
  page?: number
  size?: number
}

export interface IncidentCreateRequest {
  incident_type: string
  industry_code: string
  severity: IncidentSeverity
  occurred_at: string
  summary: string
  description?: string
  cause?: string
  countermeasure?: string
  process_type?: string
  hazard_factors?: string[]
  keywords?: string[]
}

// ── 키워드 ────────────────────────────────────────────────────────────────────
export interface KeywordResponse {
  id: number
  keyword: string
  category?: string
  weight?: number
  status: 'ACTIVE' | 'INACTIVE'
}

export interface KeywordRequest {
  keyword: string
  category?: string
  weight?: number
  status?: 'ACTIVE' | 'INACTIVE'
}

// ── 안전 프로필 ───────────────────────────────────────────────────────────────
export type RiskGrade = 'A' | 'B' | 'C' | 'D' | 'E'

export interface SafetyProfileResponse {
  user_id: number
  industry_code: string
  process_type?: string
  hazard_factors: string[]
  employee_count?: number
  risk_grade: RiskGrade
  notes?: string
  updated_at?: string
}

export interface SafetyProfileUpsertRequest {
  industry_code: string
  process_type?: string
  hazard_factors: string[]
  employee_count?: number
  risk_grade: RiskGrade
  notes?: string
}

// ── 매칭 ──────────────────────────────────────────────────────────────────────
export interface MatchedIncident {
  incident_id: number
  similarity_score: number    // 0.0 ~ 1.0
  match_reason: string        // XAI 설명
  rank: number
  incident: IncidentSummary
}

export interface MatchResultResponse {
  profile_id: number
  matched_incidents: MatchedIncident[]
  total_score?: number
  expires_at: string
  cached: boolean
  generated_at: string
}

// ── 가이드라인 보고서 ─────────────────────────────────────────────────────────
export interface ReportSummary {
  uuid: string
  title: string
  status: 'DRAFT' | 'GENERATED' | 'PUBLISHED'
  template_code?: string
  user_id: number
  created_at: string
  updated_at?: string
}

export interface ReportDetail extends ReportSummary {
  html_content: string
  pdf_url?: string
  match_summary?: string
  template_name?: string
  generated_by?: string
}

export interface ReportCreateRequest {
  template_code: string
  match_result_id?: number
  custom_title?: string
}

// ── 체크리스트 ────────────────────────────────────────────────────────────────
export type CheckStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'DONE' | 'NA' | 'BLOCKED'

export interface ChecklistItemResult {
  id: number
  report_uuid: string
  item_id: number
  item_code: string
  item_title: string
  description?: string
  status: CheckStatus
  evidence_url?: string
  note?: string
  completed_at?: string
}

export interface ChecklistUpdateRequest {
  status: CheckStatus
  evidence_url?: string
  note?: string
}

export interface ChecklistStats {
  total: number
  done: number
  in_progress: number
  na: number
  blocked: number
  not_started: number
  completion_rate: number   // 0.0 ~ 1.0
}

// ── 템플릿 ────────────────────────────────────────────────────────────────────
export interface TemplateSummary {
  id: number
  code: string
  name: string
  applicable_industry_codes: string[]
  applicable_grades: RiskGrade[]
  version: number
  status: 'ACTIVE' | 'INACTIVE' | 'DRAFT'
  updated_at?: string
}

export interface TemplateDetail extends TemplateSummary {
  body_template: string
  description?: string
  checklist_items?: TemplateChecklistItem[]
  created_at?: string
}

export interface TemplateChecklistItem {
  id?: number
  template_id?: number
  code: string
  title: string
  description?: string
  sort_order: number
  required?: boolean
}

export interface TemplateRequest {
  code: string
  name: string
  description?: string
  applicable_industry_codes: string[]
  applicable_grades: RiskGrade[]
  body_template: string
  status?: 'ACTIVE' | 'INACTIVE' | 'DRAFT'
}

// ── API 그룹 ──────────────────────────────────────────────────────────────────
export const safetyApi = {
  // 사고사례
  incidents: {
    list(params: IncidentFilter): Promise<{ data: PageResponse<IncidentSummary> }> {
      return axios.get(`${BASE}/incidents`, { params })
    },
    get(id: number): Promise<{ data: IncidentDetail }> {
      return axios.get(`${BASE}/incidents/${id}`)
    },
    create(req: IncidentCreateRequest): Promise<{ data: IncidentDetail }> {
      return axios.post(`${BASE}/incidents`, req)
    },
    update(id: number, req: Partial<IncidentCreateRequest>): Promise<{ data: IncidentDetail }> {
      return axios.put(`${BASE}/incidents/${id}`, req)
    },
    delete(id: number): Promise<void> {
      return axios.delete(`${BASE}/incidents/${id}`)
    },
    sync(): Promise<{ data: { triggered_at: string; job_id?: string } }> {
      return axios.post(`${BASE}/incidents/sync`)
    },
  },

  // 키워드
  keywords: {
    list(): Promise<{ data: KeywordResponse[] }> {
      return axios.get(`${BASE}/keywords`)
    },
    create(req: KeywordRequest): Promise<{ data: KeywordResponse }> {
      return axios.post(`${BASE}/keywords`, req)
    },
    update(id: number, req: Partial<KeywordRequest>): Promise<{ data: KeywordResponse }> {
      return axios.put(`${BASE}/keywords/${id}`, req)
    },
    delete(id: number): Promise<void> {
      return axios.delete(`${BASE}/keywords/${id}`)
    },
  },

  // 안전 프로필
  profile: {
    me(): Promise<{ data: SafetyProfileResponse }> {
      return axios.get(`${BASE}/profile/me`)
    },
    upsert(req: SafetyProfileUpsertRequest): Promise<{ data: SafetyProfileResponse }> {
      return axios.put(`${BASE}/profile/me`, req)
    },
  },

  // 매칭
  matching: {
    run(): Promise<{ data: MatchResultResponse }> {
      return axios.post(`${BASE}/match/run`)
    },
    cached(): Promise<{ data: MatchResultResponse }> {
      return axios.get(`${BASE}/match/cached`)
    },
  },

  // 가이드라인 보고서
  reports: {
    create(req: ReportCreateRequest): Promise<{ data: ReportDetail }> {
      return axios.post(`${BASE}/reports`, req)
    },
    get(uuid: string): Promise<{ data: ReportDetail }> {
      return axios.get(`${BASE}/reports/${uuid}`)
    },
    pdf(uuid: string): Promise<{ data: Blob }> {
      return axios.get(`${BASE}/reports/${uuid}/pdf`, { responseType: 'blob' })
    },
    listMine(params?: { page?: number; size?: number }): Promise<{ data: PageResponse<ReportSummary> }> {
      return axios.get(`${BASE}/reports/me`, { params })
    },
    listAll(params?: { page?: number; size?: number; user_id?: number }): Promise<{ data: PageResponse<ReportSummary> }> {
      return axios.get(`${BASE}/reports`, { params })
    },
  },

  // 체크리스트
  checklist: {
    list(reportUuid: string): Promise<{ data: ChecklistItemResult[] }> {
      return axios.get(`${BASE}/reports/${reportUuid}/checklist`)
    },
    update(reportUuid: string, itemId: number, req: ChecklistUpdateRequest): Promise<{ data: ChecklistItemResult }> {
      return axios.put(`${BASE}/reports/${reportUuid}/checklist/${itemId}`, req)
    },
    stats(reportUuid: string): Promise<{ data: ChecklistStats }> {
      return axios.get(`${BASE}/reports/${reportUuid}/checklist/stats`)
    },
  },

  // 템플릿
  templates: {
    list(): Promise<{ data: TemplateSummary[] }> {
      return axios.get(`${BASE}/templates`)
    },
    get(id: number): Promise<{ data: TemplateDetail }> {
      return axios.get(`${BASE}/templates/${id}`)
    },
    create(req: TemplateRequest): Promise<{ data: TemplateDetail }> {
      return axios.post(`${BASE}/templates`, req)
    },
    update(id: number, req: Partial<TemplateRequest>): Promise<{ data: TemplateDetail }> {
      return axios.put(`${BASE}/templates/${id}`, req)
    },
    delete(id: number): Promise<void> {
      return axios.delete(`${BASE}/templates/${id}`)
    },
    preview(id: number): Promise<{ data: { html: string } }> {
      return axios.get(`${BASE}/templates/${id}/preview`)
    },
    checklist(templateId: number): Promise<{ data: TemplateChecklistItem[] }> {
      return axios.get(`${BASE}/templates/${templateId}/checklist`)
    },
    addChecklistItem(templateId: number, req: TemplateChecklistItem): Promise<{ data: TemplateChecklistItem }> {
      return axios.post(`${BASE}/templates/${templateId}/checklist`, req)
    },
    deleteChecklistItem(templateId: number, itemId: number): Promise<void> {
      return axios.delete(`${BASE}/templates/${templateId}/checklist/${itemId}`)
    },
  },
}
