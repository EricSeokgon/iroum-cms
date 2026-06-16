// 이메일 템플릿 관리 API 래퍼 — SPEC-CMS-EMAIL-TEMPLATE-001
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] emailTemplateApi — EmailTemplateListView, SmtpConfigView 등 다수 뷰에서 공통 참조
// @MX:REASON: fan_in >= 3: 목록/상세/생성/수정/삭제/미리보기/테스트발송/발송이력/SMTP 콜사이트 다수

const BASE = '/admin/email-templates'
const SMTP_BASE = '/admin/smtp-config'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type TemplateType =
  | 'OTP'
  | 'QNA_ANSWER'
  | 'PASSWORD_RESET'
  | 'ADMIN_NOTIFICATION'
  | 'CUSTOM'

/** 템플릿 변수 정의 */
export interface TemplateVariable {
  name: string
  description: string
  required: boolean
}

export interface EmailTemplateSummary {
  id: number
  code: string
  name: string
  templateType: TemplateType
  language: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface EmailTemplateDetail extends EmailTemplateSummary {
  subject: string
  bodyHtml: string
  bodyText?: string
  variables?: TemplateVariable[]
}

export interface EmailTemplateListParams {
  page?: number
  size?: number
  templateType?: TemplateType
  language?: string
  isActive?: boolean
  keyword?: string
}

export interface CreateRequest {
  code: string
  name: string
  templateType: TemplateType
  language: string
  subject: string
  bodyHtml: string
  bodyText?: string
  variables?: TemplateVariable[]
  isActive: boolean
}

export type UpdateRequest = CreateRequest

/** 미리보기/테스트발송 렌더 결과 */
export interface PreviewResult {
  subject: string
  bodyHtml: string
  bodyText?: string
}

export interface SendLogEntry {
  id: number
  templateCode: string
  status: 'SUCCESS' | 'FAILED'
  errorMessage?: string
  sentAt: string
}

export interface SmtpConfig {
  host: string
  port: number
  username: string
  password?: string // GET 시 마스킹됨
  auth: boolean
  starttls: boolean
}

// ── 이메일 템플릿 API ─────────────────────────────────────────────────────────
export function listEmailTemplates(
  params: EmailTemplateListParams,
): Promise<{ data: PagedResponse<EmailTemplateSummary> }> {
  return apiClient.get(BASE, { params })
}

export function getEmailTemplate(id: number): Promise<{ data: EmailTemplateDetail }> {
  return apiClient.get(`${BASE}/${id}`)
}

export function createEmailTemplate(
  req: CreateRequest,
): Promise<{ data: EmailTemplateDetail }> {
  return apiClient.post(BASE, req)
}

export function updateEmailTemplate(
  id: number,
  req: UpdateRequest,
): Promise<{ data: EmailTemplateDetail }> {
  return apiClient.put(`${BASE}/${id}`, req)
}

export function deleteEmailTemplate(id: number): Promise<void> {
  return apiClient.delete(`${BASE}/${id}`)
}

/** 발송 없이 변수 치환 렌더링 */
export function previewEmailTemplate(
  id: number,
  variables: Record<string, unknown>,
): Promise<{ data: PreviewResult }> {
  return apiClient.post(`${BASE}/${id}/preview`, { variables })
}

/** 로그인한 관리자 이메일로 테스트 발송 */
export function testSendEmailTemplate(
  id: number,
  variables: Record<string, unknown>,
): Promise<void> {
  return apiClient.post(`${BASE}/${id}/test-send`, { variables })
}

/** 발송 이력 조회 */
export function getSendLogs(
  id: number,
  params: { page?: number; size?: number },
): Promise<{ data: PagedResponse<SendLogEntry> }> {
  return apiClient.get(`${BASE}/${id}/send-logs`, { params })
}

// ── SMTP 설정 API ─────────────────────────────────────────────────────────────
export function getSmtpConfig(): Promise<{ data: SmtpConfig }> {
  return apiClient.get(SMTP_BASE)
}

export function updateSmtpConfig(req: SmtpConfig): Promise<{ data: SmtpConfig }> {
  return apiClient.put(SMTP_BASE, req)
}
