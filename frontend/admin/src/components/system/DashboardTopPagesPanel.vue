<template>
  <!-- 인기 페이지 Top 10 패널 — SPEC-CMS-005 -->
  <div class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
    <div class="mb-3 flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-700">{{ t('system.dashboard.topPages.title') }}</h3>
      <div class="flex gap-1">
        <el-button
          v-for="p in ['7d', '30d'] as const"
          :key="p"
          size="small"
          :type="period === p ? 'primary' : 'default'"
          @click="emit('update:period', p)"
        >
          {{ p === '7d' ? t('system.dashboard.topPages.7d') : t('system.dashboard.topPages.30d') }}
        </el-button>
      </div>
    </div>

    <el-table :data="items" size="small" stripe>
      <el-table-column prop="rank" :label="t('system.dashboard.topPages.rank')" width="60" align="center" />
      <el-table-column prop="page_url" :label="t('system.dashboard.topPages.url')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="views" :label="t('system.dashboard.topPages.views')" width="90" align="right">
        <template #default="scope">{{ scope?.row?.views?.toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="avg_response_ms" :label="t('system.dashboard.topPages.avgResponse')" width="110" align="right">
        <template #default="scope">{{ scope?.row?.avg_response_ms }}ms</template>
      </el-table-column>
      <el-table-column prop="error_rate" :label="t('system.dashboard.topPages.errorRate')" width="90" align="right">
        <template #default="scope">
          <span v-if="scope?.row" :class="scope.row.error_rate > 0.05 ? 'text-red-600' : 'text-gray-700'">
            {{ (scope.row.error_rate * 100).toFixed(1) }}%
          </span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { TopPageResponse } from '@/api/system'

defineProps<{
  items: TopPageResponse[]
  period: '7d' | '30d'
}>()

const emit = defineEmits<{
  'update:period': [period: '7d' | '30d']
}>()

const { t } = useI18n()
</script>
