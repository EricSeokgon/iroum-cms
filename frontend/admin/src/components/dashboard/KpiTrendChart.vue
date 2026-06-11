<template>
  <!-- KPI 추이 차트 (LINE_CHART) — SPEC-CMS-KPI-001 AC-016 -->
  <div class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
    <h3 class="mb-3 text-sm font-semibold text-gray-700">{{ t('kpi.chart.trendTitle') }}</h3>

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

// ECharts 모듈러 등록 (DashboardTrendChart.vue 패턴과 동일)
use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  featureItems: KpiValueItem[]
  downloadItems: KpiValueItem[]
}>()

const { t } = useI18n()

/** dimensionJson 에서 X축 라벨(날짜/월)을 추출한다. */
function parseLabel(dimensionJson: string): string {
  try {
    const dim = JSON.parse(dimensionJson) as Record<string, string>
    return dim.date ?? dim.month ?? dim.week ?? dimensionJson
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

const featurePoints = computed(() => toPoints(props.featureItems))
const downloadPoints = computed(() => toPoints(props.downloadItems))

const isEmpty = computed(() => featurePoints.value.length === 0 && downloadPoints.value.length === 0)

/** 두 시리즈의 라벨을 합집합으로 정렬해 공통 X축을 만든다. */
const categories = computed(() => {
  const set = new Set<string>()
  featurePoints.value.forEach((p) => set.add(p.label))
  downloadPoints.value.forEach((p) => set.add(p.label))
  return Array.from(set).sort()
})

function alignSeries(points: Point[]): (number | null)[] {
  const map = new Map(points.map((p) => [p.label, p.value]))
  return categories.value.map((c) => map.get(c) ?? null)
}

const featureLabel = computed(() => t('kpi.code.FEATURE_USAGE_RATE'))
const downloadLabel = computed(() => t('kpi.code.FILE_DOWNLOAD_COUNT'))

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: [featureLabel.value, downloadLabel.value] },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: categories.value,
  },
  yAxis: [
    { type: 'value', name: '%', min: 0, max: 100, position: 'left' },
    { type: 'value', name: t('kpi.chart.count'), position: 'right' },
  ],
  series: [
    {
      name: featureLabel.value,
      type: 'line',
      smooth: true,
      yAxisIndex: 0,
      connectNulls: true,
      data: alignSeries(featurePoints.value),
      itemStyle: { color: '#3b82f6' },
    },
    {
      name: downloadLabel.value,
      type: 'line',
      smooth: true,
      yAxisIndex: 1,
      connectNulls: true,
      data: alignSeries(downloadPoints.value),
      itemStyle: { color: '#10b981' },
    },
  ],
}))
</script>
