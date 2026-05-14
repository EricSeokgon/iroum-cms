<!--
  SPEC-CMS-PUBLIC-001 T-007 — 사고사례 공개 목록
  AC: C-06 (공개 필터, industryCode 적용)

  - GET /api/v1/safety/accident-cases?industryCode=...&page=0&size=20
  - 서버가 공개 가능한 사례만 반환
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('safety.incidents') }}</h1>
    </header>

    <form
      class="flex flex-col gap-3 rounded-md border border-gray-200 bg-white p-4 md:flex-row md:items-end"
      role="search"
      :aria-label="t('common.search')"
      @submit.prevent="onApply"
    >
      <div class="flex-1">
        <label
          for="incident-industry"
          class="mb-1 block text-sm font-medium text-content-DEFAULT"
        >
          {{ t('safety.industryCode') }}
        </label>
        <select
          id="incident-industry"
          v-model="filters.industryCode"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="incident-industry-select"
        >
          <option value="">{{ t('safety.industryAll') }}</option>
          <option v-for="opt in industryOptions" :key="opt" :value="opt">{{ opt }}</option>
        </select>
      </div>
      <button
        type="submit"
        class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="incident-filter-apply"
      >
        {{ t('notice.searchSubmit') }}
      </button>
    </form>

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadIncidents" />
    <EmptyState v-else-if="items.length === 0" />
    <ul v-else class="divide-y divide-gray-100" data-testid="incident-list">
      <li v-for="incident in items" :key="incident.id">
        <IncidentCard :incident="incident" />
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
import {
  safetyApi,
  type SafetyListParams,
  type SafetyIncidentSummary,
} from '@/api/safetyApi'
import IncidentCard from '@/components/safety/IncidentCard.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const pageSize = 20
const industryOptions = ['IT', '제조', '서비스', '농업', '건설']

const filters = reactive<{ industryCode: string }>({
  industryCode: (route.query.industryCode as string) ?? '',
})

const currentPage = ref(parseInt((route.query.page as string) ?? '0', 10) || 0)
const items = ref<SafetyIncidentSummary[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref(false)

async function loadIncidents(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    const params: SafetyListParams = {
      page: currentPage.value,
      size: pageSize,
    }
    if (filters.industryCode) params.industryCode = filters.industryCode
    const res = await safetyApi.incidents(params)
    items.value = res.content
    totalElements.value = res.totalElements
    totalPages.value = res.totalPages
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
  if (filters.industryCode) query.industryCode = filters.industryCode
  router.replace({ name: 'safety-incident-list', query })
}

function onApply(): void {
  currentPage.value = 0
  syncQuery()
  loadIncidents()
}

function onPageChange(next: number): void {
  currentPage.value = next
  syncQuery()
  loadIncidents()
}

watch(
  () => route.query,
  (q) => {
    const nextPage = parseInt((q.page as string) ?? '0', 10) || 0
    const nextIndustry = (q.industryCode as string) ?? ''
    if (nextPage !== currentPage.value || nextIndustry !== filters.industryCode) {
      currentPage.value = nextPage
      filters.industryCode = nextIndustry
      loadIncidents()
    }
  },
)

onMounted(() => {
  loadIncidents()
})
</script>
