// 관리자 리뷰 모더레이션 API 래퍼 — SPEC-CMS-REVIEW-001
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] reviewAdminApi — ReviewManagementView 의 목록/숨김/삭제 콜사이트에서 참조
// @MX:REASON: fan_in >= 3: 목록 로드, 숨김 처리, 삭제, 필터 갱신에서 공통 호출

const BASE = '/admin/reviews'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type ReviewStatus = 'VISIBLE' | 'HIDDEN' | 'DELETED'

export interface AdminReviewResponse {
  id: number
  postId: number
  authorId: number | null
  authorName: string | null
  rating: number
  content: string | null
  status: ReviewStatus
  ipAddress: string | null
  createdAt: string
}

export interface AdminReviewListParams {
  postId?: number
  status?: string
  page?: number
  size?: number
}

// ── API 함수 ─────────────────────────────────────────────────────────────────

/** GET /api/v1/admin/reviews — 전체 리뷰 목록 (필터 + 페이징) */
export function listAdminReviews(
  params: AdminReviewListParams,
): Promise<{ data: PageResponse<AdminReviewResponse> }> {
  return apiClient.get(BASE, { params })
}

/** PATCH /api/v1/admin/reviews/{id}/hide — 리뷰 숨김(HIDDEN) */
export function hideAdminReview(id: number): Promise<void> {
  return apiClient.patch(`${BASE}/${id}/hide`)
}

/** DELETE /api/v1/admin/reviews/{id} — 리뷰 삭제(DELETED, idempotent) */
export function deleteAdminReview(id: number): Promise<void> {
  return apiClient.delete(`${BASE}/${id}`)
}
