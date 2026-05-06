// 대시보드 + KPI 시각화 Pinia 스토어 — SPEC-CMS-008
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import type {
  WidgetResponse,
  WidgetRequest,
  WidgetListParams,
  WidgetDataParams,
  WidgetDataResponse,
  LayoutResponse,
  LayoutRequest,
  SavedViewResponse,
  SavedViewRequest,
  ExportRequest,
  ExportResponse,
  ExportStatus,
  CacheInvalidateRequest,
  CacheStatsResponse,
} from '@/api/dashboard'

// @MX:ANCHOR: [AUTO] useDashboardStore — SPEC-CMS-008 3개 뷰에서 공통 참조
// @MX:REASON: fan_in >= 3: DashboardMain/WidgetManage/ExportHistory 뷰가 공통 상태 사용

export const useDashboardStore = defineStore('dashboard', () => {
  // ── 위젯 상태 ──────────────────────────────────────────────────────────────
  const widgets = ref<WidgetResponse[]>([])
  const widgetLoading = ref(false)
  // 위젯 ID → 데이터 응답 캐시
  const widgetDataMap = ref<Record<number, WidgetDataResponse>>({})
  const widgetDataLoading = ref<Record<number, boolean>>({})

  // ── 레이아웃 상태 ──────────────────────────────────────────────────────────
  const layouts = ref<LayoutResponse[]>([])
  const currentLayout = ref<LayoutResponse | null>(null)
  const layoutLoading = ref(false)

  // ── 저장된 뷰 상태 ──────────────────────────────────────────────────────────
  const views = ref<SavedViewResponse[]>([])
  const viewLoading = ref(false)

  // ── 내보내기 상태 ──────────────────────────────────────────────────────────
  const exportHistory = ref<ExportResponse[]>([])
  const exportLoading = ref(false)

  // ── 캐시 상태 ──────────────────────────────────────────────────────────────
  const cacheStats = ref<CacheStatsResponse | null>(null)

  // ── 공통 에러 ───────────────────────────────────────────────────────────────
  const error = ref<string | null>(null)

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 위젯 액션 ───────────────────────────────────────────────────────────────
  async function fetchWidgets(params?: WidgetListParams): Promise<void> {
    widgetLoading.value = true
    error.value = null
    try {
      const res = await dashboardApi.widgets.list(params)
      widgets.value = res.data
    } catch (e) {
      setError(e, '위젯 목록 조회 실패')
    } finally {
      widgetLoading.value = false
    }
  }

  async function createWidget(req: WidgetRequest): Promise<WidgetResponse> {
    const res = await dashboardApi.widgets.create(req)
    return res.data
  }

  async function updateWidget(id: number, req: WidgetRequest): Promise<WidgetResponse> {
    const res = await dashboardApi.widgets.update(id, req)
    return res.data
  }

  async function deleteWidget(id: number): Promise<void> {
    await dashboardApi.widgets.delete(id)
  }

  async function fetchWidgetData(id: number, params?: WidgetDataParams): Promise<WidgetDataResponse> {
    widgetDataLoading.value = { ...widgetDataLoading.value, [id]: true }
    try {
      const res = await dashboardApi.widgets.data(id, params)
      widgetDataMap.value = { ...widgetDataMap.value, [id]: res.data }
      return res.data
    } finally {
      widgetDataLoading.value = { ...widgetDataLoading.value, [id]: false }
    }
  }

  async function previewWidget(req: WidgetRequest, roles?: string[]): Promise<WidgetDataResponse> {
    const res = await dashboardApi.widgets.preview(req, roles)
    return res.data
  }

  // ── 레이아웃 액션 ──────────────────────────────────────────────────────────
  async function fetchLayouts(): Promise<void> {
    layoutLoading.value = true
    error.value = null
    try {
      const res = await dashboardApi.layouts.list()
      layouts.value = res.data
    } catch (e) {
      setError(e, '레이아웃 목록 조회 실패')
    } finally {
      layoutLoading.value = false
    }
  }

  async function fetchLayout(id: number): Promise<void> {
    layoutLoading.value = true
    error.value = null
    try {
      const res = await dashboardApi.layouts.get(id)
      currentLayout.value = res.data
    } catch (e) {
      setError(e, '레이아웃 상세 조회 실패')
    } finally {
      layoutLoading.value = false
    }
  }

  async function createLayout(req: LayoutRequest): Promise<LayoutResponse> {
    const res = await dashboardApi.layouts.create(req)
    return res.data
  }

  async function updateLayout(id: number, req: LayoutRequest): Promise<LayoutResponse> {
    const res = await dashboardApi.layouts.update(id, req)
    return res.data
  }

  async function deleteLayout(id: number): Promise<void> {
    await dashboardApi.layouts.delete(id)
  }

  async function cloneLayout(id: number): Promise<LayoutResponse> {
    const res = await dashboardApi.layouts.clone(id)
    return res.data
  }

  async function setDefaultLayout(id: number): Promise<LayoutResponse> {
    const res = await dashboardApi.layouts.setDefault(id)
    return res.data
  }

  // ── 저장된 뷰 액션 ──────────────────────────────────────────────────────────
  async function fetchViews(dashboardId?: number): Promise<void> {
    viewLoading.value = true
    error.value = null
    try {
      const res = await dashboardApi.views.list(dashboardId)
      views.value = res.data
    } catch (e) {
      setError(e, '저장된 뷰 조회 실패')
    } finally {
      viewLoading.value = false
    }
  }

  async function saveView(req: SavedViewRequest): Promise<SavedViewResponse> {
    const res = await dashboardApi.views.create(req)
    // 캐시 최신화
    views.value = [...views.value, res.data]
    return res.data
  }

  async function updateView(id: number, req: SavedViewRequest): Promise<SavedViewResponse> {
    const res = await dashboardApi.views.update(id, req)
    const idx = views.value.findIndex(v => v.id === id)
    if (idx >= 0) views.value[idx] = res.data
    return res.data
  }

  async function deleteView(id: number): Promise<void> {
    await dashboardApi.views.delete(id)
    views.value = views.value.filter(v => v.id !== id)
  }

  async function applyView(id: number): Promise<SavedViewResponse> {
    const res = await dashboardApi.views.apply(id)
    return res.data
  }

  // ── 내보내기 액션 ──────────────────────────────────────────────────────────
  async function requestExport(req: ExportRequest): Promise<ExportResponse> {
    const res = await dashboardApi.exports.create(req)
    return res.data
  }

  async function pollExportStatus(id: number): Promise<ExportResponse> {
    const res = await dashboardApi.exports.status(id)
    // 이력 캐시 갱신
    const idx = exportHistory.value.findIndex(e => e.id === id)
    if (idx >= 0) exportHistory.value[idx] = res.data
    return res.data
  }

  async function listExportHistory(status?: ExportStatus): Promise<void> {
    exportLoading.value = true
    error.value = null
    try {
      const res = await dashboardApi.exports.history(status)
      exportHistory.value = res.data
    } catch (e) {
      setError(e, '내보내기 이력 조회 실패')
    } finally {
      exportLoading.value = false
    }
  }

  // ── 캐시 액션 ──────────────────────────────────────────────────────────────
  async function invalidateCache(req: CacheInvalidateRequest): Promise<void> {
    await dashboardApi.cache.invalidate(req)
    // 위젯 데이터 캐시도 비움
    widgetDataMap.value = {}
  }

  async function fetchCacheStats(): Promise<void> {
    try {
      const res = await dashboardApi.cache.stats()
      cacheStats.value = res.data
    } catch (e) {
      setError(e, '캐시 통계 조회 실패')
    }
  }

  return {
    // 상태
    widgets,
    widgetLoading,
    widgetDataMap,
    widgetDataLoading,
    layouts,
    currentLayout,
    layoutLoading,
    views,
    viewLoading,
    exportHistory,
    exportLoading,
    cacheStats,
    error,
    // 위젯 액션
    fetchWidgets,
    createWidget,
    updateWidget,
    deleteWidget,
    fetchWidgetData,
    previewWidget,
    // 레이아웃 액션
    fetchLayouts,
    fetchLayout,
    createLayout,
    updateLayout,
    deleteLayout,
    cloneLayout,
    setDefaultLayout,
    // 뷰 액션
    fetchViews,
    saveView,
    updateView,
    deleteView,
    applyView,
    // 내보내기 액션
    requestExport,
    pollExportStatus,
    listExportHistory,
    // 캐시 액션
    invalidateCache,
    fetchCacheStats,
  }
})
