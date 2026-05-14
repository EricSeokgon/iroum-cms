// SPEC-CMS-PUBLIC-001 §5.4 — searchStore (통합 검색)
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { searchApi, type SearchResultItem, type SearchType } from '@/api/searchApi'

export const useSearchStore = defineStore('search', () => {
  const query = ref<string>('')
  const results = ref<SearchResultItem[]>([])
  const isLoading = ref(false)
  const totalCount = ref(0)
  const currentType = ref<SearchType>('ALL')

  async function search(q: string, type: SearchType = 'ALL'): Promise<void> {
    query.value = q
    currentType.value = type
    isLoading.value = true
    try {
      const response = await searchApi.search({ q, type, page: 0, size: 20 })
      results.value = response.results
      totalCount.value = response.totalElements
    } catch {
      results.value = []
      totalCount.value = 0
    } finally {
      isLoading.value = false
    }
  }

  function clearResults(): void {
    query.value = ''
    results.value = []
    totalCount.value = 0
  }

  return { query, results, isLoading, totalCount, currentType, search, clearResults }
})
