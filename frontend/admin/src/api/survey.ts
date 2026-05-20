// 설문조사 API 래퍼 — SPEC-CMS-003
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] surveyApi — SurveyListView/DetailView 등에서 공통 참조
// @MX:REASON: fan_in >= 3: 목록/상세/생성/수정/삭제/응답/결과 다수 콜사이트

const BASE = '/surveys'

// ── 공통 페이지 응답 ─────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 도메인 타입 ──────────────────────────────────────────────────────────────
export type SurveyStatus = 'DRAFT' | 'OPEN' | 'CLOSED' | 'HIDDEN'
export type QuestionType = 'SINGLE' | 'MULTI' | 'TEXT' | 'RATING' | 'DATE'

export interface SurveyQuestionDto {
  id: number
  surveyId: number
  questionText: string
  questionType: QuestionType
  required: boolean
  sortOrder: number
  options: string | null // JSON string: [{value, label}] for SINGLE/MULTI
}

export interface SurveySummary {
  id: number
  title: string
  status: SurveyStatus
  isAnonymous: boolean
  maxResponses: number | null
  responseCount: number
  startAt: string
  endAt: string
  createdAt: string
}

export interface SurveyDetail extends SurveySummary {
  descriptionHtml: string | null
  questions: SurveyQuestionDto[]
}

export interface SurveyQuestionRequest {
  questionText: string
  questionType: QuestionType
  required: boolean
  sortOrder: number
  options: string | null
}

export interface SurveyCreateRequest {
  title: string
  descriptionHtml?: string
  descriptionText?: string
  startAt: string // ISO string
  endAt: string // ISO string
  isAnonymous: boolean
  maxResponses?: number | null
  questions: SurveyQuestionRequest[]
}

export type SurveyUpdateRequest = Partial<SurveyCreateRequest> & { status?: SurveyStatus }

export interface SurveyAnswerRequest {
  questionId: number
  answerText?: string
  answerOptions?: string // JSON array string
  answerRating?: number
  answerDate?: string // date string YYYY-MM-DD
}

export interface SurveySubmitRequest {
  answers: SurveyAnswerRequest[]
}

export interface DistributionItem {
  label: string
  count: number
  percentage: number
}

export interface QuestionResult {
  questionId: number
  questionText: string
  questionType: QuestionType
  totalAnswers: number
  distribution: DistributionItem[]
}

export interface SurveyResultDto {
  surveyId: number
  title: string
  totalResponses: number
  questions: QuestionResult[]
}

export interface SurveyListParams {
  status?: SurveyStatus
  keyword?: string
  page?: number
  size?: number
}

// ── API 함수 ─────────────────────────────────────────────────────────────────
export function listSurveys(
  params: SurveyListParams,
): Promise<{ data: PageResponse<SurveySummary> }> {
  return apiClient.get(BASE, { params })
}

export function getSurvey(id: number): Promise<{ data: SurveyDetail }> {
  return apiClient.get(`${BASE}/${id}`)
}

export function createSurvey(req: SurveyCreateRequest): Promise<{ data: SurveyDetail }> {
  return apiClient.post(BASE, req)
}

export function updateSurvey(
  id: number,
  req: SurveyUpdateRequest,
): Promise<{ data: SurveyDetail }> {
  return apiClient.put(`${BASE}/${id}`, req)
}

export function deleteSurvey(id: number): Promise<void> {
  return apiClient.delete(`${BASE}/${id}`)
}

export function submitSurveyResponse(
  surveyId: number,
  req: SurveySubmitRequest,
): Promise<void> {
  return apiClient.post(`${BASE}/${surveyId}/responses`, req)
}

export function getSurveyResults(surveyId: number): Promise<{ data: SurveyResultDto }> {
  return apiClient.get(`${BASE}/${surveyId}/results`)
}
