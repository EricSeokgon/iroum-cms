<template>
  <div data-testid="point-ledger-admin">
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">포인트 내역 조회</h2>
      <p class="mt-1 text-sm text-gray-500">
        사용자·이벤트·기간 조건으로 포인트 적립 내역을 조회합니다.
      </p>
    </div>

    <el-card class="mb-4">
      <el-form :inline="true" data-testid="ledger-filter">
        <el-form-item label="사용자 ID">
          <el-input
            v-model="filter.userId"
            placeholder="사용자 ID"
            clearable
            data-testid="filter-user-id"
          />
        </el-form-item>
        <el-form-item label="이벤트">
          <el-select
            v-model="filter.eventType"
            placeholder="전체"
            clearable
            data-testid="filter-event-type"
            style="width: 180px"
          >
            <el-option label="게시글 작성" value="POST_CREATED" />
            <el-option label="댓글 작성" value="COMMENT_CREATED" />
            <el-option label="좋아요" value="LIKE_GIVEN" />
          </el-select>
        </el-form-item>
        <el-form-item label="기간">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="~"
            start-placeholder="시작"
            end-placeholder="종료"
            data-testid="filter-date-range"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" data-testid="filter-search-btn" @click="onSearch">
            조회
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading">
      <el-table :data="rows" data-testid="ledger-table" stripe>
        <el-table-column prop="userId" label="사용자 ID" width="120" />
        <el-table-column label="이벤트" width="160">
          <template #default="{ row }">{{ eventLabel(row.eventType) }}</template>
        </el-table-column>
        <el-table-column prop="referenceId" label="참조 ID" width="120" />
        <el-table-column prop="points" label="포인트" width="100" />
        <el-table-column prop="createdAt" label="적립 일시" />
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page + 1"
          data-testid="ledger-pagination"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
// SPEC-CMS-POINTS-001 — 관리자 포인트 내역 조회 화면 (REQ-PNT-006)
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getPointLedger,
  type PointEventType,
  type PointLedgerEntry,
} from '@/api/point'

const loading = ref(false)
const rows = ref<PointLedgerEntry[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const dateRange = ref<[Date, Date] | null>(null)

const filter = reactive<{ userId: string; eventType: PointEventType | '' }>({
  userId: '',
  eventType: '',
})

function eventLabel(type: PointEventType): string {
  switch (type) {
    case 'POST_CREATED':
      return '게시글 작성'
    case 'COMMENT_CREATED':
      return '댓글 작성'
    case 'LIKE_GIVEN':
      return '좋아요'
    default:
      return type
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await getPointLedger({
      userId: filter.userId ? Number(filter.userId) : undefined,
      eventType: filter.eventType || undefined,
      from: dateRange.value ? dateRange.value[0].toISOString() : undefined,
      to: dateRange.value ? dateRange.value[1].toISOString() : undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = data.content
    total.value = data.totalElements
  } catch {
    ElMessage.error('포인트 내역을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 0
  void load()
}

function onPageChange(p: number): void {
  page.value = p - 1
  void load()
}

onMounted(() => {
  void load()
})
</script>
