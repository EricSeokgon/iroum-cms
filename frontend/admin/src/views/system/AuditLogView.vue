<template>
  <!-- 통합 감사 로그 — SPEC-CMS-005 Bundle D REQ-SYS-007-D -->
  <div>
    <!-- CRITICAL 알림 패널 -->
    <el-card
      v-if="criticalLogs.length > 0"
      class="mb-4 border-red-200"
      shadow="never"
    >
      <template #header>
        <span class="text-sm font-semibold text-red-700">
          {{ t('system.auditLog.criticalPanel.title') }} ({{ criticalLogs.length }})
        </span>
      </template>
      <div class="space-y-1 max-h-32 overflow-auto">
        <div
          v-for="log in criticalLogs"
          :key="log.id"
          class="flex items-center gap-2 text-xs text-red-700"
        >
          <span class="font-mono">{{ formatDate(log.event_time) }}</span>
          <el-tag type="danger" size="small">{{ log.action }}</el-tag>
          <span>{{ log.actor_username ?? log.actor_id }}</span>
          <span v-if="log.entity_type">→ {{ log.entity_type }}#{{ log.entity_id }}</span>
        </div>
      </div>
    </el-card>

    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('system.auditLog.title') }}</h2>
      <el-button @click="exportCsv" :loading="exporting">
        {{ t('system.auditLog.exportCsv') }}
      </el-button>
    </div>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
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
            style="width: 100%"
          />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">{{ t('system.auditLog.filter.action') }}</p>
          <el-select v-model="filterAction" clearable size="small" style="width: 100%">
            <el-option v-for="a in actionOptions" :key="a" :label="a" :value="a" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">{{ t('system.auditLog.filter.severity') }}</p>
          <el-select v-model="filterSeverity" clearable size="small" style="width: 100%">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="CRITICAL" value="CRITICAL" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">{{ t('system.auditLog.filter.result') }}</p>
          <el-select v-model="filterResult" clearable size="small" style="width: 100%">
            <el-option :label="t('system.auditLog.result.success')" value="SUCCESS" />
            <el-option :label="t('system.auditLog.result.failure')" value="FAILURE" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">{{ t('system.auditLog.filter.entityType') }}</p>
          <el-input v-model="filterEntityType" clearable size="small" />
        </div>
      </div>
      <div class="mt-3 flex justify-end gap-2">
        <el-button size="small" @click="resetFilter">{{ t('common.reset') }}</el-button>
        <el-button size="small" type="primary" @click="search">{{ t('common.search') }}</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="loading">
      <el-table
        :data="rows"
        stripe
        row-class-name="cursor-pointer"
        @row-click="openDetail"
      >
        <el-table-column prop="event_time" :label="t('system.auditLog.col.eventTime')" width="180">
          <template #default="{ row }">{{ formatDate(row.event_time) }}</template>
        </el-table-column>
        <el-table-column :label="t('system.auditLog.col.actor')" width="130">
          <template #default="{ row }">{{ row.actor_username ?? row.actor_id ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="action" :label="t('system.auditLog.col.action')" width="160">
          <template #default="{ row }">
            <el-tag size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="entity_type" :label="t('system.auditLog.col.entityType')" width="130" />
        <el-table-column prop="entity_id" :label="t('system.auditLog.col.entityId')" width="100" />
        <el-table-column :label="t('system.auditLog.col.severity')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.auditLog.col.result')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
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
      :title="t('system.auditLog.detail.title')"
      width="600px"
    >
      <template v-if="selected">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="t('system.auditLog.col.eventTime')" :span="2">{{ formatDate(selected.event_time) }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.auditLog.col.actor')">{{ selected.actor_username ?? selected.actor_id }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.auditLog.col.action')">{{ selected.action }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.auditLog.col.entityType')">{{ selected.entity_type }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.auditLog.col.entityId')">{{ selected.entity_id }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.auditLog.col.severity')">{{ selected.severity }}</el-descriptions-item>
          <el-descriptions-item :label="t('system.auditLog.col.result')">{{ selected.result }}</el-descriptions-item>
          <el-descriptions-item v-if="selected.ip_address" :label="t('system.auditLog.col.ipAddress')">{{ selected.ip_address }}</el-descriptions-item>
          <el-descriptions-item v-if="selected.detail" :label="t('system.auditLog.col.detail')" :span="2">{{ selected.detail }}</el-descriptions-item>
        </el-descriptions>

        <!-- Before/After JSON diff -->
        <div v-if="selected.before || selected.after" class="mt-4 grid grid-cols-2 gap-3">
          <div v-if="selected.before">
            <p class="mb-1 text-xs font-medium text-gray-500">{{ t('system.auditLog.detail.before') }}</p>
            <pre class="rounded bg-gray-50 p-2 text-xs overflow-auto max-h-48">{{ JSON.stringify(selected.before, null, 2) }}</pre>
          </div>
          <div v-if="selected.after">
            <p class="mb-1 text-xs font-medium text-gray-500">{{ t('system.auditLog.detail.after') }}</p>
            <pre class="rounded bg-green-50 p-2 text-xs overflow-auto max-h-48">{{ JSON.stringify(selected.after, null, 2) }}</pre>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { auditLogs } from '@/api/system'
