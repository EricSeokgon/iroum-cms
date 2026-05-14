<!--
  SPEC-CMS-PUBLIC-001 T-010 — 500 페이지 (F-03)
  - h1: '일시적인 오류가 발생했습니다'
  - 다시 시도 (현재 페이지 reload)
  - 홈으로 이동
-->
<template>
  <section
    class="flex flex-col items-center justify-center py-24 text-center"
    aria-labelledby="server-error-heading"
    data-testid="server-error-view"
  >
    <p class="text-6xl font-bold text-primary-600" aria-hidden="true">500</p>
    <h1 id="server-error-heading" class="mt-4 text-2xl font-bold text-content-DEFAULT">
      {{ t('error.serverError.title') }}
    </h1>
    <p class="mt-2 text-content-muted">{{ t('error.serverError.message') }}</p>

    <div class="mt-8 flex flex-wrap items-center justify-center gap-3">
      <button
        type="button"
        class="rounded-md bg-primary-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
        data-testid="server-error-retry"
        @click="retry"
      >
        {{ t('error.serverError.retry') }}
      </button>
      <router-link
        :to="{ name: 'home' }"
        class="rounded-md border border-gray-300 bg-white px-6 py-2.5 text-sm font-medium text-content-DEFAULT hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
        data-testid="server-error-home"
      >
        {{ t('error.serverError.goHome') }}
      </router-link>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

function retry(): void {
  // 현재 경로 새로고침 — window.location.reload()는 SPA 캐시 무효화 + 재요청
  if (typeof window !== 'undefined' && window.location?.reload) {
    window.location.reload()
  }
}
</script>
