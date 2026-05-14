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

export const statsApi = {
  widget(code: string): Promise<WidgetData> {
    return apiClient.get<WidgetData>(`/dashboard/widgets/${code}/data`).then((r) => r.data)
  },
  kpiValues(codes?: string[]): Promise<KpiValue[]> {
    return apiClient
      .get<KpiValue[]>('/kpi/values', { params: codes ? { codes: codes.join(',') } : undefined })
      .then((r) => r.data)
  },
}
