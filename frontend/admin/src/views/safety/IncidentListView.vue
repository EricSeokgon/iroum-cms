<template>
  <!-- 사고사례 목록 — SPEC-CMS-006 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">사고사례 관리</h2>
      <div class="flex gap-2">
        <el-button v-if="isAdmin" :icon="Refresh" :loading="syncing" @click="handleSync">
          외부 동기화
        </el-button>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">
          수동 등록
        </el-button>
      </div>
    </div>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">업종</p>
          <el-input v-model="filter.industry_code" clearable size="small" placeholder="업종코드" style="width: 140px" />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">사고 유형</p>
          <el-input v-model="filter.incident_type" clearable size="small" placeholder="유형" style="width: 140px" />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">중증도</p>
          <el-select v-model="filter.severity" clearable size="small" placeholder="전체" style="width: 120px">
            <el-option label="LOW" value="LOW" />
            <el-option label="MEDIUM" value="MEDIUM" />
            <el-option label="HIGH" value="HIGH" />
            <el-option label="CRITICAL" value="CRITICAL" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">발생 기간</p>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="~"
            start-placeholder="시작"
            end-placeholder="종료"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            size="small"
          />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">검색어</p>
          <el-input v-model="filter.search" clearable size="small" placeholder="요약 검색" style="width: 200px" />
        </div>
        <el-button type="primary" size="small" @click="search">검색</el-button>
        <el-button size="small" @click="resetFilter">초기화</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.incidentLoading">
      <el-table
        :data="store.incidents"
        stripe
        row-class-name="cursor-pointer"
        empty-text="등록된 사고사례가 없습니다"
        @row-click="goDetail"
      >
        <el-table-column prop="incident_type" label="사고 유형" min-width="140" show-overflow-tooltip />
        <el-table-column prop="industry_code" label="업종" width="110" />
        <el-table-column prop="severity" label="중증도" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="severityTagType(row.severity)" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="occurred_at" label="발생일" width="130">
          <template #default="{ row }">{{ formatDate(row.occurred_at) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="요약" min-width="240" show-overflow-tooltip />
        <el-table-column prop="source" label="출처" width="110" />
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="store.incidentTotal"
        layout="prev, pager, next, sizes, total"
        :page-sizes="[20, 50, 100]"
        class="mt-4 justify-end"
        @change="search"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useSafetyStore } from '@/stores/safetyStore'
import { useAuthStore } from '@/stores/auth'
import type { IncidentFilter, IncidentSeverity, IncidentSummary } from '@/api/safety'

const router = useRouter()
const store = useSafetyStore()
const auth = useAuthStore()

const isAdmin = computed(() =>
  (auth.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN' || r === 'ADMIN'),
)

const filter = reactive<IncidentFilter>({
  industry_code: undefined,
  incident_type: undefined,
  severity: undefined,
  from: undefined,
  to: undefined,
  search: undefined,
})

const dateRange = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)
const syncing = ref(false)

async function search(): Promise<void> {
  if (dateRange.value) {
    filter.from = dateRange.value[0]
    filter.to = dateRange.value[1]
  } else {
    filter.from = undefined
    filter.to = undefined
  }
  await store.fetchIncidents({ ...filter, page: page.value - 1, size: size.value })
}

function resetFilter(): void {
  filter.industry_code = undefined
  filter.incident_type = undefined
  filter.severity = undefined as IncidentSeverity | undefined
  filter.search = undefined
  dateRange.value = null
  page.value = 1
  search()
}

function goDetail(row: IncidentSummary): void {
  router.push({ name: 'safety-incident-detail', params: { id: row.id } })
}

function openCreate(): void {
  router.push({ name: 'safety-incident-detail', params: { id: 'new' } })
}

async function handleSync(): Promise<void> {
  try {
    await ElMessageBox.confirm('외부 사고사례 데이터를 동기화하시겠습니까?', '동기화', {
      confirmButtonText: '실행',
      cancelButtonText: '취소',
    })
    syncing.value = true
    const res = await store.triggerSync()
    ElMessage.success(`동기화 작업 시작 (${new Date(res.triggered_at).toLocaleString('ko-KR')})`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('동기화 요청 실패')
  } finally {
    syncing.value = false
  }
}

function severityTagType(s: IncidentSeverity): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<IncidentSeverity, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    LOW: 'info',
    MEDIUM: '',
    HIGH: 'warning',
    CRITICAL: 'danger',
  }
  return map[s] ?? ''
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR')
}

onMounted(() => {
  search()
})
</script>
