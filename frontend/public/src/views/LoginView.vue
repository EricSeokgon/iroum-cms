<!-- SPEC-CMS-PUBLIC-001 §6.16 — 로그인 페이지 (Phase 0 minimal stub) -->
<template>
  <section class="flex min-h-screen items-center justify-center bg-surface-muted p-8">
    <div class="w-full max-w-md rounded-lg bg-white p-8 shadow">
      <h1 class="mb-6 text-center text-2xl font-bold text-content-DEFAULT">
        {{ t('login.title') }}
      </h1>
      <p class="text-center text-content-muted">{{ t('common.comingSoon') }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
