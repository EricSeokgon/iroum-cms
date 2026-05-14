<!--
  SPEC-CMS-PUBLIC-001 T-007 — 정책 다중 필터 바
  AC: C-01 (industry, region, type 다중 필터 + URL 동기화)
  - industry: 단일 선택 셀렉트 (필요 시 다중으로 확장 가능)
  - region: 단일 선택 셀렉트
  - type: 체크박스 (단일 선택으로 단순화 — 다중은 v-model 배열로 확장 가능)
-->
<template>
  <form
    class="flex flex-col gap-3 rounded-md border border-gray-200 bg-white p-4 md:flex-row md:items-end md:flex-wrap"
    role="search"
    :aria-label="t('common.search')"
    data-testid="policy-filter-bar"
    @submit.prevent="onApply"
  >
    <div class="flex-1 min-w-[150px]">
      <label
        for="policy-industry-select"
        class="mb-1 block text-sm font-medium text-content-DEFAULT"
      >
        {{ t('policy.industry') }}
      </label>
      <select
        id="policy-industry-select"
        v-model="local.industry"
        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="policy-industry-select"
      >
        <option value="">{{ t('policy.filterAll') }}</option>
        <option v-for="opt in industryOptions" :key="opt" :value="opt">{{ opt }}</option>
      </select>
    </div>

    <div class="flex-1 min-w-[150px]">
      <label
        for="policy-region-select"
        class="mb-1 block text-sm font-medium text-content-DEFAULT"
      >
        {{ t('policy.region') }}
      </label>
      <select
        id="policy-region-select"
        v-model="local.region"
        class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="policy-region-select"
      >
        <option value="">{{ t('policy.regionAll') }}</option>
        <option v-for="opt in regionOptions" :key="opt" :value="opt">{{ opt }}</option>
      </select>
    </div>

    <div class="flex-1 min-w-[150px]">
      <fieldset>
        <legend class="mb-1 text-sm font-medium text-content-DEFAULT">{{ t('policy.type') }}</legend>
        <div class="flex flex-wrap gap-3">
          <label
            v-for="opt in typeOptions"
            :key="opt"
            class="inline-flex items-center gap-1 text-sm text-content-DEFAULT"
          >
            <input
              type="checkbox"
              :value="opt"
              :checked="local.type === opt"
              class="h-4 w-4 rounded border-gray-300 text-primary-600"
              :data-testid="`policy-type-${opt}`"
              @change="toggleType(opt, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ opt }}</span>
          </label>
        </div>
      </fieldset>
    </div>

    <div class="flex items-center gap-2">
      <button
        type="submit"
        class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="policy-filter-apply"
      >
        {{ t('notice.searchSubmit') }}
      </button>
      <button
        type="button"
        class="rounded-md border border-gray-300 px-4 py-2 text-sm text-content-DEFAULT hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="policy-filter-reset"
        @click="onReset"
      >
        {{ t('policy.resetFilters') }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'

export interface PolicyFilters {
  industry: string
  region: string
  type: string
}

const props = defineProps<{
  modelValue: PolicyFilters
  industryOptions?: string[]
  regionOptions?: string[]
  typeOptions?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: PolicyFilters): void
  (e: 'apply', value: PolicyFilters): void
  (e: 'reset'): void
}>()

const { t } = useI18n()

const local = reactive<PolicyFilters>({
  industry: props.modelValue.industry,
  region: props.modelValue.region,
  type: props.modelValue.type,
})

watch(
  () => props.modelValue,
  (next) => {
    local.industry = next.industry
    local.region = next.region
    local.type = next.type
  },
  { deep: true },
)

function toggleType(value: string, checked: boolean): void {
  local.type = checked ? value : ''
}

function onApply(): void {
  emit('update:modelValue', { ...local })
  emit('apply', { ...local })
}

function onReset(): void {
  local.industry = ''
  local.region = ''
  local.type = ''
  emit('update:modelValue', { ...local })
  emit('reset')
}
</script>
