<template>
  <!-- 비밀번호 강도 미터 — KWCAG 1.3.3 (색상 외 텍스트 라벨 병행 표시) -->
  <div class="mt-2" aria-live="polite">
    <el-progress
      :percentage="percentage"
      :color="color"
      :stroke-width="6"
      :show-text="false"
      role="progressbar"
      :aria-valuenow="strength"
      aria-valuemin="0"
      aria-valuemax="4"
      :aria-label="t('account.password.strength.label')"
    />
    <span class="mt-1 text-xs" :class="textColor" aria-atomic="true">
      {{ label }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  password: string
}>()

// 강도 계산 — 4가지 기준 각각 1점 (최대 4점)
// @MX:NOTE: [AUTO] 정책: 길이(8자+), 소문자, 대문자, 숫자, 특수문자 중 길이 필수 + 나머지 4항목 충족 수
const strength = computed<number>(() => {
  const p = props.password
  if (!p) return 0
  let score = 0
  if (p.length >= 8) score++
  if (/[a-z]/.test(p)) score++
  if (/[A-Z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  // 5점 만점을 4단계로 환산
  if (score <= 1) return 1
  if (score === 2) return 2
  if (score === 3) return 3
  return 4
})

const percentage = computed(() => {
  if (!props.password) return 0
  return (strength.value / 4) * 100
})

const color = computed(() => {
  switch (strength.value) {
    case 1: return '#ef4444' // red-500
    case 2: return '#f97316' // orange-500
    case 3: return '#eab308' // yellow-500
    case 4: return '#22c55e' // green-500
    default: return '#e5e7eb'
  }
})

const textColor = computed(() => {
  switch (strength.value) {
    case 1: return 'text-red-500'
    case 2: return 'text-orange-500'
    case 3: return 'text-yellow-500'
    case 4: return 'text-green-500'
    default: return 'text-gray-400'
  }
})

const label = computed(() => {
  if (!props.password) return ''
  switch (strength.value) {
    case 1: return t('account.password.strength.veryWeak')
    case 2: return t('account.password.strength.weak')
    case 3: return t('account.password.strength.fair')
    case 4: return t('account.password.strength.good')
    default: return ''
  }
})
</script>
