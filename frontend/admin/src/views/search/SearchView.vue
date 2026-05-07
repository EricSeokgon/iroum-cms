<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('search.title') }}</h2>
    </div>

    <!-- 검색 입력 영역 -->
    <div class="mb-4 flex items-center gap-2">
      <el-input
        v-model="query"
        :placeholder="t('search.placeholder')"
        size="large"
        clearable
        style="max-width: 600px"
        :aria-label="t('search.placeholder')"
        @input="onQueryInput"
        @keyup.enter="executeSearch(true)"
      >
        <template #prefix>
          <el-icon><i-ep-search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" size="large" @click="executeSearch(true)">
        {{ t('search.button') }}
      </el-button>
    </div>

    <!-- 날짜 범위 필터 -->
    <div class="mb-4 flex items-center gap-3">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        :range-separator="t('common.to')"
        :start-placeholder="t('search.dateFrom')"
        :end-placeholder="t('search.dateTo')"
        format="YYYY-MM-DD"
        value-format="YYYY-MM-DD"
        :aria-label="t('search.dateRange')"
        @change="onFilterChange"
      />
      <el-button v-if="dateRange" plain @click="clearDateRange">
        {{ t('common.clear') }}
      </el-button>
    </div>

    <!-- 도메인 탭 -->
    <el-tabs
      v-model="activeDomain"
      class="mb-4"
      :aria-label="t('search.domainTabs')"
      @tab-change="onDomainChange"
    >
      <el-tab-pane
        v-for="d in domainTabs"
        :key="d"
        :name="d"
      >
        <template #label>
          <span>
            {{ t(`search.domain.${d}`) }}
            <el-badge
              v-if="getDomainCount(d) > 0"
              :value="getDomainCount(d)"
              :max="9999"
              class="ml-1"
            />
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- aria-live 알림 -->
    <div aria-live="polite" aria-atomic="true" class="sr-only">{{ liveAnnouncement }}</div>

    <!-- 인기 검색어 (검색어 없을 때) -->
    <div v-if="!hasQueried && popularQueries.length > 0" class="mb-6">
      <h3 class="mb-3 text-base font-semibold text-gray-700">{{ t('search.popular') }}</h3>
      <div class="flex flex-wrap gap-2">
        <el-tag
          v-for="p in popularQueries"
          :key="p.rank"
          size="large"
          class="cursor-pointer"
          effect="plain"
          @click="onPopularClick(p.query)"
        >
          {{ p.rank }}. {{ p.query }}
          <span class="ml-1 text-xs text-gray-500">({{ p.searchCount }})</span>
        </el-tag>
      </div>
    </div>

    <!-- 로딩 중 -->
    <el-skeleton v-if="loading" :rows="5" animated />

    <!-- 검색 결과 -->
    <div v-else-if="hasQueried">
      <!-- 결과 헤더 -->
      <div class="mb-4 text-sm text-gray-600">
        {{ t('search.totalResults', { total: totalElements }) }}
        <span v-if="responseMs >= 0" class="ml-2 text-gray-400">
          ({{ responseMs }}ms)
        </span>
        <span v-if="expandedQuery" class="ml-2 text-blue-600">
          {{ t('search.expandedQuery') }}: {{ expandedQuery }}
        </span>
      </div>

      <!-- 결과 리스트 -->
      <ul v-if="results.length > 0" class="space-y-4" role="list">
        <li
          v-for="(item, idx) in results"
          :key="`${item.docType}-${item.docId}`"
          class="search-result-item rounded border border-gray-200 bg-white p-4 hover:border-blue-300 hover:shadow-sm transition"
        >
          <div class="mb-2 flex items-center gap-2">
            <el-tag
              :type="getDocTypeTagType(item.docType)"
              size="small"
            >
              {{ t(`search.domain.${item.docType}`) }}
            </el-tag>
            <span class="text-xs text-gray-500">{{ formatDate(item.createdAt) }}</span>
          </div>
          <a
            :href="item.url || '#'"
            class="block"
            :aria-label="`${t(`search.domain.${item.docType}`)}: ${item.title}`"
            @click="onResultClick($event, item, idx)"
          >
            <h3
              class="mb-1 text-base font-semibold text-blue-600 hover:underline"
              v-html="renderHighlight(item.highlight || item.title)"
            ></h3>
            <p class="line-clamp-2 text-sm text-gray-600">{{ item.snippet }}</p>
            <p v-if="item.url" class="mt-1 text-xs text-gray-400">{{ item.url }}</p>
          </a>
        </li>
      </ul>

      <!-- 빈 상태 -->
      <el-empty
        v-else
        :description="t('search.noResults')"
        :image-size="120"
        class="mt-8"
      >
        <template #image>
          <el-icon :size="60" class="text-gray-300">
            <i-ep-search />
          </el-icon>
        </template>
      </el-empty>

      <!-- 페이지네이션 -->
      <div v-if="totalElements > 0" class="mt-6 flex justify-center">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="totalElements"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          background
          :aria-label="t('a11y.pagination')"
          @change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  searchUnified,
  getPopularQueries,
  trackClick,
  type SearchDomain,
  type DocResult,
  type PopularQuery,
} from '@/api/search'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()

