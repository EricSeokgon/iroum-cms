<template>
  <!-- 포인트 이력 관리 — SPEC-CMS-POINTS-001 REQ-PNT-007 -->
  <div>
    <h2 class="mb-4 text-xl font-semibold text-gray-800">포인트 이력</h2>

    <!-- 필터 -->
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true">
        <el-form-item label="사용자 ID">
          <el-input
            v-model.number="filterUserId"
            placeholder="전체"
            clearable
            style="width: 140px"
            @clear="filterUserId = undefined"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadFirst">검색</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 이력 테이블 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="rows" stripe size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="사용자 ID" width="100" />
        <el-table-column prop="delta" label="포인트" width="90">
          <template #default="{ row }">
            <span :class="row.delta >= 0 ? 'text-green-600' : 'text-red-500'">
              {{ row.delta >= 0 ? `+${row.delta}` : row.delta }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="사유" width="160" />
        <el-table-column prop="refType" label="참조 유형" width="100" />
        <el-table-column prop="refId" label="참조 ID" width="90" />
        <el-table-column prop="createdAt" label="일시" min-width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pointApi, type PointLedgerEntry } from '@/api/point'

const loading = ref(false)
const rows = ref<PointLedgerEntry[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterUserId = ref<number | undefined>(undefined)

function loadFirst() {
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const resp = await pointApi.getLedger({
      userId: filterUserId.value,
      page: page.value - 1,
      size: size.value,
    })
    rows.value = resp.content
    total.value = resp.totalElements
  } catch {
    ElMessage.error('포인트 이력을 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(load)
</script>
