// 통합 검색 API 래퍼 — SPEC-CMS-010
import axios from 'axios'

// @MX:ANCHOR: [AUTO] searchApi — SearchView, SynonymManagementView 등에서 공통 호출
// @MX:REASON: fan_in >= 3: 검색 뷰, 동의어 관리 뷰, 인기 검색어 위젯에서 참조

const BASE = '/api/v1/search'

// ── 공통 페이지 응답 ──────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 도메인 타입 ───────────────────────────────────────────────────────────────
export type SearchDomain =
  | 'ALL'
  | 'board'
  | 'content'
  | 'policy'
  | 'safety'
  | 'media'
  | 'publication'

// ── 검색 결과 ─────────────────────────────────────────────────────────────────
export interface DocResult {
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

export interface SearchFacets {
  byDomain: Record<string, number>
}

export interface SearchResponse {
  totalElements: number
  totalPages: number
  page: number
  size: number
  expandedQuery: string | null
  responseMs: number
  facets: SearchFacets
  content: DocResult[]
  searchLogId?: number
}

export interface SearchParams {
  q: string
  domain?: SearchDomain
  page?: number
  size?: number
  locale?: string
  from?: string
  to?: string
}

// ── 자동완성 ──────────────────────────────────────────────────────────────────
export interface AutocompleteItem {
  text: string
  source: string
  score: number
}

export interface AutocompleteResponse {
  items: AutocompleteItem[]
}

// ── 인기 검색어 ───────────────────────────────────────────────────────────────
export interface PopularQuery {
  rank: number
  query: string
  searchCount: number
}

export interface PopularResponse {
  period: string
  periodDate: string
  items: PopularQuery[]
}

// ── 동의어 ────────────────────────────────────────────────────────────────────
export type SynonymStatus = 'ACTIVE' | 'PAUSED'

export interface SynonymItem {
  id: number
  term: string
  synonym: string
  locale: string
  description?: string
  status: SynonymStatus
  createdAt?: string
  updatedAt?: string
}

export interface SynonymCreate {
  term: string
  synonym: string
  locale: string
  description?: string
}

// ── 검색 통계 ─────────────────────────────────────────────────────────────────
export interface SearchStatsItem {
  query: string
  searchCount: number
  clickCount: number
  ctr: number
}

export interface SearchStatsResponse {
  from: string
  to: string
  totalSearches: number
  uniqueQueries: number
  topQueries: SearchStatsItem[]
}

// ── API 함수 ──────────────────────────────────────────────────────────────────

/** GET /api/v1/search — 통합 검색 */
export function searchUnified(params: SearchParams): Promise<{ data: SearchResponse }> {
  return axios.get(BASE, { params })
}

/** GET /api/v1/search/autocomplete — 자동완성 */
export function autocomplete(
  prefix: string,
  limit = 10,
  locale = 'ko',
): Promise<{ data: AutocompleteResponse }> {
  return axios.get(`${BASE}/autocomplete`, {
    params: { prefix, limit, locale },
  })
}

/** GET /api/v1/search/popular — 인기 검색어 */
export function getPopularQueries(
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY' = 'DAILY',
  locale = 'ko',
  limit = 10,
): Promise<{ data: PopularResponse }> {
  return axios.get(`${BASE}/popular`, {
    params: { period, locale, limit },
  })
}

/** POST /api/v1/search/click — 결과 클릭 추적 (fire-and-forget) */
export function trackClick(
  searchLogId: number,
  docType: string,
  docId: number,
  rank: number,
): Promise<void> {
  return axios
    .post(`${BASE}/click`, { searchLogId, docType, docId, rank })
    .then(() => undefined)
}

/** GET /api/v1/search/synonyms — 동의어 목록 (ADMIN) */
export function listSynonyms(
  locale: string,
  page = 1,
  size = 20,
): Promise<{ data: PageResponse<SynonymItem> }> {
  return axios.get(`${BASE}/synonyms`, {
    params: { locale, page, size },
  })
}

/** POST /api/v1/search/synonyms — 동의어 등록 (ADMIN) */
export function createSynonym(data: SynonymCreate): Promise<{ data: SynonymItem }> {
  return axios.post(`${BASE}/synonyms`, data)
}

/** PUT /api/v1/search/synonyms/{id} — 동의어 수정 (ADMIN) */
export function updateSynonym(
  id: number,
  data: SynonymCreate,
): Promise<{ data: SynonymItem }> {
  return axios.put(`${BASE}/synonyms/${id}`, data)
}

/** DELETE /api/v1/search/synonyms/{id} — 동의어 삭제 (soft delete, ADMIN) */
export function deleteSynonym(id: number): Promise<void> {
  return axios.delete(`${BASE}/synonyms/${id}`).then(() => undefined)
}

/** GET /api/v1/search/stats/queries — 검색 통계 (ADMIN) */
export function getSearchStats(
  from: string,
  to: string,
  limit = 10,
): Promise<{ data: SearchStatsResponse }> {
  return axios.get(`${BASE}/stats/queries`, {
    params: { from, to, limit },
  })
}
