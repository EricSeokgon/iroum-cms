<template>
  <!-- 검색 통계 — SPEC-CMS-010 -->
  <div>
    <div class="mb-4 flex items-center justify-between flex-wrap gap-3">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('searchAnalytics.title') }}</h2>
      <div class="flex flex-wrap gap-2">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          :range-separator="t('common.to')"
          :start-placeholder="t('search.dateFrom')"
          :end-placeholder="t('search.dateTo')"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          size="small"
          :aria-label="t('search.dateRange')"
        />
        <el-input-number
          v-model="topLimit"
          :min="5"
          :max="50"
          :step="5"
          size="small"
          :aria-label="t('searchAnalytics.topLimit')"
        />
        <el-button type="primary" size="small" @click="loadStats">
          {{ t('common.search') }}
        </el-button>
        <el-button :icon="Refresh" size="small" @click="loadStats">
          {{ t('common.refresh') }}
        </el-button>
      </div>
    </div>

    <!-- KPI 카드 -->
    <el-row :gutter="16" class="mb-4">
      <el-col :xs="24" :sm="12" :md="6" class="mb-3">
        <el-card shadow="never" class="kpi-card">
          <p class="text-xs text-gray-500">{{ t('searchAnalytics.kpi.totalSearches') }}</p>
          <p class="mt-1 text-2xl font-semibold text-gray-800">
            {{ formatNumber(store.stats?.totalSearches) }}
          </p>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6" class="mb-3">
        <el-card shadow="never" class="kpi-card">
          <p class="text-xs text-gray-500">{{ t('searchAnalytics.kpi.uniqueQueries') }}</p>
          <p class="mt-1 text-2xl font-semibold text-gray-800">
            {{ formatNumber(store.stats?.uniqueQueries) }}
          </p>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6" class="mb-3">
        <el-card shadow="never" class="kpi-card">
          <p class="text-xs text-gray-500">{{ t('searchAnalytics.kpi.avgCtr') }}</p>
          <p class="mt-1 text-2xl font-semibold text-blue-600">
            {{ avgCtrLabel }}
          </p>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6" class="mb-3">
        <el-card shadow="never" class="kpi-card">
          <p class="text-xs text-gray-500">{{ t('searchAnalytics.kpi.zeroResultRate') }}</p>
          <p class="mt-1 text-2xl font-semibold text-orange-600">
            {{ zeroResultRateLabel }}
          </p>
        </el-card>
      </el-col>
    </el-row>

    <!-- 인기 검색어 차트 -->
    <el-card class="mb-4" shadow="never" v-loading="store.statsLoading">
      <template #header>
        <span class="text-sm font-semibold">{{ t('searchAnalytics.topQueriesChart') }}</span>
      </template>
      <div v-if="!hasData" class="py-12 text-center text-gray-400 text-sm">
        {{ t('searchAnalytics.noData') }}
      </div>
      <VChart
        v-else
        :option="topQueriesOption"
        autoresize
        style="height: 320px"
      />
    </el-card>

    <!-- 클릭률 도넛 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12" class="mb-4">
        <el-card shadow="never" v-loading="store.statsLoading">
          <template #header>
            <span class="text-sm font-semibold">{{ t('searchAnalytics.clickRatioChart') }}</span>
          </template>
          <div v-if="!hasData" class="py-12 text-center text-gray-400 text-sm">
            {{ t('searchAnalytics.noData') }}
          </div>
          <VChart
            v-else
            :option="clickRatioOption"
            autoresize
            style="height: 280px"
          />
        </el-card>
      </el-col>

      <!-- 인기 검색어 표 -->
      <el-col :xs="24" :md="12" class="mb-4">
        <el-card shadow="never" v-loading="store.statsLoading">
          <template #header>
            <span class="text-sm font-semibold">{{ t('searchAnalytics.topQueriesTable') }}</span>
          </template>
          <el-table
            :data="store.stats?.topQueries ?? []"
            stripe
            :empty-text="t('searchAnalytics.noData')"
            class="w-full"
          >
            <el-table-column type="index" label="#" width="60" align="center" />
            <el-table-column prop="query" :label="t('searchAnalytics.col.query')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="searchCount" :label="t('searchAnalytics.col.searchCount')" width="120" align="right">
              <template #default="{ row }">
                {{ formatNumber(row.searchCount) }}
              </template>
            </el-table-column>
            <el-table-column prop="clickCount" :label="t('searchAnalytics.col.clickCount')" width="120" align="right">
              <template #default="{ row }">
                {{ formatNumber(row.clickCount) }}
              </template>
            </el-table-column>
            <el-table-column prop="ctr" :label="t('searchAnalytics.col.ctr')" width="110" align="right">
              <template #default="{ row }">
                {{ formatPercent(row.ctr) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
} from 'echarts/components'
import { useSearchStore } from '@/stores/searchStore'

// @MX:NOTE: [AUTO] vue-echarts 컴포넌트 등록 — BarChart + PieChart
use([CanvasRenderer, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const { t } = useI18n()
const store = useSearchStore()

// ── 필터 ────────────────────────────────────────────────────────────────────
const dateRange = ref<[string, string] | null>(initialDateRange())
const topLimit = ref(10)

function initialDateRange(): [string, string] {
  const now = new Date()
  const past = new Date(now.getTime() - 29 * 24 * 60 * 60 * 1000)
  const fmt = (d: Date): string => d.toISOString().slice(0, 10)
  return [fmt(past), fmt(now)]
}

// ── 데이터 로드 ─────────────────────────────────────────────────────────────
async function loadStats(): Promise<void> {
  if (!dateRange.value) {
    ElMessage.warning(t('searchAnalytics.dateRequired'))
    return
  }
  try {
    await store.fetchStats(dateRange.value[0], dateRange.value[1], topLimit.value)
  } catch {
    ElMessage.error(t('common.loadError'))
  }
}

// ── 파생 상태 ───────────────────────────────────────────────────────────────
const hasData = computed(
  () => (store.stats?.topQueries?.length ?? 0) > 0,
)

const avgCtr = computed<number>(() => {
  const queries = store.stats?.topQueries ?? []
  if (queries.length === 0) return 0
  const sum = queries.reduce((acc, q) => acc + (q.ctr ?? 0), 0)
  return sum / queries.length
})

const avgCtrLabel = computed(() => formatPercent(avgCtr.value))

// 0건 검색어 비율 — clickCount === 0 인 쿼리 비율로 근사
const zeroResultRate = computed<number>(() => {
  const queries = store.stats?.topQueries ?? []
  if (queries.length === 0) return 0
  const zero = queries.filter((q) => q.clickCount === 0).length
  return zero / queries.length
})

const zeroResultRateLabel = computed(() => formatPercent(zeroResultRate.value))

// ── 차트 옵션 ───────────────────────────────────────────────────────────────
// vue-echarts 의 ECBasicOption 와 호환되도록 인덱스 시그니처 포함
type ChartOption = Record<string, unknown>

const topQueriesOption = computed<ChartOption>(() => {
  const queries = (store.stats?.topQueries ?? []).slice().reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 100, right: 32, top: 16, bottom: 24 },
    xAxis: { type: 'value' },
    yAxis: {
      type: 'category',
      data: queries.map((q) => q.query),
      axisLabel: { fontSize: 11 },
    },
    series: [
      {
        name: t('searchAnalytics.col.searchCount'),
        type: 'bar',
        data: queries.map((q) => q.searchCount),
        itemStyle: { color: '#3b82f6' },
        barMaxWidth: 18,
      },
    ],
  }
})

const clickRatioOption = computed<ChartOption>(() => {
  const queries = store.stats?.topQueries ?? []
  const totalSearch = queries.reduce((acc, q) => acc + q.searchCount, 0)
  const totalClick = queries.reduce((acc, q) => acc + q.clickCount, 0)
  const noClick = Math.max(0, totalSearch - totalClick)
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '45%'],
        data: [
          { name: t('searchAnalytics.clicked'), value: totalClick, itemStyle: { color: '#3b82f6' } },
          { name: t('searchAnalytics.notClicked'), value: noClick, itemStyle: { color: '#e5e7eb' } },
        ],
        label: { show: true, formatter: '{b}\n{d}%' },
      },
    ],
  }
})

// ── 헬퍼 ────────────────────────────────────────────────────────────────────
function formatNumber(v: number | undefined | null): string {
  if (v === undefined || v === null) return '-'
  return v.toLocaleString('ko-KR')
}

function formatPercent(v: number | undefined | null): string {
  if (v === undefined || v === null) return '-'
  return `${(v * 100).toFixed(1)}%`
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.kpi-card :deep(.el-card__body) {
  padding: 16px 18px;
}
</style>
