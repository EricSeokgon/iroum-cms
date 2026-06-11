<template>
  <!-- KPI 필터 패널 — SPEC-CMS-KPI-001 AC-016 -->
  <div class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
    <div class="flex flex-wrap items-end gap-4">
      <!-- 기간 (fromDate ~ toDate) -->
      <div>
        <label class="mb-1 block text-xs font-semibold text-gray-500">{{ t('kpi.filter.period') }}</label>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          unlink-panels
          range-separator="~"
          :start-placeholder="t('kpi.filter.fromDate')"
          :end-placeholder="t('kpi.filter.toDate')"
          value-format="YYYY-MM-DD"
          :clearable="false"
        />
      </div>

      <!-- 집계 단위 -->
      <div>
        <label class="mb-1 block text-xs font-semibold text-gray-500">{{ t('kpi.filter.granularity') }}</label>
        <el-select v-model="granularity" :placeholder="t('kpi.filter.granularity')" style="width: 140px">
          <el-option
            v-for="g in granularityOptions"
            :key="g"
            :label="t(`kpi.granularity.${g}`)"
            :value="g"
          />
        </el-select>
      </div>

      <!-- KPI 코드 필터 (선택) -->
      <div>
        <label class="mb-1 block text-xs font-semibold text-gray-500">{{ t('kpi.filter.kpiCode') }}</label>
        <el-select
          v-model="kpiCode"
          clearable
          :placeholder="t('kpi.filter.allKpi')"
          style="width: 220px"
        >
          <el-option
            v-for="code in kpiCodeOptions"
            :key="code"
            :label="t(`kpi.code.${code}`)"
            :value="code"
          />
        </el-select>
      </div>

      <!-- 버튼 -->
      <div class="flex gap-2">
        <el-button type="primary" :loading="loading" @click="onApply">
          {{ t('kpi.filter.apply') }}
        </el-button>
        <el-button :disabled="loading" @click="onReset">
          {{ t('kpi.filter.reset') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { KPI_CODES, type KpiGranularity, type KpiQueryParams } from '@/api/kpi'

const props = defineProps<{
  initial: KpiQueryParams
  loading?: boolean
}>()

const emit = defineEmits<{
  'filter-change': [params: KpiQueryParams]
  reset: []
}>()

const { t } = useI18n()

const granularityOptions: KpiGranularity[] = ['daily', 'weekly', 'monthly']
const kpiCodeOptions = Object.values(KPI_CODES)

// ── 로컬 폼 상태 (initial 로 초기화) ──────────────────────────────────────────
const dateRange = ref<[string, string]>([props.initial.fromDate, props.initial.toDate])
const granularity = ref<KpiGranularity>(props.initial.granularity ?? 'daily')
const kpiCode = ref<string | undefined>(props.initial.kpiCode)

const params = computed<KpiQueryParams>(() => ({
  fromDate: dateRange.value?.[0] ?? props.initial.fromDate,
  toDate: dateRange.value?.[1] ?? props.initial.toDate,
  granularity: granularity.value,
  kpiCode: kpiCode.value || undefined,
  page: 0,
  size: 100,
}))

function onApply(): void {
  emit('filter-change', { ...params.value })
}

function onReset(): void {
  const today = new Date()
  const from = new Date(today)
  from.setDate(from.getDate() - 30)
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  dateRange.value = [fmt(from), fmt(today)]
  granularity.value = 'daily'
  kpiCode.value = undefined
  emit('reset')
}
</script>
