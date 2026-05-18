<template>
  <!-- 거버넌스 통계 대시보드 — SPEC-CMS-009 REQ-GOV-010~014 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">거버넌스 통계</h2>
      <div class="flex gap-2">
        <el-button :icon="Refresh" @click="reloadCurrent" :loading="loading">
          현재 탭 새로고침
        </el-button>
      </div>
    </div>

    <!-- 기간 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">기간</p>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="~"
            start-placeholder="시작일"
            end-placeholder="종료일"
            value-format="YYYY-MM-DD"
            size="small"
          />
        </div>
        <el-button type="primary" size="small" @click="reloadCurrent">조회</el-button>
        <el-button size="small" @click="resetDateRange">최근 6개월</el-button>
      </div>
    </el-card>

    <!-- 통계 탭 -->
    <el-card shadow="never" v-loading="loading">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="게시판 통계" name="boards">
          <div v-if="!hasBoardData" class="py-12 text-center text-gray-400 text-sm">
            게시판 통계 데이터가 없습니다
          </div>
          <VChart
            v-else
            :option="boardChartOption"
            autoresize
            style="height: 380px"
          />
        </el-tab-pane>

        <el-tab-pane label="콘텐츠 통계" name="contents">
          <div v-if="!hasContentData" class="py-12 text-center text-gray-400 text-sm">
            콘텐츠 통계 데이터가 없습니다
          </div>
          <VChart
            v-else
            :option="contentChartOption"
            autoresize
            style="height: 380px"
          />
        </el-tab-pane>

        <el-tab-pane label="정책 통계" name="policies">
          <div v-if="!hasPolicyData" class="py-12 text-center text-gray-400 text-sm">
            정책 통계 데이터가 없습니다
          </div>
          <VChart
            v-else
            :option="policyChartOption"
            autoresize
            style="height: 380px"
          />
        </el-tab-pane>

        <el-tab-pane label="안전 통계" name="safety">
          <div v-if="!hasSafetyData" class="py-12 text-center text-gray-400 text-sm">
            안전 통계 데이터가 없습니다
          </div>
          <VChart
            v-else
            :option="safetyChartOption"
            autoresize
            style="height: 380px"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,
} from 'echarts/components'
import { useGovernanceStore } from '@/stores/governanceStore'

// @MX:NOTE: [AUTO] vue-echarts 시계열 라인 차트 — 4개 탭 공통 사용
use([
  CanvasRenderer,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,
])

const store = useGovernanceStore()

type StatsTab = 'boards' | 'contents' | 'policies' | 'safety'
const activeTab = ref<StatsTab>('boards')
const loading = ref(false)

// 기간 필터 (기본: 최근 6개월)
const dateRange = ref<[string, string]>(getDefaultDateRange())

function getDefaultDateRange(): [string, string] {
  const today = new Date()
  const sixMonthsAgo = new Date()
  sixMonthsAgo.setMonth(today.getMonth() - 6)
  return [
    sixMonthsAgo.toISOString().slice(0, 10),
    today.toISOString().slice(0, 10),
  ]
}

function resetDateRange(): void {
  dateRange.value = getDefaultDateRange()
  reloadCurrent()
}

// ── 차트 옵션 타입 ───────────────────────────────────────────────────────────
interface ChartOption {
  [key: string]: unknown
  title?: Record<string, unknown>
  tooltip?: Record<string, unknown>
  legend?: Record<string, unknown>
  grid?: Record<string, unknown>
  xAxis?: Record<string, unknown>
  yAxis?: Record<string, unknown> | unknown[]
  series?: unknown[]
  dataZoom?: unknown[]
}

// ── 게시판 통계 차트 ─────────────────────────────────────────────────────────
const hasBoardData = computed(() => store.boardStats.length > 0)

const boardChartOption = computed<ChartOption>(() => {
  // 일자별 합산 (board_id 다수일 수 있어 date 기준 집계)
  const byDate = new Map<
    string,
    { views: number; visitors: number; posts: number; comments: number }
  >()
  for (const r of store.boardStats) {
    const cur = byDate.get(r.date) ?? {
      views: 0,
      visitors: 0,
      posts: 0,
      comments: 0,
    }
    cur.views += r.total_views
    cur.visitors += r.unique_visitors
    cur.posts += r.post_count
    cur.comments += r.comment_count
    byDate.set(r.date, cur)
  }
  const dates = [...byDate.keys()].sort()
  const views = dates.map(d => byDate.get(d)!.views)
  const visitors = dates.map(d => byDate.get(d)!.visitors)
  const posts = dates.map(d => byDate.get(d)!.posts)
  const comments = dates.map(d => byDate.get(d)!.comments)

  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: ['조회수', '방문자', '게시글', '댓글'] },
    grid: { left: 60, right: 24, top: 24, bottom: 60 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value' },
    dataZoom: [{ type: 'inside' }],
    series: [
      { name: '조회수', type: 'line', data: views, smooth: true, itemStyle: { color: '#409EFF' } },
      { name: '방문자', type: 'line', data: visitors, smooth: true, itemStyle: { color: '#67C23A' } },
      { name: '게시글', type: 'line', data: posts, smooth: true, itemStyle: { color: '#E6A23C' } },
      { name: '댓글', type: 'line', data: comments, smooth: true, itemStyle: { color: '#F56C6C' } },
    ],
  }
})

// ── 콘텐츠 통계 차트 ─────────────────────────────────────────────────────────
const hasContentData = computed(() => store.contentStats.length > 0)

