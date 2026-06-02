// SPEC-CMS-DASHBOARD-PERSONALIZE-001 — 사용자별 대시보드 환경설정 API 래퍼
// @MX:ANCHOR: [AUTO] dashboardPreferenceApi — DashboardPreferencePanel, DashboardGridLayout, dashboardPreferenceStore 에서 참조
// @MX:REASON: fan_in >= 3 (preference panel + grid wrapper + Pinia store + vitest mock)
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7
import { apiClient } from '@iroum/shared/api/client'

// ── 타입 ───────────────────────────────────────────────────────────────────────

export type Theme = 'LIGHT' | 'DARK' | 'SYSTEM'
export type Density = 'COMPACT' | 'NORMAL' | 'COMFORTABLE'
export type FontScale = 0.875 | 1.0 | 1.125
export type ColorPalettePreference = 'DEFAULT' | 'COLORBLIND' | 'MONOCHROME'

export interface DashboardPreferenceResponse {
  user_id: number
  /** layout_id (string) → instance_id 배열 */
  hidden_widget_instance_ids: Record<string, string[]>
  theme: Theme
  density: Density
  font_scale: number
  color_palette_preference: ColorPalettePreference
  sidebar_collapsed: boolean
  /** SPEC-CMS-DASHBOARD-REFRESH-001: 자동 새로고침 주기(초). null 이면 꺼짐 */
  refresh_interval_seconds: number | null
  schema_version: number
  updated_at: string
}

export interface DashboardPreferenceUpdateRequest {
  theme?: Theme
  density?: Density
  font_scale?: FontScale
  color_palette_preference?: ColorPalettePreference
  sidebar_collapsed?: boolean
  /** SPEC-CMS-DASHBOARD-REFRESH-001: 자동 새로고침 주기(초) 또는 null(꺼짐) */
  refresh_interval_seconds?: number | null
  /** refresh_interval_seconds 를 갱신 대상에 포함할지 여부 (null 명시 갱신 구분용) */
  has_refresh_interval_seconds?: boolean
  /** REQ-DP-002-4 낙관적 잠금 (옵션). 응답의 updated_at 을 그대로 전달 */
  expected_updated_at?: string
}

export interface WidgetVisibilityRequest {
  instance_id: string
  hidden: boolean
}

export interface PositionPatchEntry {
  instance_id: string
  position: { x: number; y: number; w: number; h: number }
}

export interface PositionPatchRequest {
  entries: PositionPatchEntry[]
  /** REQ-DP-003-5 낙관적 잠금 (옵션) */
  expected_updated_at?: string
}

// ── API ────────────────────────────────────────────────────────────────────────

const BASE = '/dashboard/preference'
const LAYOUTS_BASE = '/dashboard/layouts'

export const dashboardPreferenceApi = {
  /** REQ-DP-002-1~3 / AC-DP-API-1: 본인 환경설정 조회 (lazy 생성). */
  get() {
    return apiClient.get<DashboardPreferenceResponse>(BASE)
  },

  /** REQ-DP-002-4 / AC-DP-API-2: 부분 갱신. */
  patch(req: DashboardPreferenceUpdateRequest) {
    return apiClient.patch<DashboardPreferenceResponse>(BASE, req)
  },

  /** REQ-DP-002-5 / AC-DP-002-5: 스타일 DEFAULT 초기화 (hidden 보존). */
  reset() {
    return apiClient.post<DashboardPreferenceResponse>(`${BASE}/reset`)
  },

  /** REQ-DP-001-1 / 001-2: 단건 위젯 가시성 토글. */
  toggleVisibility(layoutId: number, req: WidgetVisibilityRequest) {
    return apiClient.patch<DashboardPreferenceResponse>(
      `${BASE}/widgets/${layoutId}/hidden`,
      req,
    )
  },

  /** REQ-DP-001-5 / AC-DP-001-5: 특정 레이아웃 hidden 초기화. */
  showAllWidgets(layoutId: number) {
    return apiClient.post<DashboardPreferenceResponse>(
      `${BASE}/widgets/${layoutId}/show-all`,
    )
  },

  /** REQ-DP-003-2 / AC-DP-003-1: 드래그앤드롭 결과 영속화 (PATCH /layouts/{id}/positions). */
  patchPositions(layoutId: number, req: PositionPatchRequest) {
    return apiClient.patch<void>(`${LAYOUTS_BASE}/${layoutId}/positions`, req)
  },
}
