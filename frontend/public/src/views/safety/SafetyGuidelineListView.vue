<!--
  SPEC-CMS-PUBLIC-001 T-007 — 안전 가이드 목록
  - GET /api/v1/safety/guidelines?industryCode=...&page=0&size=20
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('safety.guidelines') }}</h1>
    </header>

    <form
      class="flex flex-col gap-3 rounded-md border border-gray-200 bg-white p-4 md:flex-row md:items-end"
      role="search"
      :aria-label="t('common.search')"
      @submit.prevent="onApply"
    >
      <div class="flex-1">
        <label
          for="safety-industry"
          class="mb-1 block text-sm font-medium text-content-DEFAULT"
        >
          {{ t('safety.industryCode') }}
        </label>
        <select
          id="safety-industry"
          v-model="filters.industryCode"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="safety-industry-select"
        >
          <option value="">{{ t('safety.industryAll') }}</option>
          <option v-for="opt in industryOptions" :key="opt" :value="opt">{{ opt }}</option>
        </select>
      </div>
      <button
        type="submit"
        class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="safety-guideline-apply"
      >
        {{ t('notice.searchSubmit') }}
      </button>
    </form>

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadGuidelines" />
    <EmptyState v-else-if="items.length === 0" />
    <ul v-else class="divide-y divide-gray-100" data-testid="safety-guideline-list">
      <li v-for="g in items" :key="g.id">
        <article class="px-2 py-4 hover:bg-surface-muted">
          <router-link
            :to="{ name: 'safety-guideline-detail', params: { id: g.id } }"
            class="block focus-visible:outline-2 focus-visible:outline-primary-600"
          >
            <h3 class="text-base font-semibold text-content-DEFAULT hover:text-primary-600">
              {{ g.title }}
            </h3>
            <dl class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-content-muted">
              <div class="flex items-center gap-1">
                <dt class="sr-only">{{ t('safety.industryCode') }}</dt>
                <dd>{{ g.industryCode }}</dd>
              </div>
              <div v-if="g.processCode" class="flex items-center gap-1">
                <dt class="sr-only">{{ t('safety.processCode') }}</dt>
                <dd>{{ g.processCode }}</dd>
              </div>
              <div class="flex items-center gap-1">
                <dt class="sr-only">{{ t('safety.lastUpdated') }}</dt>
                <dd>{{ g.updatedAt.slice(0, 10) }}</dd>
              </div>
            </dl>
          </router-link>
        </article>
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
  type SafetyGuidelineSummary,
} from '@/api/safetyApi'
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
const items = ref<SafetyGuidelineSummary[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref(false)

async function loadGuidelines(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    const params: SafetyListParams = {
      page: currentPage.value,
      size: pageSize,
    }
    if (filters.industryCode) params.industryCode = filters.industryCode
    const res = await safetyApi.guidelines(params)
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
  router.replace({ name: 'safety-guideline-list', query })
}

function onApply(): void {
  currentPage.value = 0
  syncQuery()
  loadGuidelines()
}

function onPageChange(next: number): void {
  currentPage.value = next
  syncQuery()
  loadGuidelines()
}

watch(
  () => route.query,
  (q) => {
    const nextPage = parseInt((q.page as string) ?? '0', 10) || 0
    const nextIndustry = (q.industryCode as string) ?? ''
    if (nextPage !== currentPage.value || nextIndustry !== filters.industryCode) {
      currentPage.value = nextPage
      filters.industryCode = nextIndustry
      loadGuidelines()
    }
  },
)

onMounted(() => {
  loadGuidelines()
})
</script>
