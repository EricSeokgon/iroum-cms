<!--
  SPEC-CMS-PUBLIC-001 T-006 — 공지 본문 렌더러
  - DOMPurify로 sanitize 후 v-html 렌더
  - 허용 태그: p, br, strong, em, ul, ol, li, a, img, table, tbody, tr, td, th, h2~h4
  - 스크립트/onerror 등 위험 속성 제거 (XSS 방어)
-->
<template>
  <!-- @MX:NOTE: [AUTO] v-html 사용 — 반드시 DOMPurify.sanitize 결과만 주입 -->
  <div
    class="prose prose-sm max-w-none break-words text-content-DEFAULT [&_a]:text-primary-600 [&_a]:underline [&_h2]:mt-6 [&_h2]:text-xl [&_h2]:font-semibold [&_h3]:mt-4 [&_h3]:text-lg [&_h3]:font-semibold [&_ul]:list-disc [&_ul]:pl-6 [&_ol]:list-decimal [&_ol]:pl-6 [&_table]:w-full [&_table]:border-collapse [&_td]:border [&_td]:px-2 [&_td]:py-1 [&_th]:border [&_th]:bg-surface-muted [&_th]:px-2 [&_th]:py-1"
    data-testid="notice-content"
    v-html="safeHtml"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'

const props = defineProps<{
  html: string
}>()

// @MX:ANCHOR: [AUTO] sanitize — XSS 방어를 위한 화이트리스트 기반 정화
// @MX:REASON: NoticeDetailView, BoardPostDetailView, QnaDetailView 등 3+ 곳에서 사용
// @MX:SPEC: SPEC-CMS-PUBLIC-001 §B-04
const ALLOWED_TAGS = [
  'p',
  'br',
  'strong',
  'em',
  'b',
  'i',
  'u',
  'ul',
  'ol',
  'li',
  'a',
  'img',
  'table',
  'thead',
  'tbody',
  'tr',
  'td',
  'th',
  'h2',
  'h3',
  'h4',
  'blockquote',
  'pre',
  'code',
  'span',
  'div',
]
const ALLOWED_ATTR = ['href', 'src', 'alt', 'class', 'style', 'title', 'target', 'rel']

const safeHtml = computed(() =>
  DOMPurify.sanitize(props.html ?? '', {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover'],
  }),
)
</script>
