<!--
  공통 stub 뷰 — Phase 0 foundation에서 라우트는 정의하지만 페이지 본문은 후속 phase에서 구현.
  Phase 1~4에서 각 라우트는 본 stub을 자체 View로 교체한다.
-->
<template>
  <section class="py-12 text-center">
    <h1 class="mb-4 text-2xl font-semibold text-content-DEFAULT">
      {{ pageTitle }}
    </h1>
    <p class="text-content-muted">{{ t('common.comingSoon') }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
const pageTitle = computed(() => {
  const meta = route.meta as { title?: string } | undefined
  return meta?.title ? t(meta.title) : t('app.title')
})
</script>
