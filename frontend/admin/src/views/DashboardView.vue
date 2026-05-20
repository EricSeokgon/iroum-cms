<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('dashboard.title') }}</h2>
      <div class="flex items-center gap-3">
        <span class="text-xs text-gray-400">{{ t('dashboard.lastUpdated') }}: {{ lastUpdatedLabel }}</span>
        <el-button size="small" :loading="loading" @click="refresh">
          <el-icon class="mr-1"><component :is="Refresh" /></el-icon>
          {{ t('common.refresh') }}
        </el-button>
      </div>
    </div>

    <!-- 서버 오류 알림 -->
    <el-alert
      v-if="error"
      :title="t('dashboard.kpiError')"
      type="warning"
      show-icon
      :closable="false"
      class="mb-6"
      role="alert"
    />

    <!-- 오늘 현황 KPI -->
    <p class="mb-3 text-xs font-semibold uppercase tracking-wide text-gray-400">{{ t('dashboard.section.today') }}</p>
    <el-row :gutter="16" class="mb-6">
      <!-- 오늘 방문 -->
      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="dashboard-card h-full">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.todayVisits') }}</p>
              <p class="mt-1 text-2xl font-bold text-gray-800">
                {{ loading ? '...' : (kpi?.today_visits?.toLocaleString() ?? '—') }}
              </p>
              <p class="mt-1 text-xs" :class="changeClass(kpi?.visits_change_pct)">
                {{ changeTxt(kpi?.visits_change_pct) }}
              </p>
            </div>
            <el-icon :size="32" class="text-blue-400 mt-1"><component :is="DataAnalysis" /></el-icon>
          </div>
        </el-card>
      </el-col>

      <!-- 오늘 순방문자 -->
      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="dashboard-card h-full">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.todayUnique') }}</p>
              <p class="mt-1 text-2xl font-bold text-gray-800">
                {{ loading ? '...' : (kpi?.today_unique?.toLocaleString() ?? '—') }}
              </p>
              <p class="mt-1 text-xs text-gray-400">{{ t('dashboard.uniqueVisitors') }}</p>
            </div>
            <el-icon :size="32" class="text-indigo-400 mt-1"><component :is="User" /></el-icon>
          </div>
        </el-card>
      </el-col>

      <!-- 오늘 페이지뷰 -->
      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="dashboard-card h-full">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.todayPageViews') }}</p>
              <p class="mt-1 text-2xl font-bold text-gray-800">
                {{ loading ? '...' : (kpi?.today_page_views?.toLocaleString() ?? '—') }}
              </p>
              <p class="mt-1 text-xs" :class="changeClass(kpi?.page_views_change_pct)">
                {{ changeTxt(kpi?.page_views_change_pct) }}
              </p>
            </div>
            <el-icon :size="32" class="text-sky-400 mt-1"><component :is="Document" /></el-icon>
          </div>
        </el-card>
      </el-col>

      <!-- 오늘 신규 가입 -->
      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="dashboard-card h-full">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.todaySignups') }}</p>
              <p class="mt-1 text-2xl font-bold text-gray-800">
                {{ loading ? '...' : (kpi?.today_signups?.toLocaleString() ?? '—') }}
              </p>
              <p class="mt-1 text-xs text-gray-400">{{ t('dashboard.newMembers') }}</p>
            </div>
            <el-icon :size="32" class="text-green-400 mt-1"><component :is="Avatar" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 시스템 상태 KPI -->
    <p class="mb-3 text-xs font-semibold uppercase tracking-wide text-gray-400">{{ t('dashboard.section.system') }}</p>
    <el-row :gutter="16" class="mb-8">
      <!-- 시스템 상태 -->
      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="dashboard-card h-full">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.systemStatus') }}</p>
              <p class="mt-1 text-2xl font-bold" :class="healthClass">
                {{ healthLabel }}
              </p>
              <p class="mt-1 text-xs text-gray-400">{{ t('dashboard.overallHealth') }}</p>
            </div>
            <el-icon :size="32" :class="['mt-1', healthClass]"><component :is="Monitor" /></el-icon>
          </div>
        </el-card>
      </el-col>

      <!-- 24h 오류율 -->
      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="dashboard-card h-full">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.errorRate24h') }}</p>
              <p class="mt-1 text-2xl font-bold" :class="errorRateClass">
                {{ loading ? '...' : (kpi?.error_rate_24h != null ? (kpi.error_rate_24h * 100).toFixed(2) + '%' : '—') }}
              </p>
              <p class="mt-1 text-xs text-gray-400">{{ t('dashboard.last24h') }}</p>
            </div>
            <el-icon :size="32" :class="['mt-1', errorRateClass]"><component :is="Warning" /></el-icon>
          </div>
        </el-card>
      </el-col>

      <!-- 평균 응답 시간 -->
      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="dashboard-card h-full">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.avgResponseMs') }}</p>
              <p class="mt-1 text-2xl font-bold" :class="responseTimeClass">
                {{ loading ? '...' : (kpi?.avg_response_ms_24h != null ? kpi.avg_response_ms_24h.toFixed(0) + 'ms' : '—') }}
              </p>
              <p class="mt-1 text-xs text-gray-400">{{ t('dashboard.last24h') }}</p>
            </div>
            <el-icon :size="32" :class="['mt-1', responseTimeClass]"><component :is="Timer" /></el-icon>
          </div>
        </el-card>
      </el-col>

      <!-- 잠긴 계정 -->
      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="dashboard-card h-full">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm text-gray-500">{{ t('dashboard.lockedAccounts') }}</p>
              <p class="mt-1 text-2xl font-bold" :class="lockedClass">
                {{ loading ? '...' : (kpi?.locked_accounts?.toLocaleString() ?? '—') }}
              </p>
              <p class="mt-1 text-xs text-gray-400">{{ t('dashboard.requiresAttention') }}</p>
            </div>
            <el-icon :size="32" :class="['mt-1', lockedClass]"><component :is="Lock" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 감사 로그 현황 + 빠른 메뉴 -->
    <el-row :gutter="16">
      <!-- 24h 감사 로그 요약 -->
      <el-col :xs="24" :lg="12" class="mb-4">
        <el-card shadow="never" class="h-full">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-semibold text-gray-700">{{ t('dashboard.auditSummary') }}</span>
              <router-link :to="{ name: 'system-audit-logs' }" class="text-xs text-blue-500 hover:underline">
                {{ t('common.viewAll') }}
              </router-link>
            </div>
          </template>

          <div class="space-y-3">
            <div class="flex items-center justify-between rounded-lg bg-gray-50 px-4 py-3">
              <span class="text-sm text-gray-600">{{ t('dashboard.auditTotal24h') }}</span>
              <span class="text-lg font-bold text-gray-800">
                {{ loading ? '...' : (kpi?.audit_log_24h_count?.toLocaleString() ?? '—') }}
              </span>
            </div>
            <div class="flex items-center justify-between rounded-lg px-4 py-3"
              :class="hasCritical ? 'bg-red-50' : 'bg-gray-50'"
            >
              <span class="text-sm" :class="hasCritical ? 'text-red-600 font-medium' : 'text-gray-600'">
                {{ t('dashboard.auditCritical24h') }}
              </span>
              <span class="text-lg font-bold" :class="hasCritical ? 'text-red-600' : 'text-gray-800'">
                {{ loading ? '...' : (kpi?.audit_log_critical_24h_count?.toLocaleString() ?? '—') }}
              </span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 빠른 메뉴 -->
      <el-col :xs="24" :lg="12" class="mb-4">
        <el-card shadow="never" class="h-full">
          <template #header>
            <span class="font-semibold text-gray-700">{{ t('dashboard.quickLinks') }}</span>
          </template>

          <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
            <router-link
              v-for="link in quickLinks"
              :key="link.name"
              :to="{ name: link.name }"
              class="flex flex-col items-center gap-1 rounded-lg border border-gray-100 p-3 text-center hover:border-blue-200 hover:bg-blue-50 transition-colors"
            >
              <el-icon :size="20" class="text-gray-500"><component :is="link.icon" /></el-icon>
              <span class="text-xs text-gray-600">{{ link.label }}</span>
            </router-link>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  DataAnalysis,
  Refresh,
  User,
  Document,
  Avatar,
  Monitor,
  Warning,
  Timer,
  Lock,
  Grid,
  Files,
  Search,
  Setting,
  DataLine,
} from '@element-plus/icons-vue'
import { dashboard as systemDashboard } from '@/api/system'
import type { DashboardKpiResponse } from '@/api/system'