const contentChartOption = computed<ChartOption>(() => {
  // 일자별 합산
  const byDate = new Map<string, { views: number; viewers: number; dwell: number; cnt: number }>()
  for (const r of store.contentStats) {
    const cur = byDate.get(r.date) ?? { views: 0, viewers: 0, dwell: 0, cnt: 0 }
    cur.views += r.view_count
    cur.viewers += r.unique_viewers
    cur.dwell += r.avg_dwell_sec
    cur.cnt += 1
    byDate.set(r.date, cur)
  }
  const dates = [...byDate.keys()].sort()
  const views = dates.map(d => byDate.get(d)!.views)
  const viewers = dates.map(d => byDate.get(d)!.viewers)
  const dwell = dates.map(d => {
    const v = byDate.get(d)!
    return v.cnt === 0 ? 0 : Number((v.dwell / v.cnt).toFixed(1))
  })

  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: ['조회수', '순방문자', '평균 체류(초)'] },
    grid: { left: 60, right: 60, top: 24, bottom: 60 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '건수', position: 'left' },
      { type: 'value', name: '초', position: 'right' },
    ],
    dataZoom: [{ type: 'inside' }],
    series: [
      { name: '조회수', type: 'line', data: views, smooth: true, yAxisIndex: 0, itemStyle: { color: '#409EFF' } },
      { name: '순방문자', type: 'line', data: viewers, smooth: true, yAxisIndex: 0, itemStyle: { color: '#67C23A' } },
      { name: '평균 체류(초)', type: 'line', data: dwell, smooth: true, yAxisIndex: 1, itemStyle: { color: '#E6A23C' } },
    ],
  }
})

// ── 정책 통계 차트 ──────────────────────────────────────────────────────────
const hasPolicyData = computed(() => store.policyStats.length > 0)

const policyChartOption = computed<ChartOption>(() => {
  // 월별 집계 (policy_id 합산)
  const byMonth = new Map<
    string,
    { match: number; apply: number; success: number; rateSum: number; cnt: number }
  >()
  for (const r of store.policyStats) {
    const cur = byMonth.get(r.month) ?? {
      match: 0,
      apply: 0,
      success: 0,
      rateSum: 0,
      cnt: 0,
    }
    cur.match += r.match_count
    cur.apply += r.apply_count
    cur.success += r.success_count
    cur.rateSum += r.apply_conversion_rate
    cur.cnt += 1
    byMonth.set(r.month, cur)
  }
  const months = [...byMonth.keys()].sort()
  const matches = months.map(m => byMonth.get(m)!.match)
  const applies = months.map(m => byMonth.get(m)!.apply)
  const successes = months.map(m => byMonth.get(m)!.success)
  const rates = months.map(m => {
    const v = byMonth.get(m)!
    return v.cnt === 0 ? 0 : Number(((v.rateSum / v.cnt) * 100).toFixed(2))
  })

  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: ['매칭', '신청', '선정', '전환율(%)'] },
    grid: { left: 60, right: 60, top: 24, bottom: 60 },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '건수', position: 'left' },
      { type: 'value', name: '%', position: 'right', max: 100 },
    ],
    dataZoom: [{ type: 'inside' }],
    series: [
      { name: '매칭', type: 'line', data: matches, smooth: true, yAxisIndex: 0, itemStyle: { color: '#409EFF' } },
      { name: '신청', type: 'line', data: applies, smooth: true, yAxisIndex: 0, itemStyle: { color: '#67C23A' } },
      { name: '선정', type: 'line', data: successes, smooth: true, yAxisIndex: 0, itemStyle: { color: '#E6A23C' } },
      { name: '전환율(%)', type: 'line', data: rates, smooth: true, yAxisIndex: 1, itemStyle: { color: '#F56C6C' } },
    ],
  }
})

// ── 안전 통계 차트 ──────────────────────────────────────────────────────────
const hasSafetyData = computed(() => store.safetyStats.length > 0)

const safetyChartOption = computed<ChartOption>(() => {
  // 월별 + 카테고리별 분리 라인
  const monthsSet = new Set<string>()
  const cats = new Set<string>()
  for (const r of store.safetyStats) {
    monthsSet.add(r.month)
    cats.add(r.incident_category)
  }
  const months = [...monthsSet].sort()
  const categories = [...cats]

  const series = categories.map(cat => {
    const data = months.map(m => {
      const found = store.safetyStats.find(
        r => r.month === m && r.incident_category === cat,
      )
      return found ? found.incident_count : 0
    })
    return { name: cat, type: 'line', data, smooth: true }
  })

  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: categories },
    grid: { left: 60, right: 24, top: 24, bottom: 60 },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: { type: 'value', name: '건수' },
    dataZoom: [{ type: 'inside' }],
    series,
  }
})

// ── 데이터 로딩 ──────────────────────────────────────────────────────────────
async function loadByTab(tab: StatsTab): Promise<void> {
  loading.value = true
  const [from, to] = dateRange.value
  try {
    if (tab === 'boards') {
      await store.fetchBoardStats({ from, to, period: 'daily' })
    } else if (tab === 'contents') {
      await store.fetchContentStats({ from, to })
    } else if (tab === 'policies') {
      await store.fetchPolicyStats({ from, to })
    } else if (tab === 'safety') {
      await store.fetchSafetyStats({ from, to })
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '통계 조회 실패')
  } finally {
    loading.value = false
  }
}

async function reloadCurrent(): Promise<void> {
  await loadByTab(activeTab.value)
}

function handleTabChange(name: string | number): void {
  activeTab.value = name as StatsTab
  loadByTab(activeTab.value)
}

onMounted(() => {
  loadByTab('boards')
})
</script>
