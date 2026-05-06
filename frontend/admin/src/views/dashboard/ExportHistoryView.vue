<template>
  <!-- 내보내기 이력 — SPEC-CMS-008 REQ-VIZ-006 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">내보내기 이력</h2>
      <el-button :icon="Refresh" @click="refresh">새로고침</el-button>
    </div>

    <!-- 상태 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">상태</p>
          <el-select v-model="filter.status" clearable size="small" placeholder="전체" style="width: 160px">
            <el-option label="PENDING" value="PENDING" />
            <el-option label="PROCESSING" value="PROCESSING" />
            <el-option label="COMPLETED" value="COMPLETED" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
        </div>
        <el-button type="primary" size="small" @click="refresh">조회</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.exportLoading">
      <el-table
        :data="store.exportHistory"
        stripe
        empty-text="내보내기 이력이 없습니다"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="export_type" label="타입" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.export_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="범위" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-xs text-gray-600">{{ row.scope }}</span>
          </template>
        </el-table-column>
        <el-table-column label="상태" width="200">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
            <el-progress
              v-if="row.status === 'PROCESSING'"
              :percentage="row.progress_pct ?? 0"
              :stroke-width="6"
              class="mt-1"
            />
          </template>
        </el-table-column>
        <el-table-column label="요청 시각" width="180">
          <template #default="{ row }">
            {{ formatDate(row.requested_at) }}
          </template>
        </el-table-column>
        <el-table-column label="만료" width="180">
          <template #default="{ row }">
            <span v-if="row.expires_at" :class="isExpired(row.expires_at) ? 'text-red-500' : 'text-gray-600'">
              {{ formatDate(row.expires_at) }}
              <el-tag v-if="isExpired(row.expires_at)" size="small" type="danger" class="ml-1">만료됨</el-tag>
            </span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="row_count" label="행 수" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.row_count != null">{{ row.row_count.toLocaleString() }}</span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="160" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'COMPLETED' && !isExpired(row.expires_at)"
              link size="small" type="primary"
              @click="handleDownload(row)"
            >
              다운로드
            </el-button>
            <span v-else-if="row.status === 'PROCESSING'" class="text-xs text-gray-500">
              ({{ row.progress_pct ?? 0 }}%)
            </span>
            <span v-else-if="row.status === 'FAILED'" class="text-xs text-red-500" :title="row.error_message">
              실패
            </span>
            <span v-else class="text-gray-400 text-xs">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboardStore'
import { dashboardApi } from '@/api/dashboard'
import type { ExportResponse, ExportStatus } from '@/api/dashboard'

const store = useDashboardStore()

interface FilterState {
  status?: ExportStatus
}

const filter = reactive<FilterState>({
  status: undefined,
})

// PROCESSING 항목 폴링용 타이머 맵 (id → setInterval handle)
const pollers = ref<Record<number, ReturnType<typeof setInterval>>>({})

async function refresh(): Promise<void> {
  await store.listExportHistory(filter.status)
  // PROCESSING/PENDING 행에 대해 폴링 시작
  for (const item of store.exportHistory) {
    if ((item.status === 'PROCESSING' || item.status === 'PENDING') && !pollers.value[item.id]) {
      startPolling(item.id)
    }
  }
}

function startPolling(id: number): void {
  if (pollers.value[id]) return
  const handle = setInterval(async () => {
    try {
      const res = await store.pollExportStatus(id)
      if (res.status === 'COMPLETED' || res.status === 'FAILED') {
        stopPolling(id)
      }
    } catch {
      stopPolling(id)
    }
  }, 3000)
  pollers.value = { ...pollers.value, [id]: handle }
}

function stopPolling(id: number): void {
  const handle = pollers.value[id]
  if (handle) {
    clearInterval(handle)
    const next = { ...pollers.value }
    delete next[id]
    pollers.value = next
  }
}

function clearAllPollers(): void {
  for (const id of Object.keys(pollers.value)) {
    clearInterval(pollers.value[Number(id)])
  }
  pollers.value = {}
}

function handleDownload(row: ExportResponse): void {
  if (row.signed_download_url) {
    // 서명된 URL 가 있으면 그대로 사용
    window.open(row.signed_download_url, '_blank')
    return
  }
  // signed_download_url 이 없으면 인증된 axios 호출 경로 사용
  const url = dashboardApi.exports.download(row.id)
  // axios baseURL 을 거치지 않고 브라우저가 쿠키/JWT 헤더와 함께 호출하도록 절대 경로로 열기
  window.open(url, '_blank')
  ElMessage.info('다운로드를 시작합니다')
}

function isExpired(expiresAt?: string): boolean {
  if (!expiresAt) return false
  return new Date(expiresAt).getTime() < Date.now()
}

function statusTagType(s: ExportStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<ExportStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'info',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger',
  }
  return map[s] ?? ''
}

function formatDate(iso?: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(() => {
  refresh()
})

onBeforeUnmount(() => {
  clearAllPollers()
})
</script>