import type { AuditLogResponse, AuditAction, AuditSeverity, AuditResult } from '@/api/system'

const { t } = useI18n()

const rows = ref<AuditLogResponse[]>([])
const criticalLogs = ref<AuditLogResponse[]>([])
const loading = ref(false)
const exporting = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

const dateRange = ref<[string, string] | null>(null)
const filterAction = ref<AuditAction | ''>('')
const filterSeverity = ref<AuditSeverity | ''>('')
const filterResult = ref<AuditResult | ''>('')
const filterEntityType = ref('')

const detailVisible = ref(false)
const selected = ref<AuditLogResponse | null>(null)

const actionOptions: AuditAction[] = [
  'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT',
  'PERMISSION_CHANGE', 'EXPORT', 'VIEW_SENSITIVE',
]

function buildFilter() {
  return {
    fromTime: dateRange.value?.[0],   // 백엔드 파라미터 이름
    toTime: dateRange.value?.[1],     // 백엔드 파라미터 이름
    action: filterAction.value || undefined,
    severity: filterSeverity.value || undefined,
    result: filterResult.value || undefined,
    entity_type: filterEntityType.value || undefined,
    page: page.value,   // 백엔드는 1-based
    size: size.value,
  }
}

async function search(): Promise<void> {
  loading.value = true
  try {
    const res = await auditLogs.search(buildFilter())
    rows.value = res.data.items
    total.value = res.data.total
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

async function loadCritical(): Promise<void> {
  try {
    const res = await auditLogs.critical()
    criticalLogs.value = res.data
  } catch { /* 조용히 무시 */ }
}

function resetFilter(): void {
  dateRange.value = null
  filterAction.value = ''
  filterSeverity.value = ''
  filterResult.value = ''
  filterEntityType.value = ''
  page.value = 1
  search()
}

async function exportCsv(): Promise<void> {
  exporting.value = true
  try {
    const res = await auditLogs.exportCsv({
      from: dateRange.value?.[0],
      to: dateRange.value?.[1],
      action: filterAction.value || undefined,
      severity: filterSeverity.value || undefined,
      result: filterResult.value || undefined,
      entity_type: filterEntityType.value || undefined,
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit-logs-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('common.exportError'))
  } finally {
    exporting.value = false
  }
}

function openDetail(row: AuditLogResponse): void {
  selected.value = row
  detailVisible.value = true
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

function severityType(severity: AuditSeverity): 'info' | 'warning' | 'danger' {
  switch (severity) {
    case 'INFO':     return 'info'
    case 'WARN':     return 'warning'
    case 'CRITICAL': return 'danger'
    default: return 'info'
  }
}

onMounted(async () => {
  await Promise.all([search(), loadCritical()])
})
</script>
