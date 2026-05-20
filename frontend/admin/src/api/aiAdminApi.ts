// AI 모델 운영 API 래퍼 — SPEC-CMS-AI-001 Step 3
// 모든 응답은 ApiResponse 래퍼 없는 평문 ResponseEntity<T> (프로젝트 컨벤션)
import { apiClient } from '@iroum/shared/api/client'
import type {
  AiMetricDto,
  AiDriftAlertDto,
  RetrainStatusDto,
  RetrainRequest,
  AiMetricQuery,
  ModelHealthDto,
  SimulationStatsDto,
} from '@/types/ai'

// @MX:ANCHOR: [AUTO] aiAdminApi — ModelDashboard, DriftAlerts, RetrainQueue 뷰 + 테스트에서 참조
// @MX:REASON: fan_in >= 3: SPEC-CMS-AI-001 3개 운영 뷰 + 각 spec 테스트에서 공통 호출

const BASE = '/admin/ai'

export const aiAdminApi = {
  getMetrics(params: AiMetricQuery): Promise<{ data: AiMetricDto[] }> {
    return apiClient.get(`${BASE}/metrics`, { params })
  },
  getDriftAlerts(): Promise<{ data: AiDriftAlertDto[] }> {
    return apiClient.get(`${BASE}/drift-alerts`)
  },
  getRetrainQueue(): Promise<{ data: RetrainStatusDto[] }> {
    return apiClient.get(`${BASE}/retrain-queue`)
  },
  requestRetrain(body: RetrainRequest): Promise<{ data: RetrainStatusDto }> {
    return apiClient.post(`${BASE}/retrain-queue`, body)
  },
  updateRetrainStatus(
    id: number,
    body: { status: string },
  ): Promise<{ data: RetrainStatusDto }> {
    return apiClient.put(`${BASE}/retrain-queue/${id}/status`, body)
  },
  getModelHealth(): Promise<{ data: ModelHealthDto }> {
    return apiClient.get(`${BASE}/model-health`)
  },
  getSimulationStats(): Promise<{ data: SimulationStatsDto }> {
    return apiClient.get(`${BASE}/simulation-stats`)
  },
  triggerAggregate(): Promise<{ data: unknown }> {
    return apiClient.post(`${BASE}/metrics/aggregate`)
  },
}
