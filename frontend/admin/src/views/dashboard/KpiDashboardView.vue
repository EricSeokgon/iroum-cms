<template>
  <!-- KPI 대시보드 — SPEC-CMS-KPI-001 Phase 4 AC-016 -->
  <div v-loading="store.loading">
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('kpi.title') }}</h2>
      <el-button :loading="store.exporting" @click="onExport">
        {{ t('kpi.export') }}
      </el-button>
    </div>

    <!-- 필터 패널 -->
    <KpiFilterPanel
      class="mb-4"
      :initial="store.filters"
      :loading="store.loading"
      @filter-change="onFilterChange"
      @reset="onReset"
    />

    <!-- 오류 -->
    <el-alert
      v-if="store.error"
      class="mb-4"
      type="error"
      :title="store.error"
      :closable="false"
      show-icon
    />

    <!-- 요약 카드 (METRIC_CARD ×3) -->
    <div class="mb-6">
      <KpiSummaryCards :items="store.kpiValues" />
    </div>

    <!-- 추이 차트 + 전환율 차트 -->
    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <KpiTrendChart
        :feature-items="store.featureUsageItems"
        :download-items="store.fileDownloadItems"
      />
      <KpiConversionFunnel :items="store.conversionItems" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useKpiStore } from '@/stores/kpiStore'
import type { KpiQueryParams } from '@/api/kpi'
import KpiFilterPanel from '@/components/dashboard/KpiFilterPanel.vue'
import KpiSummaryCards from '@/components/dashboard/KpiSummaryCards.vue'
import KpiTrendChart from '@/components/dashboard/KpiTrendChart.vue'
import KpiConversionFunnel from '@/components/dashboard/KpiConversionFunnel.vue'

const { t } = useI18n()
const store = useKpiStore()

onMounted(() => {
  // 초기 로드: 기본 필터(마지막 30일, daily)
  void store.loadKpiValues()
})

/** AC-016 — 필터 변경 시 3개 위젯이 동시에 갱신된다(공유 스토어 상태). */
function onFilterChange(params: KpiQueryParams): void {
  void store.loadKpiValues(params)
}

function onReset(): void {
  store.resetFilters()
  void store.loadKpiValues()
}

async function onExport(): Promise<void> {
  await store.exportToExcel()
  if (store.error) {
    ElMessage.error(store.error)
  }
}
</script>
