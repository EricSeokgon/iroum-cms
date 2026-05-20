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
        <el-button @click="reload" :loading="store.loading || cmsLoading">
          {{ t('system.dashboard.refresh') }}
        </el-button>
      </div>
    </div>

    <!-- 시스템 KPI 카드 그리드 -->
    <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-400">
      {{ t('system.dashboard.section.system') }}
    </p>
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
        :color="store.kpi.health_status === 'HEALTHY' ? 'success' : store.kpi.health_status === 'DEGRADED' ? 'warning' : 'danger'"
      />
    </div>

    <!-- 스켈레톤 (시스템 KPI 로딩) -->
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

    <!-- CMS 콘텐츠 현황 -->
    <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-400">
      {{ t('system.dashboard.section.cms') }}
    </p>
    <div class="grid grid-cols-2 gap-4 md:grid-cols-6 mb-6">
      <template v-if="cmsLoading">
        <div v-for="i in 6" :key="i" class="h-24 rounded-lg border border-gray-200 bg-gray-100 animate-pulse" />
      </template>
      <template v-else>
        <KpiCard
          :label="t('system.dashboard.cms.users')"
          :value="cmsStats.users"
          color="normal"
        />
        <KpiCard
          :label="t('system.dashboard.cms.media')"
          :value="cmsStats.media"
          color="normal"
        />
        <KpiCard
          :label="t('system.dashboard.cms.boards')"
          :value="cmsStats.boards"
          color="normal"
        />
        <KpiCard
          :label="t('system.dashboard.cms.qnaPending')"
          :value="cmsStats.qnaPending"
          :color="cmsStats.qnaPending > 10 ? 'danger' : cmsStats.qnaPending > 0 ? 'warning' : 'normal'"
        />
        <KpiCard
          :label="t('system.dashboard.cms.surveys')"
          :value="cmsStats.surveys"
          color="normal"
        />
        <KpiCard
          :label="t('system.dashboard.cms.faqs')"
          :value="cmsStats.faqs"
          color="normal"
        />
      </template>
    </div>

    <!-- 추이 차트 + 인기 페이지 -->
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2 mb-6">
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

    <!-- 미답변 QnA -->
    <div v-if="pendingQnas.length > 0" class="rounded-lg border border-yellow-200 bg-white shadow-sm mb-6">
      <div class="flex items-center justify-between px-4 py-3 border-b border-gray-100">
        <p class="text-sm font-semibold text-gray-700">
          {{ t('system.dashboard.cms.pendingQnaTitle') }}
          <el-tag type="warning" size="small" class="ml-2">{{ cmsStats.qnaPending }}</el-tag>
        </p>
        <router-link to="/qna" class="text-xs text-blue-500 hover:underline">
          {{ t('common.viewAll') }} →
        </router-link>
      </div>
      <el-table :data="pendingQnas" size="small" class="w-full">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" :label="t('qna.field.title')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="t('common.createdAt')" width="160">
          <template #default="{ row }">
            {{ new Date(row.createdAt).toLocaleString('ko-KR') }}
          </template>
        </el-table-column>
        <el-table-column :label="t('common.action')" width="80" align="center">
          <template #default="{ row }">
            <router-link :to="`/qna/${row.id}`" class="text-xs text-blue-500 hover:underline">
              {{ t('common.view') }}
            </router-link>
          </template>
        </el-table-column>
      </el-table>
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
import { usersApi } from '@/api/users'
import { mediaApi } from '@/api/media'
import { listQnas, type QnaSummary } from '@/api/qna'
import { listSurveys } from '@/api/survey'
import { listFaqs } from '@/api/faq'
import { boardApi } from '@/api/board'
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

const cmsLoading = ref(false)
const cmsStats = ref({ users: 0, media: 0, boards: 0, qnaPending: 0, surveys: 0, faqs: 0 })
const pendingQnas = ref<QnaSummary[]>([])

async function fetchCmsStats(): Promise<void> {
  cmsLoading.value = true
  const [users, media, boards, qna, surveys, faqs, recentQna] = await Promise.allSettled([
    usersApi.list({ size: 1, page: 0 }),
    mediaApi.list({ size: 1, page: 0 }),
    boardApi.listMasters(),
    listQnas({ status: 'PENDING', size: 1, page: 0 }),
    listSurveys({ size: 1, page: 0 }),
    listFaqs({ size: 1, page: 0 }),
    listQnas({ status: 'PENDING', size: 5, page: 0 }),
  ])
  cmsStats.value = {
    users: users.status === 'fulfilled' ? users.value.data.totalElements : 0,
    media: media.status === 'fulfilled' ? media.value.data.totalElements : 0,
    boards: boards.status === 'fulfilled' ? boards.value.data.length : 0,
    qnaPending: qna.status === 'fulfilled' ? qna.value.data.totalElements : 0,
    surveys: surveys.status === 'fulfilled' ? surveys.value.data.totalElements : 0,
    faqs: faqs.status === 'fulfilled' ? faqs.value.data.totalElements : 0,
  }
  pendingQnas.value = recentQna.status === 'fulfilled' ? recentQna.value.data.content : []
  cmsLoading.value = false
}

async function reload(): Promise<void> {
  await Promise.all([store.fetchAll(noCache.value), fetchCmsStats()])
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
