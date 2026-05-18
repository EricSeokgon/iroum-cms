// AI 정책 추천 모니터링 API 래퍼 — SPEC-CMS-AI-002
// 모든 응답은 ApiResponse 래퍼 없는 평문 ResponseEntity<T> (프로젝트 컨벤션)
import axios from 'axios'

export type PolicyMatchMetricsPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY'

export interface PolicyMatchMetricsQuery {
  period?: PolicyMatchMetricsPeriod
  from?: string
  to?: string
}

export interface PolicyMatchMetricsDto {
  period: string
  ctr: number
  conversionRate: number
  coverage: number
  totalViewed: number
  totalClicked: number
  totalApplied: number
}

const BASE = '/api/v1/admin/ai/policy-match'

export const policyMatchAdminApi = {
  getMetrics(
    params: PolicyMatchMetricsQuery,
  ): Promise<{ data: PolicyMatchMetricsDto }> {
    return axios.get(`${BASE}/metrics`, { params })
  },
}
