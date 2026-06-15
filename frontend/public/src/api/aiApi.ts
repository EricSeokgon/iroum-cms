// AI 스마트 태그 추천 API (public) — SPEC-CMS-AI-004
// 시민 사이트(비인증 허용, SecurityConfig 화이트리스트)에서 호출한다 (REQ-AI-TAG-007).
import { apiClient } from './client'

const TAG_BASE = '/ai/tag-recommend'

/** 태그 추천 요청 — content는 plain text */
export interface TagRecommendRequest {
  content: string
  existingTags: string[]
  contentType?: 'POST' | 'QNA'
}

/** 태그 추천 응답 — 최대 5개, 빈 배열 허용(그레이스풀 폴백) */
export interface TagRecommendResponse {
  recommendedTags: string[]
}

/** 태그 채택/거부 피드백 요청 */
export interface TagFeedbackRequest {
  content: string
  contentType?: 'POST' | 'QNA'
  eventType: 'ACCEPTED' | 'REJECTED'
  tagValue: string
}

export const aiApi = {
  /** POST /api/v1/ai/tag-recommend — ML 장애 시 빈 배열 200 */
  recommendTags(req: TagRecommendRequest): Promise<TagRecommendResponse> {
    return apiClient.post<TagRecommendResponse>(TAG_BASE, req).then((r) => r.data)
  },
  /** POST /api/v1/ai/tag-recommend/feedback — 채택/거부 이벤트 로깅 */
  tagFeedback(req: TagFeedbackRequest): Promise<void> {
    return apiClient.post<void>(`${TAG_BASE}/feedback`, req).then((r) => r.data)
  },
}
