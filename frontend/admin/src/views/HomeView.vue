<template>
  <section class="flex flex-col items-center justify-center py-16 text-center">
    <h1 class="mb-4 text-3xl font-bold text-content-DEFAULT">
      {{ t('home.title') }}
    </h1>
    <p class="mb-8 text-lg text-content-muted">
      {{ t('home.subtitle') }}
    </p>
    <router-link
      to="/health"
      class="rounded-md bg-primary-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600"
    >
      {{ t('home.goHealth') }}
    </router-link>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
