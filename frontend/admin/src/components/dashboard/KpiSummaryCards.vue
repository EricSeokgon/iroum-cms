<template>
  <!-- KPI 요약 카드 (METRIC_CARD ×3) — SPEC-CMS-KPI-001 AC-016 -->
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
        <el-tag v-if="card.preparing" type="info" size="small" effect="plain">
          {{ t('kpi.state.preparing') }}
        </el-tag>
      </div>

      <p class="mt-2 text-3xl font-bold text-gray-900">
        <span v-if="card.preparing" class="text-gray-400">—</span>
        <span v-else>{{ card.displayValue }}</span>
      </p>

      <p
        v-if="!card.preparing && card.trendPct !== null"
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
  items: KpiValueItem[]
}>()

const { t } = useI18n()

interface SummaryCard {
  kpiCode: string
  label: string
  displayValue: string
  trendPct: number | null
  preparing: boolean
}

/** 백분율 KPI 코드 — 값 포맷에 % 를 붙인다. */
const PERCENT_CODES = new Set<string>([
  KPI_CODES.FEATURE_USAGE_RATE,
  KPI_CODES.POLICY_APPLY_CONVERSION_RATE,
])

const CARD_ORDER = [
  KPI_CODES.FEATURE_USAGE_RATE,
  KPI_CODES.FILE_DOWNLOAD_COUNT,
  KPI_CODES.POLICY_APPLY_CONVERSION_RATE,
]

/** aggregatedAt 오름차순 정렬 — 마지막 항목이 최신 값. */
function sortByTime(items: KpiValueItem[]): KpiValueItem[] {
  return [...items].sort((a, b) => a.aggregatedAt.localeCompare(b.aggregatedAt))
}

function formatValue(code: string, value: number): string {
  if (PERCENT_CODES.has(code)) return `${value.toFixed(1)}%`
  return value.toLocaleString()
}

const cards = computed<SummaryCard[]>(() =>
  CARD_ORDER.map((code) => {
    const series = sortByTime(props.items.filter((i) => i.kpiCode === code))
    const latest = series[series.length - 1]
    const previous = series[series.length - 2]

    // 데이터 없음 또는 PREPARING → 준비 중 카드
    const preparing = !latest || latest.dataState === 'PREPARING' || latest.value === null

    let trendPct: number | null = null
    if (!preparing && latest && previous && previous.value !== null && previous.value !== 0) {
      trendPct = ((latest.value! - previous.value) / previous.value) * 100
    }

    return {
      kpiCode: code,
      label: t(`kpi.code.${code}`),
      displayValue: latest && latest.value !== null ? formatValue(code, latest.value) : '—',
      trendPct,
      preparing,
    }
  }),
)

function trendClass(pct: number): string {
  if (pct > 0) return 'text-green-600'
  if (pct < 0) return 'text-red-600'
  return 'text-gray-500'
}
</script>
