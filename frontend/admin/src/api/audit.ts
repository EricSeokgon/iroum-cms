// 감사 로그 API — REQ-AUTH-016
// @MX:ANCHOR: [AUTO] auditApi — PermissionChangeHistoryView, UserDetailView에서 공통 참조
// @MX:REASON: fan_in >= 3: PermissionChangeHistoryView, UserDetailView, 테스트 mock에서 참조

import { apiClient } from '@iroum/shared/api/client'
import type { PageResponse, PermissionChangeEntry } from '@iroum/shared/types/api'

/** 권한 변경 이력 조회 파라미터 */
export interface PermissionChangeParams {
  page?: number
  size?: number
  sort?: string
  targetUserId?: number
  changeType?: string
  changedBy?: number
  from?: string
  to?: string
}

export const auditApi = {
  /**
   * 전체 권한 변경 이력 조회
   * GET /api/v1/audit/permission-changes
   * 권한: AUDIT:READ
   */
  permissionChanges(params: PermissionChangeParams) {
    return apiClient.get<PageResponse<PermissionChangeEntry>>(
      '/audit/permission-changes',
      { params },
    )
  },

  /**
   * 특정 사용자의 권한 변경 이력 조회
   * GET /api/v1/audit/permission-changes/users/{userId}
   * 권한: AUDIT:READ
   */
  permissionChangesByUser(userId: number, params: { page?: number; size?: number }) {
    return apiClient.get<PageResponse<PermissionChangeEntry>>(
      `/audit/permission-changes/users/${userId}`,
      { params },
    )
  },
}
