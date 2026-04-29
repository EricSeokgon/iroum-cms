<template>
  <div>
    <!-- 페이지 제목 -->
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('audit.loginHistory.title') }}
      </h2>
    </div>

    <!-- API 미구현 안내 박스 -->
    <el-alert
      :title="t('audit.loginHistory.apiPending')"
      type="warning"
      :closable="false"
      show-icon
      class="mb-4"
      role="note"
    />

    <!-- 검색 결과 갱신 알림 (스크린 리더 — KWCAG aria-live) -->
    <div
      aria-live="polite"
      aria-atomic="true"
      class="sr-only"
    >
      {{ liveAnnouncement }}
    </div>

    <!-- 필터 영역 -->
    <el-card class="mb-4">
      <form
        role="search"
        :aria-label="t('audit.loginHistory.title')"
        @submit.prevent="onSearch"
      >
        <div class="flex flex-wrap gap-4">
          <!-- 사용자명 -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-username"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.loginHistory.filter.username') }}
            </label>
            <el-input
              id="filter-username"
              v-model="filterUsername"
              clearable
              style="width: 200px"
              :placeholder="t('audit.loginHistory.filter.username')"
            />
          </div>

          <!-- 성공/실패 -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-success"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.loginHistory.filter.success') }}
            </label>
            <el-select
              id="filter-success"
              v-model="filterSuccess"
              clearable
              style="width: 160px"
              :placeholder="t('audit.loginHistory.filter.all')"
            >
              <el-option
                :label="t('audit.loginHistory.filter.all')"
                :value="null"
              />
              <el-option
                :label="t('audit.loginHistory.filter.successOnly')"
                :value="true"
              />
              <el-option
                :label="t('audit.loginHistory.filter.failureOnly')"
                :value="false"
              />
            </el-select>
          </div>

          <!-- 기간 -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-date-range"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.loginHistory.filter.period') }}
            </label>
            <el-date-picker
              id="filter-date-range"
              v-model="filterDateRange"
              type="daterange"
              :start-placeholder="t('audit.loginHistory.filter.period')"
              :end-placeholder="t('audit.loginHistory.filter.period')"
              value-format="YYYY-MM-DD"
              style="width: 260px"
            />
          </div>

          <!-- IP 주소 -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-ip"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.loginHistory.filter.ipAddress') }}
            </label>
            <el-input
              id="filter-ip"
              v-model="filterIpAddress"
              clearable
              style="width: 180px"
              :placeholder="t('audit.loginHistory.filter.ipAddress')"
            />
          </div>
        </div>

        <!-- 액션 버튼 -->
        <div class="mt-4 flex gap-2">
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
          >
            {{ t('audit.permissionChanges.filter.search') }}
          </el-button>
          <el-button @click="onReset">
            {{ t('audit.permissionChanges.filter.reset') }}
          </el-button>
        </div>
      </form>
    </el-card>

    <!-- 결과 테이블 -->
    <el-table
      v-loading="loading"
      :data="entries"
      stripe
      :empty-text="t('audit.loginHistory.empty')"
      class="w-full"
      @row-click="openDetail"
    >
      <!-- KWCAG: caption sr-only 제공 -->
      <caption class="sr-only">{{ t('audit.loginHistory.title') }}</caption>

      <!-- 발생일시 -->
      <el-table-column
        prop="createdAt"
        :label="t('audit.loginHistory.field.createdAt')"
        min-width="160"
        sortable
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>

      <!-- 사용자명 -->
      <el-table-column
        prop="username"
        :label="t('audit.loginHistory.field.username')"
        min-width="130"
      />

      <!-- 결과 (badge: 색상 + 텍스트 — KWCAG 색상+텍스트 동시) -->
      <el-table-column
        :label="t('audit.loginHistory.field.success')"
        width="100"
      >
        <template #default="{ row }">
          <span
            class="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium"
            :class="row.success
              ? 'bg-green-100 text-green-700'
              : 'bg-red-100 text-red-700'"
            :aria-label="row.success
              ? t('audit.loginHistory.result.success')
              : t('audit.loginHistory.result.failure')"
          >
            {{ row.success ? '✓' : '✗' }}
            {{ row.success
              ? t('audit.loginHistory.result.success')
              : t('audit.loginHistory.result.failure') }}
          </span>
        </template>
      </el-table-column>

      <!-- 실패 사유 -->
      <el-table-column
        prop="failureReason"
        :label="t('audit.loginHistory.field.failureReason')"
        min-width="150"
      >
        <template #default="{ row }">
          <span class="text-sm text-gray-600">{{ row.failureReason ?? '-' }}</span>
        </template>
      </el-table-column>

      <!-- IP -->
      <el-table-column
        prop="ipAddress"
        :label="t('audit.loginHistory.field.ipAddress')"
        width="140"
      >
        <template #default="{ row }">
          <span class="font-mono text-xs text-gray-600">{{ row.ipAddress ?? '-' }}</span>
        </template>
      </el-table-column>

      <!-- User-Agent (truncate) -->
      <el-table-column
        prop="userAgent"
        :label="t('audit.loginHistory.field.userAgent')"
        min-width="200"
      >
        <template #default="{ row }">
          <span
            class="block max-w-xs truncate text-xs text-gray-500"
            :title="row.userAgent"
          >
            {{ row.userAgent ?? '-' }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 빈 상태 — 오류 또는 데이터 없음 -->
    <div
      v-if="!loading && entries.length === 0"
      role="alert"
      class="sr-only"
      aria-live="polite"
    >
      {{ t('audit.loginHistory.empty') }}
    </div>

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="totalElements"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50]"
        :aria-label="t('a11y.pagination')"
        @change="loadEntries"
      />
    </div>

    <!-- 상세 모달 -->
    <el-dialog
      v-model="showDetail"
      :title="t('audit.loginHistory.title')"
      width="560px"
      :aria-label="t('audit.loginHistory.title')"
      destroy-on-close
    >
      <template v-if="selectedEntry">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('audit.loginHistory.field.createdAt')">
            {{ formatDate(selectedEntry.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.loginHistory.field.username')">
            {{ selectedEntry.username }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.loginHistory.field.success')">
            <span
              class="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium"
              :class="selectedEntry.success
                ? 'bg-green-100 text-green-700'
                : 'bg-red-100 text-red-700'"
            >
              {{ selectedEntry.success ? '✓' : '✗' }}
              {{ selectedEntry.success
                ? t('audit.loginHistory.result.success')
                : t('audit.loginHistory.result.failure') }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.loginHistory.field.failureReason')">
            {{ selectedEntry.failureReason ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.loginHistory.field.ipAddress')">
            <span class="font-mono text-xs">{{ selectedEntry.ipAddress ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.loginHistory.field.userAgent')">
            <span class="break-all text-xs text-gray-600">{{ selectedEntry.userAgent ?? '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
// @MX:ANCHOR: [AUTO] LoginHistoryView — 라우터, AdminLayout 사이드바, 테스트에서 참조
// @MX:REASON: fan_in >= 3: 라우터 등록, AdminLayout 사이드바 메뉴, 테스트 mock에서 참조
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { auditApi } from '@/api/audit'
import type { LoginHistoryEntry } from '@iroum/shared/types/api'

const { t } = useI18n()

// ── 상태 ────────────────────────────────────────────────────────────────────
const entries = ref<LoginHistoryEntry[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filterUsername = ref('')
const filterSuccess = ref<boolean | null>(null)
const filterDateRange = ref<[string, string] | null>(null)
const filterIpAddress = ref('')

const showDetail = ref(false)
const selectedEntry = ref<LoginHistoryEntry | null>(null)
const liveAnnouncement = ref('')

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
async function loadEntries(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: currentPage.value - 1,
      size: pageSize.value,
      sort: 'createdAt,desc',
    }
    if (filterUsername.value) params.username = filterUsername.value
    if (filterSuccess.value !== null) params.success = filterSuccess.value
    if (filterDateRange.value) {
      params.from = filterDateRange.value[0]
      params.to = filterDateRange.value[1]
    }
    if (filterIpAddress.value) params.ipAddress = filterIpAddress.value

    const res = await auditApi.loginHistory(params)
    entries.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = `${t('audit.loginHistory.empty')} (${res.data.totalElements})`
  } catch {
    // 백엔드 미구현 상태 — 빈 결과 표시
    entries.value = []
    totalElements.value = 0
    ElMessage.warning(t('audit.loginHistory.apiPending'))
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  currentPage.value = 1
  loadEntries()
}

function onReset(): void {
  filterUsername.value = ''
  filterSuccess.value = null
  filterDateRange.value = null
  filterIpAddress.value = ''
  currentPage.value = 1
  loadEntries()
}

function openDetail(row: LoginHistoryEntry): void {
  selectedEntry.value = row
  showDetail.value = true
}

// ── 유틸 ────────────────────────────────────────────────────────────────────
function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

onMounted(() => {
  loadEntries()
})
</script>
