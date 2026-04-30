<template>
  <!-- 시스템 대시보드 — SPEC-CMS-005 Bundle D REQ-SYS-001-D -->
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('system.dashboard.title') }}</h2>
      <div class="flex items-center gap-3">
        <el-switch
          v-model="autoRefresh"
          :active-text="t('system.dashboard.autoRefresh')"
          @change="onAutoRefreshChange"
        />
        <el-checkbox v-model="noCache">{{ t('system.dashboard.forceRefresh') }}</el-checkbox>
        <el-button @click="reload" :loading="store.loading">
          {{ t('system.dashboard.refresh') }}
        </el-button>
      </div>
    </div>

    <!-- KPI 카드 그리드 -->
    <div v-if="store.kpi" class="grid grid-cols-2 gap-4 md:grid-cols-5 mb-6">
      <KpiCard
        :label="t('system.dashboard.kpi.todayVisits')"
        :value="store.kpi.today_visits"
        :change-pct="store.kpi.visits_change_pct"
        color="normal"
      />
      <KpiCard
        :label="t('system.dashboard.kpi.todayUnique')"
        :value="store.kpi.today_unique"
        color="normal"
      />
      <KpiCard
        :label="t('system.dashboard.kpi.todayPageViews')"
        :value="store.kpi.today_page_views"
        :change-pct="store.kpi.page_views_change_pct"
        color="normal"
      />
      <KpiCard
        :label="t('system.dashboard.kpi.todaySignups')"
        :value="store.kpi.today_signups"
        color="normal"
      />
      <KpiCard
        :label="t('system.dashboard.kpi.errorRate')"
        :value="store.kpi.error_rate_24h"
        :color="store.kpi.error_rate_24h > 0.05 ? 'danger' : store.kpi.error_rate_24h > 0.02 ? 'warning' : 'normal'"
        :formatter="v => `${(Number(v) * 100).toFixed(2)}%`"
      />
      <KpiCard
        :label="t('system.dashboard.kpi.avgResponse')"
        :value="store.kpi.avg_response_ms_24h"
        :color="store.kpi.avg_response_ms_24h > 2000 ? 'danger' : store.kpi.avg_response_ms_24h > 800 ? 'warning' : 'normal'"
        :formatter="v => `${v}ms`"
      />
      <KpiCard
        :label="t('system.dashboard.kpi.lockedAccounts')"
        :value="store.kpi.locked_accounts"
        :color="store.kpi.locked_accounts > 0 ? 'warning' : 'normal'"
      />
      <KpiCard
        :label="t('system.dashboard.kpi.auditLog24h')"
        :value="store.kpi.audit_log_24h_count"
        color="normal"
      />
      <KpiCard
        :label="t('system.dashboard.kpi.auditCritical')"
        :value="store.kpi.audit_log_critical_24h_count"
        :color="store.kpi.audit_log_critical_24h_count > 0 ? 'danger' : 'normal'"
      />
      <KpiCard
        :label="t('system.dashboard.kpi.healthStatus')"
        :value="store.kpi.health_status"
        :color="store.kpi.health_status === 'HEALTHY' ? 'normal' : store.kpi.health_status === 'DEGRADED' ? 'warning' : 'danger'"
      />
    </div>

    <!-- 스켈레톤 (로딩) -->
    <div v-else-if="store.loading" class="grid grid-cols-2 gap-4 md:grid-cols-5 mb-6">
      <div v-for="i in 10" :key="i" class="h-24 rounded-lg border border-gray-200 bg-gray-100 animate-pulse" />
    </div>

    <!-- 오류 표시 -->
    <el-alert
      v-if="store.error"
      type="error"
      :title="store.error"
      :closable="false"
      class="mb-4"
    />

    <!-- 추이 차트 + 인기 페이지 -->
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <DashboardTrendChart
        :items="store.trends"
        v-model:days="trendDays"
        class="lg:col-span-1"
      />
      <DashboardTopPagesPanel
        :items="store.topPages"
        v-model:period="topPagesPeriod"
        class="lg:col-span-1"
      />
    </div>

    <!-- 마지막 갱신 시각 -->
    <p v-if="store.lastFetched" class="mt-4 text-right text-xs text-gray-400">
      {{ t('system.dashboard.lastFetched') }}: {{ store.lastFetched.toLocaleString('ko-KR') }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDashboardStore } from '@/stores/system'
import { dashboard } from '@/api/system'
import KpiCard from '@/components/system/KpiCard.vue'
import DashboardTrendChart from '@/components/system/DashboardTrendChart.vue'
import DashboardTopPagesPanel from '@/components/system/DashboardTopPagesPanel.vue'

const { t } = useI18n()
const store = useDashboardStore()

const noCache = ref(false)
const autoRefresh = ref(false)
const trendDays = ref<7 | 30 | 90>(30)
const topPagesPeriod = ref<'7d' | '30d'>('7d')
let autoTimer: ReturnType<typeof setInterval> | null = null

async function reload(): Promise<void> {
  await store.fetchAll(noCache.value)
}

// 추이 기간 변경 시 재조회
watch(trendDays, async (days) => {
  try {
    const res = await dashboard.trends(days)
    store.trends = res.data
  } catch { /* 무시 */ }
})

// 인기 페이지 기간 변경 시 재조회
watch(topPagesPeriod, async (period) => {
  try {
    const res = await dashboard.topPages(period)
    store.topPages = res.data
  } catch { /* 무시 */ }
})

function onAutoRefreshChange(val: boolean): void {
  if (val) {
    autoTimer = setInterval(() => reload(), 60_000)
  } else {
    if (autoTimer) clearInterval(autoTimer)
    autoTimer = null
  }
}

onMounted(() => reload())
onUnmounted(() => {
  if (autoTimer) clearInterval(autoTimer)
})
</script>
