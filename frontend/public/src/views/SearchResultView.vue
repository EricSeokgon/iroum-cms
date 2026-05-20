<!--
  SPEC-CMS-PUBLIC-001 T-008 — 통합 검색 결과 (D-01/D-02/D-03)
  - GET /api/v1/search?q=...&type=...
  - 6 탭 필터 (ALL/POST/FAQ/QNA/POLICY/SAFETY) + URL 동기화
  - SearchResultCard 리스트 + DOMPurify <mark> 하이라이트
  - 빈 결과 → EmptyState + 검색 팁
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT" data-testid="search-page-title">
        {{ t('search.title') }}
      </h1>
      <p v-if="query" class="mt-1 text-sm text-content-muted" data-testid="search-summary">
        {{ t('search.resultsFor', { q: query, count: totalCount }) }}
      </p>
    </header>

    <SearchFilterTabs
      :model-value="currentType"
      :facets="facets"
      :total-count="allCount"
      @update:model-value="onTabChange"
    />

    <LoadingState v-if="loading" />
    <template v-else>
      <ul
        v-if="results.length > 0"
        class="divide-y divide-gray-100"
        data-testid="search-result-list"
      >
        <li v-for="(item, idx) in results" :key="`${item.docType}-${item.docId}`">
          <SearchResultCard :item="item" :search-log-id="searchLogId ?? undefined" :position="idx + 1" />
        </li>
      </ul>
      <EmptyState v-else>
        <template #action>
          <div class="space-y-1 text-sm" data-testid="search-empty-tip">
            <p class="font-semibold text-content-DEFAULT">{{ t('search.emptyTitle') }}</p>
            <p class="text-content-muted">{{ t('search.emptyTip') }}</p>
          </div>
        </template>
      </EmptyState>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { searchApi, type SearchType, type SearchResultItem } from '@/api/searchApi'
import SearchFilterTabs from '@/components/search/SearchFilterTabs.vue'
import SearchResultCard from '@/components/search/SearchResultCard.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const validTypes: SearchType[] = ['ALL', 'POST', 'FAQ', 'QNA', 'POLICY', 'SAFETY']

const query = ref<string>('')
const currentType = ref<SearchType>('ALL')
const results = ref<SearchResultItem[]>([])
const totalCount = ref(0)
const allCount = ref(0)
const facets = ref<Record<string, number>>({})
const loading = ref(false)
const searchLogId = ref<number | null>(null)

function parseType(value: unknown): SearchType {
  const v = String(value ?? 'ALL') as SearchType
  return validTypes.includes(v) ? v : 'ALL'
}

async function runSearch(): Promise<void> {
  if (!query.value) {
    results.value = []
    totalCount.value = 0
    return
  }
  loading.value = true
  try {
    const res = await searchApi.search({
      q: query.value,
      type: currentType.value,
      page: 1,
      size: 20,
    })
    results.value = res.content ?? []
    totalCount.value = res.totalElements
    searchLogId.value = res.searchLogId ?? null
    if (res.byDomainFacets) facets.value = res.byDomainFacets
    if (currentType.value === 'ALL') {
      allCount.value = res.totalElements
    }
  } catch {
    results.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
  }
}

function onTabChange(next: SearchType): void {
  currentType.value = next
  router.replace({
    name: 'search',
    query: { q: query.value, type: next === 'ALL' ? undefined : next },
  })
  runSearch()
}

function loadFromRoute(): void {
  query.value = String(route.query.q ?? '')
  currentType.value = parseType(route.query.type)
}

onMounted(() => {
  loadFromRoute()
  runSearch()
})

watch(
  () => [route.query.q, route.query.type],
  () => {
    loadFromRoute()
    runSearch()
  },
)
</script>
