<!--
  내 Q&A 목록 화면 — /me/qnas
  mine=true 파라미터로 로그인한 사용자의 작성 Q&A만 조회
-->
<template>
  <section class="space-y-6">
    <header class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('qna.myTitle') }}</h1>
      <router-link
        :to="{ name: 'qna-create' }"
        class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="qna-create-link"
      >
        {{ t('qna.createButton') }}
      </router-link>
    </header>

    <form
      class="flex flex-col gap-3 rounded-md border border-gray-200 bg-white p-4 md:flex-row md:items-end"
      role="search"
      :aria-label="t('common.search')"
      @submit.prevent="onSearchSubmit"
    >
      <div class="flex-1">
        <label for="my-qna-keyword" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('common.keyword') }}
        </label>
        <input
          id="my-qna-keyword"
          v-model="keyword"
          type="search"
          :placeholder="t('qna.searchPlaceholder')"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="my-qna-keyword-input"
        />
      </div>
      <div>
        <label for="my-qna-status" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('common.all') }}
        </label>
        <select
          id="my-qna-status"
          v-model="status"
          class="rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          @change="onStatusChange"
        >
          <option value="">{{ t('qna.statusAll') }}</option>
          <option value="PENDING">{{ t('qna.pending') }}</option>
          <option value="ANSWERED">{{ t('qna.answered') }}</option>
          <option value="CLOSED">{{ t('qna.closed') }}</option>
        </select>
      </div>
      <button
        type="submit"
        class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
      >
        {{ t('notice.searchSubmit') }}
      </button>
    </form>

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadQnas" />
    <EmptyState v-else-if="items.length === 0" :message="t('qna.myEmpty')" />
    <ul v-else class="divide-y divide-gray-100" data-testid="my-qna-list">
      <li v-for="item in items" :key="item.id">
        <article class="px-2 py-4 hover:bg-surface-muted">
          <router-link
            :to="{ name: 'qna-detail', params: { id: item.id } }"
            class="block focus-visible:outline-2 focus-visible:outline-primary-600"
          >
            <header class="flex items-center gap-2">
              <span
                v-if="item.isPrivate"
                class="rounded-md bg-gray-100 px-2 py-0.5 text-xs font-bold text-gray-700"
                :aria-label="t('qna.privateLabel')"
              >
                {{ t('qna.privateLabel') }}
              </span>
              <span
                class="rounded-md px-2 py-0.5 text-xs font-bold"
                :class="statusClass(item.status)"
              >
                {{ statusLabel(item.status) }}
              </span>
              <h3 class="text-base font-semibold text-content-DEFAULT hover:text-primary-600">
                {{ item.title }}
              </h3>
            </header>
            <dl class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-content-muted">
              <div class="flex items-center gap-1">
                <dt class="sr-only">{{ t('common.createdAt') }}</dt>
                <dd>{{ item.createdAt.slice(0, 10) }}</dd>
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
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { qnaApi, type QnaSummary } from '@/api/qnaApi'
import PaginationBar from '@/components/common/PaginationBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const pageSize = 20

const items = ref<QnaSummary[]>([])
const currentPage = ref(parseInt((route.query.page as string) ?? '0', 10) || 0)
const totalElements = ref(0)
const totalPages = ref(0)
const keyword = ref((route.query.keyword as string) ?? '')
const status = ref((route.query.status as string) ?? '')
const loading = ref(false)
const error = ref(false)

function statusLabel(s: QnaSummary['status']): string {
  switch (s) {
    case 'ANSWERED': return t('qna.answered')
    case 'PENDING':  return t('qna.pending')
    case 'CLOSED':   return t('qna.closed')
    default:         return ''
  }
}

function statusClass(s: QnaSummary['status']): string {
  switch (s) {
    case 'ANSWERED': return 'bg-green-100 text-green-700'
    case 'PENDING':  return 'bg-yellow-100 text-yellow-700'
    case 'CLOSED':   return 'bg-gray-100 text-gray-600'
    default:         return ''
  }
}

async function loadQnas(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    const res = await qnaApi.list({
      page: currentPage.value,
      size: pageSize,
      keyword: keyword.value || undefined,
      status: (status.value as QnaSummary['status']) || undefined,
      mine: true,
    })
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
  if (keyword.value) query.keyword = keyword.value
  if (status.value) query.status = status.value
  router.replace({ name: 'my-qna', query })
}

function onSearchSubmit(): void {
  currentPage.value = 0
  syncQuery()
  loadQnas()
}

function onStatusChange(): void {
  currentPage.value = 0
  syncQuery()
  loadQnas()
}

function onPageChange(next: number): void {
  currentPage.value = next
  syncQuery()
  loadQnas()
}

watch(
  () => route.query,
  (q) => {
    const nextPage = parseInt((q.page as string) ?? '0', 10) || 0
    if (nextPage !== currentPage.value) {
      currentPage.value = nextPage
      loadQnas()
    }
  },
)

onMounted(() => {
  loadQnas()
})
</script>
