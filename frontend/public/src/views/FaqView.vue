<!--
  SPEC-CMS-PUBLIC-001 T-006 — FAQ 화면
  AC: B-06 (아코디언 키보드 조작)

  - GET /faqs?categoryCode=...&keyword=...
  - 각 항목은 button[aria-expanded] + panel[aria-hidden] 구조
  - Enter/Space 토글, Tab 으로 헤더 간 이동 가능
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('faq.title') }}</h1>
    </header>

    <form
      class="flex flex-col gap-3 rounded-md border border-gray-200 bg-white p-4 md:flex-row md:items-end"
      role="search"
      :aria-label="t('common.search')"
      @submit.prevent="onSearchSubmit"
    >
      <div class="flex-1">
        <label for="faq-category" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('common.category') }}
        </label>
        <select
          id="faq-category"
          v-model="filters.categoryCode"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="faq-category-select"
        >
          <option value="">{{ t('faq.categoryAll') }}</option>
          <option v-for="cat in categories" :key="cat.code" :value="cat.code">
            {{ cat.name }}
          </option>
        </select>
      </div>
      <div class="flex-1">
        <label for="faq-keyword" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('common.keyword') }}
        </label>
        <input
          id="faq-keyword"
          v-model="filters.keyword"
          type="search"
          :placeholder="t('faq.searchPlaceholder')"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="faq-keyword-input"
        />
      </div>
      <button
        type="submit"
        class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
      >
        {{ t('notice.searchSubmit') }}
      </button>
    </form>

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadFaqs" />
    <EmptyState v-else-if="faqs.length === 0" />
    <ul v-else class="space-y-2" data-testid="faq-list">
      <li
        v-for="(faq, idx) in faqs"
        :key="faq.id"
        class="rounded-md border border-gray-200 bg-white"
      >
        <h3>
          <button
            :id="`faq-header-${faq.id}`"
            type="button"
            class="flex w-full items-center justify-between gap-4 px-4 py-3 text-left font-medium text-content-DEFAULT hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary-600"
            :aria-expanded="expandedSet.has(faq.id)"
            :aria-controls="`faq-panel-${faq.id}`"
            :data-testid="`faq-header-${idx}`"
            @click="toggle(faq.id)"
            @keydown.enter.prevent="toggle(faq.id)"
            @keydown.space.prevent="toggle(faq.id)"
          >
            <span class="flex items-center gap-2">
              <span class="font-bold text-primary-600">Q.</span>
              <span>{{ faq.question }}</span>
            </span>
            <span aria-hidden="true">{{ expandedSet.has(faq.id) ? '−' : '+' }}</span>
          </button>
        </h3>
        <div
          :id="`faq-panel-${faq.id}`"
          role="region"
          :aria-labelledby="`faq-header-${faq.id}`"
          :aria-hidden="!expandedSet.has(faq.id)"
          :hidden="!expandedSet.has(faq.id)"
          :data-testid="`faq-panel-${idx}`"
          class="border-t border-gray-100 bg-surface-muted px-4 py-3 text-sm text-content-muted"
        >
          <span class="mr-2 font-bold text-primary-600">A.</span>
          <span v-if="faq.answerHtml" class="prose prose-sm max-w-none" v-html="faq.answerHtml" />
          <span v-else class="text-gray-400">{{ t('common.loading') }}</span>
        </div>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { faqApi, type FaqSummary } from '@/api/faqApi'
import DOMPurify from 'dompurify'

import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()

const filters = reactive({
  categoryCode: '',
  keyword: '',
})

const faqs = ref<FaqSummary[]>([])
const categories = ref<Array<{ code: string; name: string }>>([])
const loading = ref(false)
const error = ref(false)
const expandedSet = ref<Set<number>>(new Set())

async function loadCategories(): Promise<void> {
  try {
    categories.value = await faqApi.categories()
  } catch {
    categories.value = []
  }
}

async function loadFaqs(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    const res = await faqApi.list({
      categoryCode: filters.categoryCode || undefined,
      keyword: filters.keyword || undefined,
      page: 0,
      size: 50,
    })
    faqs.value = res.content
  } catch {
    error.value = true
    faqs.value = []
  } finally {
    loading.value = false
  }
}

function onSearchSubmit(): void {
  loadFaqs()
}

async function toggle(id: number): Promise<void> {
  const next = new Set(expandedSet.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
    // answerHtml 아직 없으면 상세 API 호출
    const faq = faqs.value.find((f) => f.id === id)
    if (faq && !faq.answerHtml) {
      try {
        const detail = await faqApi.detail(id)
        faq.answerHtml = DOMPurify.sanitize(detail.answerHtml ?? '')
      } catch {
        faq.answerHtml = '답변을 불러올 수 없습니다.'
      }
    }
  }
  expandedSet.value = next
}

onMounted(async () => {
  await Promise.allSettled([loadCategories(), loadFaqs()])
})
</script>
