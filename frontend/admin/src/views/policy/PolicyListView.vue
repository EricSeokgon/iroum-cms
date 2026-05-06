<template>
  <!-- 정책사업 목록 — SPEC-CMS-007 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">정책사업 관리</h2>
      <div class="flex gap-2">
        <el-button v-if="isAdmin" :icon="Refresh" :loading="syncing" @click="handleSync">
          K-Startup 동기화
        </el-button>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">
          정책 등록
        </el-button>
      </div>
    </div>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">상태</p>
          <el-select v-model="filter.status" clearable size="small" placeholder="전체" style="width: 130px">
            <el-option label="DRAFT" value="DRAFT" />
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="CLOSED" value="CLOSED" />
            <el-option label="EXPIRED" value="EXPIRED" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">업종</p>
          <el-input v-model="filter.industry" clearable size="small" placeholder="업종코드" style="width: 140px" />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">지역</p>
          <el-input v-model="filter.region" clearable size="small" placeholder="지역코드" style="width: 140px" />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">부처</p>
          <el-input v-model="filter.ministry" clearable size="small" placeholder="부처명" style="width: 140px" />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">검색어</p>
          <el-input v-model="filter.search" clearable size="small" placeholder="정책명 검색" style="width: 200px" />
        </div>
        <el-button type="primary" size="small" @click="search">검색</el-button>
        <el-button size="small" @click="resetFilter">초기화</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.programLoading">
      <el-table
        :data="store.programs"
        stripe
        row-class-name="cursor-pointer"
        empty-text="등록된 정책사업이 없습니다"
        @row-click="goDetail"
      >
        <el-table-column prop="title" label="정책명" min-width="240" show-overflow-tooltip />
        <el-table-column prop="ministry" label="부처" width="140" show-overflow-tooltip />
        <el-table-column label="업종" width="180">
          <template #default="{ row }">
            <span v-if="row.target_industries?.length">
              {{ row.target_industries.slice(0, 2).join(', ') }}
              <span v-if="row.target_industries.length > 2" class="text-gray-400">
                +{{ row.target_industries.length - 2 }}
              </span>
            </span>
            <span v-else class="text-gray-400">전체</span>
          </template>
        </el-table-column>
        <el-table-column label="지역" width="160">
          <template #default="{ row }">
            <span v-if="row.target_regions?.length">
              {{ row.target_regions.slice(0, 2).join(', ') }}
              <span v-if="row.target_regions.length > 2" class="text-gray-400">
                +{{ row.target_regions.length - 2 }}
              </span>
            </span>
            <span v-else class="text-gray-400">전국</span>
          </template>
        </el-table-column>
        <el-table-column label="신청기간" width="220">
          <template #default="{ row }">
            <span v-if="row.application_start_at && row.application_end_at" class="text-sm">
              {{ formatShortDate(row.application_start_at) }} ~ {{ formatShortDate(row.application_end_at) }}
            </span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="상태" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="store.programsTotal"
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
import { usePolicyStore } from '@/stores/policyStore'
import { useAuthStore } from '@/stores/auth'
import type { PolicyFilter, PolicyStatus, PolicyProgramSummary } from '@/api/policy'

const router = useRouter()
const store = usePolicyStore()
const auth = useAuthStore()

const isAdmin = computed(() =>
  (auth.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN' || r === 'ADMIN'),
)

const filter = reactive<PolicyFilter>({
  status: undefined,
  industry: undefined,
  region: undefined,
  ministry: undefined,
  search: undefined,
})

const page = ref(1)
const size = ref(20)
const syncing = ref(false)

async function search(): Promise<void> {
  await store.fetchPrograms({ ...filter, page: page.value - 1, size: size.value })
}

function resetFilter(): void {
  filter.status = undefined
  filter.industry = undefined
  filter.region = undefined
  filter.ministry = undefined
  filter.search = undefined
  page.value = 1
  search()
}

function goDetail(row: PolicyProgramSummary): void {
  router.push({ name: 'policy-program-detail', params: { id: row.id } })
}

function openCreate(): void {
  router.push({ name: 'policy-program-detail', params: { id: 'new' } })
}

async function handleSync(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      'K-Startup 등 외부 데이터 소스에서 정책사업을 동기화하시겠습니까?',
      'K-Startup 동기화',
      { confirmButtonText: '실행', cancelButtonText: '취소' },
    )
    syncing.value = true
    const res = await store.syncPrograms()
    ElMessage.success(`동기화 작업 시작 (${new Date(res.triggered_at).toLocaleString('ko-KR')})`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('동기화 요청 실패')
  } finally {
    syncing.value = false
  }
}

function statusTagType(s: PolicyStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<PolicyStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    DRAFT: 'info',
    ACTIVE: 'success',
    CLOSED: 'warning',
    EXPIRED: 'danger',
  }
  return map[s] ?? ''
}

function formatShortDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR')
}

onMounted(() => {
  search()
})
</script>
