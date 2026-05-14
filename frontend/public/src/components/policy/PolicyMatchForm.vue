<!--
  SPEC-CMS-PUBLIC-001 T-007 — 정책 매칭 폼
  AC: C-03 (익명 매칭 가능, 401 시 리다이렉트 없음)
-->
<template>
  <form
    class="space-y-4 rounded-md border border-gray-200 bg-white p-6"
    :aria-label="t('policy.matchTitle')"
    data-testid="policy-match-form"
    @submit.prevent="onSubmit"
  >
    <div>
      <label
        for="match-industry"
        class="mb-1 block text-sm font-medium text-content-DEFAULT"
      >
        {{ t('policy.industry') }}
      </label>
      <input
        id="match-industry"
        v-model.trim="form.industry"
        type="text"
        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="match-industry-input"
      />
    </div>

    <div class="grid gap-4 md:grid-cols-3">
      <div>
        <label
          for="match-capital"
          class="mb-1 block text-sm font-medium text-content-DEFAULT"
        >
          {{ t('policy.capitalAmount') }}
        </label>
        <input
          id="match-capital"
          v-model.number="form.capitalAmount"
          type="number"
          min="0"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="match-capital-input"
        />
      </div>
      <div>
        <label
          for="match-revenue"
          class="mb-1 block text-sm font-medium text-content-DEFAULT"
        >
          {{ t('policy.revenueAmount') }}
        </label>
        <input
          id="match-revenue"
          v-model.number="form.revenueAmount"
          type="number"
          min="0"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="match-revenue-input"
        />
      </div>
      <div>
        <label
          for="match-employees"
          class="mb-1 block text-sm font-medium text-content-DEFAULT"
        >
          {{ t('policy.employeeCount') }}
        </label>
        <input
          id="match-employees"
          v-model.number="form.employeeCount"
          type="number"
          min="0"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="match-employees-input"
        />
      </div>
    </div>

    <div>
      <label
        for="match-region"
        class="mb-1 block text-sm font-medium text-content-DEFAULT"
      >
        {{ t('policy.region') }}
      </label>
      <input
        id="match-region"
        v-model.trim="form.region"
        type="text"
        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="match-region-input"
      />
    </div>

    <div class="flex justify-end">
      <button
        type="submit"
        :disabled="submitting"
        class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="match-submit"
      >
        {{ t('policy.matchSubmit') }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PolicyMatchRequest } from '@/api/policyApi'

const props = defineProps<{
  submitting?: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', payload: PolicyMatchRequest): void
}>()

const { t } = useI18n()

const form = reactive<{
  industry: string
  capitalAmount: number | undefined
  revenueAmount: number | undefined
  employeeCount: number | undefined
  region: string
}>({
  industry: '',
  capitalAmount: undefined,
  revenueAmount: undefined,
  employeeCount: undefined,
  region: '',
})

function onSubmit(): void {
  // 빈 값은 요청에서 제외
  const payload: PolicyMatchRequest = {}
  if (form.industry) payload.industry = form.industry
  if (form.capitalAmount !== undefined && form.capitalAmount !== null)
    payload.capitalAmount = form.capitalAmount
  if (form.revenueAmount !== undefined && form.revenueAmount !== null)
    payload.revenueAmount = form.revenueAmount
  if (form.employeeCount !== undefined && form.employeeCount !== null)
    payload.employeeCount = form.employeeCount
  if (form.region) payload.region = form.region
  emit('submit', payload)
}

// suppress unused warning — props is used for binding submitting
void props
</script>
