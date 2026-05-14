<!--
  SPEC-CMS-PUBLIC-001 T-010 — 403 페이지 (F-02)
  - h1: '권한이 없습니다'
  - 홈으로 이동 버튼
-->
<template>
  <section
    class="flex flex-col items-center justify-center py-24 text-center"
    aria-labelledby="forbidden-heading"
    data-testid="forbidden-view"
  >
    <p class="text-6xl font-bold text-primary-600" aria-hidden="true">403</p>
    <h1 id="forbidden-heading" class="mt-4 text-2xl font-bold text-content-DEFAULT">
      {{ t('error.forbidden.title') }}
    </h1>
    <p class="mt-2 text-content-muted">{{ t('error.forbidden.message') }}</p>

    <router-link
      :to="{ name: 'home' }"
      class="mt-8 rounded-md bg-primary-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
      data-testid="forbidden-home"
    >
      {{ t('error.forbidden.goHome') }}
    </router-link>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
