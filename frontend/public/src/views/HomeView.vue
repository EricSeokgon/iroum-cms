<!--
  SPEC-CMS-PUBLIC-001 T-008 / T-010 — 홈 페이지
  - Hero + 최신 공지 + 정책 하이라이트 + 빠른 링크 + KPI 섹션
  - F-07: Promise.allSettled 로 섹션별 부분 실패 격리 (notice 실패해도 policy/kpi 렌더링)
-->
<template>
  <section class="space-y-12">
    <!-- 1. Hero 배너 -->
    <section
      class="rounded-lg bg-gradient-to-br from-primary-50 to-primary-100 px-6 py-10 text-center"
      data-testid="home-hero"
    >
      <h1 class="text-3xl font-bold text-content-DEFAULT">{{ t('home.title') }}</h1>
      <p class="mt-2 text-base text-content-muted">{{ t('home.hero.tagline') }}</p>
      <form
        class="mx-auto mt-6 flex max-w-xl items-center gap-2"
        role="search"
        :aria-label="t('common.search')"
        data-testid="home-search-form"
        @submit.prevent="onHeroSearch"
      >
        <label for="home-hero-search" class="sr-only">{{ t('common.search') }}</label>
        <input
          id="home-hero-search"
          v-model="heroQuery"
          type="search"
          class="flex-1 rounded-md border border-gray-300 bg-white px-4 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          :placeholder="t('home.hero.searchPlaceholder')"
          :aria-label="t('common.search')"
          data-testid="home-search-input"
        />
        <button
          type="submit"
          class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
        >
          {{ t('common.search') }}
        </button>
      </form>
    </section>

    <!-- 2. 최신 공지 -->
    <section data-testid="home-notices-section">
      <header class="mb-4 flex items-center justify-between">
        <h2 class="text-xl font-bold text-content-DEFAULT">{{ t('home.sections.latestNotices') }}</h2>
        <router-link
          :to="{ name: 'notice-list' }"
          class="text-sm text-primary-600 hover:underline focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="home-notices-more"
        >
          {{ t('home.sections.more') }}
        </router-link>
      </header>
      <LoadingState v-if="loadingNotices" :rows="2" />
      <ErrorState
        v-else-if="errorNotices"
        data-testid="home-notices-error"
        @retry="loadNotices"
      />
      <ul v-else class="divide-y divide-gray-100" data-testid="home-notices-list">
        <li v-for="n in notices" :key="n.id">
          <NoticeCard :notice="n" />
        </li>
      </ul>
    </section>

    <!-- 3. 정책 하이라이트 -->
    <section data-testid="home-policies-section">
      <header class="mb-4 flex items-center justify-between">
        <h2 class="text-xl font-bold text-content-DEFAULT">{{ t('home.sections.policyHighlights') }}</h2>
        <router-link
          :to="{ name: 'policy-list' }"
          class="text-sm text-primary-600 hover:underline focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="home-policies-more"
        >
          {{ t('home.sections.more') }}
        </router-link>
      </header>
      <LoadingState v-if="loadingPolicies" :rows="2" />
      <ErrorState
        v-else-if="errorPolicies"
        data-testid="home-policies-error"
        @retry="loadPolicies"
      />
      <ul v-else class="divide-y divide-gray-100" data-testid="home-policies-list">
        <li v-for="p in policies" :key="p.id">
          <PolicyCard :policy="p" />
        </li>
      </ul>
    </section>

    <!-- 4. 빠른 링크 -->
    <section data-testid="home-quicklinks-section">
      <h2 class="mb-4 text-xl font-bold text-content-DEFAULT">{{ t('home.sections.quickLinks') }}</h2>
      <ul class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4" data-testid="home-quicklinks">
        <li v-for="link in quickLinks" :key="link.key">
          <router-link
            :to="link.to"
            class="block rounded-lg border border-gray-200 bg-white p-4 hover:border-primary-600 hover:shadow-md focus-visible:outline-2 focus-visible:outline-primary-600"
            :data-testid="`home-quicklink-${link.key}`"
          >
            <p class="text-base font-semibold text-content-DEFAULT">{{ link.label }}</p>
            <p class="mt-1 text-sm text-content-muted">{{ link.desc }}</p>
          </router-link>
        </li>
      </ul>
    </section>

    <!-- 5. KPI -->
    <section v-if="!loadingKpi && kpiValues.length > 0" data-testid="home-stats-section">
      <header class="mb-4 flex items-center justify-between">
        <h2 class="text-xl font-bold text-content-DEFAULT">{{ t('home.sections.stats') }}</h2>
        <router-link
          :to="{ name: 'public-stats' }"
          class="text-sm text-primary-600 hover:underline focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="home-stats-more"
        >
          {{ t('home.sections.more') }}
        </router-link>
      </header>
      <ul class="grid grid-cols-2 gap-4 sm:grid-cols-4" data-testid="home-kpi-list">
        <li
          v-for="kpi in kpiValues.slice(0, 4)"
          :key="kpi.code"
          class="rounded-lg border border-gray-200 bg-white p-4"
          :data-testid="`home-kpi-${kpi.code}`"
        >
          <p class="text-xs text-content-muted">{{ kpi.label }}</p>
          <p class="mt-1 text-2xl font-bold text-primary-700">
            {{ kpi.value.toLocaleString() }}<span v-if="kpi.unit" class="ml-1 text-sm">{{ kpi.unit }}</span>
          </p>
        </li>
      </ul>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { noticeApi, type NoticeSummary } from '@/api/noticeApi'