// ── 상태 ──────────────────────────────────────────────────────────────────────
const query = ref<string>('')
const activeDomain = ref<SearchDomain>('ALL')
const dateRange = ref<[string, string] | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)

const loading = ref(false)
const hasQueried = ref(false)
const results = ref<DocResult[]>([])
const totalElements = ref(0)
const responseMs = ref(-1)
const expandedQuery = ref<string | null>(null)
const facets = ref<Record<string, number>>({})
const searchLogId = ref<number | undefined>(undefined)

const popularQueries = ref<PopularQuery[]>([])
const liveAnnouncement = ref('')

const domainTabs: SearchDomain[] = [
  'ALL',
  'board',
  'content',
  'policy',
  'safety',
  'media',
  'publication',
]

// ── 도메인별 페싯 카운트 ──────────────────────────────────────────────────────
function getDomainCount(domain: SearchDomain): number {
  if (domain === 'ALL') return totalElements.value
  return facets.value[domain] ?? 0
}

// ── 도메인별 태그 색상 ────────────────────────────────────────────────────────
function getDocTypeTagType(
  docType: string,
): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  switch (docType) {
    case 'board':
      return 'primary'
    case 'content':
      return 'success'
    case 'policy':
      return 'warning'
    case 'safety':
      return 'danger'
    case 'media':
      return 'info'
    case 'publication':
      return 'primary'
    default:
      return 'info'
  }
}

// ── 검색 실행 — 디바운스 (500ms) ──────────────────────────────────────────────
let debounceTimer: ReturnType<typeof setTimeout> | null = null
function onQueryInput(): void {
  if (debounceTimer) clearTimeout(debounceTimer)
  // 빈 검색어는 즉시 결과 초기화
  if (!query.value.trim()) {
    hasQueried.value = false
    results.value = []
    totalElements.value = 0
    return
  }
  debounceTimer = setTimeout(() => {
    executeSearch(true)
  }, 500)
}

// @MX:ANCHOR: [AUTO] executeSearch — 검색 입력, 도메인 변경, 페이지 변경, 날짜 필터에서 호출
// @MX:REASON: fan_in >= 3: 디바운스 입력, 탭 변경, 페이지네이션, 날짜 필터, 인기 검색어 클릭에서 사용
async function executeSearch(resetPage = false): Promise<void> {
  if (!query.value.trim()) {
    hasQueried.value = false
    return
  }
  if (resetPage) currentPage.value = 1

  loading.value = true
  hasQueried.value = true
  try {
    const res = await searchUnified({
      q: query.value.trim(),
      domain: activeDomain.value,
      page: currentPage.value,
      size: pageSize.value,
      locale: locale.value,
      from: dateRange.value?.[0],
      to: dateRange.value?.[1],
    })
    results.value = res.data.content
    totalElements.value = res.data.totalElements
    responseMs.value = res.data.responseMs
    expandedQuery.value = res.data.expandedQuery
    facets.value = res.data.facets?.byDomain ?? {}
    searchLogId.value = res.data.searchLogId

    liveAnnouncement.value = t('search.totalResults', { total: res.data.totalElements })

    // URL 파라미터 동기화
    syncUrlParams()
  } catch {
    ElMessage.error(t('common.loadError'))
    results.value = []
    totalElements.value = 0
  } finally {
    loading.value = false
  }
}

