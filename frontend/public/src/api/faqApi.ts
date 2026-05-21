// SPEC-CMS-003 FAQ
import { apiClient } from './client'
import type { PageResponse } from '@iroum/shared/types/api'

export interface FaqSummary {
  id: number
  question: string
  categoryCode: string
  sortOrder: number
  answerHtml?: string
}

export interface FaqListParams {
  page?: number
  size?: number
  keyword?: string
  categoryCode?: string
}

export const faqApi = {
  list(params: FaqListParams = {}): Promise<PageResponse<FaqSummary>> {
    return apiClient.get<PageResponse<FaqSummary>>('/faqs', { params }).then((r) => r.data)
  },
  detail(id: number): Promise<FaqSummary> {
    return apiClient.get<FaqSummary>(`/faqs/${id}`).then((r) => r.data)
  },
  categories(): Promise<Array<{ code: string; name: string }>> {
    return apiClient
      .get<Array<{ code: string; name: string }>>('/faqs/categories')
      .then((r) => r.data)
  },
}
