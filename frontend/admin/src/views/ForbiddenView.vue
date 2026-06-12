<template>
  <!-- SPEC-CMS-RBAC-001 REQ-RBAC-007 — 403 접근 거부 화면 -->
  <section
    class="flex flex-col items-center justify-center py-24 text-center"
    aria-labelledby="forbidden-heading"
    data-testid="forbidden"
  >
    <p class="text-6xl font-bold text-red-600" aria-hidden="true">403</p>
    <h1 id="forbidden-heading" class="mt-4 text-2xl font-bold text-content-DEFAULT">
      {{ t('error.forbidden.title') }}
    </h1>
    <p class="mt-2 text-content-muted">
      {{ t('error.forbidden.message') }}
    </p>
    <div class="mt-8 flex gap-3">
      <button
        type="button"
        class="rounded-md border border-gray-300 px-6 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
        data-testid="forbidden-back"
        @click="goBack"
      >
        {{ t('error.forbidden.goBack') }}
      </button>
      <router-link
        to="/"
        class="rounded-md bg-primary-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
        data-testid="forbidden-home"
      >
        {{ t('error.forbidden.goHome') }}
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
  // 이전 페이지 없으면 대시보드로
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>
