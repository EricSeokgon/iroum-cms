<!--
  SPEC-CMS-PUBLIC-001 T-007 — 발간자료 카드
  AC: C-07 — thumbnailUrl, title, publicationYear, downloadCount 표시
-->
<template>
  <article
    class="flex gap-4 border-b border-gray-200 px-2 py-4 hover:bg-surface-muted focus-within:bg-surface-muted"
    data-testid="publication-card"
  >
    <router-link
      :to="{ name: 'publication-detail', params: { id: publication.id } }"
      class="flex flex-1 gap-4 focus-visible:outline-2 focus-visible:outline-primary-600"
    >
      <img
        v-if="publication.thumbnailUrl"
        :src="publication.thumbnailUrl"
        :alt="publication.title"
        class="h-24 w-20 rounded-md border border-gray-200 object-cover"
        data-testid="publication-thumbnail"
        loading="lazy"
      />
      <div class="flex-1">
        <h3 class="text-base font-semibold text-content-DEFAULT hover:text-primary-600">
          {{ publication.title }}
        </h3>
        <dl class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-content-muted">
          <div class="flex items-center gap-1">
            <dt class="sr-only">{{ t('publication.publicationYear') }}</dt>
            <dd data-testid="publication-year">{{ publication.publicationYear }}</dd>
          </div>
          <div class="flex items-center gap-1">
            <dt class="sr-only">{{ t('publication.downloadCount') }}</dt>
            <dd data-testid="publication-download-count">
              {{ t('publication.downloadCount') }} {{ publication.downloadCount }}
            </dd>
          </div>
        </dl>
      </div>
    </router-link>
  </article>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PublicationSummary } from '@/api/publicationTypes'

defineProps<{
  publication: PublicationSummary
}>()

const { t } = useI18n()
</script>
