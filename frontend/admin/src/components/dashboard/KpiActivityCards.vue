<template>
  <!-- 운영 활동 요약 카드 (METRIC_CARD ×3: DAU/MAU/오류율) — SPEC-CMS-KPI-002 REQ-KPI2-007-1 -->
  <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
    <div
      v-for="card in cards"
      :key="card.kpiCode"
      class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
      role="region"
      :aria-label="card.label"
    >
      <div class="flex items-center justify-between">
        <p class="text-xs font-medium uppercase tracking-wide text-gray-500">{{ card.label }}</p>
      </div>

      <p class="mt-2 text-3xl font-bold text-gray-900">
        <span v-if="card.empty" class="text-gray-400">—</span>
        <span v-else>{{ card.displayValue }}</span>
      </p>

      <p
        v-if="!card.empty && card.trendPct !== null"
        class="mt-1 flex items-center gap-1 text-sm"
      >
        <span :class="trendClass(card.trendPct)">
          {{ card.trendPct > 0 ? '▲' : card.trendPct < 0 ? '▼' : '–' }}
          {{ Math.abs(card.trendPct).toFixed(1) }}%
        </span>
        <span class="text-gray-400">{{ t('kpi.card.vsPrevious') }}</span>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { KPI_CODES, type KpiValueItem } from '@/api/kpi'

const props = defineProps<{
  dauItems: KpiValueItem[]
  mauItems: KpiValueItem[]
  errorRateItems: KpiValueItem[]
}>()

const { t } = useI18n()

interface ActivityCard {
  kpiCode: string
  label: string
  displayValue: string
  trendPct: number | null
  empty: boolean
}

function sortByTime(items: KpiValueItem[]): KpiValueItem[] {
  return [...items].sort((a, b) => a.aggregatedAt.localeCompare(b.aggregatedAt))
}

function buildCard(code: string, items: KpiValueItem[], formatter: (v: number) => string): ActivityCard {
  const series = sortByTime(items)
  const latest = series[series.length - 1]
  const previous = series[series.length - 2]
  const empty = !latest || latest.value === null

  let trendPct: number | null = null
  if (!empty && latest && previous && previous.value !== null && previous.value !== 0) {
    trendPct = ((latest.value! - previous.value) / previous.value) * 100
  }

  return {
    kpiCode: code,
    label: t(`kpi.code.${code}`),
    displayValue: latest && latest.value !== null ? formatter(latest.value) : '—',
    trendPct,
    empty,
  }
}

const cards = computed<ActivityCard[]>(() => [
  buildCard(KPI_CODES.DAU, props.dauItems, (v) => v.toLocaleString()),
  buildCard(KPI_CODES.MAU, props.mauItems, (v) => v.toLocaleString()),
  buildCard(KPI_CODES.API_ERROR_RATE, props.errorRateItems, (v) => `${v.toFixed(2)}%`),
])

function trendClass(pct: number): string {
  if (pct > 0) return 'text-green-600'
  if (pct < 0) return 'text-red-600'
  return 'text-gray-500'
}
</script>
