<!--
  SPEC-CMS-PUBLIC-001 T-009 — 공개 KPI 차트 (D-05)
  - 차트 시각화 + <details><summary>차트 데이터 보기</summary><table>...</table></details>
  - CARD: 단순 수치 표시
  - BAR/LINE/PIE: vue-echarts
  - KWCAG 2.2 AA: 데이터 테이블 fallback, aria-label
-->
<template>
  <section
    class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
    :data-testid="`kpi-chart-${widget.code}`"
    :aria-labelledby="`kpi-title-${widget.code}`"
  >
    <h3
      :id="`kpi-title-${widget.code}`"
      class="mb-3 text-base font-semibold text-content-DEFAULT"
    >
      {{ widget.title }}
    </h3>

    <!-- CARD: 단순 KPI 카드 -->
    <div
      v-if="widget.type === 'CARD'"
      class="flex items-baseline gap-2"
      data-testid="kpi-card-value"
    >
      <span class="text-3xl font-bold text-primary-700">{{ cardValue }}</span>
      <span v-if="cardUnit" class="text-base text-content-muted">{{ cardUnit }}</span>
    </div>

    <!-- BAR/LINE/PIE: vue-echarts -->
    <div
      v-else-if="hasChartData"
      class="h-64"
      data-testid="kpi-chart-canvas"
    >
      <v-chart :option="chartOption" autoresize aria-hidden="true" />
    </div>

    <p v-else class="text-sm text-content-muted">{{ t('stats.noData') }}</p>

    <!-- 데이터 테이블 fallback — D-05 -->
    <details
      v-if="hasTableData"
      class="mt-3 text-sm"
      data-testid="kpi-data-toggle"
    >
      <summary
        class="cursor-pointer text-primary-600 hover:underline focus-visible:outline-2 focus-visible:outline-primary-600"
      >
        {{ t('stats.dataTableToggle') }}
      </summary>
      <table
        class="mt-2 w-full border-collapse text-left"
        :aria-label="t('stats.dataTableLabel', { title: widget.title })"
        data-testid="kpi-data-table"
      >
        <thead>
          <tr class="border-b border-gray-200 bg-surface-muted">
            <th class="px-2 py-1 text-xs font-semibold">{{ t('stats.category') }}</th>
            <th class="px-2 py-1 text-xs font-semibold">{{ t('stats.value') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(row, idx) in tableRows"
            :key="idx"
            class="border-b border-gray-100"
          >
            <td class="px-2 py-1">{{ row.category }}</td>
            <td class="px-2 py-1">{{ row.value }}</td>
          </tr>
        </tbody>
      </table>
    </details>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { use } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import type { WidgetData } from '@/api/statsApi'

// @MX:NOTE: [AUTO] ECharts 등록 — Bar/Line/Pie 만 사용 (번들 사이즈 절감)
use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  CanvasRenderer,
])

const props = defineProps<{
  widget: WidgetData
}>()

const { t } = useI18n()

// 데이터 형태별 가드
interface BarLineData {
  categories: string[]
  values: number[]
}
interface PieData {
  names: string[]
  values: number[]
}
interface CardData {
  value: number
  label?: string
  unit?: string
}

function isBarLineData(d: unknown): d is BarLineData {
  if (!d || typeof d !== 'object') return false
  const obj = d as Record<string, unknown>
  return Array.isArray(obj.categories) && Array.isArray(obj.values)
}
function isPieData(d: unknown): d is PieData {
  if (!d || typeof d !== 'object') return false
  const obj = d as Record<string, unknown>
  return Array.isArray(obj.names) && Array.isArray(obj.values)
}
function isCardData(d: unknown): d is CardData {
  if (!d || typeof d !== 'object') return false
  const obj = d as Record<string, unknown>
  return typeof obj.value === 'number'
}

const cardValue = computed(() => {
  if (props.widget.type === 'CARD' && isCardData(props.widget.data)) {
    return props.widget.data.value.toLocaleString()
  }
  return ''
})

const cardUnit = computed(() => {
  if (props.widget.type === 'CARD' && isCardData(props.widget.data)) {
    return props.widget.data.unit ?? ''
  }
  return ''
})

const tableRows = computed<Array<{ category: string; value: number | string }>>(() => {
  if (props.widget.type === 'CARD' && isCardData(props.widget.data)) {
    return [{ category: props.widget.data.label ?? props.widget.title, value: props.widget.data.value }]
  }
  if ((props.widget.type === 'BAR' || props.widget.type === 'LINE') && isBarLineData(props.widget.data)) {
    return props.widget.data.categories.map((c, i) => ({
      category: c,
      value: props.widget.data ? (props.widget.data as BarLineData).values[i] ?? 0 : 0,
    }))
  }
  if (props.widget.type === 'PIE' && isPieData(props.widget.data)) {
    return props.widget.data.names.map((n, i) => ({
      category: n,
      value: props.widget.data ? (props.widget.data as PieData).values[i] ?? 0 : 0,
    }))
  }
  return []
})

const hasTableData = computed(() => tableRows.value.length > 0)

const hasChartData = computed(() => {
  if (props.widget.type === 'BAR' || props.widget.type === 'LINE') {
    return isBarLineData(props.widget.data) && props.widget.data.categories.length > 0
  }
  if (props.widget.type === 'PIE') {
    return isPieData(props.widget.data) && props.widget.data.names.length > 0
  }
  return false
})

const chartOption = computed(() => {
  if ((props.widget.type === 'BAR' || props.widget.type === 'LINE') && isBarLineData(props.widget.data)) {
    return {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: props.widget.data.categories },
      yAxis: { type: 'value' },
      series: [
        {
          type: props.widget.type === 'BAR' ? 'bar' : 'line',
          data: props.widget.data.values,
          itemStyle: { color: '#2563eb' },
        },
      ],
    }
  }
  if (props.widget.type === 'PIE' && isPieData(props.widget.data)) {
    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: '60%',
          data: props.widget.data.names.map((n, i) => ({
            name: n,
            value: props.widget.data ? (props.widget.data as PieData).values[i] ?? 0 : 0,
          })),
        },
      ],
    }
  }
  return {}
})
</script>
