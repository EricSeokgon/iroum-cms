// 관리자 댓글 모더레이션 API 래퍼 — SPEC-CMS-COMMENT-MODERATE-001
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] commentAdminApi — CommentManagementView 의 목록/상태변경/삭제 콜사이트에서 참조
// @MX:REASON: fan_in >= 3: 목록 로드, 상태 변경, 삭제, 필터 갱신에서 공통 호출

const BASE = '/admin/comments'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type CommentStatus = 'VISIBLE' | 'HIDDEN' | 'DELETED'

export interface CommentAdminSummary {
  id: number
  postId: number
  postTitle: string
  boardCode: string
  boardName: string
  authorUsername: string | null
  contentPreview: string
  status: CommentStatus
  createdAt: string
}

export interface CommentAdminListParams {
  boardId?: number
  status?: string
  keyword?: string
  page?: number
  size?: number
}

// ── API 함수 ─────────────────────────────────────────────────────────────────

/** GET /api/v1/admin/comments — 전체 댓글 목록 (필터 + 페이징) */
export function listAdminComments(
  params: CommentAdminListParams,
): Promise<{ data: PageResponse<CommentAdminSummary> }> {
  return apiClient.get(BASE, { params })
}

/** PATCH /api/v1/admin/comments/{id}/status — 상태 변경 (VISIBLE/HIDDEN) */
export function changeCommentStatus(
  id: number,
  status: 'VISIBLE' | 'HIDDEN',
): Promise<{ data: CommentAdminSummary }> {
  return apiClient.patch(`${BASE}/${id}/status`, { status })
}

/** DELETE /api/v1/admin/comments/{id} — 강제 삭제 (소프트 삭제) */
export function deleteAdminComment(id: number): Promise<void> {
  return apiClient.delete(`${BASE}/${id}`)
}
