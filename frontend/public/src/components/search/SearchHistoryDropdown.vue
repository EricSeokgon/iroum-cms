<!--
  SPEC-CMS-PUBLIC-001 T-008 — 검색어 히스토리 드롭다운 (D-04)
  - localStorage['public.search.history'] (최대 5 개, dedup, 최근순)
  - 빈 입력 + 포커스 시 드롭다운 표시
  - 각 항목 X 삭제 / 전체 삭제 버튼
-->
<template>
  <div
    v-if="open && items.length > 0"
    class="absolute left-0 right-0 top-full z-50 mt-1 max-h-64 overflow-y-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg"
    role="listbox"
    :aria-label="t('search.history')"
    data-testid="search-history-dropdown"
  >
    <div class="flex items-center justify-between px-3 py-1 text-xs font-medium text-content-muted">
      <span>{{ t('search.history') }}</span>
      <button
        type="button"
        class="text-primary-600 hover:underline focus-visible:outline-2 focus-visible:outline-primary-600"
        data-testid="search-history-clear-all"
        @mousedown.prevent
        @click="$emit('clearAll')"
      >
        {{ t('search.historyClearAll') }}
      </button>
    </div>
    <ul>
      <li
        v-for="(q, idx) in items"
        :key="q + idx"
        class="flex items-center justify-between gap-2 px-3 py-2 hover:bg-surface-muted"
        role="option"
        :aria-selected="false"
      >
        <button
          type="button"
          class="flex-1 truncate text-left text-sm text-content-DEFAULT focus-visible:outline-2 focus-visible:outline-primary-600"
          :data-testid="`search-history-item-${idx}`"
          @mousedown.prevent
          @click="$emit('select', q)"
        >
          {{ q }}
        </button>
        <button
          type="button"
          class="rounded p-1 text-content-muted hover:bg-gray-100 hover:text-content-DEFAULT focus-visible:outline-2 focus-visible:outline-primary-600"
          :aria-label="t('search.historyRemove', { q })"
          :data-testid="`search-history-remove-${idx}`"
          @mousedown.prevent
          @click="$emit('remove', q)"
        >
          <span aria-hidden="true">×</span>
        </button>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  open: boolean
  items: string[]
}>()

defineEmits<{
  (e: 'select', q: string): void
  (e: 'remove', q: string): void
  (e: 'clearAll'): void
}>()

const { t } = useI18n()
</script>
