<!--
  SPEC-CMS-SIM-001 — 공개 창업 시뮬레이션 위저드 (1단계: 입력)
  비회원 허용 (meta.requiresAuth 미설정). 제출 시 결과 화면으로 리다이렉트.
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('simulation.title') }}</h1>
      <p class="mt-1 text-sm text-content-muted">{{ t('simulation.subtitle') }}</p>
    </header>

    <form
      data-testid="simulation-form"
      class="grid grid-cols-1 gap-4 sm:grid-cols-2"
      @submit.prevent="onSubmit"
    >
      <!-- 업종코드(KSIC) -->
      <div class="space-y-1">
        <label for="sim-ksic" class="block text-sm font-medium text-content-DEFAULT">
          {{ t('simulation.ksicLabel') }}
        </label>
        <input
          id="sim-ksic"
          v-model="form.ksicCode"
          data-testid="simulation-ksic"
          type="text"
          maxlength="5"
          required
          :placeholder="t('simulation.ksicPlaceholder')"
          class="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
      </div>

      <!-- 자본금(원) -->
      <div class="space-y-1">
        <label for="sim-capital" class="block text-sm font-medium text-content-DEFAULT">
          {{ t('simulation.capitalLabel') }}
        </label>
        <input
          id="sim-capital"
          v-model.number="form.capitalAmount"
          data-testid="simulation-capital"
          type="number"
          min="0"
          required
          class="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
      </div>

      <!-- 설립연도 -->
      <div class="space-y-1">
        <label for="sim-founding-year" class="block text-sm font-medium text-content-DEFAULT">
          {{ t('simulation.foundingYearLabel') }}
        </label>
        <input
          id="sim-founding-year"
          v-model.number="form.foundingYear"
          data-testid="simulation-founding-year"
          type="number"
          min="2000"
          :max="currentYear"
          required
          class="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
      </div>

      <!-- 예상 매출(원) -->
      <div class="space-y-1">
        <label for="sim-revenue" class="block text-sm font-medium text-content-DEFAULT">
          {{ t('simulation.revenueLabel') }}
        </label>
        <input
          id="sim-revenue"
          v-model.number="form.revenueAmount"
          data-testid="simulation-revenue"
          type="number"
          min="0"
          required
          class="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
      </div>

      <!-- 직원 수(선택) -->
      <div class="space-y-1">
        <label for="sim-employee" class="block text-sm font-medium text-content-DEFAULT">
          {{ t('simulation.employeeLabel') }}
        </label>
        <input
          id="sim-employee"
          v-model.number="form.employeeCount"
          data-testid="simulation-employee"
          type="number"
          min="0"
          class="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
      </div>

      <!-- 투영 기간 -->
      <div class="space-y-1">
        <label for="sim-horizon" class="block text-sm font-medium text-content-DEFAULT">
          {{ t('simulation.horizonLabel') }}
        </label>
        <select
          id="sim-horizon"
          v-model.number="form.horizonYears"
          data-testid="simulation-horizon"
          required
          class="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        >
          <option :value="3">{{ t('simulation.horizon3') }}</option>
          <option :value="5">{{ t('simulation.horizon5') }}</option>
        </select>
      </div>

      <!-- 입력 오류 메시지 -->
      <p
        v-if="inputError"
        data-testid="simulation-input-error"
        class="text-sm text-red-600 sm:col-span-2"
      >
        {{ inputError }}
      </p>

      <!-- 서버 오류 메시지 -->
      <p
        v-if="store.error"
        data-testid="simulation-server-error"
        class="text-sm text-red-600 sm:col-span-2"
      >
        {{ t('simulation.error') }}
      </p>

      <div class="sm:col-span-2">
        <button
          type="submit"
          data-testid="simulation-submit"
          :disabled="store.loading"
          class="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
        >
          {{ store.loading ? t('simulation.submitting') : t('simulation.submit') }}
        </button>
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useSimulationStore } from '@/stores/simulationStore'
import type { SimulationStartRequest } from '@/api/simulationApi'

const { t } = useI18n()
const router = useRouter()
const store = useSimulationStore()

const currentYear = new Date().getFullYear()
const inputError = ref('')

// 폼 상태 — number 입력은 빈 값 시 undefined 가능 (검증에서 처리)
const form = reactive<{
  ksicCode: string
  capitalAmount: number | undefined
  foundingYear: number | undefined
  revenueAmount: number | undefined
  employeeCount: number | undefined
  horizonYears: 3 | 5
}>({
  ksicCode: '',
  capitalAmount: undefined,
  foundingYear: undefined,
  revenueAmount: undefined,
  employeeCount: undefined,
  horizonYears: 3,
})

function validate(): SimulationStartRequest | null {
  inputError.value = ''
  const ksic = form.ksicCode.trim()
  if (ksic.length !== 5) {
    inputError.value = t('simulation.ksicInvalid')
    return null
  }
  if (form.capitalAmount === undefined || form.capitalAmount < 0) {
    inputError.value = t('simulation.capitalRequired')
    return null
  }
  if (
    form.foundingYear === undefined ||
    form.foundingYear < 2000 ||
    form.foundingYear > currentYear
  ) {
    inputError.value = t('simulation.foundingYearInvalid')
    return null
  }
  if (form.revenueAmount === undefined || form.revenueAmount < 0) {
    inputError.value = t('simulation.revenueRequired')
    return null
  }
  return {
    ksicCode: ksic,
    capitalAmount: form.capitalAmount,
    foundingYear: form.foundingYear,
    revenueAmount: form.revenueAmount,
    employeeCount: form.employeeCount,
    horizonYears: form.horizonYears,
  }
}

async function onSubmit(): Promise<void> {
  const req = validate()
  if (!req) return
  try {
    store.clearResult()
    const sessionId = await store.startSimulation(req)
    router.push({ name: 'simulation-result', params: { sessionId } })
  } catch {
    // 서버 오류는 store.error로 표시 — 사용자 입력 보존
  }
}

defineExpose({ form, onSubmit, inputError })
</script>
