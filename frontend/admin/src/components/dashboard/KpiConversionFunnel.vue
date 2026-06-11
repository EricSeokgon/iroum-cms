<template>
  <!-- KPI 전환율 차트 (BAR_CHART, PREPARING 인지) — SPEC-CMS-KPI-001 AC-016 -->
  <div class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
    <h3 class="mb-3 text-sm font-semibold text-gray-700">{{ t('kpi.chart.conversionTitle') }}</h3>

    <!-- PREPARING: 모든 항목이 준비 중이거나 데이터 없음 → 안내 메시지만 표시 -->
    <el-alert
      v-if="isPreparing"
      type="info"
      :closable="false"
      show-icon
      :title="t('kpi.state.preparingNotice')"
    />

    <v-chart
      v-else
      :option="chartOption"
      :style="{ height: '260px' }"
      autoresize
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { KpiValueItem } from '@/api/kpi'

// ECharts 모듈러 등록
use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  items: KpiValueItem[]
}>()

const { t } = useI18n()

/**
 * AC-016 PREPARING 처리: 항목이 비어 있거나 모든 항목이 PREPARING(또는 value null)이면
 * 차트 대신 안내 메시지를 표시한다.
 */
const isPreparing = computed(() => {
  if (props.items.length === 0) return true
  return props.items.every((i) => i.dataState === 'PREPARING' || i.value === null)
})

/** dimensionJson 에서 월 라벨을 추출한다. */
function parseMonth(dimensionJson: string): string {
  try {
    const dim = JSON.parse(dimensionJson) as Record<string, string>
    return dim.month ?? dim.date ?? dimensionJson
  } catch {
    return dimensionJson
  }
}

const points = computed(() =>
  [...props.items]
    .filter((i) => i.dataState === 'READY' && i.value !== null)
    .sort((a, b) => a.aggregatedAt.localeCompare(b.aggregatedAt))
    .map((i) => ({ label: parseMonth(i.dimensionJson), value: i.value as number })),
)

const chartOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: (v: number) => `${v.toFixed(1)}%`,
  },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: {
    type: 'category',
    data: points.value.map((p) => p.label),
  },
  yAxis: { type: 'value', name: '%', min: 0, max: 100 },
  series: [
    {
      name: t('kpi.code.POLICY_APPLY_CONVERSION_RATE'),
      type: 'bar',
      data: points.value.map((p) => p.value),
      itemStyle: { color: '#8b5cf6' },
      barMaxWidth: 48,
    },
  ],
}))
</script>
