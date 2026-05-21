<!--
  SPEC-CMS-PUBLIC-001 T-006 — 공지 목록 화면
  AC: B-01 (페이징), B-02 (카테고리·키워드 검색), B-03 (상단 고정)

  - GET /api/v1/notices?page=...&size=...&keyword=...&categoryCode=...
  - PostSummary 의 isNotice=true 인 항목을 페이지 0 에서 상단 고정 영역에 분리 표시
  - 카테고리 선택, 키워드 입력 → URL query 동기화 (page, category, keyword)
  - 페이지 변경 → 1-indexed 가 아닌 0-indexed page 사용
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('notice.list') }}</h1>
    </header>

    <!-- 검색 영역 -->
    <form
      class="flex flex-col gap-3 rounded-md border border-gray-200 bg-white p-4 md:flex-row md:items-end"
      role="search"
      :aria-label="t('common.search')"
      @submit.prevent="onSearchSubmit"
    >
      <div class="flex-1">
        <label for="notice-category" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('common.category') }}
        </label>
        <select
          id="notice-category"
          v-model="filters.categoryCode"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="notice-category-select"
        >
          <option value="">{{ t('notice.categoryAll') }}</option>
          <option value="EVENT">EVENT</option>
          <option value="NEWS">NEWS</option>
          <option value="GENERAL">GENERAL</option>
        </select>
      </div>
      <div class="flex-1">
        <label for="notice-keyword" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('common.keyword') }}
        </label>
        <input
          id="notice-keyword"
          v-model="filters.keyword"
          type="search"
          :placeholder="t('notice.searchPlaceholder')"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="notice-keyword-input"
        />
      </div>
      <button
        type="submit"
        class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="notice-search-submit"
      >
        {{ t('notice.searchSubmit') }}
      </button>
    </form>

    <!-- 상단 고정 공지: page=0 에서만 노출 -->
    <section
      v-if="pinnedNotices.length > 0 && currentPage === 0"
      :aria-label="t('notice.pinned')"
      data-testid="pinned-section"
    >
      <h2 class="mb-2 text-sm font-bold text-red-700">{{ t('notice.pinned') }}</h2>
      <ul class="divide-y divide-gray-100 rounded-md border border-red-100 bg-red-50">
        <li v-for="item in pinnedNotices" :key="`pin-${item.id}`">
          <NoticeCard :notice="item" />
        </li>
      </ul>
    </section>

    <!-- 일반 목록 -->
    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="error"
      :message="persistentError ? t('error.serverError.retryPersistent') : undefined"
      :show-retry="!persistentError"
      data-testid="notice-error-state"
      @retry="loadNotices"
    />
    <EmptyState v-else-if="normalNotices.length === 0">
      <template #action>
        <button
          type="button"
          class="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="empty-reset"
          @click="resetFilters"
        >
          {{ t('common.emptyReset') }}
        </button>
      </template>
    </EmptyState>
    <ul v-else class="divide-y divide-gray-100" data-testid="notice-list">
      <li v-for="item in normalNotices" :key="item.id">
        <NoticeCard :notice="item" />
      </li>
    </ul>

    <PaginationBar
      :page="currentPage"
      :page-size="pageSize"
      :total-elements="totalElements"
      :total-pages="totalPages"
      @change="onPageChange"
    />
  </section>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { noticeApi, type NoticeListParams, type NoticeSummary } from '@/api/noticeApi'
import NoticeCard from '@/components/notice/NoticeCard.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const pageSize = 20

const filters = reactive({
  categoryCode: (route.query.category as string) ?? '',
  keyword: (route.query.keyword as string) ?? '',
})

const currentPage = ref(parseInt((route.query.page as string) ?? '0', 10) || 0)
const items = ref<NoticeSummary[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref(false)
// F-05: 최대 3회 재시도 후 영구 실패 표시
const MAX_RETRIES = 3
const retryCount = ref(0)
const persistentError = ref(false)

const pinnedNotices = computed(() => items.value.filter((n) => n.isNotice))
const normalNotices = computed(() =>
  // 페이지 0 에서는 상단 고정 영역으로 분리 → 일반 목록에서는 제외
  currentPage.value === 0 ? items.value.filter((n) => !n.isNotice) : items.value,
)

async function loadNotices(): Promise<void> {
  // F-05: 영구 실패 상태에서는 더 이상 호출 금지
  if (persistentError.value) return

  loading.value = true
  error.value = false
  try {
    const params: NoticeListParams = {
      page: currentPage.value,
      size: pageSize,
    }
    if (filters.categoryCode) params.categoryCode = filters.categoryCode
    if (filters.keyword) params.keyword = filters.keyword
    const res = await noticeApi.list(params)
    items.value = res.content
    totalElements.value = res.totalElements
    totalPages.value = res.totalPages
    // 성공 시 retry 카운터 리셋
    retryCount.value = 0
  } catch {
    error.value = true
    items.value = []
    totalElements.value = 0
    totalPages.value = 0
    retryCount.value += 1
    if (retryCount.value >= MAX_RETRIES) {
      persistentError.value = true
    }
  } finally {
    loading.value = false
  }
}

function syncQuery(): void {
  const query: Record<string, string> = {}
  if (currentPage.value > 0) query.page = String(currentPage.value)
  if (filters.categoryCode) query.category = filters.categoryCode
  if (filters.keyword) query.keyword = filters.keyword
  router.replace({ name: 'notice-list', query })
}

function onSearchSubmit(): void {
  currentPage.value = 0
  syncQuery()
  loadNotices()
}

function onPageChange(next: number): void {
  currentPage.value = next
  syncQuery()
  loadNotices()
}

function resetFilters(): void {
  filters.categoryCode = ''
  filters.keyword = ''
  currentPage.value = 0
  syncQuery()
  loadNotices()
}

// route.query 변경(브라우저 뒤로가기 등)에도 반응
watch(
  () => route.query,
  (q) => {
    const nextPage = parseInt((q.page as string) ?? '0', 10) || 0
    const nextCategory = (q.category as string) ?? ''
    const nextKeyword = (q.keyword as string) ?? ''
    if (
      nextPage !== currentPage.value ||
      nextCategory !== filters.categoryCode ||
      nextKeyword !== filters.keyword
    ) {
      currentPage.value = nextPage
      filters.categoryCode = nextCategory
      filters.keyword = nextKeyword
      loadNotices()
    }
  },
)

onMounted(() => {
  loadNotices()
})
</script>
