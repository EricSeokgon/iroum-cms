<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('ai.policyMatch.title') }}
      </h2>
    </div>

    <!-- 필터 -->
    <el-card class="mb-4">
      <form
        role="search"
        :aria-label="t('ai.policyMatch.title')"
        @submit.prevent="onSearch"
      >
        <div class="flex flex-wrap gap-4">
          <div class="flex flex-col gap-1">
            <label for="pm-period" class="text-sm font-medium text-gray-700">
              {{ t('ai.policyMatch.filter.period') }}
            </label>
            <el-select
              id="pm-period"
              v-model="period"
              style="width: 160px"
            >
              <el-option label="DAILY" value="DAILY" />
              <el-option label="WEEKLY" value="WEEKLY" />
              <el-option label="MONTHLY" value="MONTHLY" />
            </el-select>
          </div>
          <div class="flex flex-col gap-1">
            <label for="pm-range" class="text-sm font-medium text-gray-700">
              {{ t('ai.policyMatch.filter.range') }}
            </label>
            <el-date-picker
              id="pm-range"
              v-model="dateRange"
              type="daterange"
              :start-placeholder="t('ai.policyMatch.filter.range')"
              :end-placeholder="t('ai.policyMatch.filter.range')"
              value-format="YYYY-MM-DD"
              style="width: 260px"
            />
          </div>
        </div>
        <div class="mt-4 flex gap-2">
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ t('ai.policyMatch.filter.search') }}
          </el-button>
          <el-button @click="onReset">
            {{ t('ai.policyMatch.filter.reset') }}
          </el-button>
        </div>
      </form>
    </el-card>

    <!-- 핵심 지표 카드 -->
    <div v-loading="loading" class="grid grid-cols-1 gap-4 md:grid-cols-3">
      <el-card data-testid="metric-ctr">
        <div class="text-sm font-medium text-gray-500">
          {{ t('ai.policyMatch.metric.ctr') }}
        </div>
        <div class="mt-2 text-3xl font-bold text-blue-600">
          {{ pct(metrics?.ctr) }}
        </div>
        <div class="mt-1 text-xs text-gray-400">
          {{ t('ai.policyMatch.metric.ctrDetail', {
            c: metrics?.totalClicked ?? 0, v: metrics?.totalViewed ?? 0 }) }}
        </div>
      </el-card>

      <el-card data-testid="metric-conversion">
        <div class="text-sm font-medium text-gray-500">
          {{ t('ai.policyMatch.metric.conversion') }}
        </div>
        <div class="mt-2 text-3xl font-bold text-green-600">
          {{ pct(metrics?.conversionRate) }}
        </div>
        <div class="mt-1 text-xs text-gray-400">
          {{ t('ai.policyMatch.metric.conversionDetail', {
            a: metrics?.totalApplied ?? 0, v: metrics?.totalViewed ?? 0 }) }}
        </div>
      </el-card>

      <el-card data-testid="metric-coverage">
        <div class="text-sm font-medium text-gray-500">
          {{ t('ai.policyMatch.metric.coverage') }}
        </div>
        <div class="mt-2 text-3xl font-bold text-purple-600">
          {{ pct(metrics?.coverage) }}
        </div>
        <div class="mt-1 text-xs text-gray-400">
          {{ t('ai.policyMatch.metric.coverageHint') }}
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
// @MX:NOTE: [AUTO] PolicyMatchMetrics — SPEC-CMS-AI-002 추천 품질 모니터링 뷰
// @MX:SPEC: SPEC-CMS-AI-002
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAiMonitor } from '@/composables/useAiMonitor'
import {
  policyMatchAdminApi,
  type PolicyMatchMetricsDto,
  type PolicyMatchMetricsPeriod,
  type PolicyMatchMetricsQuery,
} from '@/api/policyMatchAdminApi'

const { t } = useI18n()
const { run } = useAiMonitor()

const metrics = ref<PolicyMatchMetricsDto | null>(null)
const loading = ref(false)
const period = ref<PolicyMatchMetricsPeriod>('DAILY')
const dateRange = ref<[string, string] | null>(null)

function pct(value: number | undefined | null): string {
  if (value == null) return '-'
  return `${(value * 100).toFixed(1)}%`
}

async function loadMetrics(): Promise<void> {
  loading.value = true
  const params: PolicyMatchMetricsQuery = { period: period.value }
  if (dateRange.value) {
    params.from = dateRange.value[0]
    params.to = dateRange.value[1]
  }
  metrics.value = await run(() => policyMatchAdminApi.getMetrics(params), {
    errorMessage: t('ai.policyMatch.loadError'),
  })
  loading.value = false
}

function onSearch(): void {
  loadMetrics()
}

function onReset(): void {
  period.value = 'DAILY'
  dateRange.value = null
  loadMetrics()
}

onMounted(loadMetrics)

defineExpose({ period, dateRange, onSearch, onReset, metrics, pct })
</script>
