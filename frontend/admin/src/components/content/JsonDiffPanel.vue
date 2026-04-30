<template>
  <!-- JSON 변경 diff 패널 — 좌우 분할 카드 + 변경 필드 하이라이트 -->
  <div class="grid grid-cols-2 gap-3">
    <!-- 이전 버전 -->
    <div class="rounded border border-gray-200 bg-gray-50 p-3">
      <div class="mb-2 text-xs font-medium text-gray-500">{{ t('content.diff.before') }}</div>
      <div class="space-y-1 text-xs font-mono">
        <div
          v-for="key in allKeys"
          :key="`left-${key}`"
          class="rounded px-1 py-0.5"
          :class="isChanged(key) ? 'bg-red-100 text-red-700' : 'text-gray-600'"
        >
          <span class="font-medium">{{ key }}: </span>
          <span>{{ formatValue(left[key]) }}</span>
        </div>
      </div>
    </div>

    <!-- 이후 버전 -->
    <div class="rounded border border-gray-200 bg-gray-50 p-3">
      <div class="mb-2 text-xs font-medium text-gray-500">{{ t('content.diff.after') }}</div>
      <div class="space-y-1 text-xs font-mono">
        <div
          v-for="key in allKeys"
          :key="`right-${key}`"
          class="rounded px-1 py-0.5"
          :class="isChanged(key) ? 'bg-green-100 text-green-700' : 'text-gray-600'"
        >
          <span class="font-medium">{{ key }}: </span>
          <span>{{ formatValue(right[key]) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  left: Record<string, unknown>
  right: Record<string, unknown>
}>()

const allKeys = computed(() => {
  return [...new Set([...Object.keys(props.left), ...Object.keys(props.right)])]
    .filter(k => !['id', 'createdAt', 'updatedAt'].includes(k))
    .slice(0, 20) // 표시 제한
})

function isChanged(key: string): boolean {
  return JSON.stringify(props.left[key]) !== JSON.stringify(props.right[key])
}

function formatValue(val: unknown): string {
  if (val === null || val === undefined) return '—'
  if (typeof val === 'object') return JSON.stringify(val).slice(0, 80)
  return String(val).slice(0, 80)
}
</script>
