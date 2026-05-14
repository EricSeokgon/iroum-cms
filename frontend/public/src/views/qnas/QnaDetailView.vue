<!--
  SPEC-CMS-PUBLIC-001 T-006 — Q&A 상세 화면
  AC: B-08 (비공개 게시글 접근 시 404 → NotFoundView 로 리다이렉트)

  - GET /qnas/{id}
  - 404 → router.replace({ name: 'not-found' })
-->
<template>
  <section class="space-y-6">
    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadQna" />
    <article v-else-if="qna" class="space-y-6">
      <header class="border-b border-gray-200 pb-4">
        <div class="flex items-center gap-2">
          <span
            class="rounded-md px-2 py-0.5 text-xs font-bold"
            :class="statusClass"
          >
            {{ statusLabel }}
          </span>
          <span
            v-if="qna.isPrivate"
            class="rounded-md bg-gray-100 px-2 py-0.5 text-xs font-bold text-gray-700"
          >
            {{ t('qna.privateLabel') }}
          </span>
        </div>
        <h1 class="mt-2 text-2xl font-bold text-content-DEFAULT">{{ qna.title }}</h1>
        <dl class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-content-muted">
          <div class="flex items-center gap-1">
            <dt class="sr-only">{{ t('common.author') }}</dt>
            <dd>{{ qna.authorUsername }}</dd>
          </div>
          <div class="flex items-center gap-1">
            <dt class="sr-only">{{ t('common.createdAt') }}</dt>
            <dd>{{ qna.createdAt.slice(0, 10) }}</dd>
          </div>
        </dl>
      </header>

      <NoticeContent :html="qna.questionHtml" />

      <section
        :aria-label="t('qna.answerSection')"
        class="rounded-md border border-gray-200 bg-surface-muted p-4"
        data-testid="qna-answer-section"
      >
        <h2 class="mb-2 text-base font-bold text-content-DEFAULT">
          {{ t('qna.answerSection') }}
        </h2>
        <NoticeContent v-if="qna.answerHtml" :html="qna.answerHtml" />
        <p v-else class="text-sm text-content-muted">{{ t('qna.noAnswer') }}</p>
      </section>

      <footer class="flex justify-end border-t border-gray-200 pt-4">
        <router-link
          :to="{ name: 'qna-list' }"
          class="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary-600"
        >
          {{ t('common.back') }}
        </router-link>
      </footer>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { qnaApi, type QnaDetail } from '@/api/qnaApi'
import NoticeContent from '@/components/notice/NoticeContent.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const qna = ref<QnaDetail | null>(null)
const loading = ref(false)
const error = ref(false)

const statusLabel = computed(() => {
  if (!qna.value) return ''
  switch (qna.value.status) {
    case 'ANSWERED':
      return t('qna.answered')
    case 'PENDING':
      return t('qna.pending')
    case 'CLOSED':
      return t('qna.closed')
    default:
      return ''
  }
})

const statusClass = computed(() => {
  if (!qna.value) return ''
  switch (qna.value.status) {
    case 'ANSWERED':
      return 'bg-green-100 text-green-700'
    case 'PENDING':
      return 'bg-yellow-100 text-yellow-700'
    case 'CLOSED':
      return 'bg-gray-100 text-gray-600'
    default:
      return ''
  }
})

async function loadQna(): Promise<void> {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    error.value = true
    return
  }
  loading.value = true
  error.value = false
  try {
    qna.value = await qnaApi.detail(id)
  } catch (err) {
    // @MX:NOTE: [AUTO] 404 → NotFoundView 리다이렉트 (비공개 글 우회 차단)
    if (axios.isAxiosError(err) && err.response?.status === 404) {
      await router.replace({ name: 'not-found' })
      return
    }
    error.value = true
    qna.value = null
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, loadQna)

onMounted(() => {
  loadQna()
})
</script>
