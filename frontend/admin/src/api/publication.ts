// 발간자료 API 래퍼 — SPEC-CMS-003
import axios from 'axios'

// @MX:ANCHOR: [AUTO] publicationApi — PublicationListView/DetailView 등에서 공통 참조
// @MX:REASON: fan_in >= 3: 목록/상세/카테고리/생성/수정/삭제/ZIP 다운로드 다수 콜사이트

const BASE = '/api/v1/publications'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type DocumentType = 'REPORT' | 'BROCHURE' | 'RESEARCH' | 'GUIDE' | 'OTHER'

export interface PublicationCategoryDto {
  id: number
  code: string
  name: string
  parentId: number | null
  depth: number
  sortOrder: number
  status: string
  children: PublicationCategoryDto[]
}

export interface PublicationSummary {
  postId: number
  title: string
  publicationYear: number
  publicationMonth: number | null
  documentType: DocumentType
  categoryName: string | null
  fileCount: number
  isbn: string | null
  publisher: string | null
  viewCount: number
  publishedAt: string
}

export interface PublicationDetail extends PublicationSummary {
  contentHtml: string
  categoryId: number | null
}

export interface PublicationCreateRequest {
  title: string
  contentHtml?: string
  contentText?: string
  publicationYear: number
  publicationMonth?: number | null
  documentType: DocumentType
  publicationCategoryId?: number | null
  isbn?: string
  publisher?: string
  metadata?: string
}

export type PublicationUpdateRequest = Partial<PublicationCreateRequest>

export interface ZipDownloadRequest {
  assetUuids: string[]
}

export interface ZipDownloadResponse {
  downloadId: string
  mode: 'SYNC' | 'ASYNC'
  message: string
  sizeBytes: number | null
}

export interface PublicationListParams {
  year?: number
  month?: number
  documentType?: DocumentType
  categoryId?: number
  keyword?: string
  page?: number
  size?: number
}

// ── API 함수 ─────────────────────────────────────────────────────────────────
export function listPublications(
  params: PublicationListParams,
): Promise<{ data: PageResponse<PublicationSummary> }> {
  return axios.get(BASE, { params })
}

export function getPublication(id: number): Promise<{ data: PublicationDetail }> {
  return axios.get(`${BASE}/${id}`)
}

export function getCategories(): Promise<{ data: PublicationCategoryDto[] }> {
  return axios.get(`${BASE}/categories`)
}

export function createPublication(
  req: PublicationCreateRequest,
): Promise<{ data: PublicationDetail }> {
  return axios.post(BASE, req)
}

export function updatePublication(
  id: number,
  req: PublicationUpdateRequest,
): Promise<{ data: PublicationDetail }> {
  return axios.put(`${BASE}/${id}`, req)
}

export function deletePublication(id: number): Promise<void> {
  return axios.delete(`${BASE}/${id}`)
}

export function requestZipDownload(
  postId: number,
  req: ZipDownloadRequest,
): Promise<{ data: ZipDownloadResponse }> {
  return axios.post(`${BASE}/${postId}/download-zip`, req)
}
