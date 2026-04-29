<template>
  <section class="flex flex-col items-center justify-center py-16 text-center">
    <h1 class="mb-4 text-3xl font-bold text-content-DEFAULT">
      {{ t('home.title') }}
    </h1>
    <p class="text-lg text-content-muted">
      {{ t('home.subtitle') }}
    </p>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
