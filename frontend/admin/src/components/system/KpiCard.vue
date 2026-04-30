<template>
  <!-- KPI 위젯 카드 — SPEC-CMS-005 Bundle D REQ-SYS-001-D -->
  <div
    class="rounded-lg border bg-white p-4 shadow-sm"
    :class="borderClass"
    role="region"
    :aria-label="label"
  >
    <p class="text-xs font-medium uppercase tracking-wide text-gray-500">{{ label }}</p>
    <p class="mt-1 text-3xl font-bold" :class="valueClass">{{ formattedValue }}</p>
    <p v-if="changePct !== undefined" class="mt-1 flex items-center gap-1 text-sm">
      <span :class="changeClass">
        {{ changePct >= 0 ? '+' : '' }}{{ changePct.toFixed(1) }}%
      </span>
      <span class="text-gray-400">{{ t('system.dashboard.kpi.vsYesterday') }}</span>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

type ColorLevel = 'normal' | 'warning' | 'danger'

const props = defineProps<{
  label: string
  value: number | string
  /** 변화율 (%) — 양수: 증가, 음수: 감소 */
  changePct?: number
  /** 색상 수준 (기본: normal) */
  color?: ColorLevel
  /** 값 포맷 함수 (기본: 숫자 천단위 콤마) */
  formatter?: (v: number | string) => string
}>()

const { t } = useI18n()

const formattedValue = computed(() => {
  if (props.formatter) return props.formatter(props.value)
  if (typeof props.value === 'number') return props.value.toLocaleString()
  return props.value
})

const borderClass = computed(() => {
  switch (props.color) {
    case 'warning': return 'border-yellow-300'
    case 'danger':  return 'border-red-300'
    default:        return 'border-gray-200'
  }
})

const valueClass = computed(() => {
  switch (props.color) {
    case 'warning': return 'text-yellow-600'
    case 'danger':  return 'text-red-600'
    default:        return 'text-gray-900'
  }
})

const changeClass = computed(() => {
  if (props.changePct === undefined) return ''
  if (props.changePct > 0) return 'text-green-600'
  if (props.changePct < 0) return 'text-red-600'
  return 'text-gray-500'
})
</script>
