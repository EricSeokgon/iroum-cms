// 대시보드 + KPI 시각화 API 래퍼 — SPEC-CMS-008
import axios from 'axios'

// @MX:ANCHOR: [AUTO] dashboardApi — DashboardMainView, WidgetManageView, ExportHistoryView 및 dashboardStore에서 참조
// @MX:REASON: fan_in >= 3: SPEC-CMS-008 3개 뷰 + dashboardStore에서 공통 호출

const BASE = '/api/v1/dashboard'

// ── 위젯 (Widget) ─────────────────────────────────────────────────────────────
export type WidgetType =
  | 'METRIC_CARD'
  | 'LINE_CHART'
  | 'BAR_CHART'
  | 'PIE_CHART'
  | 'RADAR_CHART'
  | 'MATRIX_HEATMAP'
  | 'TABLE'
  | 'PROGRESS_BAR'
  | 'MAP_KOREA'

export type WidgetStatus = 'ACTIVE' | 'INACTIVE' | 'DEPRECATED'

export type DataSourceType = 'KPI_VALUE' | 'CUSTOM_QUERY' | 'EXTERNAL_API'

export interface WidgetSummary {
  id: number
  code: string
  type: string
}

export interface WidgetDataSeries {
  name: string
  data: Array<number | string | null>
}

export interface WidgetDataset {
  categories: string[]
  series: WidgetDataSeries[]
}

export interface WidgetDataResponse {
  widget: WidgetSummary
  available_dimensions: string[]
  applied_filter: Record<string, unknown>
  dataset: WidgetDataset
  generated_at: string
  cache_hit: boolean
}

export interface WidgetResponse {
  id: number
  code: string
  name: string
  description?: string
  widget_type: string
  data_source: string
  data_source_config?: string
  default_config?: string
  available_dimensions?: string[]
  required_role_codes?: string[]
  status: WidgetStatus
  created_at?: string
  updated_at?: string
}

export interface WidgetRequest {
  code: string
  name: string
  description?: string
  widget_type: string
  data_source: string
  data_source_config: string
  default_config?: string
  available_dimensions?: string[]
  required_role_codes?: string[]
  status?: WidgetStatus
}

export interface WidgetListParams {
  widget_type?: string
  status?: string
  page?: number
  size?: number
}

export interface WidgetDataParams {
  from?: string
  to?: string
  dim?: string
  roles?: string[]
}

// ── 레이아웃 (Layout) ─────────────────────────────────────────────────────────
export interface LayoutWidgetMapping {
  widget_id: number
  instance_id: string
  position: string
  config_override?: string
  sort_order: number
}

export interface LayoutWidgetEntry {
  widget_id: number
  instance_id: string
  position: string
  config_override?: string
  sort_order: number
}

export interface LayoutResponse {
  id: number
  owner_id: number
  name: string
  description?: string
  is_default: boolean
  grid_config?: string
  shared_with?: string[]
  widgets: LayoutWidgetMapping[]
  created_at?: string
  updated_at?: string
}

export interface LayoutRequest {
  name: string
  description?: string
  grid_config?: string
  shared_with?: string[]
  widgets?: LayoutWidgetEntry[]
}

// ── 저장된 뷰 (Saved View) ────────────────────────────────────────────────────
export interface SavedViewResponse {
  id: number
  owner_id: number
  dashboard_id?: number
  name: string
  description?: string
  filter_state: string
  is_default: boolean
  is_shared: boolean
  shared_with?: string[]
  created_at?: string
  last_used_at?: string
}

export interface SavedViewRequest {
  dashboard_id?: number
  name: string
  description?: string
  filter_state: string
  is_default?: boolean
  is_shared?: boolean
  shared_with?: string[]
}

// ── 내보내기 (Export) ─────────────────────────────────────────────────────────
export type ExportType = 'EXCEL' | 'CSV' | 'PDF'
export type ExportStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface ExportRequest {
  export_type: ExportType
  scope: string             // JSON 직렬화 문자열 (e.g. {"dashboard_id":5})
  async?: boolean
}

export interface ExportResponse {
  id: number
  requestor_id: number
  export_type: string
  scope: string
  file_path?: string
  size_bytes?: number
  row_count?: number
  status: ExportStatus
  progress_pct?: number
  error_message?: string
  requested_at?: string
  completed_at?: string
  expires_at?: string
  signed_download_url?: string
}

// ── 캐시 (Cache) ──────────────────────────────────────────────────────────────
export interface CacheInvalidateRequest {
  widget_ids?: number[]
  kpi_ids?: number[]
  all?: boolean
}

export interface CacheStatsResponse {
  active_entries: number
  expired_entries: number
}

