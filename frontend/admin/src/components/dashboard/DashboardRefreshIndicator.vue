<script setup lang="ts">
// SPEC-CMS-DASHBOARD-REFRESH-001 — 대시보드 자동 새로고침 인디케이터
// 다음 새로고침까지 남은 초 표시 + "지금 새로고침" 수동 트리거
// @MX:NOTE: [AUTO] intervalSeconds 가 null 이면 전체 비표시 (자동 새로고침 OFF)
// @MX:SPEC: SPEC-CMS-DASHBOARD-REFRESH-001 REQ-REFRESH-002
import { ElButton } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

interface Props {
  /** 다음 새로고침까지 남은 초 */
  secondsRemaining: number
  /** 새로고침 주기(초). null 이면 자동 새로고침 꺼짐 → 비표시 */
  intervalSeconds: number | null
}

defineProps<Props>()

const emit = defineEmits<{
  (e: 'refresh'): void
}>()
</script>

<template>
  <div
    v-if="intervalSeconds !== null"
    class="dashboard-refresh-indicator"
    data-testid="refresh-indicator"
  >
    <span class="text-xs text-gray-500" aria-live="polite">
      다음 새로고침: {{ secondsRemaining }}초
    </span>
    <ElButton
      :icon="Refresh"
      size="small"
      text
      type="primary"
      data-testid="refresh-now"
      aria-label="지금 새로고침"
      @click="emit('refresh')"
    >
      지금 새로고침
    </ElButton>
  </div>
</template>

<style scoped>
.dashboard-refresh-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
