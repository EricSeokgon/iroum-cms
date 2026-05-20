<template>
  <!-- 접속 로그 — SPEC-CMS-005 Bundle D REQ-SYS-002-D -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('system.accessLog.title') }}</h2>
      <el-button @click="exportCsv" :loading="exporting">
        {{ t('system.accessLog.exportCsv') }}
      </el-button>
    </div>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">{{ t('system.accessLog.filter.period') }}</p>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="t('common.to')"
            :start-placeholder="t('common.startDate')"
            :end-placeholder="t('common.endDate')"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            size="small"
          />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">{{ t('system.accessLog.filter.status') }}</p>
          <el-select v-model="filterStatus" clearable size="small" :placeholder="t('common.all')" style="width: 120px">
            <el-option label="200" :value="200" />
            <el-option label="4xx" :value="400" />
            <el-option label="5xx" :value="500" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">페이지 URL</p>
          <el-input v-model="filterPageUrl" clearable size="small" style="width: 200px" placeholder="/path/..." />
        </div>
        <el-button type="primary" size="small" @click="search">{{ t('common.search') }}</el-button>
        <el-button size="small" @click="resetFilter">{{ t('common.reset') }}</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="rows" stripe row-class-name="cursor-pointer" @row-click="openDetail">
        <el-table-column prop="createdAt" :label="t('system.accessLog.col.time')" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="pageUrl" :label="t('system.accessLog.col.url')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="statusCode" :label="t('system.accessLog.col.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.statusCode)" size="small">{{ row.statusCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responseTimeMs" :label="t('system.accessLog.col.responseTime')" width="110" align="right">
          <template #default="{ row }">{{ row.responseTimeMs }}ms</template>
        </el-table-column>
        <el-table-column prop="ipHash" :label="t('system.accessLog.col.ipHash')" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ipHash?.slice(0, 12) }}...</template>
        </el-table-column>
        <el-table-column prop="userAgent" :label="t('system.accessLog.col.userAgent')" min-width="160" show-overflow-tooltip />
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="prev, pager, next, sizes"
        :page-sizes="[20, 50, 100]"
        class="mt-4 justify-end"
        @change="search"
      />
    </el-card>

    <!-- 상세 다이얼로그 -->
    <el-dialog
      v-model="detailVisible"
      :title="t('system.accessLog.detail.title')"
      width="560px"
    >
      <template v-if="selected">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('system.accessLog.col.time')">{{ formatDate(selected.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.accessLog.col.url')">{{ selected.pageUrl }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.accessLog.col.status')">{{ selected.statusCode }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.accessLog.col.responseTime')">{{ selected.responseTimeMs }}ms</el-descriptions-item>
          <el-descriptions-item :label="t('system.accessLog.col.ipHash')">{{ selected.ipHash }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.accessLog.col.userAgent')">{{ selected.userAgent }}</el-descriptions-item>
          <el-descriptions-item v-if="selected.referrer" :label="t('system.accessLog.col.referrer')">{{ selected.referrer }}</el-descriptions-item>
          <el-descriptions-item v-if="selected.userId" :label="t('system.accessLog.col.userId')">{{ selected.userId }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { accessLogs } from '@/api/system'
import type { AccessLogResponse, AccessLogPageResponse } from '@/api/system'

const { t } = useI18n()

const rows = ref<AccessLogResponse[]>([])
const loading = ref(false)
const exporting = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

const dateRange = ref<[string, string] | null>(null)
const filterStatus = ref<number | null>(null)
const filterPageUrl = ref('')

const detailVisible = ref(false)
const selected = ref<AccessLogResponse | null>(null)

function buildFilter() {
  return {
    from: dateRange.value?.[0],
    to: dateRange.value?.[1],
    statusCode: filterStatus.value ?? undefined,
    pageUrl: filterPageUrl.value || undefined,
    page: page.value - 1,   // 백엔드는 0-based, el-pagination은 1-based
    size: size.value,
  }
}

async function search(): Promise<void> {
  loading.value = true
  try {
    const res = await accessLogs.list(buildFilter())
    rows.value = res.data.items
    total.value = res.data.total
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

function resetFilter(): void {
  dateRange.value = null
  filterStatus.value = null
  filterPageUrl.value = ''
  page.value = 1
  search()
}

async function exportCsv(): Promise<void> {
  exporting.value = true
  try {
    const res = await accessLogs.exportCsv({
      from: dateRange.value?.[0],
      to: dateRange.value?.[1],
      statusCode: filterStatus.value ?? undefined,
      pageUrl: filterPageUrl.value || undefined,
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `access-logs-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('common.exportError'))
  } finally {
    exporting.value = false
  }
}

function openDetail(row: AccessLogResponse): void {
  selected.value = row
  detailVisible.value = true
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

function statusType(status: number): 'success' | 'warning' | 'danger' | 'info' {
  if (status < 400) return 'success'
  if (status < 500) return 'warning'
  return 'danger'
}

onMounted(() => search())
</script>
