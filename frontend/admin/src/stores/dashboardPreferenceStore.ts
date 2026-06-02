// SPEC-CMS-DASHBOARD-PERSONALIZE-001 — 사용자별 대시보드 환경설정 Pinia 스토어
// @MX:ANCHOR: [AUTO] useDashboardPreferenceStore — DashboardPreferencePanel, DashboardGridLayout, DashboardMainView 에서 참조
// @MX:REASON: fan_in >= 3 (preference panel + grid wrapper + main view 통합 hook)
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 REQ-DP-001 ~ 003
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  dashboardPreferenceApi,
  type ColorPalettePreference,
  type DashboardPreferenceResponse,
  type DashboardPreferenceUpdateRequest,
  type Density,
  type FontScale,
  type PositionPatchEntry,
  type Theme,
} from '@/api/dashboardPreference'

const DEFAULT_PREFERENCE: DashboardPreferenceResponse = {
  user_id: 0,
  hidden_widget_instance_ids: {},
  theme: 'SYSTEM',
  density: 'NORMAL',
  font_scale: 1.0,
  color_palette_preference: 'DEFAULT',
  sidebar_collapsed: false,
  refresh_interval_seconds: null,
  schema_version: 1,
  updated_at: '',
}

export const useDashboardPreferenceStore = defineStore('dashboardPreference', () => {
  // ── 상태 ─────────────────────────────────────────────────────────────────
  const preference = ref<DashboardPreferenceResponse>({ ...DEFAULT_PREFERENCE })
  const loading = ref(false)
  const error = ref<string | null>(null)

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── Getter ───────────────────────────────────────────────────────────────
  /** AC-DP-001-3: 특정 layout 의 instance_id 가 hidden 인지 확인 */
  function isHidden(layoutId: number, instanceId: string): boolean {
    const list = preference.value.hidden_widget_instance_ids[String(layoutId)]
    return Array.isArray(list) && list.includes(instanceId)
  }

  /** AC-DP-002-2 / SYSTEM 테마 — matchMedia 결과 반영 */
  const effectiveTheme = computed<'light' | 'dark'>(() => {
    const t = preference.value.theme
    if (t === 'LIGHT') return 'light'
    if (t === 'DARK') return 'dark'
    // SYSTEM
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    }
    return 'light'
  })

  // ── 액션 ─────────────────────────────────────────────────────────────────
  async function fetch(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await dashboardPreferenceApi.get()
      preference.value = res.data
    } catch (e) {
      setError(e, '환경설정 조회 실패')
    } finally {
      loading.value = false
    }
  }

  /** REQ-DP-002-4 / AC-DP-002-6: PATCH 실패 시 이전 값으로 롤백 */
  async function update(req: DashboardPreferenceUpdateRequest): Promise<void> {
    const previous = { ...preference.value }
    // optimistic UI
    preference.value = {
      ...preference.value,
      ...(req.theme !== undefined ? { theme: req.theme } : {}),
      ...(req.density !== undefined ? { density: req.density } : {}),
      ...(req.font_scale !== undefined ? { font_scale: req.font_scale } : {}),
      ...(req.color_palette_preference !== undefined
        ? { color_palette_preference: req.color_palette_preference }
        : {}),
      ...(req.sidebar_collapsed !== undefined
        ? { sidebar_collapsed: req.sidebar_collapsed }
        : {}),
      ...(req.has_refresh_interval_seconds === true
        ? { refresh_interval_seconds: req.refresh_interval_seconds ?? null }
        : {}),
    }
    try {
      const res = await dashboardPreferenceApi.patch(req)
      preference.value = res.data
    } catch (e) {
      preference.value = previous
      setError(e, '환경설정 갱신 실패')
      throw e
    }
  }

  async function reset(): Promise<void> {
    try {
      const res = await dashboardPreferenceApi.reset()
      preference.value = res.data
    } catch (e) {
      setError(e, '환경설정 초기화 실패')
      throw e
    }
  }

  async function toggleVisibility(
    layoutId: number,
    instanceId: string,
    hidden: boolean,
  ): Promise<void> {
    try {
      const res = await dashboardPreferenceApi.toggleVisibility(layoutId, {
        instance_id: instanceId,
        hidden,
      })
      preference.value = res.data
    } catch (e) {
      setError(e, '위젯 가시성 변경 실패')
      throw e
    }
  }

  async function showAllWidgets(layoutId: number): Promise<void> {
    try {
      const res = await dashboardPreferenceApi.showAllWidgets(layoutId)
      preference.value = res.data
    } catch (e) {
      setError(e, '모든 위젯 표시 실패')
      throw e
    }
  }

  /** REQ-DP-003-2: DnD 결과 영속화 (낙관적 잠금 옵션) */
  async function patchPositions(
    layoutId: number,
    entries: PositionPatchEntry[],
    options?: { expectedUpdatedAt?: string },
  ): Promise<void> {
    try {
      await dashboardPreferenceApi.patchPositions(layoutId, {
        entries,
        expected_updated_at: options?.expectedUpdatedAt,
      })
    } catch (e) {
      setError(e, '위젯 위치 저장 실패')
      throw e
    }
  }

  // ── 편의 setter (단건) ─────────────────────────────────────────────────
  function setTheme(theme: Theme) {
    return update({ theme })
  }

  function setDensity(density: Density) {
    return update({ density })
  }

  function setFontScale(font_scale: FontScale) {
    return update({ font_scale })
  }

  function setColorPalette(palette: ColorPalettePreference) {
    return update({ color_palette_preference: palette })
  }

  /** SPEC-CMS-DASHBOARD-REFRESH-001: 자동 새로고침 주기 설정 (null=끄기) */
  function setRefreshInterval(seconds: number | null) {
    return update({
      refresh_interval_seconds: seconds,
      has_refresh_interval_seconds: true,
    })
  }

  return {
    // state
    preference,
    loading,
    error,
    // getters
    isHidden,
    effectiveTheme,
    // actions
    fetch,
    update,
    reset,
    toggleVisibility,
    showAllWidgets,
    patchPositions,
    setTheme,
    setDensity,
    setFontScale,
    setColorPalette,
    setRefreshInterval,
  }
})
