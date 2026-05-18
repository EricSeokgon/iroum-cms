<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('ai.rag.title') }}
      </h2>
    </div>

    <!-- 필터 -->
    <el-card class="mb-4">
      <form
        role="search"
        :aria-label="t('ai.rag.title')"
        @submit.prevent="onSearch"
      >
        <div class="flex flex-wrap gap-4">
          <div class="flex flex-col gap-1">
            <label for="rag-range" class="text-sm font-medium text-gray-700">
              {{ t('ai.rag.filter.range') }}
            </label>
            <el-date-picker
              id="rag-range"
              v-model="dateRange"
              type="daterange"
              :start-placeholder="t('ai.rag.filter.range')"
              :end-placeholder="t('ai.rag.filter.range')"
              value-format="YYYY-MM-DD"
              style="width: 260px"
            />
          </div>
        </div>
        <div class="mt-4 flex gap-2">
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ t('ai.rag.filter.search') }}
          </el-button>
          <el-button @click="onReset">
            {{ t('ai.rag.filter.reset') }}
          </el-button>
        </div>
      </form>
    </el-card>

    <!-- 핵심 지표 카드 -->
    <div v-loading="loading" class="grid grid-cols-1 gap-4 md:grid-cols-4">
      <el-card data-testid="metric-satisfaction">
        <div class="text-sm font-medium text-gray-500">
          {{ t('ai.rag.metric.satisfaction') }}
        </div>
        <div class="mt-2 text-3xl font-bold text-green-600">
          {{ pct(metrics?.satisfactionRate) }}
        </div>
        <div class="mt-1 text-xs text-gray-400">
          {{ t('ai.rag.metric.satisfactionHint') }}
        </div>
      </el-card>

      <el-card data-testid="metric-cache-hit">
        <div class="text-sm font-medium text-gray-500">
          {{ t('ai.rag.metric.cacheHit') }}
        </div>
        <div class="mt-2 text-3xl font-bold text-blue-600">
          {{ pct(metrics?.cacheHitRate) }}
        </div>
        <div class="mt-1 text-xs text-gray-400">
          {{ t('ai.rag.metric.cacheHitHint') }}
        </div>
      </el-card>

      <el-card data-testid="metric-latency">
        <div class="text-sm font-medium text-gray-500">
          {{ t('ai.rag.metric.latency') }}
        </div>
        <div class="mt-2 text-3xl font-bold text-purple-600">
          {{ num(metrics?.avgLatencyMs) }}
          <span class="text-base">{{ t('ai.rag.metric.latencyUnit') }}</span>
        </div>
        <div class="mt-1 text-xs text-gray-400">
          {{ t('ai.rag.metric.total') }}: {{ metrics?.totalQueries ?? 0 }}
        </div>
      </el-card>

      <el-card data-testid="metric-degraded">
        <div class="text-sm font-medium text-gray-500">
          {{ t('ai.rag.metric.degraded') }}
        </div>
        <div class="mt-2 text-3xl font-bold text-amber-600">
          {{ pct(metrics?.degradedRate) }}
        </div>
        <div class="mt-1 text-xs text-gray-400">
          {{ t('ai.rag.metric.degradedHint') }}
        </div>
      </el-card>
    </div>

    <!-- 시계열 (간단 테이블 — AI-002 패턴과 정합, 차트 라이브러리 비의존) -->
    <el-card class="mt-4">
      <div class="mb-2 text-sm font-medium text-gray-700">
        {{ t('ai.rag.timeSeries') }}
      </div>
      <el-table
        :data="metrics?.timeSeries ?? []"
        data-testid="rag-timeseries"
        size="small"
      >
        <el-table-column prop="date" label="date" />
        <el-table-column prop="queryCount" label="queries" />
        <el-table-column label="satisfaction">
          <template #default="{ row }">
            {{ pct(row.satisfactionRate) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
// @MX:NOTE: [AUTO] RagMetrics — SPEC-CMS-AI-003 RAG 품질 모니터링 뷰 (AI-002 패턴)
// @MX:SPEC: SPEC-CMS-AI-003
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAiMonitor } from '@/composables/useAiMonitor'
import {
  ragMetricsApi,
  type RagMetricsDto,
  type RagMetricsQuery,
} from '@/api/ragMetricsApi'

const { t } = useI18n()
const { run } = useAiMonitor()

const metrics = ref<RagMetricsDto | null>(null)
const loading = ref(false)
const dateRange = ref<[string, string] | null>(null)

function pct(value: number | undefined | null): string {
  if (value == null) return '-'
  return `${(value * 100).toFixed(1)}%`
}

function num(value: number | undefined | null): string {
  if (value == null) return '-'
  return value.toFixed(0)
}

async function loadMetrics(): Promise<void> {
  loading.value = true
  const params: RagMetricsQuery = {}
  if (dateRange.value) {
    params.from = dateRange.value[0]
    params.to = dateRange.value[1]
  }
  metrics.value = await run(() => ragMetricsApi.getMetrics(params), {
    errorMessage: t('ai.rag.loadError'),
  })
  loading.value = false
}

function onSearch(): void {
  loadMetrics()
}

function onReset(): void {
  dateRange.value = null
  loadMetrics()
}

onMounted(loadMetrics)

defineExpose({ dateRange, onSearch, onReset, metrics, pct, num })
</script>
