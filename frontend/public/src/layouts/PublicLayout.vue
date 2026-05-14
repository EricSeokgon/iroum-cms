<!--
  SPEC-CMS-PUBLIC-001 §5.2 — PublicLayout (시민 사이트 공용 레이아웃)
  KWCAG 2.2 AA: skip nav, role="banner|navigation|main|contentinfo", h1~h6 계층
-->
<template>
  <div class="flex min-h-screen flex-col bg-surface-muted">
    <!-- Skip nav: KWCAG 2.4.1 -->
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded focus:bg-primary-600 focus:px-4 focus:py-2 focus:text-white"
    >
      {{ t('common.skipNav') }}
    </a>

    <PublicHeader />
    <PublicBreadcrumb />

    <main
      id="main-content"
      role="main"
      tabindex="-1"
      class="mx-auto w-full max-w-screen-xl flex-1 px-4 py-6"
    >
      <router-view />
    </main>

    <PublicFooter />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PublicHeader from '@/components/layout/PublicHeader.vue'
import PublicBreadcrumb from '@/components/layout/PublicBreadcrumb.vue'
import PublicFooter from '@/components/layout/PublicFooter.vue'

const { t } = useI18n()
</script>
