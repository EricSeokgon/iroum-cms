<!--
  SPEC-CMS-PUBLIC-001 §6.15 — 점검 안내 페이지 (F-04 자동 리프레시 포함)
  - 5분 간격으로 maintenance 상태를 polling
  - maintenanceMode=false 가 되면 자동으로 홈으로 replace
-->
<template>
  <section class="flex min-h-screen items-center justify-center bg-surface-muted p-8">
    <div class="max-w-md rounded-lg bg-white p-8 text-center shadow" data-testid="maintenance-view">
      <h1 class="mb-4 text-2xl font-bold text-content-DEFAULT">
        {{ t('maintenance.title') }}
      </h1>
      <p class="mb-2 text-content-muted">{{ t('maintenance.message') }}</p>
      <p v-if="maintenance.maintenanceMessage" class="text-sm text-content-subtle">
        {{ maintenance.maintenanceMessage }}
      </p>
      <p v-if="maintenance.estimatedEndTime" class="mt-2 text-sm text-content-subtle">
        {{ t('maintenance.until') }}: {{ maintenance.estimatedEndTime }}
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useMaintenanceStore } from '@/stores/maintenanceStore'

const { t } = useI18n()
const router = useRouter()
const maintenance = useMaintenanceStore()

// F-04: 5분(300_000ms) 간격 자동 리프레시 — maintenanceMode=false 이면 홈으로 복귀
// @MX:NOTE: [AUTO] setInterval 누수 방지 — onBeforeUnmount 에서 clearInterval
const REFRESH_INTERVAL_MS = 300_000
let timerId: ReturnType<typeof setInterval> | null = null

async function pollAndRecover(): Promise<void> {
  await maintenance.checkMaintenance()
  if (!maintenance.isMaintenanceMode) {
    // 점검 해제 — 홈으로 replace (history stack 오염 방지)
    router.replace({ name: 'home' })
  }
}

onMounted(() => {
  // 마운트 시 즉시 한 번 polling — 이미 해제된 상태일 수 있음
  void pollAndRecover()
  timerId = setInterval(() => {
    void pollAndRecover()
  }, REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (timerId !== null) {
    clearInterval(timerId)
    timerId = null
  }
})
</script>
