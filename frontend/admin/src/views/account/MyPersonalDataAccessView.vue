<template>
  <div>
    <!-- 페이지 헤더 -->
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('audit.personalDataAccess.my.title') }}
      </h2>
      <p class="mt-1 text-sm text-gray-500">
        {{ t('audit.personalDataAccess.my.description') }}
      </p>
    </div>

    <!-- 보안 안내 박스 — KWCAG: role="note"로 보조기술에 전달 -->
    <div
      role="note"
      class="mb-4 flex items-start gap-2 rounded border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"
    >
      <el-icon class="mt-0.5 flex-shrink-0" :aria-hidden="true">
        <i-ep-warning />
      </el-icon>
      <span>{{ t('audit.personalDataAccess.my.warning') }}</span>
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
      :empty-text="t('audit.personalDataAccess.my.empty')"
      class="w-full"
    >
      <!-- KWCAG: caption sr-only 제공 -->
      <caption class="sr-only">{{ t('audit.personalDataAccess.my.title') }}</caption>

      <!-- 발생일시 -->
      <el-table-column
        prop="accessedAt"
        :label="t('audit.personalDataAccess.field.accessedAt')"
        min-width="160"
        sortable
      >
        <template #default="{ row }">
          {{ formatDate(row.accessedAt) }}
        </template>
      </el-table-column>

      <!-- 조회자 -->
      <el-table-column
        prop="viewerUsername"
        :label="t('audit.personalDataAccess.field.viewer')"
        min-width="130"
      >
        <template #default="{ row }">
          {{ row.viewerUsername }}
        </template>
      </el-table-column>

      <!-- 접근 필드 (tag list) — KWCAG: el-tag로 시각+텍스트 동시 제공 -->
      <el-table-column
        :label="t('audit.personalDataAccess.field.accessedFields')"
        min-width="200"
      >
        <template #default="{ row }">
          <div class="flex flex-wrap gap-1">
            <el-tag
              v-for="field in row.accessedFields"
              :key="field"
              size="small"
              type="warning"
            >
              {{ field }}
            </el-tag>
          </div>
        </template>
      </el-table-column>

      <!-- 목적 -->
      <el-table-column
        prop="purpose"
        :label="t('audit.personalDataAccess.field.purpose')"
        width="160"
      >
        <template #default="{ row }">
          {{ t(`audit.personalDataAccess.purpose.${row.purpose}`) }}
        </template>
      </el-table-column>

      <!-- 비고 (IP + user-agent 요약) -->
      <el-table-column
        :label="t('audit.personalDataAccess.field.ipAddress')"
        width="140"
      >
        <template #default="{ row }">
          <span class="font-mono text-xs text-gray-600">{{ row.ipAddress ?? '-' }}</span>
        </template>
      </el-table-column>
    </el-table>

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
// @MX:ANCHOR: [AUTO] MyPersonalDataAccessView — 라우터, AdminLayout 사용자 드롭다운에서 참조
// @MX:REASON: fan_in >= 3: 라우터 등록, AdminLayout 드롭다운 메뉴, 테스트 mock에서 참조
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { meApi } from '@/api/me'
import type { PersonalDataAccessEntry } from '@iroum/shared/types/api'

const { t } = useI18n()

// ── 상태 ────────────────────────────────────────────────────────────────────
const entries = ref<PersonalDataAccessEntry[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const liveAnnouncement = ref('')

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
async function loadEntries(): Promise<void> {
  loading.value = true
  try {
    const res = await meApi.myPersonalDataAccess({
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    entries.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = `${t('audit.personalDataAccess.my.empty')} (${res.data.totalElements})`
  } catch {
    ElMessage.error(t('common.error.unknown'))
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
