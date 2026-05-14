<!--
  SPEC-CMS-PUBLIC-001 T-006 — 로딩 상태 컴포넌트
  KWCAG: aria-live="polite", aria-busy="true" — 스크린리더 안내
-->
<template>
  <div
    role="status"
    aria-live="polite"
    aria-busy="true"
    class="flex flex-col items-center justify-center py-12 text-content-muted"
    data-testid="loading-state"
  >
    <el-skeleton :rows="rows" animated />
    <span class="sr-only">{{ t('common.loading') }}</span>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ElSkeleton } from 'element-plus'

withDefaults(
  defineProps<{
    /** el-skeleton rows 개수 */
    rows?: number
  }>(),
  { rows: 4 },
)

const { t } = useI18n()
</script>
