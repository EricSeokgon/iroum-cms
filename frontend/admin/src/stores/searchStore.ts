// 통합 검색 Pinia 스토어 — SPEC-CMS-010
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  searchUnified,
  autocomplete as apiAutocomplete,
  getPopularQueries,
  trackClick,
  listSynonyms,
  createSynonym,
  updateSynonym,
  deleteSynonym,
  getSearchStats,
  type SearchResponse,
  type SearchParams,
  type AutocompleteItem,
  type PopularQuery,
  type SynonymItem,
  type SynonymCreate,
  type SearchStatsResponse,
} from '@/api/search'

// @MX:ANCHOR: [AUTO] useSearchStore — SPEC-CMS-010 검색 도메인 4개 view에서 공통 상태 관리
// @MX:REASON: fan_in >= 3: SearchView, SynonymManagementView, SearchAnalyticsView, 인기 검색어 위젯에서 공유

export const useSearchStore = defineStore('search', () => {
  // ── 통합 검색 결과 상태 ─────────────────────────────────────────────────
  const result = ref<SearchResponse | null>(null)
  const searchLoading = ref(false)

  // ── 자동완성 상태 ───────────────────────────────────────────────────────
  const autocompleteItems = ref<AutocompleteItem[]>([])
  const autocompleteLoading = ref(false)

  // ── 인기 검색어 상태 ────────────────────────────────────────────────────
  const popularQueries = ref<PopularQuery[]>([])
  const popularLoading = ref(false)

  // ── 동의어 사전 상태 ────────────────────────────────────────────────────
  const synonyms = ref<SynonymItem[]>([])
  const synonymsTotal = ref(0)
  const synonymsLoading = ref(false)

  // ── 검색 통계 상태 ──────────────────────────────────────────────────────
  const stats = ref<SearchStatsResponse | null>(null)
  const statsLoading = ref(false)

  // ── 공통 에러 ───────────────────────────────────────────────────────────
  const error = ref<string | null>(null)

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 통합 검색 액션 ──────────────────────────────────────────────────────
  async function search(params: SearchParams): Promise<void> {
    searchLoading.value = true
    error.value = null
    try {
      const res = await searchUnified(params)
      result.value = res.data
    } catch (e) {
      setError(e, '검색 실패')
      result.value = null
    } finally {
      searchLoading.value = false
    }
  }

  // ── 자동완성 액션 ───────────────────────────────────────────────────────
  async function autocomplete(prefix: string, limit = 10, locale = 'ko'): Promise<void> {
    if (!prefix.trim()) {
      autocompleteItems.value = []
      return
    }
    autocompleteLoading.value = true
    try {
      const res = await apiAutocomplete(prefix, limit, locale)
      autocompleteItems.value = res.data.items
    } catch (e) {
      setError(e, '자동완성 조회 실패')
      autocompleteItems.value = []
    } finally {
      autocompleteLoading.value = false
    }
  }

  // ── 인기 검색어 액션 ────────────────────────────────────────────────────
  async function loadPopular(
    period: 'DAILY' | 'WEEKLY' | 'MONTHLY' = 'DAILY',
    locale = 'ko',
    limit = 10,
  ): Promise<void> {
    popularLoading.value = true
    try {
      const res = await getPopularQueries(period, locale, limit)
      popularQueries.value = res.data
    } catch (e) {
      setError(e, '인기 검색어 조회 실패')
      popularQueries.value = []
    } finally {
      popularLoading.value = false
    }
  }

  // ── 클릭 추적 액션 ──────────────────────────────────────────────────────
  async function recordClick(
    searchLogId: number,
    docType: string,
    docId: number,
    rank = 0,
  ): Promise<void> {
    try {
      await trackClick(searchLogId, docType, docId, rank)
    } catch {
      // 클릭 추적 실패는 사용자 흐름을 막지 않음
    }
  }

  // ── 동의어 사전 액션 ────────────────────────────────────────────────────
  async function fetchSynonyms(filter: {
    locale?: string
    page: number
    size: number
  }): Promise<void> {
    synonymsLoading.value = true
    error.value = null
    try {
      const res = await listSynonyms(filter.locale ?? 'ko', filter.page, filter.size)
      synonyms.value = res.data.content
      synonymsTotal.value = res.data.totalElements
    } catch (e) {
      setError(e, '동의어 조회 실패')
    } finally {
      synonymsLoading.value = false
    }
  }

  async function addSynonym(req: SynonymCreate): Promise<SynonymItem> {
    const res = await createSynonym(req)
    return res.data
  }

  async function modifySynonym(id: number, req: SynonymCreate): Promise<SynonymItem> {
    const res = await updateSynonym(id, req)
    return res.data
  }

  async function removeSynonym(id: number): Promise<void> {
    await deleteSynonym(id)
  }

  // ── 검색 통계 액션 ──────────────────────────────────────────────────────
  async function fetchStats(from: string, to: string, limit = 10): Promise<void> {
    statsLoading.value = true
    error.value = null
    try {
      const res = await getSearchStats(from, to, limit)
      stats.value = res.data
    } catch (e) {
      setError(e, '검색 통계 조회 실패')
      stats.value = null
    } finally {
      statsLoading.value = false
    }
  }

  return {
    // 상태
    result,
    searchLoading,
    autocompleteItems,
    autocompleteLoading,
    popularQueries,
    popularLoading,
    synonyms,
    synonymsTotal,
    synonymsLoading,
    stats,
    statsLoading,
    error,
    // 액션
    search,
    autocomplete,
    loadPopular,
    recordClick,
    fetchSynonyms,
    addSynonym,
    modifySynonym,
    removeSynonym,
    fetchStats,
  }
})
