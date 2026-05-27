// SPEC-CMS-008 대시보드·KPI 데이터 — 공개 위젯만 사용
import { apiClient } from './client'

export interface WidgetData {
  code: string
  title: string
  type: 'CARD' | 'BAR' | 'LINE' | 'PIE' | 'TABLE'
  data: unknown
}

export interface KpiValue {
  code: string
  label: string
  value: number
  unit?: string
}

// 공개 통계 페이지에 표시할 위젯 코드 목록 (DB 기준)
const PUBLIC_WIDGET_CODES = [
  'WIDGET_TODAY_VISITORS',
  'WIDGET_TOTAL_VISITORS',
  'WIDGET_TOTAL_POSTS',
  'WIDGET_MONTHLY_CHART',
]

// 백엔드 widgetType → 프론트엔드 type 매핑
function mapType(backendType: string): WidgetData['type'] {
  switch (backendType) {
    case 'METRIC_CARD': return 'CARD'
    case 'BAR_CHART':   return 'BAR'
    case 'LINE_CHART':  return 'LINE'
    case 'PIE_CHART':   return 'PIE'
    default:            return 'CARD'
  }
}

interface WidgetInfo {
  id: number
  code: string
  name: string
  widgetType: string
}

interface BackendDataset {
  categories: string[]
  series: Array<{ name: string; data: number[] }>
}

interface BackendWidgetDataResponse {
  widget: { id: number; code: string; type: string }
  dataset: BackendDataset
}

function adaptWidgetData(info: WidgetInfo, raw: BackendWidgetDataResponse): WidgetData {
  const type = mapType(info.widgetType)
  const series0 = raw.dataset.series[0]
  let data: unknown

  if (type === 'CARD') {
    data = { value: series0?.data[0] ?? 0, label: series0?.name ?? info.name }
  } else if (type === 'BAR' || type === 'LINE') {
    data = { categories: raw.dataset.categories, values: series0?.data ?? [] }
  } else if (type === 'PIE') {
    data = { names: raw.dataset.categories, values: series0?.data ?? [] }
  }

  return { code: info.code, title: info.name, type, data }
}

export const statsApi = {
  // 공개 위젯 목록 조회: 목록에서 코드 필터링 → 데이터 병렬 조회 → 변환
  async publicWidgets(): Promise<WidgetData[]> {
    const all = await apiClient
      .get<WidgetInfo[]>('/dashboard/widgets', { params: { size: 50 } })
      .then((r) => r.data)

    const targets = all.filter((w) => PUBLIC_WIDGET_CODES.includes(w.code))

    const results = await Promise.allSettled(
      targets.map((info) =>
        apiClient
          .get<BackendWidgetDataResponse>(`/dashboard/widgets/${info.id}/data`)
          .then((r) => adaptWidgetData(info, r.data)),
      ),
    )

    return results
      .filter((r): r is PromiseFulfilledResult<WidgetData> => r.status === 'fulfilled')
      .map((r) => r.value)
  },

  // 단일 위젯 데이터 조회 (코드 → id 변환 후 조회)
  async widget(code: string): Promise<WidgetData | null> {
    const all = await apiClient
      .get<WidgetInfo[]>('/dashboard/widgets', { params: { size: 50 } })
      .then((r) => r.data)
    const info = all.find((w) => w.code === code)
    if (!info) return null
    const raw = await apiClient
      .get<BackendWidgetDataResponse>(`/dashboard/widgets/${info.id}/data`)
      .then((r) => r.data)
    return adaptWidgetData(info, raw)
  },

  kpiValues(codes?: string[]): Promise<KpiValue[]> {
    return apiClient
      .get<KpiValue[]>('/kpi/values', { params: codes ? { codes: codes.join(',') } : undefined })
      .then((r) => r.data)
  },
}
