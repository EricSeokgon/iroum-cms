// 알림 템플릿 관리 API 래퍼 — SPEC-CMS-NOTI-EXT-001
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] notificationTemplateApi — NotificationTemplateListView, PolicyDispatchView, notificationTemplateStore에서 공통 참조
// @MX:REASON: fan_in >= 3: 목록/상세/생성/수정/삭제/미리보기 콜사이트가 뷰 2곳 + 스토어에서 공통 사용

// baseURL이 '/api/v1'이므로 여기서는 '/notification/...'만 사용
const BASE = '/notification/admin/template'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export interface NotificationTemplateCreateRequest {
  code: string
  name?: string
  channel?: string
  subject?: string
  bodyHtml?: string
  variables?: string
  language: string
  isActive?: boolean
  emailTemplateId?: number
}

export interface NotificationTemplateUpdateRequest {
  code?: string
  name?: string
  channel?: string
  subject?: string
  bodyHtml?: string
  variables?: string
  language?: string
  isActive?: boolean
  emailTemplateId?: number
}

export interface NotificationTemplateResponse {
  id: number
  code: string
  name?: string
  channel?: string
  subject?: string
  bodyHtml?: string
  variables?: string
  language: string
  isActive: boolean
  emailTemplateId?: number
  createdBy?: number
  updatedBy?: number
  createdAt: string
  updatedAt: string
}

export interface NotificationTemplatePreviewRequest {
  templateId: number
  sampleVariables?: Record<string, string>
}

export interface NotificationTemplatePreviewResult {
  subject?: string
  bodyHtml?: string
}

export interface NotificationTemplateListParams {
  page?: number
  size?: number
  isActive?: boolean
}

// ── 알림 템플릿 API ──────────────────────────────────────────────────────────
export function getTemplates(
  params: NotificationTemplateListParams,
): Promise<{ data: PagedResponse<NotificationTemplateResponse> }> {
  return apiClient.get(BASE, { params })
}

export function getTemplate(id: number): Promise<{ data: NotificationTemplateResponse }> {
  return apiClient.get(`${BASE}/${id}`)
}

export function createTemplate(
  data: NotificationTemplateCreateRequest,
): Promise<{ data: NotificationTemplateResponse }> {
  return apiClient.post(BASE, data)
}

export function updateTemplate(
  id: number,
  data: NotificationTemplateUpdateRequest,
): Promise<{ data: NotificationTemplateResponse }> {
  return apiClient.put(`${BASE}/${id}`, data)
}

export function deleteTemplate(id: number): Promise<void> {
  return apiClient.delete(`${BASE}/${id}`)
}

/** 발송 없이 변수 치환 렌더링 */
export function previewTemplate(
  id: number,
  sampleVariables?: Record<string, string>,
): Promise<{ data: NotificationTemplatePreviewResult }> {
  return apiClient.post(`${BASE}/${id}/preview`, { templateId: id, sampleVariables })
}
