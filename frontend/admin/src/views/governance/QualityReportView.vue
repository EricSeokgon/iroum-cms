<template>
  <!-- 품질 리포트 — SPEC-CMS-009 REQ-DATA-008 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">품질 리포트</h2>
      <div class="flex gap-2">
        <el-button :icon="Refresh" @click="search">새로고침</el-button>
      </div>
    </div>

    <!-- 위반 추이 차트 -->
    <el-card class="mb-4" shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-sm font-semibold">최근 위반 추이</span>
          <div class="flex gap-2">
            <el-tag size="small" type="danger">전체 위반: {{ violationCount }}</el-tag>
            <el-tag size="small" type="warning">CRITICAL: {{ criticalCount }}</el-tag>
            <el-tag size="small" type="info">미알림: {{ unnotifiedCount }}</el-tag>
          </div>
        </div>
      </template>
      <div v-if="!hasChartData" class="py-8 text-center text-gray-400 text-sm">
        표시할 데이터가 없습니다
      </div>
      <VChart
        v-else
        :option="violationChartOption"
        autoresize
        style="height: 240px"
      />
    </el-card>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">위반만 보기</p>
          <el-switch v-model="filter.violation" />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">심각도</p>
          <el-select v-model="filter.severity" clearable size="small" placeholder="전체" style="width: 130px">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="CRITICAL" value="CRITICAL" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">룰 ID</p>
          <el-input-number v-model="filter.ruleId" :min="1" size="small" />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">기간</p>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="~"
            start-placeholder="시작"
            end-placeholder="종료"
            value-format="YYYY-MM-DD"
            size="small"
          />
        </div>
        <el-button type="primary" size="small" @click="search">검색</el-button>
        <el-button size="small" @click="resetFilter">초기화</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.qualityReportsLoading">
      <el-table :data="store.qualityReports" stripe empty-text="리포트가 없습니다">
        <el-table-column prop="checked_at" label="검사 시각" width="170">
          <template #default="{ row }">{{ formatDateTime(row.checked_at) }}</template>
        </el-table-column>
        <el-table-column label="대상" min-width="220">
          <template #default="{ row }">
            <span class="font-mono text-xs">{{ row.target_table }}.{{ row.target_column }}</span>
            <el-tag size="small" class="ml-2">{{ row.rule_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="측정값" min-width="120">
          <template #default="{ row }">
            <span :class="row.violation ? 'text-red-600 font-semibold' : ''">
              {{ row.measured_value !== undefined ? row.measured_value : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="기준" min-width="140">
          <template #default="{ row }">{{ formatThreshold(row) }}</template>
        </el-table-column>
        <el-table-column prop="severity" label="심각도" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="severityTagType(row.severity)">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="violation" label="위반" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.violation" type="danger" size="small">위반</el-tag>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="notified" label="알림" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.notified" type="success" size="small">발송</el-tag>
            <span v-else class="text-gray-400">미발송</span>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="openDetail(row.id)">상세</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="store.qualityReportsTotal"
        layout="prev, pager, next, sizes, total"
        :page-sizes="[20, 50, 100]"
        class="mt-4 justify-end"
        @change="search"
      />
    </el-card>

    <!-- 상세 드로어 -->
    <el-drawer v-model="detailDrawerVisible" title="품질 리포트 상세" size="42%">
      <div v-if="store.currentQualityReport">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="대상">
            <span class="font-mono">{{ store.currentQualityReport.target_table }}.{{ store.currentQualityReport.target_column }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="룰 유형">{{ store.currentQualityReport.rule_type }}</el-descriptions-item>
          <el-descriptions-item label="검사 시각">{{ formatDateTime(store.currentQualityReport.checked_at) }}</el-descriptions-item>
          <el-descriptions-item label="측정값">{{ store.currentQualityReport.measured_value ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="기준">{{ formatThreshold(store.currentQualityReport) }}</el-descriptions-item>
          <el-descriptions-item label="심각도">
            <el-tag :type="severityTagType(store.currentQualityReport.severity)">{{ store.currentQualityReport.severity }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="위반 여부">
            <el-tag v-if="store.currentQualityReport.violation" type="danger">위반</el-tag>
            <span v-else>정상</span>
          </el-descriptions-item>
          <el-descriptions-item label="알림 발송">{{ store.currentQualityReport.notified ? '완료' : '미발송' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="store.currentQualityReport.sample_pks?.length" class="mt-4">
          <p class="mb-2 text-sm font-semibold">샘플 PK ({{ store.currentQualityReport.sample_pks.length }}건)</p>
          <div class="max-h-40 overflow-auto rounded bg-gray-50 p-3">
            <el-tag
              v-for="pk in store.currentQualityReport.sample_pks"
              :key="pk"
              size="small"
              class="mr-2 mb-2"
            >
              {{ pk }}
            </el-tag>
          </div>
        </div>

        <div v-if="store.currentQualityReport.detail_json" class="mt-4">
          <p class="mb-2 text-sm font-semibold">상세 데이터</p>
          <pre class="max-h-64 overflow-auto rounded bg-gray-50 p-3 text-xs">{{ formatJson(store.currentQualityReport.detail_json) }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { useGovernanceStore } from '@/stores/governanceStore'
import type { QualityReport, QualityReportFilter, QualitySeverity } from '@/api/governance'

// @MX:NOTE: [AUTO] vue-echarts 컴포넌트 등록 — LineChart 1종
use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const store = useGovernanceStore()

// 필터/페이지
const filter = reactive<QualityReportFilter>({
  violation: true,
  severity: undefined,
  ruleId: undefined,
})
const dateRange = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)

async function search(): Promise<void> {
  if (dateRange.value) {
    filter.from = dateRange.value[0]
    filter.to = dateRange.value[1]
  } else {
    filter.from = undefined
    filter.to = undefined
  }
  await store.fetchQualityReports({ ...filter, page: page.value - 1, size: size.value })
}

function resetFilter(): void {
  filter.violation = true
  filter.severity = undefined as QualitySeverity | undefined
  filter.ruleId = undefined
  dateRange.value = null
  page.value = 1
  search()
}

// 위반 추이 차트
const violationCount = computed(() => store.qualityReports.filter(r => r.violation).length)
const criticalCount = computed(() =>
  store.qualityReports.filter(r => r.violation && r.severity === 'CRITICAL').length,
)
const unnotifiedCount = computed(() =>
  store.qualityReports.filter(r => r.violation && !r.notified).length,
)

interface DateBucket {
  date: string
  CRITICAL: number
  WARN: number
  INFO: number
}

const violationByDate = computed<DateBucket[]>(() => {
  const map = new Map<string, DateBucket>()
  store.qualityReports
    .filter(r => r.violation)
    .forEach(r => {
      const d = r.checked_at.slice(0, 10)
      if (!map.has(d)) map.set(d, { date: d, CRITICAL: 0, WARN: 0, INFO: 0 })
      const bucket = map.get(d)!
      bucket[r.severity] += 1
    })
  return Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date))
})

const hasChartData = computed(() => violationByDate.value.length > 0)

interface ChartOption {
  [key: string]: unknown
  tooltip?: Record<string, unknown>
  legend?: Record<string, unknown>
  grid?: Record<string, unknown>
  xAxis?: Record<string, unknown>
  yAxis?: Record<string, unknown>
  series?: unknown[]
}

const violationChartOption = computed<ChartOption>(() => {
  const buckets = violationByDate.value
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 50, right: 16, top: 16, bottom: 36 },
    xAxis: {
      type: 'category',
      data: buckets.map(b => b.date),
      axisLabel: { fontSize: 10 },
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: 'CRITICAL',
        type: 'line',
        smooth: true,
        data: buckets.map(b => b.CRITICAL),
        itemStyle: { color: '#d32f2f' },
      },
      {
        name: 'WARN',
        type: 'line',
        smooth: true,
        data: buckets.map(b => b.WARN),
        itemStyle: { color: '#ed6c02' },
      },
      {
        name: 'INFO',
        type: 'line',
        smooth: true,
        data: buckets.map(b => b.INFO),
        itemStyle: { color: '#0288d1' },
      },
    ],
  }
})

// 상세
const detailDrawerVisible = ref(false)
async function openDetail(id: number): Promise<void> {
  await store.fetchQualityReport(id)
  detailDrawerVisible.value = true
}

// 헬퍼
function severityTagType(s: QualitySeverity): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (s === 'CRITICAL') return 'danger'
  if (s === 'WARN') return 'warning'
  return 'info'
}

function formatThreshold(row: QualityReport): string {
  if (row.rule_type === 'RANGE') {
    return `[${row.range_min ?? '-'}, ${row.range_max ?? '-'}]`
  }
  return row.threshold !== undefined ? String(row.threshold) : '-'
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

function formatJson(s: string): string {
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

onMounted(() => {
  search()
})
</script>
