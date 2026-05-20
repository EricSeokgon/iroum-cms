<template>
  <!-- 메뉴별 방문 통계 — REQ-SYSTEM-002-D -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">메뉴별 방문 통계</h2>
      <el-button type="primary" :loading="loading" @click="search">조회</el-button>
    </div>

    <!-- 필터 -->
    <div class="mb-4 flex flex-wrap gap-4 items-end">
      <div>
        <p class="mb-1 text-xs text-gray-500">기간</p>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="~"
          start-placeholder="시작일"
          end-placeholder="종료일"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 280px"
        />
      </div>
      <div class="flex gap-2">
        <el-button @click="setPreset(7)">최근 7일</el-button>
        <el-button @click="setPreset(30)">최근 30일</el-button>
        <el-button @click="setPreset(90)">최근 90일</el-button>
      </div>
    </div>

    <!-- 테이블 -->
    <div v-loading="loading">
      <el-table
        :data="rows"
        stripe
        border
        :default-sort="{ prop: 'visit_count', order: 'descending' }"
        style="width: 100%"
      >
        <el-table-column type="index" label="순위" width="70" align="center" />
        <el-table-column prop="page_url" label="페이지 URL" min-width="280" show-overflow-tooltip />
        <el-table-column prop="visit_count" label="방문수" width="110" align="right" sortable>
          <template #default="{ row }">
            {{ row.visit_count.toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="unique_visitors" label="순방문자" width="110" align="right" sortable>
          <template #default="{ row }">
            {{ row.unique_visitors.toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="avg_response_ms" label="평균 응답(ms)" width="130" align="right" sortable>
          <template #default="{ row }">
            <span :class="row.avg_response_ms > 2000 ? 'text-red-500' : row.avg_response_ms > 800 ? 'text-yellow-500' : ''">
              {{ row.avg_response_ms.toLocaleString() }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="error_rate" label="오류율" width="100" align="right" sortable>
          <template #default="{ row }">
            <span :class="Number(row.error_rate) > 0.05 ? 'text-red-500' : Number(row.error_rate) > 0.01 ? 'text-yellow-500' : ''">
              {{ (Number(row.error_rate) * 100).toFixed(2) }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column label="방문 비율" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="total > 0 ? Math.round((row.visit_count / maxVisits) * 100) : 0"
              :stroke-width="8"
              :show-text="false"
            />
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && rows.length === 0" class="py-12 text-center text-gray-400">
        조회된 데이터가 없습니다.
      </div>
    </div>

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { stats as statsApi, type MenuPageStatsResponse } from '@/api/system'

// 기본 기간: 최근 30일
function defaultRange(): [string, string] {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - 29)
  return [fmt(from), fmt(to)]
}

function fmt(d: Date): string {
  return d.toISOString().slice(0, 10)
}

const dateRange = ref<[string, string]>(defaultRange())
const rows = ref<MenuPageStatsResponse[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = 50

const maxVisits = computed(() => rows.value[0]?.visit_count ?? 1)

function setPreset(days: number) {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - (days - 1))
  dateRange.value = [fmt(from), fmt(to)]
  currentPage.value = 1
  load()
}

function search() {
  currentPage.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await statsApi.menuPages({
      from: dateRange.value[0],
      to: dateRange.value[1],
      page: currentPage.value - 1,
      size: pageSize,
    })
    rows.value = res.data.items
    total.value = res.data.total
  } catch {
    ElMessage.error('메뉴별 방문 통계를 불러오는 데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
