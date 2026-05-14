<!--
  SPEC-CMS-PUBLIC-001 T-008 — 통합 검색 결과 카드 (D-02)
  - snippet 의 <mark> 태그만 허용 (DOMPurify ALLOWED_TAGS: ['mark'])
  - type 뱃지 + 결과 링크 (url 또는 라우터 경로)
-->
<template>
  <article
    class="border-b border-gray-200 px-2 py-4 hover:bg-surface-muted focus-within:bg-surface-muted"
    data-testid="search-result-card"
  >
    <a
      :href="item.url"
      class="block focus-visible:outline-2 focus-visible:outline-primary-600"
    >
      <header class="flex flex-wrap items-center gap-2">
        <span
          class="rounded-md bg-primary-100 px-2 py-0.5 text-xs font-bold text-primary-700"
          :data-testid="`search-type-badge-${item.type}`"
        >
          {{ t(`search.typeBadge.${item.type}`) }}
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
import DOMPurify from 'dompurify'
import type { SearchResultItem } from '@/api/searchApi'

const props = defineProps<{
  item: SearchResultItem
}>()

const { t } = useI18n()

// D-02: <mark> 태그만 허용 — script/a/img 모두 제거
const safeSnippet = computed(() =>
  DOMPurify.sanitize(props.item.snippet ?? '', {
    ALLOWED_TAGS: ['mark'],
    ALLOWED_ATTR: [],
  }),
)
</script>
