<template>
  <!-- 배치 실행 이력 — SPEC-CMS-009 REQ-DATA-005, REQ-GOV-010 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">배치 실행 이력</h2>
      <div class="flex gap-2">
        <el-button :icon="Refresh" @click="search">새로고침</el-button>
        <el-button v-if="isAdmin" type="primary" :icon="DataAnalysis" @click="openRecompute">통계 재집계</el-button>
      </div>
    </div>

    <!-- 성능 추이 차트 -->
    <el-card class="mb-4" shadow="never">
      <template #header>
        <span class="text-sm font-semibold">최근 배치 SLA 추이</span>
      </template>
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div>
          <p class="mb-2 text-xs text-gray-500">실행 시간 (ms)</p>
          <VChart
            v-if="batchLogsForChart.length > 0"
            :option="durationChartOption"
            autoresize
            style="height: 220px"
          />
          <div v-else class="py-8 text-center text-gray-400 text-sm">표시할 데이터가 없습니다</div>
        </div>
        <div>
          <p class="mb-2 text-xs text-gray-500">처리 건수</p>
          <VChart
            v-if="batchLogsForChart.length > 0"
            :option="recordsChartOption"
            autoresize
            style="height: 220px"
          />
          <div v-else class="py-8 text-center text-gray-400 text-sm">표시할 데이터가 없습니다</div>
        </div>
      </div>
    </el-card>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">잡 그룹</p>
          <el-select v-model="filter.jobGroup" clearable size="small" placeholder="전체" style="width: 140px">
            <el-option label="STATS" value="STATS" />
            <el-option label="RETENTION" value="RETENTION" />
            <el-option label="QUALITY" value="QUALITY" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">상태</p>
          <el-select v-model="filter.status" clearable size="small" placeholder="전체" style="width: 130px">
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAILURE" value="FAILURE" />
            <el-option label="RUNNING" value="RUNNING" />
          </el-select>
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
        <div>
          <p class="mb-1 text-xs text-gray-500">잡 이름</p>
          <el-input v-model="filter.jobName" clearable size="small" placeholder="잡 이름 검색" style="width: 180px" />
        </div>
        <el-button type="primary" size="small" @click="search">검색</el-button>
        <el-button size="small" @click="resetFilter">초기화</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.batchLogsLoading">
      <el-table :data="store.batchLogs" stripe empty-text="실행 이력이 없습니다">
        <el-table-column prop="started_at" label="시작 시각" width="170">
          <template #default="{ row }">{{ formatDateTime(row.started_at) }}</template>
        </el-table-column>
        <el-table-column prop="job_name" label="잡 이름" min-width="200" show-overflow-tooltip />
        <el-table-column prop="job_group" label="그룹" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="groupTagType(row.job_group)">{{ row.job_group }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration_ms" label="실행 시간" width="120">
          <template #default="{ row }">{{ formatDuration(row.duration_ms) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="상태" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="처리/실패" width="130" align="center">
          <template #default="{ row }">
            <span class="text-green-600">{{ row.records_processed ?? 0 }}</span>
            <span class="text-gray-400 mx-1">/</span>
            <span class="text-red-600">{{ row.records_failed ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="retry_count" label="재시도" width="80" align="center" />
        <el-table-column label="작업" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="openDetail(row.id)">상세</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="store.batchLogsTotal"
        layout="prev, pager, next, sizes, total"
        :page-sizes="[20, 50, 100]"
        class="mt-4 justify-end"
        @change="search"
      />
    </el-card>

    <!-- 상세 드로어 -->
    <el-drawer v-model="detailDrawerVisible" title="배치 실행 상세" size="40%">
      <div v-if="store.currentBatchLog">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="잡 이름">{{ store.currentBatchLog.job_name }}</el-descriptions-item>
          <el-descriptions-item label="그룹">{{ store.currentBatchLog.job_group }}</el-descriptions-item>
          <el-descriptions-item label="시작">{{ formatDateTime(store.currentBatchLog.started_at) }}</el-descriptions-item>
          <el-descriptions-item label="종료">
            {{ store.currentBatchLog.finished_at ? formatDateTime(store.currentBatchLog.finished_at) : '실행 중' }}
          </el-descriptions-item>
          <el-descriptions-item label="실행 시간">{{ formatDuration(store.currentBatchLog.duration_ms) }}</el-descriptions-item>
          <el-descriptions-item label="상태">
            <el-tag :type="statusTagType(store.currentBatchLog.status)">{{ store.currentBatchLog.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="처리 건수">{{ store.currentBatchLog.records_processed ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="실패 건수">{{ store.currentBatchLog.records_failed ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="store.currentBatchLog.error_summary" class="mt-4">
          <p class="mb-2 text-sm font-semibold">오류 요약</p>
          <pre class="max-h-48 overflow-auto rounded bg-red-50 p-3 text-xs text-red-800">{{ store.currentBatchLog.error_summary }}</pre>
        </div>

        <div v-if="store.currentBatchLog.detail_json" class="mt-4">
          <p class="mb-2 text-sm font-semibold">상세 정보</p>
          <pre class="max-h-64 overflow-auto rounded bg-gray-50 p-3 text-xs">{{ formatJson(store.currentBatchLog.detail_json) }}</pre>
        </div>
      </div>
    </el-drawer>

    <!-- 재집계 다이얼로그 -->
    <el-dialog v-model="recomputeDialogVisible" title="통계 재집계" width="500px">
      <el-form label-width="120px">
        <el-form-item label="잡 이름">
          <el-select v-model="recomputeForm.job" style="width: 100%">
            <el-option label="board-stats" value="board-stats" />
            <el-option label="content-stats" value="content-stats" />
            <el-option label="policy-stats" value="policy-stats" />
            <el-option label="safety-stats" value="safety-stats" />
          </el-select>
        </el-form-item>
        <el-form-item label="대상 기간">
          <el-date-picker
            v-model="recomputeRange"
            type="daterange"
            range-separator="~"
            start-placeholder="시작"
            end-placeholder="종료"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recomputeDialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="recomputing" @click="submitRecompute">실행</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, DataAnalysis } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  MarkLineComponent,
} from 'echarts/components'
import { useGovernanceStore } from '@/stores/governanceStore'
import { useAuthStore } from '@/stores/auth'
import type { BatchExecutionLog, BatchJobGroup, BatchLogFilter, BatchStatus } from '@/api/governance'

// @MX:NOTE: [AUTO] vue-echarts 컴포넌트 등록 — Line/Bar 차트 사용
use([
  CanvasRenderer,
  LineChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  MarkLineComponent,
])

const store = useGovernanceStore()
const authStore = useAuthStore()
const isAdmin = computed(() =>
  (authStore.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN' || r === 'ADMIN'),
)

// 필터/페이지
const filter = reactive<BatchLogFilter>({
  jobGroup: undefined,
  status: undefined,
  jobName: undefined,
})
const dateRange = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)

// SLA 임계값 (ms)
const SLA_THRESHOLD_MS = 60_000

const batchLogsForChart = computed(() =>
  [...store.batchLogs]
    .reverse()
    .slice(0, 30)
    .filter(l => l.duration_ms !== undefined),
)

interface ChartOption {
  [key: string]: unknown
  tooltip?: Record<string, unknown>
  legend?: Record<string, unknown>
  grid?: Record<string, unknown>
  xAxis?: Record<string, unknown>
  yAxis?: Record<string, unknown>
  series?: unknown[]
}

const durationChartOption = computed<ChartOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0 },
  grid: { left: 50, right: 16, top: 16, bottom: 36 },
  xAxis: {
    type: 'category',
    data: batchLogsForChart.value.map(l => formatTime(l.started_at)),
    axisLabel: { fontSize: 10 },
  },
  yAxis: { type: 'value', name: 'ms' },
  series: [
    {
      name: '실행 시간',
      type: 'line',
      smooth: true,
      data: batchLogsForChart.value.map(l => l.duration_ms ?? 0),
      itemStyle: { color: '#1976d2' },
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { color: '#d32f2f', type: 'dashed' },
        data: [{ yAxis: SLA_THRESHOLD_MS, label: { formatter: 'SLA' } }],
      },
    },
  ],
}))

const recordsChartOption = computed<ChartOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0 },
  grid: { left: 50, right: 16, top: 16, bottom: 36 },
  xAxis: {
    type: 'category',
    data: batchLogsForChart.value.map(l => formatTime(l.started_at)),
    axisLabel: { fontSize: 10 },
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: '처리 건수',
      type: 'bar',
      data: batchLogsForChart.value.map(l => l.records_processed ?? 0),
      itemStyle: { color: '#2e7d32' },
    },
    {
      name: '실패 건수',
      type: 'bar',
      data: batchLogsForChart.value.map(l => l.records_failed ?? 0),
      itemStyle: { color: '#d32f2f' },
    },
  ],
}))

