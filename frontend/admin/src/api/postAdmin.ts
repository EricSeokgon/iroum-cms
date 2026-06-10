// 관리자 게시글 모더레이션 API 래퍼 — SPEC-CMS-POST-MODERATE-001
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] postAdminApi — PostManagementView 의 목록/상태변경/삭제 콜사이트에서 참조
// @MX:REASON: fan_in >= 3: 목록 로드, 상태 변경, 삭제, 필터 갱신에서 공통 호출

const BASE = '/admin/posts'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type PostStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'HIDDEN' | 'DELETED'

export interface PostAdminSummary {
  id: number
  bbsId: number
  bbsName: string
  title: string
  authorId: number
  authorName: string
  status: PostStatus
  createdAt: string
}

export interface PostAdminListParams {
  bbsId?: number
  status?: string
  keyword?: string
  page?: number
  size?: number
}

// ── API 함수 ─────────────────────────────────────────────────────────────────

/** GET /api/v1/admin/posts — 전체 게시글 목록 (HIDDEN 포함, 교차 게시판) */
export function listAdminPosts(
  params: PostAdminListParams,
): Promise<{ data: PageResponse<PostAdminSummary> }> {
  return apiClient.get(BASE, { params })
}

/** PATCH /api/v1/admin/posts/{id}/status — 상태 변경 */
export function changePostStatus(
  id: number,
  status: PostStatus,
): Promise<{ data: PostAdminSummary }> {
  return apiClient.patch(`${BASE}/${id}/status`, { status })
}

/** DELETE /api/v1/admin/posts/{id} — 강제 삭제 (소프트 삭제) */
export function deleteAdminPost(id: number): Promise<void> {
  return apiClient.delete(`${BASE}/${id}`)
}
