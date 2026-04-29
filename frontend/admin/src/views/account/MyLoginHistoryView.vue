<template>
  <div>
    <!-- 페이지 헤더 -->
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('audit.loginHistory.my.title') }}
      </h2>
      <p class="mt-1 text-sm text-gray-500">
        {{ t('audit.loginHistory.my.description') }}
      </p>
    </div>

    <!-- 보안 경고 박스 — KWCAG: role="note"로 보조기술에 전달 -->
    <div
      role="note"
      class="mb-4 flex items-start gap-2 rounded border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"
    >
      <el-icon class="mt-0.5 flex-shrink-0" :aria-hidden="true">
        <i-ep-warning />
      </el-icon>
      <span>{{ t('audit.loginHistory.my.warning') }}</span>
    </div>

    <!-- 검색 결과 갱신 알림 (스크린 리더 — KWCAG aria-live) -->
    <div
      aria-live="polite"
      aria-atomic="true"
      class="sr-only"
    >
      {{ liveAnnouncement }}
    </div>

    <!-- 결과 테이블 -->
    <el-table
      v-loading="loading"
      :data="entries"
      stripe
      :empty-text="t('audit.loginHistory.my.empty')"
      class="w-full"
    >
      <!-- KWCAG: caption sr-only 제공 -->
      <caption class="sr-only">{{ t('audit.loginHistory.my.title') }}</caption>

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

    <!-- 빈 상태 스크린 리더 알림 -->
    <div
      v-if="!loading && entries.length === 0"
      role="alert"
      aria-live="polite"
      class="sr-only"
    >
      {{ t('audit.loginHistory.my.empty') }}
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
  </div>
</template>

<script setup lang="ts">
// @MX:ANCHOR: [AUTO] MyLoginHistoryView — 라우터, AdminLayout 드롭다운에서 참조
// @MX:REASON: fan_in >= 3: 라우터 등록, AdminLayout 드롭다운 메뉴, 테스트 mock에서 참조
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { meApi } from '@/api/me'
import type { LoginHistoryEntry } from '@iroum/shared/types/api'

const { t } = useI18n()

// ── 상태 ────────────────────────────────────────────────────────────────────
const entries = ref<LoginHistoryEntry[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const liveAnnouncement = ref('')

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
async function loadEntries(): Promise<void> {
  loading.value = true
  try {
    const res = await meApi.myLoginHistory({
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    entries.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = `${t('audit.loginHistory.my.empty')} (${res.data.totalElements})`
  } catch {
    entries.value = []
    totalElements.value = 0
  } finally {
    loading.value = false
  }
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
