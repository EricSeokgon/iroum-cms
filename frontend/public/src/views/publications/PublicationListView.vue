<!--
  SPEC-CMS-PUBLIC-001 T-007 — 발간자료 목록
  AC: C-07 — year, documentType, categoryId 필터

  - GET /api/v1/publications?year=&documentType=&categoryId=&page=&size=
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('publication.list') }}</h1>
    </header>

    <form
      class="flex flex-col gap-3 rounded-md border border-gray-200 bg-white p-4 md:flex-row md:items-end md:flex-wrap"
      role="search"
      :aria-label="t('common.search')"
      @submit.prevent="onApply"
    >
      <div class="flex-1 min-w-[120px]">
        <label
          for="pub-year"
          class="mb-1 block text-sm font-medium text-content-DEFAULT"
        >
          {{ t('publication.year') }}
        </label>
        <select
          id="pub-year"
          v-model="filters.year"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="publication-year-select"
        >
          <option value="">{{ t('publication.yearAll') }}</option>
          <option v-for="y in yearOptions" :key="y" :value="String(y)">{{ y }}</option>
        </select>
      </div>
      <div class="flex-1 min-w-[140px]">
        <label
          for="pub-doctype"
          class="mb-1 block text-sm font-medium text-content-DEFAULT"
        >
          {{ t('publication.documentType') }}
        </label>
        <select
          id="pub-doctype"
          v-model="filters.documentType"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="publication-doctype-select"
        >
          <option value="">{{ t('publication.documentTypeAll') }}</option>
          <option v-for="opt in documentTypeOptions" :key="opt" :value="opt">{{ opt }}</option>
        </select>
      </div>
      <div class="flex-1 min-w-[140px]">
        <label
          for="pub-category"
          class="mb-1 block text-sm font-medium text-content-DEFAULT"
        >
          {{ t('publication.category') }}
        </label>
        <input
          id="pub-category"
          v-model="filters.categoryId"
          type="number"
          min="0"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="publication-category-input"
        />
      </div>
      <button
        type="submit"
        class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="publication-filter-apply"
      >
        {{ t('notice.searchSubmit') }}
      </button>
    </form>

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadPublications" />
    <EmptyState v-else-if="items.length === 0" />
    <ul v-else class="divide-y divide-gray-100" data-testid="publication-list">
      <li v-for="pub in items" :key="pub.id">
        <PublicationCard :publication="pub" />
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
import { reactive, ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { apiClient } from '@/api/client'
import type {
  PublicationListParams,
  PublicationSummary,
} from '@/api/publicationTypes'
import type { PageResponse } from '@iroum/shared/types/api'
import PublicationCard from '@/components/publication/PublicationCard.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const pageSize = 20
const currentYear = new Date().getFullYear()
const yearOptions = Array.from({ length: 6 }, (_, i) => currentYear - i)
const documentTypeOptions = ['RESEARCH', 'STATISTICS', 'WHITE_PAPER', 'GUIDE']

const filters = reactive<{
  year: string
  documentType: string
  categoryId: string
}>({
  year: (route.query.year as string) ?? '',
  documentType: (route.query.documentType as string) ?? '',
  categoryId: (route.query.categoryId as string) ?? '',
})

const currentPage = ref(parseInt((route.query.page as string) ?? '0', 10) || 0)
const items = ref<PublicationSummary[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref(false)

async function loadPublications(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    const params: PublicationListParams = {
      page: currentPage.value,
      size: pageSize,
    }
    if (filters.year) params.year = parseInt(filters.year, 10)
    if (filters.documentType) params.documentType = filters.documentType
    if (filters.categoryId) params.categoryId = parseInt(filters.categoryId, 10)
    const res = await apiClient.get<PageResponse<PublicationSummary>>('/publications', {
      params,
    })
    items.value = res.data.content
    totalElements.value = res.data.totalElements
    totalPages.value = res.data.totalPages
  } catch {
    error.value = true
    items.value = []
  } finally {
    loading.value = false
  }
}

function syncQuery(): void {
  const query: Record<string, string> = {}
  if (currentPage.value > 0) query.page = String(currentPage.value)
  if (filters.year) query.year = filters.year
  if (filters.documentType) query.documentType = filters.documentType
  if (filters.categoryId) query.categoryId = filters.categoryId
  router.replace({ name: 'publication-list', query })
}

function onApply(): void {
  currentPage.value = 0
  syncQuery()
  loadPublications()
}

function onPageChange(next: number): void {
  currentPage.value = next
  syncQuery()
  loadPublications()
}

watch(
  () => route.query,
  (q) => {
    const nextPage = parseInt((q.page as string) ?? '0', 10) || 0
    const nextYear = (q.year as string) ?? ''
    const nextDocType = (q.documentType as string) ?? ''
    const nextCategoryId = (q.categoryId as string) ?? ''
    if (
      nextPage !== currentPage.value ||
      nextYear !== filters.year ||
      nextDocType !== filters.documentType ||
      nextCategoryId !== filters.categoryId
    ) {
      currentPage.value = nextPage
      filters.year = nextYear
      filters.documentType = nextDocType
      filters.categoryId = nextCategoryId
      loadPublications()
    }
  },
)

onMounted(() => {
  loadPublications()
})
</script>
