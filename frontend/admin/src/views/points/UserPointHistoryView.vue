<template>
  <div data-testid="user-point-history">
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">내 포인트</h2>
      <p class="mt-1 text-sm text-gray-500">참여 활동으로 적립한 포인트 총액과 내역을 확인합니다.</p>
    </div>

    <el-card class="mb-4" v-loading="summaryLoading">
      <div class="flex items-baseline gap-2">
        <span class="text-sm text-gray-500">누적 포인트</span>
        <span class="text-2xl font-semibold text-gray-800" data-testid="point-total">
          {{ summary.totalPoints }}
        </span>
        <span class="text-sm text-gray-500">점</span>
      </div>
    </el-card>

    <el-card v-loading="loading">
      <el-table :data="rows" data-testid="history-table" stripe>
        <el-table-column label="이벤트" width="160">
          <template #default="{ row }">{{ eventLabel(row.eventType) }}</template>
        </el-table-column>
        <el-table-column prop="points" label="포인트" width="100" />
        <el-table-column prop="createdAt" label="적립 일시" />
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page + 1"
          data-testid="history-pagination"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
// SPEC-CMS-POINTS-001 — 사용자 본인 포인트 총액·내역 화면 (REQ-PNT-006)
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getMyPointHistory,
  getMyPointSummary,
  type PointEventType,
  type PointLedgerEntry,
  type PointSummary,
} from '@/api/point'

const summaryLoading = ref(false)
const loading = ref(false)
const rows = ref<PointLedgerEntry[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)

const summary = reactive<PointSummary>({
  userId: 0,
  totalPoints: 0,
  updatedAt: null,
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

async function loadSummary(): Promise<void> {
  summaryLoading.value = true
  try {
    const { data } = await getMyPointSummary()
    summary.userId = data.userId
    summary.totalPoints = data.totalPoints
    summary.updatedAt = data.updatedAt
  } catch {
    ElMessage.error('포인트 총액을 불러오지 못했습니다.')
  } finally {
    summaryLoading.value = false
  }
}

async function loadHistory(): Promise<void> {
  loading.value = true
  try {
    const { data } = await getMyPointHistory({ page: page.value, size: size.value })
    rows.value = data.content
    total.value = data.totalElements
  } catch {
    ElMessage.error('포인트 내역을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number): void {
  page.value = p - 1
  void loadHistory()
}

onMounted(() => {
  void loadSummary()
  void loadHistory()
})
</script>
