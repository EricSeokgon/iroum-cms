<!--
  SPEC-CMS-PUBLIC-001 T-007 — 안전 가이드 체크리스트 (읽기 전용)
  AC: C-05 (ul[role=list] + disabled checkboxes, jest-axe 통과)
-->
<template>
  <ul role="list" class="space-y-2" data-testid="safety-checklist">
    <li
      v-for="item in items"
      :key="item.id"
      class="flex items-start gap-3 rounded-md border border-gray-200 bg-surface-muted px-3 py-2"
      data-testid="safety-checklist-item"
    >
      <input
        type="checkbox"
        :id="`checklist-item-${item.id}`"
        disabled
        class="mt-1 h-4 w-4 rounded border-gray-300 text-primary-600"
        :aria-label="item.text"
        data-testid="safety-checklist-checkbox"
      />
      <label
        :for="`checklist-item-${item.id}`"
        class="text-sm text-content-DEFAULT"
      >
        {{ item.text }}
      </label>
    </li>
  </ul>
</template>

<script setup lang="ts">
defineProps<{
  items: ReadonlyArray<{ id: number; text: string; order: number }>
}>()
</script>