// ── 필터 상태 (FE 전용) ───────────────────────────────────────────────────────
export interface DashboardFilterState {
  period?: '7d' | '30d' | '90d' | 'custom'
  from?: string
  to?: string
  features?: string[]
  industries?: string[]
}

// ── API 그룹 ──────────────────────────────────────────────────────────────────
export const dashboardApi = {
  // 위젯 CRUD
  widgets: {
    list(params?: WidgetListParams): Promise<{ data: WidgetResponse[] }> {
      return axios.get(`${BASE}/widgets`, { params })
    },
    get(id: number): Promise<{ data: WidgetResponse }> {
      return axios.get(`${BASE}/widgets/${id}`)
    },
    create(req: WidgetRequest): Promise<{ data: WidgetResponse }> {
      return axios.post(`${BASE}/widgets`, req)
    },
    update(id: number, req: WidgetRequest): Promise<{ data: WidgetResponse }> {
      return axios.put(`${BASE}/widgets/${id}`, req)
    },
    delete(id: number): Promise<void> {
      return axios.delete(`${BASE}/widgets/${id}`)
    },
    preview(req: WidgetRequest, roles?: string[]): Promise<{ data: WidgetDataResponse }> {
      return axios.post(`${BASE}/widgets/preview`, req, { params: { roles } })
    },
    data(id: number, params?: WidgetDataParams): Promise<{ data: WidgetDataResponse }> {
      return axios.get(`${BASE}/widgets/${id}/data`, { params })
    },
    series(id: number, dim?: string, group?: string, roles?: string[]): Promise<{ data: WidgetDataResponse }> {
      return axios.get(`${BASE}/widgets/${id}/data/series`, { params: { dim, group, roles } })
    },
  },

  // 레이아웃
  layouts: {
    list(): Promise<{ data: LayoutResponse[] }> {
      return axios.get(`${BASE}/layouts`)
    },
    get(id: number): Promise<{ data: LayoutResponse }> {
      return axios.get(`${BASE}/layouts/${id}`)
    },
    create(req: LayoutRequest): Promise<{ data: LayoutResponse }> {
      return axios.post(`${BASE}/layouts`, req)
    },
    update(id: number, req: LayoutRequest): Promise<{ data: LayoutResponse }> {
      return axios.put(`${BASE}/layouts/${id}`, req)
    },
    delete(id: number): Promise<void> {
      return axios.delete(`${BASE}/layouts/${id}`)
    },
    clone(id: number): Promise<{ data: LayoutResponse }> {
      return axios.post(`${BASE}/layouts/${id}/clone`)
    },
    setDefault(id: number): Promise<{ data: LayoutResponse }> {
      return axios.put(`${BASE}/layouts/${id}/default`)
    },
  },

  // 저장된 뷰
  views: {
    list(dashboardId?: number): Promise<{ data: SavedViewResponse[] }> {
      return axios.get(`${BASE}/views`, { params: { dashboard_id: dashboardId } })
    },
    create(req: SavedViewRequest): Promise<{ data: SavedViewResponse }> {
      return axios.post(`${BASE}/views`, req)
    },
    update(id: number, req: SavedViewRequest): Promise<{ data: SavedViewResponse }> {
      return axios.put(`${BASE}/views/${id}`, req)
    },
    delete(id: number): Promise<void> {
      return axios.delete(`${BASE}/views/${id}`)
    },
    apply(id: number): Promise<{ data: SavedViewResponse }> {
      return axios.post(`${BASE}/views/${id}/apply`)
    },
  },

  // 내보내기
  exports: {
    create(req: ExportRequest): Promise<{ data: ExportResponse }> {
      return axios.post(`${BASE}/export`, req)
    },
    status(id: number): Promise<{ data: ExportResponse }> {
      return axios.get(`${BASE}/export/${id}/status`)
    },
    download(id: number, signature?: string, exp?: number): string {
      // 다운로드는 브라우저가 직접 처리 (URL 생성만 반환)
      const params = new URLSearchParams()
      if (signature) params.set('sig', signature)
      if (exp != null) params.set('exp', String(exp))
      const qs = params.toString()
      return `${BASE}/export/${id}/download${qs ? '?' + qs : ''}`
    },
    history(status?: ExportStatus): Promise<{ data: ExportResponse[] }> {
      return axios.get(`${BASE}/export`, { params: { status } })
    },
  },

  // 캐시 (관리자)
  cache: {
    invalidate(req: CacheInvalidateRequest): Promise<void> {
      return axios.post(`${BASE}/cache/invalidate`, req)
    },
    stats(): Promise<{ data: CacheStatsResponse }> {
      return axios.get(`${BASE}/cache/stats`)
    },
  },
}
