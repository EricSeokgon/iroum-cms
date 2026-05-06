// 거버넌스 API 래퍼 — SPEC-CMS-009
import axios from 'axios'

// @MX:ANCHOR: [AUTO] governanceApi — 6개 거버넌스 뷰에서 공통 참조
// @MX:REASON: fan_in >= 3: DataDictionary/RetentionPolicy/BatchLogs/QualityRule/QualityReport/RecoveryDrill 뷰

const BASE = '/api/v1/governance'

// ── 공통 페이지 응답 ──────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 데이터 사전 ───────────────────────────────────────────────────────────────
export type DataDomain = 'MASTER' | 'TRANSACTION' | 'STATISTICS' | 'LOG'
export type DataDictionaryStatus = 'ACTIVE' | 'DEPRECATED' | 'REMOVED'

export interface DataDictionary {
  id: number
  table_name: string
  column_name: string
  logical_name_ko: string
  logical_name_en?: string
  data_domain: DataDomain
  data_type: string
  description?: string
  is_pii: boolean
  is_required: boolean
  status: DataDictionaryStatus
  created_at?: string
  updated_at?: string
}

export interface DataDictionaryHistory {
  id: number
  dictionary_id: number
  changed_at: string
  changed_by?: string
  before_json?: string
  after_json?: string
  change_type: 'CREATE' | 'UPDATE' | 'DELETE'
}

export interface DataDictionaryDetail extends DataDictionary {
  history?: DataDictionaryHistory[]
}

export interface DataDictionaryFilter {
  table?: string
  domain?: DataDomain
  status?: DataDictionaryStatus
  page?: number
  size?: number
}

export interface DataDictionaryRequest {
  table_name: string
  column_name: string
  logical_name_ko: string
  logical_name_en?: string
  data_domain: DataDomain
  data_type: string
  description?: string
  is_pii: boolean
  is_required: boolean
  status: DataDictionaryStatus
}

export interface FreshnessResult {
  checked_at: string
  missing: Array<{ table_name: string; column_name: string; data_type?: string }>
  stale: Array<{ table_name: string; column_name: string; reason: string }>
  total_checked: number
}

// ── 보존 정책 ─────────────────────────────────────────────────────────────────
export type RetentionPolicyType = 'ARCHIVE' | 'DELETE'
export type RetentionStatus = 'ACTIVE' | 'PAUSED' | 'INACTIVE'

export interface RetentionPolicy {
  id: number
  target_table: string
  policy_type: RetentionPolicyType
  retention_months: number
  archive_table?: string
  schedule_cron: string
  status: RetentionStatus
  last_run_at?: string
  last_run_status?: string
  created_at?: string
  updated_at?: string
}

export interface RetentionPolicyRequest {
  target_table: string
  policy_type: RetentionPolicyType
  retention_months: number
  archive_table?: string
  schedule_cron: string
  status: RetentionStatus
}

export interface RetentionRunResult {
  batch_log_id: number
  triggered_at: string
}

// ── 배치 실행 이력 ────────────────────────────────────────────────────────────
export type BatchJobGroup = 'STATS' | 'RETENTION' | 'QUALITY'
export type BatchStatus = 'SUCCESS' | 'FAILURE' | 'RUNNING'

export interface BatchExecutionLog {
  id: number
  job_name: string
  job_group: BatchJobGroup
  started_at: string
  finished_at?: string
  duration_ms?: number
  status: BatchStatus
  records_processed?: number
  records_failed?: number
  retry_count?: number
  error_summary?: string
  detail_json?: string
}

export interface BatchLogFilter {
  jobGroup?: BatchJobGroup
  status?: BatchStatus
  from?: string
  to?: string
  jobName?: string
  page?: number
  size?: number
}

export interface StatsRecomputeRequest {
  job: string
  dateRange: { from: string; to: string }
}

// ── 통계 (ECharts용) ──────────────────────────────────────────────────────────
export interface BoardStatRow {
  date: string
  board_id: number
  total_views: number
  unique_visitors: number
  post_count: number
  comment_count: number
}

export interface ContentStatRow {
  date: string
  content_id: number
  view_count: number
  unique_viewers: number
  avg_dwell_sec: number
}

