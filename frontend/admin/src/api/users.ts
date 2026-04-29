// 사용자 API 래퍼 — SPEC-CMS-002 REQ-AUTH-006
// 모든 사용자 CRUD 엔드포인트를 캡슐화합니다

import { apiClient } from '@iroum/shared/api/client'
import type {
  UserSummary,
  UserDetail,
  UserCreateRequest,
  UserUpdateRequest,
  PageResponse,
} from '@iroum/shared/types/api'

// @MX:ANCHOR: [AUTO] usersApi — UserListView, UserDetailView, UserFormView에서 공통 참조
// @MX:REASON: fan_in >= 3: 목록/상세/폼 뷰 3곳에서 직접 호출하는 API 집합체

export interface UserListParams {
  page?: number
  size?: number
  sort?: string
  search?: string
  status?: string
}

export const usersApi = {
  /** 사용자 목록 조회 — GET /api/v1/users */
  list(params: UserListParams = {}) {
    return apiClient.get<PageResponse<UserSummary>>('/users', {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        sort: params.sort ?? 'createdAt,desc',
        search: params.search ?? '',
        status: params.status ?? '',
      },
    })
  },

  /** 사용자 상세 조회 — GET /api/v1/users/{id} */
  detail(id: number) {
    return apiClient.get<UserDetail>(`/users/${id}`)
  },

  /** 사용자 생성 — POST /api/v1/users */
  create(req: UserCreateRequest) {
    return apiClient.post<UserDetail>('/users', req)
  },

  /** 사용자 수정 — PUT /api/v1/users/{id} */
  update(id: number, req: UserUpdateRequest) {
    return apiClient.put<UserDetail>(`/users/${id}`, req)
  },

  /** 사용자 삭제 — DELETE /api/v1/users/{id} */
  delete(id: number) {
    return apiClient.delete<void>(`/users/${id}`)
  },

  /** 사용자 잠금 해제 — POST /api/v1/users/{id}/unlock */
  unlock(id: number) {
    return apiClient.post<void>(`/users/${id}/unlock`)
  },

  /** 강제 로그아웃 — POST /api/v1/users/{id}/force-logout */
  forceLogout(id: number) {
    return apiClient.post<void>(`/users/${id}/force-logout`)
  },
}
