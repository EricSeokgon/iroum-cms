// 참여 포인트 관리 API 래퍼 — SPEC-CMS-POINTS-001
import { apiClient } from '@iroum/shared/api/client'

// @MX:NOTE: [AUTO] pointApi — PointPolicyAdminView/PointLedgerAdminView/UserPointHistoryView 공통 참조
//   baseURL '/api/v1' 이 client에 설정되어 있으므로 경로는 /admin/points/* · /users/me/points/* 사용.

const POLICY_BASE = '/admin/points/policy'
const LEDGER_BASE = '/admin/points/ledger'
const ME_BASE = '/users/me/points'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type PointEventType = 'POST_CREATED' | 'COMMENT_CREATED' | 'LIKE_GIVEN'

export interface PointPolicy {
  enabled: boolean
  postPoints: number
  commentPoints: number
  likePoints: number
}

export type PointPolicyUpdate = PointPolicy

export interface PointLedgerEntry {
  id: number
  userId: number
  eventType: PointEventType
  referenceId: number | null
  points: number
  createdAt: string
}

export interface PointSummary {
  userId: number
  totalPoints: number
  updatedAt: string | null
}

export interface PointLedgerParams {
  userId?: number
  eventType?: PointEventType
  from?: string
  to?: string
  page?: number
  size?: number
}

// ── 정책 API (관리자) ─────────────────────────────────────────────────────────
export function getPointPolicy(): Promise<{ data: PointPolicy }> {
  return apiClient.get(POLICY_BASE)
}

export function updatePointPolicy(req: PointPolicyUpdate): Promise<{ data: PointPolicy }> {
  return apiClient.put(POLICY_BASE, req)
}

// ── 내역 API (관리자) ─────────────────────────────────────────────────────────
export function getPointLedger(
  params: PointLedgerParams,
): Promise<{ data: PagedResponse<PointLedgerEntry> }> {
  return apiClient.get(LEDGER_BASE, { params })
}

// ── 본인 내역 API (사용자) ──────────────────────────────────────────────────────
export function getMyPointSummary(): Promise<{ data: PointSummary }> {
  return apiClient.get(`${ME_BASE}/summary`)
}

export function getMyPointHistory(
  params: { page?: number; size?: number },
): Promise<{ data: PagedResponse<PointLedgerEntry> }> {
  return apiClient.get(`${ME_BASE}/history`, { params })
}
