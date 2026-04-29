<template>
  <div>
    <!-- 페이지 제목 -->
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('audit.permissionChanges.title') }}
      </h2>
    </div>

    <!-- 필터 영역 -->
    <el-card class="mb-4">
      <form
        role="search"
        :aria-label="t('audit.permissionChanges.title')"
        @submit.prevent="onSearch"
      >
        <div class="flex flex-wrap gap-4">
          <!-- 대상 사용자 ID -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-target-user"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.permissionChanges.filter.targetUser') }}
            </label>
            <el-input
              id="filter-target-user"
              v-model="filterTargetUserId"
              type="number"
              clearable
              style="width: 180px"
              :placeholder="t('audit.permissionChanges.filter.targetUser')"
            />
          </div>

          <!-- 변경 유형 -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-change-type"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.permissionChanges.filter.changeType') }}
            </label>
            <el-select
              id="filter-change-type"
              v-model="filterChangeType"
              clearable
              style="width: 200px"
              :placeholder="t('audit.permissionChanges.filter.changeType')"
            >
              <el-option
                v-for="ct in changeTypeOptions"
                :key="ct.value"
                :label="ct.label"
                :value="ct.value"
              />
            </el-select>
          </div>

          <!-- 변경자 ID -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-changed-by"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.permissionChanges.filter.changedBy') }}
            </label>
            <el-input
              id="filter-changed-by"
              v-model="filterChangedBy"
              type="number"
              clearable
              style="width: 180px"
              :placeholder="t('audit.permissionChanges.filter.changedBy')"
            />
          </div>

          <!-- 기간 -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-date-range"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.permissionChanges.filter.period') }}
            </label>
            <el-date-picker
              id="filter-date-range"
              v-model="filterDateRange"
              type="daterange"
              :start-placeholder="t('audit.permissionChanges.filter.period')"
              :end-placeholder="t('audit.permissionChanges.filter.period')"
              value-format="YYYY-MM-DD"
              style="width: 260px"
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
          <!-- CSV 다운로드 (placeholder) -->
          <el-button plain disabled class="ml-auto">
            CSV
          </el-button>
        </div>
      </form>
    </el-card>

    <!-- 검색 결과 갱신 알림 (스크린 리더) -->
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
      :empty-text="t('audit.permissionChanges.empty')"
      class="w-full"
      @row-click="openDetail"
    >
      <!-- KWCAG: el-table caption은 aria-label로 대체 (Element Plus 렌더링 구조상) -->
      <caption class="sr-only">{{ t('audit.permissionChanges.title') }}</caption>

      <el-table-column
        prop="changedAt"
        :label="t('audit.permissionChanges.field.changedAt')"
        min-width="160"
        sortable
      >
        <template #default="{ row }">
          {{ formatDate(row.changedAt) }}
        </template>
      </el-table-column>

      <el-table-column
        prop="changeType"
        :label="t('audit.permissionChanges.field.changeType')"
        width="160"
      >
        <template #default="{ row }">
          <el-tag :type="changeTypeTagType(row.changeType)" size="small">
            {{ t(`audit.permissionChanges.type.${row.changeType}`) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        prop="targetUsername"
        :label="t('audit.permissionChanges.field.targetUser')"
        min-width="130"
      >
        <template #default="{ row }">
          {{ row.targetUsername ?? '-' }}
        </template>
      </el-table-column>

      <el-table-column
        prop="targetResource"
        :label="t('audit.permissionChanges.field.targetResource')"
        min-width="160"
      />

      <el-table-column
        prop="changedByUsername"
        :label="t('audit.permissionChanges.field.changedBy')"
        min-width="130"
      >
        <template #default="{ row }">
          {{ row.changedByUsername ?? '-' }}
        </template>
      </el-table-column>

      <el-table-column
        prop="severity"
        :label="t('audit.permissionChanges.field.severity')"
        width="110"
      >
        <template #default="{ row }">
          <!-- KWCAG: 색상 + 텍스트 동시 표시 -->
          <span
            class="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium"
            :class="severityClass(row.severity)"
            :aria-label="`${t('audit.permissionChanges.field.severity')}: ${t(`audit.permissionChanges.severity.${row.severity}`)}`"
          >
            {{ t(`audit.permissionChanges.severity.${row.severity}`) }}
          </span>
        </template>
      </el-table-column>

      <el-table-column
        prop="reason"
        :label="t('audit.permissionChanges.field.reason')"
        min-width="160"
      >
        <template #default="{ row }">
          <span class="text-sm text-gray-600">{{ row.reason ?? '-' }}</span>
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

    <!-- 상세 모달 -->
    <el-dialog
      v-model="showDetail"
      :title="t('audit.permissionChanges.title')"
      width="560px"
      :aria-label="t('audit.permissionChanges.title')"
      destroy-on-close
    >
      <template v-if="selectedEntry">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('audit.permissionChanges.field.changedAt')">
            {{ formatDate(selectedEntry.changedAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.permissionChanges.field.changeType')">
            {{ t(`audit.permissionChanges.type.${selectedEntry.changeType}`) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.permissionChanges.field.targetUser')">
            {{ selectedEntry.targetUsername ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.permissionChanges.field.targetResource')">
            {{ selectedEntry.targetResource }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.permissionChanges.field.changedBy')">
            {{ selectedEntry.changedByUsername ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.permissionChanges.field.severity')">
            <span
              class="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium"
              :class="severityClass(selectedEntry.severity)"
            >
              {{ t(`audit.permissionChanges.severity.${selectedEntry.severity}`) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.permissionChanges.field.reason')">
            {{ selectedEntry.reason ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
// @MX:ANCHOR: [AUTO] PermissionChangeHistoryView — router, AdminLayout, UserDetailView에서 참조
// @MX:REASON: fan_in >= 3: 라우터 등록, 사이드바 메뉴, UserDetailView '전체 보기' 링크에서 참조
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { auditApi } from '@/api/audit'
import type { PermissionChangeEntry, PermissionChangeType, AuditSeverity } from '@iroum/shared/types/api'

const { t } = useI18n()
const route = useRoute()

// ── 상태 ────────────────────────────────────────────────────────────────────
const entries = ref<PermissionChangeEntry[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filterTargetUserId = ref<string>('')
const filterChangeType = ref<string>('')
const filterChangedBy = ref<string>('')
const filterDateRange = ref<[string, string] | null>(null)

const showDetail = ref(false)
const selectedEntry = ref<PermissionChangeEntry | null>(null)
const liveAnnouncement = ref('')

// ── 변경 유형 옵션 ────────────────────────────────────────────────────────────
const changeTypeOptions = computed(() => [
  { value: 'ROLE_ASSIGN', label: t('audit.permissionChanges.type.ROLE_ASSIGN') },
  { value: 'ROLE_UNASSIGN', label: t('audit.permissionChanges.type.ROLE_UNASSIGN') },
  { value: 'ROLE_PERMISSION_GRANT', label: t('audit.permissionChanges.type.ROLE_PERMISSION_GRANT') },
  { value: 'ROLE_PERMISSION_REVOKE', label: t('audit.permissionChanges.type.ROLE_PERMISSION_REVOKE') },
])

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
async function loadEntries(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: currentPage.value - 1,
      size: pageSize.value,
      sort: 'changedAt,desc',
    }
    if (filterTargetUserId.value) params.targetUserId = Number(filterTargetUserId.value)
    if (filterChangeType.value) params.changeType = filterChangeType.value
    if (filterChangedBy.value) params.changedBy = Number(filterChangedBy.value)
    if (filterDateRange.value) {
      params.from = filterDateRange.value[0]
      params.to = filterDateRange.value[1]
    }

    const res = await auditApi.permissionChanges(params)
    entries.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = t('audit.permissionChanges.empty')
      + ` (${res.data.totalElements})`
  } catch {
    ElMessage.error(t('common.error.unknown'))
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  currentPage.value = 1
  loadEntries()
}

function onReset(): void {
  filterTargetUserId.value = ''
  filterChangeType.value = ''
  filterChangedBy.value = ''
  filterDateRange.value = null
  currentPage.value = 1
  loadEntries()
}

function openDetail(row: PermissionChangeEntry): void {
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

function changeTypeTagType(ct: PermissionChangeType): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<PermissionChangeType, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    ROLE_ASSIGN: 'success',
    ROLE_UNASSIGN: 'warning',
    ROLE_PERMISSION_GRANT: '',
    ROLE_PERMISSION_REVOKE: 'danger',
  }
  return map[ct] ?? 'info'
}

function severityClass(severity: AuditSeverity): string {
  const map: Record<AuditSeverity, string> = {
    INFO: 'bg-gray-100 text-gray-700',
    WARN: 'bg-orange-100 text-orange-700',
    CRITICAL: 'bg-red-100 text-red-700',
  }
  return map[severity] ?? 'bg-gray-100 text-gray-700'
}

onMounted(() => {
  // URL 쿼리에서 targetUserId 필터 초기값 적용 (UserDetailView '전체 보기' 링크)
  if (route.query.targetUserId) {
    filterTargetUserId.value = String(route.query.targetUserId)
  }
  loadEntries()
})
</script>
