<!--
  SPEC-CMS-PUBLIC-001 T-006 — 빈 결과 상태 컴포넌트
  사용 예) 검색 결과 없음, 게시글 없음 등
-->
<template>
  <div
    class="flex flex-col items-center justify-center gap-3 py-12 text-center text-content-muted"
    data-testid="empty-state"
    role="status"
  >
    <p class="text-base">{{ message ?? t('common.empty') }}</p>
    <slot name="action" />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  /** 표시할 메시지 — 기본값: common.empty */
  message?: string
}>()

const { t } = useI18n()
</script>