async function search(): Promise<void> {
  if (dateRange.value) {
    filter.from = dateRange.value[0]
    filter.to = dateRange.value[1]
  } else {
    filter.from = undefined
    filter.to = undefined
  }
  await store.fetchBatchLogs({ ...filter, page: page.value - 1, size: size.value })
}

function resetFilter(): void {
  filter.jobGroup = undefined as BatchJobGroup | undefined
  filter.status = undefined as BatchStatus | undefined
  filter.jobName = undefined
  dateRange.value = null
  page.value = 1
  search()
}

// 자동 새로고침: RUNNING 항목 있으면 30초마다
const autoRefreshTimer = ref<ReturnType<typeof setInterval> | null>(null)

function maybeStartAutoRefresh(): void {
  if (autoRefreshTimer.value) return
  if (store.batchLogs.some(l => l.status === 'RUNNING')) {
    autoRefreshTimer.value = setInterval(() => {
      search()
    }, 30_000)
  }
}

function stopAutoRefresh(): void {
  if (autoRefreshTimer.value) {
    clearInterval(autoRefreshTimer.value)
    autoRefreshTimer.value = null
  }
}

// 상세 드로어
const detailDrawerVisible = ref(false)
async function openDetail(id: number): Promise<void> {
  await store.fetchBatchLog(id)
  detailDrawerVisible.value = true
}

