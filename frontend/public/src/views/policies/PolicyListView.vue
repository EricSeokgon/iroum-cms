<!--
  SPEC-CMS-PUBLIC-001 T-007 — 정책 목록
  AC: C-01 (industry/region/type 다중 필터 + URL 동기화)

  - GET /api/v1/policies?industry=...&region=...&type=...&page=0&size=20
  - 필터 적용 시 URL query 동기화
  - "필터 초기화" 버튼 → 모든 필터 클리어 + /policies 로 URL 리셋
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('policy.list') }}</h1>
    </header>

    <PolicyFilterBar
      v-model="filters"
      :industry-options="industryOptions"
      :region-options="regionOptions"
      :type-options="typeOptions"
      @apply="onApply"
      @reset="onReset"
    />

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadPolicies" />
    <EmptyState v-else-if="items.length === 0" />
    <ul v-else class="divide-y divide-gray-100" data-testid="policy-list">
      <li v-for="item in items" :key="item.id">
        <PolicyCard :policy="item" />
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
import { policyApi, type PolicyListParams, type PolicySummary } from '@/api/policyApi'
import PolicyCard from '@/components/policy/PolicyCard.vue'
import PolicyFilterBar, { type PolicyFilters } from '@/components/policy/PolicyFilterBar.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const pageSize = 20

// 옵션 — 실제 운영에서는 별도 API 에서 가져올 수 있으나 SPEC 단순화를 위해 정적
const industryOptions = ['IT', '제조', '서비스', '농업', '건설']
const regionOptions = ['서울', '부산', '대구', '인천', '광주', '대전', '울산', '경기']
const typeOptions = ['자금지원', '컨설팅', '교육', '판로지원']

const filters = reactive<PolicyFilters>({
  industry: (route.query.industry as string) ?? '',
  region: (route.query.region as string) ?? '',
  type: (route.query.type as string) ?? '',
})

const currentPage = ref(parseInt((route.query.page as string) ?? '0', 10) || 0)
const items = ref<PolicySummary[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref(false)

async function loadPolicies(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    const params: PolicyListParams = {
      page: currentPage.value,
      size: pageSize,
    }
    if (filters.industry) params.industry = filters.industry
    if (filters.region) params.region = filters.region
    if (filters.type) params.type = filters.type
    const res = await policyApi.list(params)
    items.value = res.content
    totalElements.value = res.totalElements
    totalPages.value = res.totalPages
  } catch {
    error.value = true
    items.value = []
    totalElements.value = 0
    totalPages.value = 0
  } finally {
    loading.value = false
  }
}

function syncQuery(): void {
  const query: Record<string, string> = {}
  if (currentPage.value > 0) query.page = String(currentPage.value)
  if (filters.industry) query.industry = filters.industry
  if (filters.region) query.region = filters.region
  if (filters.type) query.type = filters.type
  router.replace({ name: 'policy-list', query })
}

function onApply(): void {
  currentPage.value = 0
  syncQuery()
  loadPolicies()
}

function onReset(): void {
  filters.industry = ''
  filters.region = ''
  filters.type = ''
  currentPage.value = 0
  // 명시적으로 빈 query 로 리셋 (URL → /policies)
  router.replace({ name: 'policy-list', query: {} })
  loadPolicies()
}

function onPageChange(next: number): void {
  currentPage.value = next
  syncQuery()
  loadPolicies()
}

watch(
  () => route.query,
  (q) => {
    const nextPage = parseInt((q.page as string) ?? '0', 10) || 0
    const nextIndustry = (q.industry as string) ?? ''
    const nextRegion = (q.region as string) ?? ''
    const nextType = (q.type as string) ?? ''
    if (
      nextPage !== currentPage.value ||
      nextIndustry !== filters.industry ||
      nextRegion !== filters.region ||
      nextType !== filters.type
    ) {
      currentPage.value = nextPage
      filters.industry = nextIndustry
      filters.region = nextRegion
      filters.type = nextType
      loadPolicies()
    }
  },
)

onMounted(() => {
  loadPolicies()
})
</script>
