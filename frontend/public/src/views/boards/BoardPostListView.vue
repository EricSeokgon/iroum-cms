<!--
  SPEC-CMS-PUBLIC-001 T-006 — 게시판 게시글 목록 화면
  - 라우트 파라미터 :code 로 게시판 마스터 조회 → 마스터의 bbsId로 게시글 페이지 호출
  - keyword 검색 + 페이지네이션
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">
        {{ master?.name ?? t('board.title') }}
      </h1>
      <p v-if="master?.description" class="mt-1 text-sm text-content-muted">
        {{ master.description }}
      </p>
    </header>

    <form
      class="flex flex-col gap-3 rounded-md border border-gray-200 bg-white p-4 md:flex-row md:items-end"
      role="search"
      :aria-label="t('common.search')"
      @submit.prevent="onSearchSubmit"
    >
      <div class="flex-1">
        <label for="board-keyword" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('common.keyword') }}
        </label>
        <input
          id="board-keyword"
          v-model="keyword"
          type="search"
          :placeholder="t('board.searchPlaceholder')"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="board-keyword-input"
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
    <ErrorState v-else-if="error" @retry="loadAll" />
    <EmptyState v-else-if="posts.length === 0" :message="t('board.noContent')" />
    <ul v-else class="divide-y divide-gray-100" data-testid="board-post-list">
      <li v-for="post in posts" :key="post.id">
        <article class="px-2 py-4 hover:bg-surface-muted">
          <router-link
            :to="{
              name: 'board-post-detail',
              params: { code: routeCode, id: post.id },
            }"
            class="block focus-visible:outline-2 focus-visible:outline-primary-600"
          >
            <h3 class="text-base font-semibold text-content-DEFAULT hover:text-primary-600">
              {{ post.title }}
            </h3>
            <dl class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-content-muted">
              <div class="flex items-center gap-1">
                <dt class="sr-only">{{ t('common.author') }}</dt>
                <dd>{{ post.authorUsername }}</dd>
              </div>
              <div class="flex items-center gap-1">
                <dt class="sr-only">{{ t('common.createdAt') }}</dt>
                <dd>{{ (post.publishedAt ?? post.createdAt).slice(0, 10) }}</dd>
              </div>
              <div class="flex items-center gap-1">
                <dt class="sr-only">{{ t('common.viewCount') }}</dt>
                <dd>{{ t('common.viewCount') }} {{ post.viewCount }}</dd>
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
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { boardApi } from '@/api/boardApi'
import type { BbsMasterDetail, PostSummary } from '@iroum/shared/types/api'
import PaginationBar from '@/components/common/PaginationBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const pageSize = 20
const routeCode = computed(() => route.params.code as string)

const master = ref<BbsMasterDetail | null>(null)
const posts = ref<PostSummary[]>([])
const currentPage = ref(parseInt((route.query.page as string) ?? '0', 10) || 0)
const totalElements = ref(0)
const totalPages = ref(0)
const keyword = ref((route.query.keyword as string) ?? '')
const loading = ref(false)
const error = ref(false)

async function loadAll(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    if (!master.value || master.value.code !== routeCode.value) {
      master.value = await boardApi.master(routeCode.value)
    }
    const res = await boardApi.posts(master.value.id, {
      page: currentPage.value,
      size: pageSize,
      keyword: keyword.value || undefined,
    })
    posts.value = res.content
    totalElements.value = res.totalElements
    totalPages.value = res.totalPages
  } catch {
    error.value = true
    posts.value = []
  } finally {
    loading.value = false
  }
}

function syncQuery(): void {
  const query: Record<string, string> = {}
  if (currentPage.value > 0) query.page = String(currentPage.value)
  if (keyword.value) query.keyword = keyword.value
  router.replace({ name: 'board-post-list', params: { code: routeCode.value }, query })
}

function onSearchSubmit(): void {
  currentPage.value = 0
  syncQuery()
  loadAll()
}

function onPageChange(next: number): void {
  currentPage.value = next
  syncQuery()
  loadAll()
}

watch(routeCode, () => {
  currentPage.value = 0
  keyword.value = ''
  master.value = null
  loadAll()
})

onMounted(() => {
  loadAll()
})
</script>
