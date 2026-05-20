// SPEC-CMS-010 통합 검색
import { apiClient } from './client'

export type SearchType = 'ALL' | 'POST' | 'FAQ' | 'QNA' | 'POLICY' | 'SAFETY'

// 백엔드 SearchController: domain 파라미터 허용 값
export type SearchDomain = 'ALL' | 'board' | 'content' | 'policy' | 'safety' | 'media' | 'publication'

// 프론트 탭 → 백엔드 domain 매핑
const TYPE_TO_DOMAIN: Record<SearchType, SearchDomain> = {
  ALL: 'ALL',
  POST: 'board',
  FAQ: 'board',
  QNA: 'board',
  POLICY: 'policy',
  SAFETY: 'safety',
}

// 백엔드 DocResult 필드와 1:1 대응
export interface SearchResultItem {
  docType: string
  docId: number
  title: string
  snippet: string
  highlight: string
  rank: number
  domain: string
  url: string
  createdAt: string
}

// 백엔드 SearchResponse 필드와 1:1 대응
export interface SearchResponse {
  searchLogId?: number
  totalElements: number
  totalPages: number
  content: SearchResultItem[]
  byDomainFacets: Record<string, number>
  expandedQuery?: string
}

export interface SearchParams {
  q: string
  type?: SearchType
  page?: number
  size?: number
}

export const searchApi = {
  search(params: SearchParams): Promise<SearchResponse> {
    return apiClient
      .get<SearchResponse>('/search', {
        params: {
          q: params.q,
          domain: params.type ? TYPE_TO_DOMAIN[params.type] : undefined,
          page: params.page ?? 1,
          size: params.size ?? 20,
          locale: 'ko',
        },
      })
      .then((r) => r.data)
  },

  trackClick(searchLogId: number, docType: string, docId: number, rank: number): Promise<void> {
    return apiClient
      .post('/search/click', { searchLogId, docType, docId, rank })
      .then(() => {})
      .catch(() => {})
  },
}
