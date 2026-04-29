<template>
  <div>
    <h2 class="mb-6 text-xl font-semibold text-gray-800">{{ t('dashboard.title') }}</h2>

    <!-- 요약 카드 4개 -->
    <el-row :gutter="20" class="mb-8">
      <!-- 총 사용자 — placeholder -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="dashboard-card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.userCount') }}</p>
              <p class="mt-1 text-2xl font-bold text-gray-800">—</p>
              <p class="mt-1 text-xs text-gray-400">{{ t('dashboard.comingSoon') }}</p>
            </div>
            <el-icon :size="36" class="text-blue-400"><i-ep-user /></el-icon>
          </div>
        </el-card>
      </el-col>

      <!-- 오늘 로그인 — placeholder -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="dashboard-card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.todayLogin') }}</p>
              <p class="mt-1 text-2xl font-bold text-gray-800">—</p>
              <p class="mt-1 text-xs text-gray-400">{{ t('dashboard.comingSoon') }}</p>
            </div>
            <el-icon :size="36" class="text-green-400"><i-ep-check /></el-icon>
          </div>
        </el-card>
      </el-col>

      <!-- 시스템 상태 — 실제 API 호출 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="dashboard-card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.systemStatus') }}</p>
              <p
                class="mt-1 text-2xl font-bold"
                :class="healthStatusClass"
              >
                {{ healthLabel }}
              </p>
              <p class="mt-1 text-xs text-gray-400">{{ health.data.value?.version ?? '...' }}</p>
            </div>
            <el-icon :size="36" :class="healthStatusClass">
              <i-ep-monitor />
            </el-icon>
          </div>
        </el-card>
      </el-col>

      <!-- 최근 활동 — placeholder -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="dashboard-card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.recentActivity') }}</p>
              <p class="mt-1 text-2xl font-bold text-gray-800">—</p>
              <p class="mt-1 text-xs text-gray-400">{{ t('dashboard.comingSoon') }}</p>
            </div>
            <el-icon :size="36" class="text-purple-400"><i-ep-bell /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 서버 상태 오류 시 표시 -->
    <el-alert
      v-if="health.error.value"
      :title="t('health.error')"
      type="warning"
      show-icon
      :closable="false"
      role="alert"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useApi } from '@/composables/useApi'
import type { HealthResponse } from '@iroum/shared/types/api'

const { t } = useI18n()

const health = useApi<HealthResponse>('/health')

onMounted(() => health.execute())

const healthLabel = computed(() => {
  if (health.loading.value) return '...'
  if (health.error.value) return t('dashboard.statusError')
  return health.data.value?.status ?? '—'
})

const healthStatusClass = computed(() => {
  if (health.error.value) return 'text-red-500'
  const status = health.data.value?.status?.toLowerCase()
  if (status === 'up' || status === 'ok') return 'text-green-500'
  return 'text-yellow-500'
})
</script>

<style scoped>
.dashboard-card {
  @apply transition-shadow hover:shadow-md;
}
</style>
