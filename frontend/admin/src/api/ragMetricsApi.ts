// RAG 질의응답 품질 모니터링 API 래퍼 — SPEC-CMS-AI-003
// 모든 응답은 ApiResponse 래퍼 없는 평문 ResponseEntity<T> (프로젝트 컨벤션)
import axios from 'axios'

export interface RagMetricsQuery {
  from?: string
  to?: string
}

export interface RagTimeSeriesPoint {
  date: string
  queryCount: number
  satisfactionRate: number
}

export interface RagMetricsDto {
  satisfactionRate: number
  cacheHitRate: number
  avgLatencyMs: number
  degradedRate: number
  totalQueries: number
  timeSeries: RagTimeSeriesPoint[]
}

const BASE = '/api/v1/admin/ai/rag'

export const ragMetricsApi = {
  getMetrics(params: RagMetricsQuery): Promise<{ data: RagMetricsDto }> {
    return axios.get(`${BASE}/metrics`, { params })
  },
}
