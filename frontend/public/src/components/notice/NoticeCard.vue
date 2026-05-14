<!--
  SPEC-CMS-PUBLIC-001 T-006 — 공지 목록 카드
  - 제목 + 메타(작성자, 작성일, 조회수, 카테고리)
  - 고정 여부(isNotice) 뱃지 표시
-->
<template>
  <article
    class="border-b border-gray-200 px-2 py-4 hover:bg-surface-muted focus-within:bg-surface-muted"
    data-testid="notice-card"
  >
    <router-link
      :to="{ name: 'notice-detail', params: { id: notice.id } }"
      class="block focus-visible:outline-2 focus-visible:outline-primary-600"
    >
      <header class="flex items-center gap-2">
        <span
          v-if="notice.isNotice"
          class="rounded-md bg-red-100 px-2 py-0.5 text-xs font-bold text-red-700"
          :aria-label="t('notice.pinned')"
          data-testid="pinned-badge"
        >
          {{ t('notice.pinned') }}
        </span>
        <h3 class="text-base font-semibold text-content-DEFAULT hover:text-primary-600">
          {{ notice.title }}
        </h3>
      </header>
      <dl class="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-content-muted">
        <div class="flex items-center gap-1">
          <dt class="sr-only">{{ t('common.author') }}</dt>
          <dd>{{ notice.authorUsername }}</dd>
        </div>
        <div class="flex items-center gap-1">
          <dt class="sr-only">{{ t('common.createdAt') }}</dt>
          <dd>{{ formattedDate }}</dd>
        </div>
        <div class="flex items-center gap-1">
          <dt class="sr-only">{{ t('common.viewCount') }}</dt>
          <dd>{{ t('common.viewCount') }} {{ notice.viewCount }}</dd>
        </div>
      </dl>
    </router-link>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PostSummary } from '@iroum/shared/types/api'

const props = defineProps<{
  notice: PostSummary
}>()

const { t } = useI18n()

const formattedDate = computed(() => {
  const raw = props.notice.publishedAt ?? props.notice.createdAt
  if (!raw) return ''
  // YYYY-MM-DD 형식으로 잘라낸다 (ISO 8601 기준)
  return raw.slice(0, 10)
})
</script>
