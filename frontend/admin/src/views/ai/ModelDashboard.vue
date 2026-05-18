<template>
  <div>
    <!-- 페이지 제목 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('ai.dashboard.title') }}
      </h2>
      <!-- 드리프트 알림 카운트 뱃지 -->
      <span
        v-if="driftCount > 0"
        data-testid="drift-count-badge"
        class="inline-flex items-center gap-1 rounded bg-red-100 px-3 py-1 text-sm font-medium text-red-700"
        :aria-label="t('ai.dashboard.driftBadge', { n: driftCount })"
      >
        ⚠ {{ t('ai.dashboard.driftBadge', { n: driftCount }) }}
      </span>
    </div>

    <!-- 모델 헬스 -->
    <el-card class="mb-4">
      <div class="flex flex-wrap items-center gap-6">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-gray-700">
            {{ t('ai.dashboard.health.status') }}
          </span>
          <el-tag :type="modelHealth?.status === 'UP' ? 'success' : 'danger'">
            {{ modelHealth?.status ?? '-' }}
          </el-tag>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-gray-700">
            {{ t('ai.dashboard.health.loadedModels') }}
          </span>
          <span class="text-sm text-gray-600">
            {{ (modelHealth?.loadedModels ?? []).join(', ') || '-' }}
          </span>
        </div>
      </div>
    </el-card>

    <!-- 필터 영역 -->
    <el-card class="mb-4">
      <form
        role="search"
        :aria-label="t('ai.dashboard.title')"
        @submit.prevent="onSearch"
      >
        <div class="flex flex-wrap gap-4">
          <div class="flex flex-col gap-1">
            <label for="ai-filter-model" class="text-sm font-medium text-gray-700">
              {{ t('ai.dashboard.filter.modelName') }}
            </label>
            <el-input
              id="ai-filter-model"
              v-model="filterModelName"
              clearable
              style="width: 200px"
              :placeholder="t('ai.dashboard.filter.modelName')"
            />
          </div>
          <div class="flex flex-col gap-1">
            <label for="ai-filter-type" class="text-sm font-medium text-gray-700">
              {{ t('ai.dashboard.filter.type') }}
            </label>
            <el-select
              id="ai-filter-type"
              v-model="filterType"
              clearable
              style="width: 180px"
              :placeholder="t('ai.dashboard.filter.all')"
            >
              <el-option label="GROWTH_STAGE" value="GROWTH_STAGE" />
              <el-option label="RISK_SCORE" value="RISK_SCORE" />
              <el-option label="SIMULATION" value="SIMULATION" />
            </el-select>
          </div>
          <div class="flex flex-col gap-1">
            <label for="ai-filter-range" class="text-sm font-medium text-gray-700">
              {{ t('ai.dashboard.filter.period') }}
            </label>
            <el-date-picker
              id="ai-filter-range"
              v-model="filterDateRange"
              type="daterange"
              :start-placeholder="t('ai.dashboard.filter.period')"
              :end-placeholder="t('ai.dashboard.filter.period')"
              value-format="YYYY-MM-DD"
              style="width: 260px"
            />
          </div>
        </div>
        <div class="mt-4 flex gap-2">
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ t('ai.dashboard.filter.search') }}
          </el-button>
          <el-button @click="onReset">
            {{ t('ai.dashboard.filter.reset') }}
          </el-button>
        </div>
      </form>
    </el-card>

    <!-- 메트릭 테이블 -->
    <el-table
      v-loading="loading"
      :data="metrics"
      stripe
      :empty-text="t('ai.dashboard.empty')"
      class="w-full"
    >
      <caption class="sr-only">{{ t('ai.dashboard.title') }}</caption>

      <el-table-column
        prop="modelName"
        :label="t('ai.dashboard.field.modelName')"
        min-width="160"
      />
      <el-table-column
        prop="predictionType"
        :label="t('ai.dashboard.field.predictionType')"
        width="150"
      />
      <el-table-column
        :label="t('ai.dashboard.field.period')"
        min-width="150"
      >
        <template #default="{ row }">
          {{ row.aggregatePeriod }} / {{ row.periodStart }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.dashboard.field.accuracy')"
        width="110"
      >
        <template #default="{ row }">
          {{ row.accuracy != null ? `${(row.accuracy * 100).toFixed(1)}%` : '-' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.dashboard.field.latencyP95')"
        width="120"
      >
        <template #default="{ row }">
          {{ row.latencyP95 != null ? `${row.latencyP95}ms` : '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="sampleCount"
        :label="t('ai.dashboard.field.sampleCount')"
        width="110"
      />
      <el-table-column
        :label="t('ai.dashboard.field.drift')"
        width="100"
      >
        <template #default="{ row }">
          <span
            class="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium"
            :class="row.driftDetected
              ? 'bg-red-100 text-red-700'
              : 'bg-green-100 text-green-700'"
            :aria-label="row.driftDetected
              ? t('ai.dashboard.drift.detected')
              : t('ai.dashboard.drift.normal')"
          >
            {{ row.driftDetected ? '⚠' : '✓' }}
            {{ row.driftDetected
              ? t('ai.dashboard.drift.detected')
              : t('ai.dashboard.drift.normal') }}
          </span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
// @MX:ANCHOR: [AUTO] ModelDashboard — 라우터, AdminLayout 사이드바, 테스트에서 참조
// @MX:REASON: fan_in >= 3: 라우터 등록, AdminLayout AI 메뉴, ModelDashboard.spec 테스트에서 참조
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { aiAdminApi } from '@/api/aiAdminApi'
import { useAiMonitor } from '@/composables/useAiMonitor'
import type { AiMetricDto, ModelHealthDto, AiMetricQuery } from '@/types/ai'

const { t } = useI18n()
const { run } = useAiMonitor()

// ── 상태 ────────────────────────────────────────────────────────────────────
const metrics = ref<AiMetricDto[]>([])
const modelHealth = ref<ModelHealthDto | null>(null)
const loading = ref(false)

const filterModelName = ref('')
const filterType = ref<string>('')
const filterDateRange = ref<[string, string] | null>(null)

const driftCount = computed(
  () => metrics.value.filter((m) => m.driftDetected).length,
)

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
async function loadMetrics(): Promise<void> {
  loading.value = true
  const params: AiMetricQuery = {}
  if (filterModelName.value) params.modelName = filterModelName.value
  if (filterType.value) params.type = filterType.value
  if (filterDateRange.value) {
    params.from = filterDateRange.value[0]
    params.to = filterDateRange.value[1]
  }
  const data = await run(() => aiAdminApi.getMetrics(params), {
    errorMessage: t('ai.dashboard.loadError'),
  })
  metrics.value = data ?? []
  loading.value = false
}

async function loadHealth(): Promise<void> {
  modelHealth.value = await run(() => aiAdminApi.getModelHealth())
}

function onSearch(): void {
  loadMetrics()
}

function onReset(): void {
  filterModelName.value = ''
  filterType.value = ''
  filterDateRange.value = null
  loadMetrics()
}

onMounted(() => {
  loadHealth()
  loadMetrics()
})

// 테스트에서 vm 접근을 위해 노출
defineExpose({ filterModelName, filterType, onSearch, driftCount })
</script>
