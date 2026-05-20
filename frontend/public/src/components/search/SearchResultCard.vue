<!--
  SPEC-CMS-PUBLIC-001 T-008 — 통합 검색 결과 카드 (D-02)
  - snippet 의 <mark> 태그만 허용 (DOMPurify ALLOWED_TAGS: ['mark'])
  - type 뱃지 + 결과 링크 (url 또는 라우터 경로)
  - 클릭 시 searchLogId가 있으면 /search/click 추적 + history state 전달
-->
<template>
  <article
    class="border-b border-gray-200 px-2 py-4 hover:bg-surface-muted focus-within:bg-surface-muted"
    data-testid="search-result-card"
  >
    <a
      :href="item.url"
      class="block focus-visible:outline-2 focus-visible:outline-primary-600"
      @click.prevent="handleClick"
    >
      <header class="flex flex-wrap items-center gap-2">
        <span
          class="rounded-md bg-primary-100 px-2 py-0.5 text-xs font-bold text-primary-700"
          :data-testid="`search-type-badge-${item.docType}`"
        >
          {{ t(`search.typeBadge.${item.docType}`, item.docType) }}
        </span>
        <h3
          class="text-base font-semibold text-content-DEFAULT hover:text-primary-600"
          data-testid="search-result-title"
        >
          {{ item.title }}
        </h3>
      </header>
      <!-- @MX:NOTE: [AUTO] v-html 사용 — DOMPurify ALLOWED_TAGS ['mark'] 만 허용 (D-02 검색 하이라이트) -->
      <p
        class="mt-2 text-sm text-content-muted [&_mark]:bg-yellow-100 [&_mark]:px-1 [&_mark]:rounded"
        data-testid="search-result-snippet"
        v-html="safeSnippet"
      />
    </a>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { searchApi, type SearchResultItem } from '@/api/searchApi'

const props = defineProps<{
  item: SearchResultItem
  searchLogId?: number
  position?: number
}>()

const { t } = useI18n()
const router = useRouter()

// D-02: <mark> 태그만 허용 — script/a/img 모두 제거
const safeSnippet = computed(() =>
  DOMPurify.sanitize(props.item.snippet ?? '', {
    ALLOWED_TAGS: ['mark'],
    ALLOWED_ATTR: [],
  }),
)

function handleClick(): void {
  // 클릭 추적 (fire-and-forget) — searchLogId가 있을 때만
  if (props.searchLogId && props.item.docId) {
    searchApi.trackClick(
      props.searchLogId,
      props.item.docType,
      props.item.docId,
      props.position ?? 1,
    )
  }

  // history state에 추적 정보 저장 → PolicyDetailView 등에서 활용
  const state = {
    searchLogId: props.searchLogId ?? null,
    docType: props.item.docType,
    docId: props.item.docId,
    rank: props.position ?? 1,
  }

  router.push({ path: props.item.url, state })
}
</script>
