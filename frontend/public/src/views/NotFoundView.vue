<!--
  SPEC-CMS-PUBLIC-001 T-010 — 404 페이지 (F-01)
  - h1: '페이지를 찾을 수 없습니다'
  - 홈으로 이동 (router-link to home)
  - 이전 페이지 (router.back())
  - 통합 검색 (router-link to search)
-->
<template>
  <section
    class="flex flex-col items-center justify-center py-24 text-center"
    aria-labelledby="not-found-heading"
    data-testid="not-found-view"
  >
    <p class="text-6xl font-bold text-primary-600" aria-hidden="true">404</p>
    <h1 id="not-found-heading" class="mt-4 text-2xl font-bold text-content-DEFAULT">
      {{ t('error.notFound.title') }}
    </h1>
    <p class="mt-2 text-content-muted">{{ t('error.notFound.message') }}</p>

    <div class="mt-8 flex flex-wrap items-center justify-center gap-3">
      <router-link
        :to="{ name: 'home' }"
        class="rounded-md bg-primary-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
        data-testid="not-found-home"
      >
        {{ t('error.notFound.goHome') }}
      </router-link>
      <button
        type="button"
        class="rounded-md border border-gray-300 bg-white px-6 py-2.5 text-sm font-medium text-content-DEFAULT hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
        data-testid="not-found-back"
        @click="goBack"
      >
        {{ t('error.notFound.back') }}
      </button>
      <router-link
        :to="{ name: 'search' }"
        class="rounded-md border border-gray-300 bg-white px-6 py-2.5 text-sm font-medium text-content-DEFAULT hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
        data-testid="not-found-search"
      >
        {{ t('error.notFound.search') }}
      </router-link>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()

function goBack(): void {
  // 이전 페이지가 있으면 뒤로, 없으면 홈으로
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'home' })
  }
}
</script>
