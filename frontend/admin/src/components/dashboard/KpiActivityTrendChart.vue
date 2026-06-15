<template>
  <!-- 활동 지표 추이 (LINE_CHART: 세션 지속·오류율) — SPEC-CMS-KPI-002 REQ-KPI2-007-2 -->
  <div class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
    <h3 class="mb-3 text-sm font-semibold text-gray-700">{{ t('kpi.chart.activityTitle') }}</h3>

    <div
      v-if="isEmpty"
      class="flex h-[260px] items-center justify-center text-sm text-gray-400"
    >
      {{ t('kpi.chart.empty') }}
    </div>
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
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { KpiValueItem } from '@/api/kpi'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  sessionItems: KpiValueItem[]
  errorRateItems: KpiValueItem[]
}>()

const { t } = useI18n()

function parseLabel(dimensionJson: string): string {
  try {
    const dim = JSON.parse(dimensionJson) as Record<string, string>
    return dim.date ?? dim.month ?? dimensionJson
  } catch {
    return dimensionJson
  }
}

interface Point {
  label: string
  value: number | null
}

function toPoints(items: KpiValueItem[]): Point[] {
  return [...items]
    .sort((a, b) => a.aggregatedAt.localeCompare(b.aggregatedAt))
    .map((i) => ({ label: parseLabel(i.dimensionJson), value: i.value }))
}

const sessionPoints = computed(() => toPoints(props.sessionItems))
const errorPoints = computed(() => toPoints(props.errorRateItems))

const isEmpty = computed(() => sessionPoints.value.length === 0 && errorPoints.value.length === 0)

const categories = computed(() => {
  const set = new Set<string>()
  sessionPoints.value.forEach((p) => set.add(p.label))
  errorPoints.value.forEach((p) => set.add(p.label))
  return Array.from(set).sort()
})

function alignSeries(points: Point[]): (number | null)[] {
  const map = new Map(points.map((p) => [p.label, p.value]))
  return categories.value.map((c) => map.get(c) ?? null)
}

const sessionLabel = computed(() => t('kpi.code.AVG_SESSION_DURATION'))
const errorLabel = computed(() => t('kpi.code.API_ERROR_RATE'))

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: [sessionLabel.value, errorLabel.value] },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: categories.value,
  },
  yAxis: [
    { type: 'value', name: t('kpi.chart.seconds'), position: 'left' },
    { type: 'value', name: '%', min: 0, max: 100, position: 'right' },
  ],
  series: [
    {
      name: sessionLabel.value,
      type: 'line',
      smooth: true,
      yAxisIndex: 0,
      connectNulls: true,
      data: alignSeries(sessionPoints.value),
      itemStyle: { color: '#f59e0b' },
    },
    {
      name: errorLabel.value,
      type: 'line',
      smooth: true,
      yAxisIndex: 1,
      connectNulls: true,
      data: alignSeries(errorPoints.value),
      itemStyle: { color: '#ef4444' },
    },
  ],
}))
</script>
