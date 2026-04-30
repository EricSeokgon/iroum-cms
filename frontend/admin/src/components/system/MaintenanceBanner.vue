<template>
  <!-- 활성 점검 모드 알림 배너 — SPEC-CMS-005 REQ-SYS-006-D -->
  <div
    v-if="activeMaintenance"
    class="flex items-center justify-between bg-yellow-50 border-b border-yellow-300 px-6 py-2"
    role="alert"
    aria-live="polite"
  >
    <div class="flex items-center gap-2 text-sm text-yellow-800">
      <el-icon class="text-yellow-600"><i-ep-warning /></el-icon>
      <span class="font-medium">{{ t('system.maintenance.banner.active') }}</span>
      <span>{{ activeMaintenance.message_ko }}</span>
      <span v-if="countdown" class="ml-2 text-yellow-600 font-mono">
        {{ t('system.maintenance.banner.endsIn') }}: {{ countdown }}
      </span>
    </div>
    <span class="text-xs text-yellow-600">
      {{ t('system.maintenance.banner.until') }}: {{ formatDate(activeMaintenance.end_at) }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { maintenance } from '@/api/system'
import type { MaintenanceResponse } from '@/api/system'

const { t } = useI18n()

const activeMaintenance = ref<MaintenanceResponse | null>(null)
const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | null = null

// 카운트다운 계산
const countdown = computed(() => {
  if (!activeMaintenance.value) return null
  const diff = new Date(activeMaintenance.value.end_at).getTime() - now.value.getTime()
  if (diff <= 0) return null
  const h = Math.floor(diff / 3_600_000)
  const m = Math.floor((diff % 3_600_000) / 60_000)
  const s = Math.floor((diff % 60_000) / 1_000)
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

async function fetchActive(): Promise<void> {
  try {
    const res = await maintenance.list()
    const found = res.data.find(m => m.status === 'ACTIVE') ?? null
    activeMaintenance.value = found
  } catch {
    // 조회 실패 시 배너 미표시
    activeMaintenance.value = null
  }
}

onMounted(() => {
  fetchActive()
  // 30초마다 상태 갱신
  timer = setInterval(() => {
    now.value = new Date()
    fetchActive()
  }, 30_000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
