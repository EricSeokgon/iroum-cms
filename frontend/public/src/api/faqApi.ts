// SPEC-CMS-003 FAQ
import { apiClient } from './client'
import type { PageResponse } from '@iroum/shared/types/api'

export interface FaqSummary {
  id: number
  question: string
  answer: string
  categoryCode: string
  sortOrder: number
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
  categories(): Promise<Array<{ code: string; name: string }>> {
    return apiClient
      .get<Array<{ code: string; name: string }>>('/faqs/categories')
      .then((r) => r.data)
  },
}
