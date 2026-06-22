// SPEC-CMS-REVIEW-001 게시물 별점 리뷰 — 공개 조회 + 인증 작성
// 실제 백엔드 경로: GET/POST /api/v1/posts/{postId}/reviews
import { apiClient } from './client'

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type ReviewStatus = 'VISIBLE' | 'HIDDEN' | 'DELETED'

/** GET /posts/{postId}/reviews 응답 단건 (공개 조회 — VISIBLE 만 반환) */
export interface ReviewResponse {
  id: number
  postId: number
  authorId: number | null
  rating: number
  content: string | null
  createdAt: string
  status: ReviewStatus
}

/** POST /posts/{postId}/reviews 요청 본문 (rating 1~5, content 선택) */
export interface ReviewCreateRequest {
  rating: number
  content: string | null
}

export const reviewApi = {
  /** 게시물의 공개(VISIBLE) 리뷰 목록 조회 — 비인증 허용 (REQ-REV-005) */
  list(postId: number): Promise<ReviewResponse[]> {
    return apiClient.get<ReviewResponse[]>(`/posts/${postId}/reviews`).then((r) => r.data)
  },
  /** 리뷰 작성 — 인증 필요 (REQ-REV-001, REQ-REV-007) */
  create(postId: number, req: ReviewCreateRequest): Promise<ReviewResponse> {
    return apiClient.post<ReviewResponse>(`/posts/${postId}/reviews`, req).then((r) => r.data)
  },
}
