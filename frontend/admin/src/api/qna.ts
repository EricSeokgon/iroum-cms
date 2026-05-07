// Q&A API 래퍼 — SPEC-CMS-003
import axios from 'axios'

// @MX:ANCHOR: [AUTO] qnaApi — QnaListView, QnaDetailView 등 Q&A 뷰에서 공통 참조
// @MX:REASON: fan_in >= 3: 목록/상세/등록/답변/종결/삭제 콜사이트 다수

const BASE = '/api/v1/qnas'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type QnaStatus = 'PENDING' | 'ANSWERED' | 'CLOSED' | 'HIDDEN'

export interface QnaSummary {
  id: number
  title: string
  questionerId: number
  status: string
  isPrivate: boolean
  createdAt: string
}

export interface QnaDetail {
  id: number
  title: string
  questionHtml: string
  questionText: string
  questionerId: number
  answerHtml: string
  answerText: string
  answererId: number
  answeredAt: string
  isPrivate: boolean
  status: string
  createdAt: string
  updatedAt: string
}

export interface QnaListParams {
  status?: string
  isPrivate?: boolean
  keyword?: string
  page?: number
  size?: number
}

export interface QnaCreateRequest {
  title: string
  questionHtml: string
  isPrivate: boolean
}

export interface QnaAnswerRequest {
  answerHtml: string
}

// ── API 함수 ─────────────────────────────────────────────────────────────────
export function listQnas(params: QnaListParams): Promise<{ data: PageResponse<QnaSummary> }> {
  return axios.get(BASE, { params })
}

export function getQna(id: number): Promise<{ data: QnaDetail }> {
  return axios.get(`${BASE}/${id}`)
}

export function createQna(req: QnaCreateRequest): Promise<{ data: QnaDetail }> {
  return axios.post(BASE, req)
}

export function answerQna(id: number, req: QnaAnswerRequest): Promise<{ data: QnaDetail }> {
  return axios.post(`${BASE}/${id}/answer`, req)
}

export function closeQna(id: number): Promise<{ data: QnaDetail }> {
  return axios.post(`${BASE}/${id}/close`)
}

export function deleteQna(id: number): Promise<void> {
  return axios.delete(`${BASE}/${id}`)
}
