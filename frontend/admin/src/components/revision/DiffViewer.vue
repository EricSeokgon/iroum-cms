<template>
  <!-- 버전 diff 뷰어 — SPEC-CMS-CONTENT-REVISION-001 M4 -->
  <div class="space-y-4">
    <section
      v-for="diff in diffs"
      :key="diff.field"
      class="overflow-hidden rounded border border-gray-200"
    >
      <!-- 필드 헤더 -->
      <header class="flex items-center justify-between bg-gray-50 px-3 py-2 text-sm">
        <span class="font-medium text-gray-700">{{ diff.field }}</span>
        <span class="text-xs text-gray-400">
          v{{ diff.fromVersion }} → v{{ diff.toVersion }}
        </span>
      </header>

      <!-- 라인 단위 diff -->
      <table class="w-full border-collapse font-mono text-xs">
        <tbody>
          <tr
            v-for="(line, i) in diff.lines"
            :key="i"
            :class="rowClass(line.type)"
          >
            <td class="w-10 select-none border-r border-gray-100 px-1 text-right text-gray-400">
              {{ line.oldLineNo ?? '' }}
            </td>
            <td class="w-10 select-none border-r border-gray-100 px-1 text-right text-gray-400">
              {{ line.newLineNo ?? '' }}
            </td>
            <td class="w-4 select-none px-1 text-center text-gray-500">{{ sign(line.type) }}</td>
            <td class="whitespace-pre-wrap break-all px-2 py-0.5">{{ line.text }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <p v-if="diffs.length === 0" class="py-4 text-center text-sm text-gray-400">
      {{ t('revision.noHistory') }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { RevisionDiffResponse, DiffLine } from '@/types/revision'

defineProps<{
  diffs: RevisionDiffResponse[]
}>()

const { t } = useI18n()

// 라인 유형별 배경색 (INSERT=초록, DELETE=빨강, EQUAL=무색)
function rowClass(type: DiffLine['type']): string {
  if (type === 'INSERT') return 'bg-green-50 text-green-800'
  if (type === 'DELETE') return 'bg-red-50 text-red-800'
  return ''
}

// 라인 유형별 기호 표시
function sign(type: DiffLine['type']): string {
  if (type === 'INSERT') return '+'
  if (type === 'DELETE') return '-'
  return ''
}
</script>
