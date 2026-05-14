// SPEC-CMS-010 통합 검색
import { apiClient } from './client'

export type SearchType = 'ALL' | 'POST' | 'FAQ' | 'QNA' | 'POLICY' | 'SAFETY'

export interface SearchResultItem {
  id: number
  type: SearchType
  title: string
  snippet: string
  url: string
  score: number
}

export interface SearchResponse {
  totalElements: number
  page: number
  size: number
  results: SearchResultItem[]
  facets?: Record<string, number>
}

export interface SearchParams {
  q: string
  type?: SearchType
  page?: number
  size?: number
}

export const searchApi = {
  search(params: SearchParams): Promise<SearchResponse> {
    return apiClient.get<SearchResponse>('/search', { params }).then((r) => r.data)
  },
}