// ── 도메인 탭 변경 ────────────────────────────────────────────────────────────
function onDomainChange(): void {
  if (hasQueried.value) executeSearch(true)
}

// ── 페이지 변경 ───────────────────────────────────────────────────────────────
function onPageChange(): void {
  executeSearch(false)
}

// ── 날짜 범위 변경 ────────────────────────────────────────────────────────────
function onFilterChange(): void {
  if (hasQueried.value) executeSearch(true)
}

function clearDateRange(): void {
  dateRange.value = null
  if (hasQueried.value) executeSearch(true)
}

// ── 인기 검색어 클릭 ──────────────────────────────────────────────────────────
function onPopularClick(q: string): void {
  query.value = q
  executeSearch(true)
}

// ── 결과 클릭 — fire-and-forget 추적 + 라우팅 ─────────────────────────────────
function onResultClick(event: MouseEvent, item: DocResult, idx: number): void {
  // 클릭 추적은 비동기 fire-and-forget
  if (searchLogId.value && searchLogId.value > 0) {
    trackClick(searchLogId.value, item.docType, item.docId, item.rank ?? idx + 1).catch(() => {
      // 추적 실패는 사용자 흐름을 막지 않음
      console.warn(t('search.clickResult'))
    })
  }

  // URL이 외부 절대경로가 아니면 SPA 라우팅으로 처리
  if (item.url && item.url.startsWith('/')) {
    event.preventDefault()
    router.push(item.url)
  }
  // 빈 URL은 기본 동작 차단
  if (!item.url) {
    event.preventDefault()
  }
}

// ── 하이라이트 렌더링 — 백엔드에서 sanitize된 <mark> 태그 ────────────────────
function renderHighlight(html: string): string {
  return html || ''
}

// ── 날짜 포맷 ─────────────────────────────────────────────────────────────────
function formatDate(iso: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleDateString(locale.value === 'ko' ? 'ko-KR' : 'en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

// ── URL 파라미터 동기화 ───────────────────────────────────────────────────────
function syncUrlParams(): void {
  const params: Record<string, string> = {}
  if (query.value) params.q = query.value
  if (activeDomain.value !== 'ALL') params.domain = activeDomain.value
  if (currentPage.value !== 1) params.page = String(currentPage.value)
  router.replace({ path: '/search', query: params }).catch(() => {
    // 라우팅 오류 무시
  })
}

// ── 인기 검색어 로드 ──────────────────────────────────────────────────────────
async function loadPopular(): Promise<void> {
  try {
    const res = await getPopularQueries('DAILY', locale.value, 10)
    popularQueries.value = res.data.items
  } catch {
    // 인기 검색어 로드 실패는 무시
  }
}

// ── 마운트 시 — URL 파라미터 → 검색 실행 ─────────────────────────────────────
onMounted(() => {
  const q = (route.query.q as string) || ''
  const d = (route.query.domain as SearchDomain) || 'ALL'
  const p = Number(route.query.page) || 1

  query.value = q
  activeDomain.value = d
  currentPage.value = p

  if (q.trim()) {
    executeSearch(false)
  }

  loadPopular()
})
</script>

<style scoped>
/* 검색 결과 하이라이트 — 백엔드에서 sanitize된 <mark> 태그 스타일 */
:deep(mark) {
  background: #fef3c7;
  padding: 0 2px;
  border-radius: 2px;
  font-weight: 600;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.search-result-item {
  list-style: none;
}
</style>
