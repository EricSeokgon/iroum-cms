// FAQ API 래퍼 — SPEC-CMS-003
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] faqApi — FaqListView 및 FAQ 관련 뷰에서 공통 참조
// @MX:REASON: fan_in >= 3: 목록/카테고리/생성/수정/삭제/순서변경 등 다수 콜사이트

const BASE = '/faqs'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type FaqStatus = 'PUBLISHED' | 'HIDDEN'

export interface FaqSummary {
  id: number
  categoryCode: string
  question: string
  sortOrder: number
  viewCount: number
  status: string
  createdAt: string
}

export interface FaqDetail {
  id: number
  categoryCode: string
  question: string
  answerHtml: string
  answerText: string
  sortOrder: number
  viewCount: number
  status: string
  createdAt: string
  updatedAt: string
}

export interface FaqCategoryCount {
  categoryCode: string
  count: number
}

export interface FaqListParams {
  category?: string
  keyword?: string
  page?: number
  size?: number
}

export interface FaqCreateRequest {
  categoryCode: string
  question: string
  answerHtml: string
  sortOrder: number
}

export interface FaqUpdateRequest {
  categoryCode: string
  question: string
  answerHtml: string
  sortOrder: number
  status: FaqStatus
}

export interface FaqReorderItem {
  id: number
  sortOrder: number
}

export interface FaqReorderRequest {
  items: FaqReorderItem[]
}

// ── API 함수 ─────────────────────────────────────────────────────────────────
export function listFaqs(params: FaqListParams): Promise<{ data: PageResponse<FaqSummary> }> {
  return apiClient.get(BASE, { params })
}

export function getFaq(id: number): Promise<{ data: FaqDetail }> {
  return apiClient.get(`${BASE}/${id}`)
}

export function getCategories(): Promise<{ data: FaqCategoryCount[] }> {
  return apiClient.get(`${BASE}/categories`)
}

export function createFaq(req: FaqCreateRequest): Promise<{ data: FaqDetail }> {
  return apiClient.post(BASE, req)
}

export function updateFaq(id: number, req: FaqUpdateRequest): Promise<{ data: FaqDetail }> {
  return apiClient.put(`${BASE}/${id}`, req)
}

export function deleteFaq(id: number): Promise<void> {
  return apiClient.delete(`${BASE}/${id}`)
}

export function reorderFaqs(req: FaqReorderRequest): Promise<void> {
  return apiClient.put(`${BASE}/reorder`, req)
}