export interface PolicyStatRow {
  month: string
  policy_id: number
  match_count: number
  apply_count: number
  apply_conversion_rate: number
  success_count: number
}

export interface SafetyStatRow {
  month: string
  incident_category: string
  incident_count: number
  casualty_count: number
  severity_avg: number
}

// ── 품질 룰 ───────────────────────────────────────────────────────────────────
export type QualityRuleType = 'NULL_RATIO' | 'RANGE' | 'IQR' | 'UNIQUE' | 'FRESHNESS'
export type QualitySeverity = 'INFO' | 'WARN' | 'CRITICAL'
export type QualityRuleStatus = 'ACTIVE' | 'INACTIVE'

export interface QualityRule {
  id: number
  target_table: string
  target_column: string
  rule_type: QualityRuleType
  threshold?: number
  range_min?: number
  range_max?: number
  severity: QualitySeverity
  schedule_cron: string
  status: QualityRuleStatus
  created_at?: string
  updated_at?: string
}

export interface QualityRuleFilter {
  ruleType?: QualityRuleType
  severity?: QualitySeverity
  active?: boolean
  page?: number
  size?: number
}

export interface QualityRuleRequest {
  target_table: string
  target_column: string
  rule_type: QualityRuleType
  threshold?: number
  range_min?: number
  range_max?: number
  severity: QualitySeverity
  schedule_cron: string
  status: QualityRuleStatus
}

// ── 품질 리포트 ───────────────────────────────────────────────────────────────
export interface QualityReport {
  id: number
  rule_id: number
  target_table: string
  target_column: string
  rule_type: QualityRuleType
  severity: QualitySeverity
  checked_at: string
  measured_value?: number
  threshold?: number
  range_min?: number
  range_max?: number
  violation: boolean
  notified: boolean
  sample_pks?: string[]
  detail_json?: string
}

export interface QualityReportFilter {
  violation?: boolean
  severity?: QualitySeverity
  ruleId?: number
  from?: string
  to?: string
  limit?: number
  page?: number
  size?: number
}

// ── 복구 시험 ─────────────────────────────────────────────────────────────────
export type DrillType = 'BACKUP_RESTORE' | 'FAILOVER' | 'FULL_DR'
export type DrillResult = 'PASS' | 'FAIL'

export interface RecoveryDrill {
  id: number
  drill_date: string
  drill_type: DrillType
  result: DrillResult
  rto_actual_min: number
  rpo_actual_min: number
  rto_target_min: number
  rpo_target_min: number
  checklist_json?: Record<string, boolean>
  notes?: string
  created_at?: string
}

export interface RecoveryDrillRequest {
  drill_date: string
  drill_type: DrillType
  result: DrillResult
  rto_actual_min: number
  rpo_actual_min: number
  checklist_json: Record<string, boolean>
  notes?: string
}

export interface RecoveryDrillFilter {
  from?: string
  to?: string
}

// ── 백업 상태 ─────────────────────────────────────────────────────────────────
export interface BackupStatus {
  last_backup_at: string
  last_backup_size_bytes: number
  last_backup_result: string
  hours_since_backup: number
  target_rpo_min: number
  rpo_compliance: boolean
}

