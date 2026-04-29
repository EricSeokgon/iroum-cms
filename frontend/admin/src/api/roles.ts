// 역할·권한 API 래퍼 — REQ-AUTH-013
// GET/POST/PUT/DELETE /api/v1/roles, GET /api/v1/permissions 엔드포인트 캡슐화

import { apiClient } from '@iroum/shared/api/client'
import type {
  RoleSummary,
  RoleDetail,
  RoleCreateRequest,
  RoleUpdateRequest,
  PermissionSummary,
} from '@iroum/shared/types/api'

// @MX:ANCHOR: [AUTO] rolesApi — RoleMatrixView, RoleFormView, UserFormView에서 공통 참조
// @MX:REASON: fan_in >= 3: 매트릭스 뷰·폼 뷰·사용자 폼 뷰 3곳에서 직접 호출하는 API 집합체

export const rolesApi = {
  /** 역할 목록 조회 — GET /api/v1/roles */
  list() {
    return apiClient.get<RoleSummary[]>('/roles')
  },

  /** 역할 상세 조회 — GET /api/v1/roles/{code} */
  detail(code: string) {
    return apiClient.get<RoleDetail>(`/roles/${code}`)
  },

  /** 역할 생성 — POST /api/v1/roles */
  create(req: RoleCreateRequest) {
    return apiClient.post<RoleDetail>('/roles', req)
  },

  /** 역할 수정 — PUT /api/v1/roles/{code} */
  update(code: string, req: RoleUpdateRequest) {
    return apiClient.put<RoleDetail>(`/roles/${code}`, req)
  },

  /** 역할 삭제 — DELETE /api/v1/roles/{code} */
  delete(code: string) {
    return apiClient.delete<void>(`/roles/${code}`)
  },

  /** 역할 권한 일괄 교체 — PUT /api/v1/roles/{code}/permissions */
  updatePermissions(code: string, permissionCodes: string[]) {
    return apiClient.put<void>(`/roles/${code}/permissions`, { permissionCodes })
  },
}

// @MX:ANCHOR: [AUTO] permissionsApi — RoleMatrixView, RoleFormView에서 권한 카탈로그 로딩
// @MX:REASON: fan_in >= 3: 매트릭스 뷰·폼 뷰·그리드 컴포넌트에서 공통 사용

export const permissionsApi = {
  /** 권한 카탈로그 전체 조회 — GET /api/v1/permissions */
  list() {
    return apiClient.get<PermissionSummary[]>('/permissions')
  },
}
