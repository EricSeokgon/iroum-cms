<template>
  <!-- 대시보드 추이 차트 — vue-echarts 기반 시계열 (SPEC-CMS-005) -->
  <div class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
    <div class="mb-3 flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-700">{{ t('system.dashboard.chart.title') }}</h3>
      <div class="flex gap-1">
        <el-button
          v-for="d in [7, 30, 90] as const"
          :key="d"
          size="small"
          :type="days === d ? 'primary' : 'default'"
          @click="emit('update:days', d)"
        >
          {{ d }}{{ t('system.dashboard.chart.days') }}
        </el-button>
      </div>
    </div>
    <v-chart
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
import type { TrendItemResponse } from '@/api/system'

// ECharts 모듈러 등록
use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  items: TrendItemResponse[]
  days: 7 | 30 | 90
}>()

const emit = defineEmits<{
  'update:days': [days: 7 | 30 | 90]
}>()

const { t } = useI18n()

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: {
    data: [
      t('system.dashboard.chart.visits'),
      t('system.dashboard.chart.pageViews'),
      t('system.dashboard.chart.errors'),
    ],
  },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: props.items.map(i => i.date),
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: t('system.dashboard.chart.visits'),
      type: 'line',
      smooth: true,
      data: props.items.map(i => i.visits),
      itemStyle: { color: '#3b82f6' },
    },
    {
      name: t('system.dashboard.chart.pageViews'),
      type: 'line',
      smooth: true,
      data: props.items.map(i => i.page_views),
      itemStyle: { color: '#10b981' },
    },
    {
      name: t('system.dashboard.chart.errors'),
      type: 'line',
      smooth: true,
      data: props.items.map(i => i.errors),
      itemStyle: { color: '#ef4444' },
    },
  ],
}))
</script>