// ── API 그룹 ──────────────────────────────────────────────────────────────────
export const governanceApi = {
  // 데이터 사전
  dictionary: {
    list(params: DataDictionaryFilter): Promise<{ data: PageResponse<DataDictionary> }> {
      return axios.get(`${BASE}/dictionary`, { params })
    },
    get(id: number): Promise<{ data: DataDictionaryDetail }> {
      return axios.get(`${BASE}/dictionary/${id}`)
    },
    create(req: DataDictionaryRequest): Promise<{ data: DataDictionary }> {
      return axios.post(`${BASE}/dictionary`, req)
    },
    update(id: number, req: DataDictionaryRequest): Promise<{ data: DataDictionary }> {
      return axios.put(`${BASE}/dictionary/${id}`, req)
    },
    remove(id: number): Promise<void> {
      return axios.delete(`${BASE}/dictionary/${id}`)
    },
    exportFile(format: 'csv' | 'xlsx'): Promise<{ data: Blob }> {
      return axios.get(`${BASE}/dictionary/export`, {
        params: { format },
        responseType: 'blob',
      })
    },
    freshness(): Promise<{ data: FreshnessResult }> {
      return axios.get(`${BASE}/dictionary/freshness`)
    },
  },

  // 보존 정책
  retention: {
    list(): Promise<{ data: RetentionPolicy[] }> {
      return axios.get(`${BASE}/retention-policies`)
    },
    create(req: RetentionPolicyRequest): Promise<{ data: RetentionPolicy }> {
      return axios.post(`${BASE}/retention-policies`, req)
    },
    update(id: number, req: RetentionPolicyRequest): Promise<{ data: RetentionPolicy }> {
      return axios.put(`${BASE}/retention-policies/${id}`, req)
    },
    runNow(id: number): Promise<{ data: RetentionRunResult }> {
      return axios.post(`${BASE}/retention-policies/${id}/run`)
    },
  },

  // 배치 로그
  batchLogs: {
    list(params: BatchLogFilter): Promise<{ data: PageResponse<BatchExecutionLog> }> {
      return axios.get(`${BASE}/batch-logs`, { params })
    },
    get(id: number): Promise<{ data: BatchExecutionLog }> {
      return axios.get(`${BASE}/batch-logs/${id}`)
    },
    recompute(req: StatsRecomputeRequest): Promise<{ data: BatchExecutionLog }> {
      return axios.post(`${BASE}/stats/recompute`, req)
    },
  },

  // 통계 (차트용)
  stats: {
    boards(params: { boardId?: number; from?: string; to?: string; period?: 'daily' | 'monthly' }): Promise<{ data: BoardStatRow[] }> {
      return axios.get(`${BASE}/stats/boards`, { params })
    },
    contents(params: { contentId?: number; from?: string; to?: string }): Promise<{ data: ContentStatRow[] }> {
      return axios.get(`${BASE}/stats/contents`, { params })
    },
    policies(params: { policyId?: number; from?: string; to?: string }): Promise<{ data: PolicyStatRow[] }> {
      return axios.get(`${BASE}/stats/policies`, { params })
    },
    safety(params: { category?: string; from?: string; to?: string }): Promise<{ data: SafetyStatRow[] }> {
      return axios.get(`${BASE}/stats/safety`, { params })
    },
  },

  // 품질 룰
  qualityRules: {
    list(params: QualityRuleFilter): Promise<{ data: PageResponse<QualityRule> }> {
      return axios.get(`${BASE}/quality-rules`, { params })
    },
    create(req: QualityRuleRequest): Promise<{ data: QualityRule }> {
      return axios.post(`${BASE}/quality-rules`, req)
    },
    update(id: number, req: QualityRuleRequest): Promise<{ data: QualityRule }> {
      return axios.put(`${BASE}/quality-rules/${id}`, req)
    },
    remove(id: number): Promise<void> {
      return axios.delete(`${BASE}/quality-rules/${id}`)
    },
    runNow(id: number): Promise<{ data: BatchExecutionLog }> {
      return axios.post(`${BASE}/quality-rules/${id}/run`)
    },
  },

  // 품질 리포트
  qualityReports: {
    list(params: QualityReportFilter): Promise<{ data: PageResponse<QualityReport> }> {
      return axios.get(`${BASE}/quality-reports`, { params })
    },
    get(id: number): Promise<{ data: QualityReport }> {
      return axios.get(`${BASE}/quality-reports/${id}`)
    },
  },

  // 복구 시험
  recoveryDrills: {
    list(params: RecoveryDrillFilter): Promise<{ data: RecoveryDrill[] }> {
      return axios.get(`${BASE}/recovery-drills`, { params })
    },
    create(req: RecoveryDrillRequest): Promise<{ data: RecoveryDrill }> {
      return axios.post(`${BASE}/recovery-drills`, req)
    },
  },

  // 백업 상태
  backup: {
    status(): Promise<{ data: BackupStatus }> {
      // 503 도 정상 응답으로 받아 rpo_compliance 판단
      return axios.get(`/api/v1/actuator/backup-status`, {
        validateStatus: (s) => s === 200 || s === 503,
      })
    },
  },
}