const { t } = useI18n()

const kpi = ref<DashboardKpiResponse | null>(null)
const loading = ref(false)
const error = ref(false)
const lastUpdated = ref<Date | null>(null)

const lastUpdatedLabel = computed(() => {
  if (!lastUpdated.value) return '—'
  return lastUpdated.value.toLocaleTimeString('ko-KR')
})

async function refresh(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    const res = await systemDashboard.kpi()
    kpi.value = res.data
    lastUpdated.value = new Date()
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

const healthLabel = computed(() => {
  if (loading.value) return '...'
  switch (kpi.value?.health_status) {
    case 'HEALTHY': return t('dashboard.health.healthy')
    case 'DEGRADED': return t('dashboard.health.degraded')
    case 'DOWN': return t('dashboard.health.down')
    default: return '—'
  }
})

const healthClass = computed(() => {
  switch (kpi.value?.health_status) {
    case 'HEALTHY': return 'text-green-500'
    case 'DEGRADED': return 'text-yellow-500'
    case 'DOWN': return 'text-red-500'
    default: return 'text-gray-400'
  }
})

const errorRateClass = computed(() => {
  const rate = kpi.value?.error_rate_24h ?? 0
  if (rate >= 0.05) return 'text-red-500'
  if (rate >= 0.01) return 'text-yellow-500'
  return 'text-green-500'
})

const responseTimeClass = computed(() => {
  const ms = kpi.value?.avg_response_ms_24h ?? 0
  if (ms >= 1000) return 'text-red-500'
  if (ms >= 500) return 'text-yellow-500'
  return 'text-green-500'
})

const lockedClass = computed(() => {
  const count = kpi.value?.locked_accounts ?? 0
  return count > 0 ? 'text-orange-500' : 'text-gray-500'
})

const hasCritical = computed(() => (kpi.value?.audit_log_critical_24h_count ?? 0) > 0)

function changeClass(pct?: number): string {
  if (pct == null) return 'text-gray-400'
  if (pct > 0) return 'text-green-500'
  if (pct < 0) return 'text-red-500'
  return 'text-gray-400'
}

function changeTxt(pct?: number): string {
  if (pct == null) return t('dashboard.noChange')
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(1)}% ${t('dashboard.vsPrevDay')}`
}

const quickLinks = computed(() => [
  { name: 'board-masters', label: t('nav.board'), icon: Grid },
  { name: 'user-list', label: t('nav.users'), icon: User },
  { name: 'media-library', label: t('nav.media'), icon: Files },
  { name: 'search', label: t('nav.search'), icon: Search },
  { name: 'system-audit-logs', label: t('nav.auditLog'), icon: DataLine },
  { name: 'system-settings', label: t('nav.systemSettings'), icon: Setting },
])

onMounted(() => {
  refresh()
})
</script>

<style scoped>
.dashboard-card {
  @apply transition-shadow hover:shadow-md;
}
</style>
