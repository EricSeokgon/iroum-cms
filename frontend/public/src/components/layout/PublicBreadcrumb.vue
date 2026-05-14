<!--
  SPEC-CMS-PUBLIC-001 §5.2 — PublicBreadcrumb
  KWCAG: nav aria-label="현재 위치", ol > li[aria-current="page"]
-->
<template>
  <nav
    v-if="items.length > 0"
    :aria-label="t('common.currentLocation')"
    class="mx-auto max-w-screen-xl px-4 py-2"
  >
    <ol class="flex items-center gap-2 text-sm text-content-muted">
      <li v-for="(item, idx) in items" :key="`${item.path}-${idx}`" class="flex items-center gap-2">
        <span v-if="idx > 0" aria-hidden="true">/</span>
        <span
          v-if="idx === items.length - 1"
          :aria-current="'page'"
          class="font-medium text-content-DEFAULT"
        >
          {{ t(item.label) }}
        </span>
        <router-link v-else :to="item.path" class="hover:text-primary-600">
          {{ t(item.label) }}
        </router-link>
      </li>
    </ol>
  </nav>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useBreadcrumbStore } from '@/stores/breadcrumbStore'
import { storeToRefs } from 'pinia'

const { t } = useI18n()
const breadcrumbStore = useBreadcrumbStore()
const { items } = storeToRefs(breadcrumbStore)
</script>
