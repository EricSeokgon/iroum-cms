// 가입 승인 관리 API 래퍼 — SPEC-CMS-USER-APPROVAL-001
// 대기열 조회/단건 승인·거절/일괄 승인·거절 엔드포인트 캡슐화.

import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] userApprovalsApi — ApprovalQueueView 등에서 공통 참조
// @MX:REASON: 목록/단건/일괄 6개 엔드포인트를 단일 진입점으로 묶음 (fan_in 증가 대비)

const BASE = '/users/approvals'

/** 페이지 응답 공통 형태 (백엔드 PageResponse). */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** 승인 대기 사용자. */
export interface PendingUser {
  userId: number
  username: string
  email: string
  name: string
  createdAt: string
  organizationId: number | null
  /** 이메일 인증 완료 시각 (null=미인증). SPEC-CMS-USER-APPROVAL-002 REQ-UA2-008 */
  emailVerifiedAt: string | null
}

/** 일괄 처리 결과. */
export interface BulkOperationResult {
  successCount: number
  failureCount: number
  failures: Array<{ userId: number; error: string }>
}

export interface ApprovalListParams {
  page?: number
  size?: number
  keyword?: string
}

export const userApprovalsApi = {
  /** 대기열 목록 조회 — GET /api/v1/users/approvals */
  list(params: ApprovalListParams = {}) {
    return apiClient.get<PageResponse<PendingUser>>(BASE, {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        ...(params.keyword ? { keyword: params.keyword } : {}),
      },
    })
  },

  /** 대기 사용자 상세 — GET /api/v1/users/approvals/{id} */
  detail(userId: number) {
    return apiClient.get<PendingUser>(`${BASE}/${userId}`)
  },

  /** 단건 승인 — POST /api/v1/users/approvals/{id}/approve */
  approve(userId: number) {
    return apiClient.post<void>(`${BASE}/${userId}/approve`)
  },

  /** 단건 거절 — POST /api/v1/users/approvals/{id}/reject */
  reject(userId: number, reason: string) {
    return apiClient.post<void>(`${BASE}/${userId}/reject`, { reason })
  },

  /** 일괄 승인 — POST /api/v1/users/approvals/bulk-approve */
  bulkApprove(userIds: number[]) {
    return apiClient.post<BulkOperationResult>(`${BASE}/bulk-approve`, { userIds })
  },

  /** 일괄 거절 — POST /api/v1/users/approvals/bulk-reject */
  bulkReject(userIds: number[], reason: string) {
    return apiClient.post<BulkOperationResult>(`${BASE}/bulk-reject`, { userIds, reason })
  },
}
