// SPEC-CMS-003 Q&A
import { apiClient } from './client'
import type { PageResponse } from '@iroum/shared/types/api'

export interface QnaSummary {
  id: number
  title: string
  authorUsername: string
  status: 'PENDING' | 'ANSWERED' | 'CLOSED'
  isPrivate: boolean
  createdAt: string
}

export interface QnaDetail extends QnaSummary {
  questionHtml: string
  answerHtml?: string
  answeredAt?: string
}

export interface QnaCreateRequest {
  title: string
  questionHtml: string
  isPrivate: boolean
}

export interface QnaListParams {
  page?: number
  size?: number
  keyword?: string
  status?: 'ANSWERED' | 'PENDING' | 'CLOSED'
  mine?: boolean
}

export const qnaApi = {
  list(params: QnaListParams = {}): Promise<PageResponse<QnaSummary>> {
    return apiClient.get<PageResponse<QnaSummary>>('/qnas', { params }).then((r) => r.data)
  },
  detail(id: number): Promise<QnaDetail> {
    return apiClient.get<QnaDetail>(`/qnas/${id}`).then((r) => r.data)
  },
  create(req: QnaCreateRequest): Promise<QnaDetail> {
    return apiClient.post<QnaDetail>('/qnas', req).then((r) => r.data)
  },
}
