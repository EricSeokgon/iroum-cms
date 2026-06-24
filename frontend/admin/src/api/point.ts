// 포인트 정책 및 이력 API 래퍼 — SPEC-CMS-POINTS-001
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] pointApi — PointPolicyAdminView, PointLedgerAdminView 에서 참조
// @MX:REASON: fan_in >= 3: 정책 조회/수정, 이력 조회에서 공통 호출

export interface PointPolicy {
  enabled: boolean
  postCreated: number
  commentCreated: number
  likeGiven: number
}

export interface PointLedgerEntry {
  id: number
  userId: number
  delta: number
  reason: string
  refType: string
  refId: number
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface PointPolicyUpdateRequest {
  enabled?: boolean
  postCreated?: number
  commentCreated?: number
  likeGiven?: number
}

export interface PointLedgerSearchParams {
  userId?: number
  page?: number
  size?: number
}

export const pointApi = {
  getPolicy(): Promise<PointPolicy> {
    return apiClient.get('/api/v1/admin/points/policy').then(r => r.data)
  },

  updatePolicy(payload: PointPolicyUpdateRequest): Promise<PointPolicy> {
    return apiClient.put('/api/v1/admin/points/policy', payload).then(r => r.data)
  },

  getLedger(params: PointLedgerSearchParams = {}): Promise<PageResponse<PointLedgerEntry>> {
    return apiClient.get('/api/v1/admin/points/ledger', { params }).then(r => r.data)
  },
}
