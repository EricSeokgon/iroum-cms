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

// @MX:ANCHOR: [AUTO] aiAdminApi — ModelDashboard, DriftAlerts, RetrainQueue 뷰 + 태그 추천 컴포저블에서 참조
// @MX:REASON: fan_in >= 3: SPEC-CMS-AI-001 3개 운영 뷰 + SPEC-CMS-AI-004 태그 추천 + 각 spec 테스트에서 공통 호출

const BASE = '/admin/ai'

// ── SPEC-CMS-AI-004 스마트 태그 추천 ──────────────────────────────────────────
// 추천/피드백 엔드포인트는 /ai/tag-recommend (관리자·시민 공용 공개 경로)
const TAG_BASE = '/ai/tag-recommend'

/** 태그 추천 요청 — content는 plain text, existingTags는 중복 회피 컨텍스트 */
export interface TagRecommendRequest {
  content: string
  existingTags: string[]
  contentType?: 'POST' | 'QNA'
}

/** 태그 추천 응답 — 최대 5개, 빈 배열 허용(ML 장애·짧은 본문 시 그레이스풀 폴백) */
export interface TagRecommendResponse {
  recommendedTags: string[]
}

/** 태그 채택/거부 피드백 요청 — content_hash 산출용 content 포함(평문 미저장) */
export interface TagFeedbackRequest {
  content: string
  contentType?: 'POST' | 'QNA'
  eventType: 'ACCEPTED' | 'REJECTED'
  tagValue: string
}

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

  // ── 스마트 태그 추천 (SPEC-CMS-AI-004) ──────────────────────────────────────
  /** POST /api/v1/ai/tag-recommend — 본문 기반 태그 후보 조회 (ML 장애 시 빈 배열 200) */
  recommendTags(body: TagRecommendRequest): Promise<{ data: TagRecommendResponse }> {
    return apiClient.post(TAG_BASE, body)
  },
  /** POST /api/v1/ai/tag-recommend/feedback — 태그 채택/거부 이벤트 로깅 */
  tagFeedback(body: TagFeedbackRequest): Promise<{ data: void }> {
    return apiClient.post(`${TAG_BASE}/feedback`, body)
  },
}
