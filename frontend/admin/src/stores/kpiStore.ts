// SPEC-CMS-KPI-001 Phase 4 — KPI 대시보드 Pinia 스토어
// @MX:ANCHOR: [AUTO] useKpiStore — KpiDashboardView, KPI 위젯 컴포넌트, 단위 테스트에서 공유 상태
// @MX:REASON: fan_in >= 3: KPI 필터/조회/내보내기 상태의 단일 진실 공급원
import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'
import {
  kpiApi,
  KPI_CODES,
  type KpiQueryParams,
  type KpiValueItem,
  type ExportJobResponse,
} from '@/api/kpi'

/** 마지막 30일을 기본 조회 범위로 사용한다. */
function defaultFilters(): KpiQueryParams {
  const today = new Date()
  const from = new Date(today)
  from.setDate(from.getDate() - 30)
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  return {
    fromDate: fmt(from),
    toDate: fmt(today),
    granularity: 'daily',
    page: 0,
    size: 100,
  }
}

export const useKpiStore = defineStore('kpi', () => {
  // ── 상태 ─────────────────────────────────────────────────────────────────
  const filters = reactive<KpiQueryParams>(defaultFilters())
  const kpiValues = ref<KpiValueItem[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const exportJobId = ref<string | null>(null)
  const exporting = ref(false)

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 액션 ─────────────────────────────────────────────────────────────────

  /** AC-016 — KPI 값 조회. 전달 params 로 필터를 갱신하고 위젯 데이터를 채운다. */
  async function loadKpiValues(params?: Partial<KpiQueryParams>): Promise<void> {
    if (params) Object.assign(filters, params)
    loading.value = true
    error.value = null
    try {
      const res = await kpiApi.fetchKpiValues({ ...filters })
      kpiValues.value = res.items
    } catch (e) {
      // 스토어는 error state 만 설정 — 토스트는 뷰의 책임
      setError(e, 'KPI 데이터를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /**
   * Excel 내보내기. 동기(Blob)면 즉시 다운로드, 비동기(202)면 작업 ID 저장 후 폴링한다.
   */
  async function exportToExcel(params?: Partial<KpiQueryParams>): Promise<void> {
    const query: KpiQueryParams = { ...filters, ...params }
    exporting.value = true
    error.value = null
    exportJobId.value = null
    try {
      const result = await kpiApi.exportKpi(query)
      if (result instanceof Blob) {
        kpiApi.saveBlob(result, `kpi-${Date.now()}.xls`)
        return
      }
      // 비동기 작업 — jobId 저장 후 폴링
      exportJobId.value = (result as ExportJobResponse).jobId
      await pollExportJob(exportJobId.value)
    } catch (e) {
      setError(e, 'KPI 내보내기에 실패했습니다.')
    } finally {
      exporting.value = false
    }
  }

  /** 비동기 내보내기 작업을 COMPLETED 또는 FAILED 까지 폴링한다. */
  async function pollExportJob(jobId: string, maxAttempts = 30, intervalMs = 1000): Promise<void> {
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const status = await kpiApi.pollExportStatus(jobId)
      if (status.status === 'COMPLETED') {
        if (status.downloadToken) kpiApi.downloadExport(status.downloadToken)
        return
      }
      if (status.status === 'FAILED') {
        error.value = status.message ?? 'KPI 내보내기 작업이 실패했습니다.'
        return
      }
      await new Promise((resolve) => setTimeout(resolve, intervalMs))
    }
    error.value = 'KPI 내보내기 작업 시간이 초과되었습니다.'
  }

  /** 필터를 기본값으로 초기화한다(재조회는 호출자 책임). */
  function resetFilters(): void {
    // defaultFilters() 에 없는 선택 키(kpiCode/dimensionJson)는 명시적으로 비운다.
    filters.kpiCode = undefined
    filters.dimensionJson = undefined
    Object.assign(filters, defaultFilters())
  }

  // ── 게터 ─────────────────────────────────────────────────────────────────
  const featureUsageItems = computed(() =>
    kpiValues.value.filter((i) => i.kpiCode === KPI_CODES.FEATURE_USAGE_RATE),
  )
  const fileDownloadItems = computed(() =>
    kpiValues.value.filter((i) => i.kpiCode === KPI_CODES.FILE_DOWNLOAD_COUNT),
  )
  const conversionItems = computed(() =>
    kpiValues.value.filter((i) => i.kpiCode === KPI_CODES.POLICY_APPLY_CONVERSION_RATE),
  )
  const hasPreparingData = computed(() =>
    kpiValues.value.some((i) => i.dataState === 'PREPARING'),
  )

  return {
    // state
    filters,
    kpiValues,
    loading,
    error,
    exportJobId,
    exporting,
    // actions
    loadKpiValues,
    exportToExcel,
    pollExportJob,
    resetFilters,
    // getters
    featureUsageItems,
    fileDownloadItems,
    conversionItems,
    hasPreparingData,
  }
})