import { policyApi, type PolicySummary } from '@/api/policyApi'
import { statsApi, type KpiValue } from '@/api/statsApi'
import NoticeCard from '@/components/notice/NoticeCard.vue'
import PolicyCard from '@/components/policy/PolicyCard.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'

const { t } = useI18n()
const router = useRouter()

const heroQuery = ref('')
const notices = ref<NoticeSummary[]>([])
const policies = ref<PolicySummary[]>([])
const kpiValues = ref<KpiValue[]>([])
const loadingNotices = ref(false)
const loadingPolicies = ref(false)
const loadingKpi = ref(false)
const errorNotices = ref(false)
const errorPolicies = ref(false)

const quickLinks = computed(() => [
  { key: 'faq', to: { name: 'faq' }, label: t('home.quickLinks.faq'), desc: t('home.quickLinks.faqDesc') },
  { key: 'policy-match', to: { name: 'policy-match' }, label: t('home.quickLinks.policyMatch'), desc: t('home.quickLinks.policyMatchDesc') },
  { key: 'safety', to: { name: 'safety-guideline-list' }, label: t('home.quickLinks.safety'), desc: t('home.quickLinks.safetyDesc') },
  { key: 'qna', to: { name: 'qna-list' }, label: t('home.quickLinks.qna'), desc: t('home.quickLinks.qnaDesc') },
])

function onHeroSearch(): void {
  const q = heroQuery.value.trim()
  if (!q) return
  router.push({ name: 'search', query: { q } })
}

async function loadNotices(): Promise<void> {
  loadingNotices.value = true
  errorNotices.value = false
  try {
    const res = await noticeApi.list({ page: 0, size: 4 })
    notices.value = res.content
  } catch {
    notices.value = []
    errorNotices.value = true
  } finally {
    loadingNotices.value = false
  }
}

async function loadPolicies(): Promise<void> {
  loadingPolicies.value = true
  errorPolicies.value = false
  try {
    const res = await policyApi.list({ page: 0, size: 3 })
    policies.value = res.content
  } catch {
    policies.value = []
    errorPolicies.value = true
  } finally {
    loadingPolicies.value = false
  }
}

async function loadKpi(): Promise<void> {
  loadingKpi.value = true
  try {
    kpiValues.value = await statsApi.kpiValues()
  } catch {
    kpiValues.value = []
  } finally {
    loadingKpi.value = false
  }
}

// F-07: Promise.allSettled — 어느 한 섹션이 실패해도 나머지는 정상 렌더링
async function loadAll(): Promise<void> {
  await Promise.allSettled([loadNotices(), loadPolicies(), loadKpi()])
}

onMounted(() => {
  void loadAll()
})
</script>
