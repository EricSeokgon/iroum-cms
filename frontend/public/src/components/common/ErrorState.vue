<!--
  SPEC-CMS-PUBLIC-001 T-006 — 에러 상태 컴포넌트
  사용 예) API 실패, 네트워크 오류 등
-->
<template>
  <div
    role="alert"
    class="flex flex-col items-center justify-center gap-3 py-12 text-center"
    data-testid="error-state"
  >
    <p class="text-base text-red-600">{{ message ?? t('common.errorOccurred') }}</p>
    <button
      v-if="showRetry"
      type="button"
      class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
      @click="$emit('retry')"
    >
      {{ t('common.retry') }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

withDefaults(
  defineProps<{
    message?: string
    showRetry?: boolean
  }>(),
  { showRetry: true },
)

defineEmits<{
  (e: 'retry'): void
}>()

const { t } = useI18n()
</script>