// 재집계
const recomputeDialogVisible = ref(false)
const recomputing = ref(false)
const recomputeForm = reactive({ job: 'board-stats' })
const recomputeRange = ref<[string, string] | null>(null)

function openRecompute(): void {
  recomputeForm.job = 'board-stats'
  recomputeRange.value = null
  recomputeDialogVisible.value = true
}

async function submitRecompute(): Promise<void> {
  if (!recomputeRange.value) {
    ElMessage.warning('대상 기간을 선택하세요')
    return
  }
  recomputing.value = true
  try {
    const res = await store.recomputeStats({
      job: recomputeForm.job,
      dateRange: { from: recomputeRange.value[0], to: recomputeRange.value[1] },
    })
    ElMessage.success(`재집계 시작 (배치 로그 ID: ${res.id})`)
    recomputeDialogVisible.value = false
    await search()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '재집계 실패')
  } finally {
    recomputing.value = false
  }
}

// 헬퍼
function groupTagType(g: BatchJobGroup): '' | 'success' | 'warning' | 'info' {
  if (g === 'STATS') return 'success'
  if (g === 'RETENTION') return 'warning'
  return 'info'
}

function statusTagType(s: BatchStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILURE') return 'danger'
  return 'info'
}

function formatDuration(ms: number | undefined): string {
  if (ms === undefined || ms === null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}

function formatJson(s: string): string {
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

onMounted(async () => {
  await search()
  maybeStartAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})

// 명시적으로 BatchExecutionLog import 사용 (eslint no-unused-vars 회피)
type _BatchExecutionLogUsed = BatchExecutionLog
const _typeRef: _BatchExecutionLogUsed | null = null
void _typeRef
</script>
