<template>
  <section class="flex flex-col items-center justify-center py-24 text-center" aria-labelledby="not-found-heading" data-testid="not-found">
    <p class="text-6xl font-bold text-primary-600" aria-hidden="true">404</p>
    <h1 id="not-found-heading" class="mt-4 text-2xl font-bold text-content-DEFAULT">
      {{ t('error.notFound.title') }}
    </h1>
    <p class="mt-2 text-content-muted">
      {{ t('error.notFound.message') }}
    </p>
    <router-link
      to="/"
      class="mt-8 rounded-md bg-primary-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
    >
      {{ t('error.notFound.goHome') }}
    </router-link>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
