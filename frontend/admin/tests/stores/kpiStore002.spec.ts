// SPEC-CMS-KPI-002 — 운영 활동 지표 KPI 4종(코드 5개) 스토어/상수 확장 단위 테스트
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
  dimension: Record<string, string>,
  aggregatedAt = '2026-06-12T00:00:00Z',
): KpiValueItem {
  return {
    kpiCode,
    kpiName: kpiCode,
    dimensionJson: JSON.stringify(dimension),
    value,
    aggregatedAt,
    dataState: 'READY',
  }
}

function response(items: KpiValueItem[]): KpiQueryResponse {
  return { items, hasMore: false, totalCount: items.length, filters: {} }
}

describe('SPEC-CMS-KPI-002 운영 활동 지표 KPI 확장', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('REQ-KPI2-007-5: KPI_CODES 에 신규 4종(코드 5개)이 추가되어 있다', () => {
    expect(KPI_CODES.DAU).toBe('DAU')
    expect(KPI_CODES.MAU).toBe('MAU')
    expect(KPI_CODES.CONTENT_VIEW).toBe('CONTENT_VIEW')
    expect(KPI_CODES.AVG_SESSION_DURATION).toBe('AVG_SESSION_DURATION')
    expect(KPI_CODES.API_ERROR_RATE).toBe('API_ERROR_RATE')
  })

  it('REQ-KPI2-007-5: 기존 3종 getter 가 변경되지 않는다(회귀)', async () => {
    vi.mocked(kpiApi.fetchKpiValues).mockResolvedValueOnce(
      response([
        item(KPI_CODES.FEATURE_USAGE_RATE, 42, { date: '2026-06-12' }),
        item(KPI_CODES.FILE_DOWNLOAD_COUNT, 7, { date: '2026-06-12' }),
        item(KPI_CODES.POLICY_APPLY_CONVERSION_RATE, 5, { date: '2026-06-12' }),
      ]),
    )
    const store = useKpiStore()
    await store.loadKpiValues()
    expect(store.featureUsageItems).toHaveLength(1)
    expect(store.fileDownloadItems).toHaveLength(1)
    expect(store.conversionItems).toHaveLength(1)
  })

  it('신규 getter 가 kpiCode 별로 항목을 분리한다', async () => {
    vi.mocked(kpiApi.fetchKpiValues).mockResolvedValueOnce(
      response([
        item(KPI_CODES.DAU, 120, { date: '2026-06-12' }),
        item(KPI_CODES.MAU, 3400, { month: '2026-06' }),
        item(KPI_CODES.CONTENT_VIEW, 50, { date: '2026-06-12', contentType: 'notice' }),
        item(KPI_CODES.CONTENT_VIEW, 30, { date: '2026-06-12', contentType: 'post' }),
        item(KPI_CODES.AVG_SESSION_DURATION, 900, { date: '2026-06-12' }),
        item(KPI_CODES.API_ERROR_RATE, 1.5, { date: '2026-06-12' }),
      ]),
    )

    const store = useKpiStore()
    await store.loadKpiValues()

    expect(store.dauItems).toHaveLength(1)
    expect(store.dauItems[0].value).toBe(120)
    expect(store.mauItems).toHaveLength(1)
    expect(store.mauItems[0].value).toBe(3400)
    expect(store.contentViewItems).toHaveLength(2)
    expect(store.sessionDurationItems).toHaveLength(1)
    expect(store.errorRateItems).toHaveLength(1)
    expect(store.errorRateItems[0].value).toBe(1.5)
  })

  it('contentViewItems 는 contentType 별로 모두 포함한다(미분류 없음)', async () => {
    vi.mocked(kpiApi.fetchKpiValues).mockResolvedValueOnce(
      response([
        item(KPI_CODES.CONTENT_VIEW, 50, { date: '2026-06-12', contentType: 'notice' }),
        item(KPI_CODES.CONTENT_VIEW, 30, { date: '2026-06-12', contentType: 'post' }),
        item(KPI_CODES.CONTENT_VIEW, 10, { date: '2026-06-12', contentType: 'publication' }),
      ]),
    )
    const store = useKpiStore()
    await store.loadKpiValues()
    const types = store.contentViewItems.map(
      (i) => (JSON.parse(i.dimensionJson) as { contentType: string }).contentType,
    )
    expect(types).toEqual(expect.arrayContaining(['notice', 'post', 'publication']))
  })
})
