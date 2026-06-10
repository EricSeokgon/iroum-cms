// 관리자 Q&A 모더레이션 API 래퍼 — SPEC-CMS-QNA-MODERATE-001
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] qnaAdminApi — QnaManagementView 의 목록/상태변경/삭제 콜사이트에서 참조
// @MX:REASON: fan_in >= 3: 목록 로드, 상태 변경, 삭제, 필터 갱신에서 공통 호출

const BASE = '/admin/qnas'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type QnaStatus = 'PENDING' | 'ANSWERED' | 'CLOSED' | 'HIDDEN'

export interface QnaAdminSummary {
  id: number
  title: string
  questionerId: number
  status: QnaStatus
  isPrivate: boolean
  createdAt: string
}

export interface QnaAdminListParams {
  status?: string
  keyword?: string
  page?: number
  size?: number
}

// ── API 함수 ─────────────────────────────────────────────────────────────────

/** GET /api/v1/admin/qnas — 전체 Q&A 목록 (필터 + 페이징) */
export function listAdminQnas(
  params: QnaAdminListParams,
): Promise<{ data: PageResponse<QnaAdminSummary> }> {
  return apiClient.get(BASE, { params })
}

/** PATCH /api/v1/admin/qnas/{id}/status — 상태 변경 */
export function changeQnaStatus(
  id: number,
  status: QnaStatus,
): Promise<{ data: QnaAdminSummary }> {
  return apiClient.patch(`${BASE}/${id}/status`, { status })
}

/** DELETE /api/v1/admin/qnas/{id} — 강제 삭제 (소프트 삭제) */
export function deleteAdminQna(id: number): Promise<void> {
  return apiClient.delete(`${BASE}/${id}`)
}
