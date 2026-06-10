// 발간자료 카테고리 관리자 API 래퍼 — SPEC-CMS-PUB-CAT-001
import { apiClient } from '@iroum/shared/api/client'

const BASE = '/admin/publication-categories'

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type CategoryStatus = 'ACTIVE' | 'INACTIVE'

export interface PublicationCategoryDto {
  id: number
  code: string
  name: string
  parentId: number | null
  depth: number
  sortOrder: number
  status: CategoryStatus
  children: PublicationCategoryDto[]
}

export interface PublicationCategoryCreateRequest {
  code: string
  name: string
  parentId?: number | null
  sortOrder?: number
}

export interface PublicationCategoryUpdateRequest {
  name: string
  sortOrder: number
  status: CategoryStatus
}

// ── API 함수 ─────────────────────────────────────────────────────────────────
export const publicationCategoryAdminApi = {
  /** REQ-PCA-004: 어드민 카테고리 트리 전체 조회 (INACTIVE 포함). */
  listAll: (): Promise<PublicationCategoryDto[]> =>
    apiClient.get<PublicationCategoryDto[]>(BASE).then(r => r.data),

  /** REQ-PCA-001: 카테고리 생성. */
  create: (request: PublicationCategoryCreateRequest): Promise<PublicationCategoryDto> =>
    apiClient.post<PublicationCategoryDto>(BASE, request).then(r => r.data),

  /** REQ-PCA-002: 카테고리 수정. */
  update: (id: number, request: PublicationCategoryUpdateRequest): Promise<PublicationCategoryDto> =>
    apiClient.put<PublicationCategoryDto>(`${BASE}/${id}`, request).then(r => r.data),

  /** REQ-PCA-003: 카테고리 삭제. */
  remove: (id: number): Promise<void> =>
    apiClient.delete(`${BASE}/${id}`).then(() => undefined),
}
