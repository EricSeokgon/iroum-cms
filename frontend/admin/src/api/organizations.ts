// 조직 API 래퍼 — REQ-AUTH-014
// 조직 트리 CRUD 및 사용자 부서 배정 엔드포인트를 캡슐화합니다

import { apiClient } from '@iroum/shared/api/client'
import type {
  OrganizationTreeNode,
  OrganizationSummary,
  OrganizationDetail,
  OrganizationCreateRequest,
  OrganizationUpdateRequest,
  OrganizationHistoryEntry,
} from '@iroum/shared/types/api'

// @MX:ANCHOR: [AUTO] organizationsApi — OrganizationTreeView, OrganizationFormView, UserFormView에서 공통 참조
// @MX:REASON: fan_in >= 3: 트리뷰, 폼뷰, 사용자폼 3곳에서 직접 호출하는 API 집합체

export const organizationsApi = {
  /** 조직 트리 조회 — GET /api/v1/organizations/tree */
  tree() {
    return apiClient.get<OrganizationTreeNode[]>('/organizations/tree')
  },

  /** 조직 목록 조회 — GET /api/v1/organizations */
  list(status?: string) {
    return apiClient.get<OrganizationSummary[]>('/organizations', {
      params: status ? { status } : undefined,
    })
  },

  /** 조직 상세 조회 — GET /api/v1/organizations/{id} */
  detail(id: number) {
    return apiClient.get<OrganizationDetail>(`/organizations/${id}`)
  },

  /** 조직 생성 — POST /api/v1/organizations */
  create(req: OrganizationCreateRequest) {
    return apiClient.post<OrganizationDetail>('/organizations', req)
  },

  /** 조직 수정 — PUT /api/v1/organizations/{id} */
  update(id: number, req: OrganizationUpdateRequest) {
    return apiClient.put<OrganizationDetail>(`/organizations/${id}`, req)
  },

  /** 조직 삭제 — DELETE /api/v1/organizations/{id} */
  delete(id: number) {
    return apiClient.delete<void>(`/organizations/${id}`)
  },

  /** 조직 변경 이력 조회 — GET /api/v1/organizations/{id}/history */
  history(id: number) {
    return apiClient.get<OrganizationHistoryEntry[]>(`/organizations/${id}/history`)
  },

  /** 사용자 부서 배정 — POST /api/v1/users/{userId}/organization */
  assignUser(userId: number, organizationId: number | null) {
    return apiClient.post<void>(`/users/${userId}/organization`, { organizationId })
  },
}
