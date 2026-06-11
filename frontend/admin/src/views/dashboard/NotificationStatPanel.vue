<template>
  <div v-loading="store.loading">
    <!-- 서버 오류 알림 -->
    <el-alert
      v-if="store.error"
      :title="store.error"
      type="warning"
      show-icon
      :closable="false"
      class="mb-4"
      role="alert"
    />

    <!-- 요약 카드 (오늘 발송 / 오늘 읽음률 / 오늘 오류 / 30일 발송) -->
    <el-row :gutter="16" class="mb-6">
      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="h-full">
          <p class="text-sm text-gray-500">오늘 발송</p>
          <p class="mt-1 text-2xl font-bold text-gray-800">
            {{ store.loading ? '...' : (store.summary?.todayDispatched?.toLocaleString() ?? '—') }}
          </p>
          <p class="mt-1 text-xs text-gray-400">금일 발송 건수</p>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="h-full">
          <p class="text-sm text-gray-500">오늘 읽음률</p>
          <p class="mt-1 text-2xl font-bold" :class="readRateClass(store.summary?.todayReadRate)">
            {{ store.loading ? '...' : formatRate(store.summary?.todayReadRate) }}
          </p>
          <p class="mt-1 text-xs text-gray-400">미열람 {{ store.summary?.todayUnread?.toLocaleString() ?? '—' }}건</p>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="h-full">
          <p class="text-sm text-gray-500">오늘 오류</p>
          <p class="mt-1 text-2xl font-bold" :class="(store.summary?.todayErrors ?? 0) > 0 ? 'text-red-500' : 'text-gray-800'">
            {{ store.loading ? '...' : (store.summary?.todayErrors?.toLocaleString() ?? '—') }}
          </p>
          <p class="mt-1 text-xs text-gray-400">실패/대기 건수</p>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="6" class="mb-4">
        <el-card shadow="never" class="h-full">
          <p class="text-sm text-gray-500">30일 발송</p>
          <p class="mt-1 text-2xl font-bold text-gray-800">
            {{ store.loading ? '...' : (store.summary?.thirtyDayDispatched?.toLocaleString() ?? '—') }}
          </p>
          <p class="mt-1 text-xs text-gray-400">읽음률 {{ formatRate(store.summary?.thirtyDayReadRate) }}</p>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 일자별 추이 차트 -->
      <el-col :xs="24" :lg="14" class="mb-4">
        <el-card shadow="never" class="h-full">
          <template #header>
            <span class="font-semibold text-gray-700">일자별 발송/읽음 추이 (최근 30일)</span>
          </template>
          <div v-if="!hasTrendData" class="py-12 text-center text-sm text-gray-400">
            추이 데이터가 없습니다
          </div>
          <VChart
            v-else
            :option="trendChartOption"
            autoresize
            style="height: 320px"
          />
        </el-card>
      </el-col>

      <!-- 카테고리별 통계 테이블 -->
      <el-col :xs="24" :lg="10" class="mb-4">
        <el-card shadow="never" class="h-full">
          <template #header>
            <span class="font-semibold text-gray-700">알림 타입별 통계</span>
          </template>
          <el-table :data="store.categoryStats" size="small" class="w-full" empty-text="데이터가 없습니다">
            <el-table-column prop="type" label="타입" min-width="120" />
            <el-table-column prop="dispatched" label="발송" align="right" width="80">
              <template #default="{ row }">{{ row.dispatched?.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="readCount" label="읽음" align="right" width="80">
              <template #default="{ row }">{{ row.readCount?.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="읽음률" align="right" width="90">
              <template #default="{ row }">{{ categoryReadRate(row) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 실패/대기 알림 목록 -->
    <el-card shadow="never" class="mt-2">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="font-semibold text-gray-700">실패/대기 알림</span>
          <span class="text-xs text-gray-400">총 {{ store.errors?.totalElements ?? 0 }}건</span>
        </div>
      </template>

      <el-table
        :data="store.errors?.content ?? []"
        v-loading="store.errorsLoading"
        size="small"
        class="w-full"
        empty-text="실패/대기 알림이 없습니다"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="사용자" width="90" align="right" />
        <el-table-column prop="type" label="타입" min-width="120" />
        <el-table-column prop="title" label="제목" min-width="180" show-overflow-tooltip />
        <el-table-column prop="deliveryStatus" label="상태" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.deliveryStatus === 'FAILED' ? 'danger' : 'warning'" size="small">
              {{ row.deliveryStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="생성일" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="작업" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :loading="resendingId === row.id"
              @click="handleResend(row.id)"
            >
              재발송
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 페이지네이션 -->
      <div v-if="(store.errors?.totalElements ?? 0) > 0" class="mt-4 flex justify-end">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="store.errors?.totalElements ?? 0"
          :page-size="store.errors?.size ?? 20"
          :current-page="(store.errors?.page ?? 0) + 1"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
} from 'echarts/components'
import { useNotificationStatStore } from '@/stores/notificationStatStore'
import type { CategoryStat } from '@/api/notificationStat'

// @MX:NOTE: [AUTO] vue-echarts 라인 차트 — 발송/읽음 2개 시리즈 시계열
use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

const store = useNotificationStatStore()
const resendingId = ref<number | null>(null)

// ── 포맷 헬퍼 ─────────────────────────────────────────────────────────────────
function formatRate(rate?: number): string {
  if (rate == null) return '—'
  return `${rate.toFixed(1)}%`
}

function readRateClass(rate?: number): string {
  if (rate == null) return 'text-gray-800'
  if (rate >= 70) return 'text-green-500'
  if (rate >= 40) return 'text-yellow-500'
  return 'text-orange-500'
}

function categoryReadRate(row: CategoryStat): string {
  if (!row.dispatched) return '—'
  return `${((row.readCount / row.dispatched) * 100).toFixed(1)}%`
}

function formatDateTime(value?: string): string {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString('ko-KR')
}

// ── 일자별 추이 차트 ──────────────────────────────────────────────────────────
const hasTrendData = computed(() => store.dailyTrend.length > 0)

interface ChartOption {
  [key: string]: unknown
}

const trendChartOption = computed<ChartOption>(() => {
  const dates = store.dailyTrend.map((p) => p.date)
  const dispatched = store.dailyTrend.map((p) => p.dispatched)
  const readCount = store.dailyTrend.map((p) => p.readCount)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['발송', '읽음'], bottom: 0 },
    grid: { left: 48, right: 16, top: 24, bottom: 48 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '발송', type: 'line', smooth: true, data: dispatched, itemStyle: { color: '#3b82f6' } },
      { name: '읽음', type: 'line', smooth: true, data: readCount, itemStyle: { color: '#22c55e' } },
    ],
  }
})

// ── 이벤트 핸들러 ─────────────────────────────────────────────────────────────
async function handleResend(id: number): Promise<void> {
  resendingId.value = id
  try {
    await store.resend(id)
    ElMessage.success('재발송 처리되었습니다')
  } catch {
    ElMessage.error('재발송에 실패했습니다')
  } finally {
    resendingId.value = null
  }
}

function handlePageChange(page: number): void {
  // el-pagination은 1-base, 백엔드는 0-base
  store.loadErrors(page - 1, store.errors?.size ?? 20)
}

onMounted(() => {
  store.loadAll()
})
</script>
