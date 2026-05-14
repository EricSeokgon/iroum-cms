<!--
  SPEC-CMS-PUBLIC-001 T-008 — 검색 결과 타입 필터 탭 (D-01)
  - 6 탭 ALL/POST/FAQ/QNA/POLICY/SAFETY
  - 각 탭에 facets 카운트 뱃지
  - KWCAG 2.2: role="tablist", role="tab", aria-selected
-->
<template>
  <div
    class="flex flex-wrap items-center gap-1 border-b border-gray-200"
    role="tablist"
    :aria-label="t('search.title')"
    data-testid="search-filter-tabs"
  >
    <button
      v-for="t_ in tabs"
      :key="t_.value"
      type="button"
      role="tab"
      :aria-selected="modelValue === t_.value"
      :data-testid="`search-tab-${t_.value}`"
      class="border-b-2 px-4 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-primary-600"
      :class="
        modelValue === t_.value
          ? 'border-primary-600 text-primary-700'
          : 'border-transparent text-content-muted hover:text-content-DEFAULT'
      "
      @click="$emit('update:modelValue', t_.value)"
    >
      <span>{{ t_.label }}</span>
      <span
        v-if="getCount(t_.value) > 0"
        class="ml-2 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-content-muted"
        :data-testid="`search-tab-count-${t_.value}`"
      >
        {{ getCount(t_.value) }}
      </span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SearchType } from '@/api/searchApi'

const props = defineProps<{
  modelValue: SearchType
  facets?: Record<string, number>
  totalCount?: number
}>()

defineEmits<{
  (e: 'update:modelValue', value: SearchType): void
}>()

const { t } = useI18n()

const tabs = computed<Array<{ value: SearchType; label: string }>>(() => [
  { value: 'ALL', label: t('search.typeAll') },
  { value: 'POST', label: t('search.typePost') },
  { value: 'FAQ', label: t('search.typeFaq') },
  { value: 'QNA', label: t('search.typeQna') },
  { value: 'POLICY', label: t('search.typePolicy') },
  { value: 'SAFETY', label: t('search.typeSafety') },
])

function getCount(value: SearchType): number {
  if (value === 'ALL') return props.totalCount ?? 0
  return props.facets?.[value] ?? 0
}
</script>
