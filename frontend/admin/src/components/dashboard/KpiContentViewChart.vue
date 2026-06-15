<template>
  <!-- 콘텐츠 유형별 조회 수 (BAR_CHART) — SPEC-CMS-KPI-002 REQ-KPI2-007-3 -->
  <div class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
    <h3 class="mb-3 text-sm font-semibold text-gray-700">
      {{ t('kpi.chart.contentViewTitle') }}
    </h3>

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
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { KpiValueItem } from '@/api/kpi'

use([BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  /** CONTENT_VIEW 항목 (dimension={date,contentType}). */
  items: KpiValueItem[]
}>()

const { t } = useI18n()

const CONTENT_TYPES = ['notice', 'post', 'publication'] as const

function parseType(dimensionJson: string): string | null {
  try {
    const dim = JSON.parse(dimensionJson) as Record<string, string>
    return dim.contentType ?? null
  } catch {
    return null
  }
}

/** 유형별 조회 수 합산 (기간 내 모든 일자 누적). */
const totals = computed<Record<string, number>>(() => {
  const acc: Record<string, number> = { notice: 0, post: 0, publication: 0 }
  for (const item of props.items) {
    const type = parseType(item.dimensionJson)
    if (type && type in acc && item.value !== null) {
      acc[type] += item.value
    }
  }
  return acc
})

const isEmpty = computed(() =>
  CONTENT_TYPES.every((type) => totals.value[type] === 0),
)

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: {
    type: 'category',
    data: CONTENT_TYPES.map((type) => t(`kpi.contentType.${type}`)),
  },
  yAxis: { type: 'value', name: t('kpi.chart.count') },
  series: [
    {
      name: t('kpi.code.CONTENT_VIEW'),
      type: 'bar',
      data: CONTENT_TYPES.map((type) => totals.value[type]),
      itemStyle: { color: '#6366f1' },
      barWidth: '50%',
    },
  ],
}))
</script>
