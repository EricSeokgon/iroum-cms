// SPEC-CMS-KPI-001 Phase 4 — useKpiStore 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/kpi', async () => {
  const actual = await vi.importActual<typeof import('@/api/kpi')>('@/api/kpi')
  return {
    ...actual,
    kpiApi: {
      fetchKpiValues: vi.fn(),
      exportKpi: vi.fn(),
      pollExportStatus: vi.fn(),
      downloadExport: vi.fn(),
      saveBlob: vi.fn(),
    },
  }
})

import { useKpiStore } from '@/stores/kpiStore'
import { kpiApi, KPI_CODES, type KpiValueItem, type KpiQueryResponse } from '@/api/kpi'

function item(
  kpiCode: string,
  value: number | null,
  aggregatedAt: string,
  dataState: KpiValueItem['dataState'] = 'READY',
): KpiValueItem {
  return {
    kpiCode,
    kpiName: kpiCode,
    dimensionJson: JSON.stringify({ date: aggregatedAt.slice(0, 10) }),
    value,
    aggregatedAt,
    dataState,
  }
}

function response(items: KpiValueItem[]): KpiQueryResponse {
  return { items, hasMore: false, totalCount: items.length, filters: {} }
}

describe('useKpiStore (SPEC-CMS-KPI-001 Phase 4)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('기본 필터는 마지막 30일 + daily 집계로 초기화된다', () => {
    const store = useKpiStore()
    expect(store.filters.granularity).toBe('daily')
    expect(store.filters.fromDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(store.filters.toDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(new Date(store.filters.fromDate).getTime()).toBeLessThan(
      new Date(store.filters.toDate).getTime(),
    )
  })

  it('AC-016: loadKpiValues 가 응답을 kpiValues 에 채우고 loading 을 리셋한다', async () => {
    vi.mocked(kpiApi.fetchKpiValues).mockResolvedValueOnce(
      response([
        item(KPI_CODES.FEATURE_USAGE_RATE, 42.5, '2026-06-01T00:00:00Z'),
        item(KPI_CODES.FILE_DOWNLOAD_COUNT, 120, '2026-06-01T00:00:00Z'),
      ]),
    )

    const store = useKpiStore()
    await store.loadKpiValues()

    expect(store.kpiValues).toHaveLength(2)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('AC-016: loadKpiValues(params) 는 필터를 갱신하고 백엔드에 전달한다', async () => {
    vi.mocked(kpiApi.fetchKpiValues).mockResolvedValueOnce(response([]))

    const store = useKpiStore()
    await store.loadKpiValues({ granularity: 'monthly', kpiCode: KPI_CODES.FEATURE_USAGE_RATE })

    expect(store.filters.granularity).toBe('monthly')
    expect(kpiApi.fetchKpiValues).toHaveBeenCalledWith(
      expect.objectContaining({ granularity: 'monthly', kpiCode: KPI_CODES.FEATURE_USAGE_RATE }),
    )
  })

  it('게터: kpiCode 별로 항목을 분리한다', async () => {
    vi.mocked(kpiApi.fetchKpiValues).mockResolvedValueOnce(
      response([
        item(KPI_CODES.FEATURE_USAGE_RATE, 10, '2026-06-01T00:00:00Z'),
        item(KPI_CODES.FILE_DOWNLOAD_COUNT, 20, '2026-06-01T00:00:00Z'),
        item(KPI_CODES.POLICY_APPLY_CONVERSION_RATE, 5, '2026-06-01T00:00:00Z'),
      ]),
    )

    const store = useKpiStore()
    await store.loadKpiValues()

    expect(store.featureUsageItems).toHaveLength(1)
    expect(store.fileDownloadItems).toHaveLength(1)
    expect(store.conversionItems).toHaveLength(1)
    expect(store.featureUsageItems[0].kpiCode).toBe(KPI_CODES.FEATURE_USAGE_RATE)
  })

  it('hasPreparingData: PREPARING 항목이 있으면 true', async () => {
    vi.mocked(kpiApi.fetchKpiValues).mockResolvedValueOnce(
      response([
        item(KPI_CODES.FEATURE_USAGE_RATE, 10, '2026-06-01T00:00:00Z'),
        item(KPI_CODES.POLICY_APPLY_CONVERSION_RATE, null, '2026-06-01T00:00:00Z', 'PREPARING'),
      ]),
    )

    const store = useKpiStore()
    await store.loadKpiValues()
    expect(store.hasPreparingData).toBe(true)
  })

  it('hasPreparingData: 모두 READY 면 false', async () => {
    vi.mocked(kpiApi.fetchKpiValues).mockResolvedValueOnce(
      response([item(KPI_CODES.FEATURE_USAGE_RATE, 10, '2026-06-01T00:00:00Z')]),
    )

    const store = useKpiStore()
    await store.loadKpiValues()
    expect(store.hasPreparingData).toBe(false)
  })

  it('loadKpiValues API 실패 시 error state 를 설정한다 (토스트 없음)', async () => {
    vi.mocked(kpiApi.fetchKpiValues).mockRejectedValueOnce(new Error('boom'))

    const store = useKpiStore()
    await store.loadKpiValues()

    expect(store.error).toBe('boom')
    expect(store.loading).toBe(false)
    expect(store.kpiValues).toEqual([])
  })

  it('exportToExcel: 동기 Blob 응답이면 즉시 다운로드한다', async () => {
    const blob = new Blob(['xls'], { type: 'application/vnd.ms-excel' })
    vi.mocked(kpiApi.exportKpi).mockResolvedValueOnce(blob)

    const store = useKpiStore()
    await store.exportToExcel()

    expect(kpiApi.saveBlob).toHaveBeenCalledWith(blob, expect.stringMatching(/^kpi-\d+\.xls$/))
    expect(store.exporting).toBe(false)
    expect(store.exportJobId).toBeNull()
  })

  it('exportToExcel: 비동기 202 응답이면 jobId 저장 후 폴링하고 COMPLETED 시 다운로드한다', async () => {
    vi.mocked(kpiApi.exportKpi).mockResolvedValueOnce({ jobId: 'job-1', status: 'PROCESSING' })
    vi.mocked(kpiApi.pollExportStatus).mockResolvedValueOnce({
      jobId: 'job-1',
      status: 'COMPLETED',
      downloadToken: 'tok-1',
    })

    const store = useKpiStore()
    await store.exportToExcel()

    expect(store.exportJobId).toBe('job-1')
    expect(kpiApi.pollExportStatus).toHaveBeenCalledWith('job-1')
    expect(kpiApi.downloadExport).toHaveBeenCalledWith('tok-1')
  })

  it('pollExportJob: FAILED 응답이면 error 를 설정한다', async () => {
    vi.mocked(kpiApi.pollExportStatus).mockResolvedValueOnce({
      jobId: 'job-2',
      status: 'FAILED',
      message: '집계 실패',
    })

    const store = useKpiStore()
    await store.pollExportJob('job-2')

    expect(store.error).toBe('집계 실패')
    expect(kpiApi.downloadExport).not.toHaveBeenCalled()
  })

  it('resetFilters 는 기본 필터를 복원한다', () => {
    const store = useKpiStore()
    store.filters.granularity = 'monthly'
    store.filters.kpiCode = KPI_CODES.FEATURE_USAGE_RATE
    store.resetFilters()
    expect(store.filters.granularity).toBe('daily')
    expect(store.filters.kpiCode).toBeUndefined()
  })
})
