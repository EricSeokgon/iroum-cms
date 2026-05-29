<template>
  <div data-testid="notification-center">
    <!-- 페이지 제목 -->
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('notificationCenter.title') }}
      </h2>
      <p class="mt-1 text-sm text-gray-500">
        {{ t('notificationCenter.subtitle') }}
      </p>
    </div>

    <!-- aria-live 영역 — 신규 알림 도착/필터 변경 통지 (KWCAG REQ-NC-013-2) -->
    <div aria-live="polite" aria-atomic="true" class="sr-only">
      {{ liveAnnouncement }}
    </div>

    <!-- 필터 바 -->
    <el-card class="mb-4" data-testid="notification-filter">
      <div class="flex flex-wrap items-end gap-4">
        <!-- 상태 -->
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium text-gray-700" for="filter-status">
            {{ t('notificationCenter.filter.status') }}
          </label>
          <el-select
            id="filter-status"
            v-model="statusFilter"
            multiple
            collapse-tags
            style="width: 220px"
            data-testid="filter-status"
            :placeholder="t('notificationCenter.filter.statusPlaceholder')"
            @change="applyFilter"
          >
            <el-option label="UNREAD" value="UNREAD" />
            <el-option label="READ" value="READ" />
            <el-option label="ARCHIVED" value="ARCHIVED" />
          </el-select>
        </div>

        <!-- 심각도 -->
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium text-gray-700" for="filter-severity">
            {{ t('notificationCenter.filter.severity') }}
          </label>
          <el-select
            id="filter-severity"
            v-model="severityFilter"
            multiple
            collapse-tags
            style="width: 220px"
            data-testid="filter-severity"
            :placeholder="t('notificationCenter.filter.severityPlaceholder')"
            @change="applyFilter"
          >
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="ERROR" value="ERROR" />
          </el-select>
        </div>

        <!-- 기간 -->
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium text-gray-700" for="filter-date">
            {{ t('notificationCenter.filter.period') }}
          </label>
          <el-date-picker
            id="filter-date"
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="-"
            :start-placeholder="t('notificationCenter.filter.from')"
            :end-placeholder="t('notificationCenter.filter.to')"
            data-testid="filter-date-range"
            @change="applyFilter"
          />
        </div>

        <div class="ml-auto flex gap-2">
          <el-button data-testid="reset-filter-btn" @click="onResetFilter">
            {{ t('notificationCenter.filter.reset') }}
          </el-button>
          <el-button
            type="primary"
            data-testid="mark-all-read-btn"
            :disabled="loading"
            @click="onMarkAllRead"
          >
            {{ t('notificationCenter.markAllRead') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 알림 목록 -->
    <el-card>
      <el-table
        v-loading="loading"
        :data="store.notifications"
        data-testid="notification-table"
        :aria-label="t('notificationCenter.title')"
        row-key="id"
        stripe
        @row-click="onRowClick"
      >
        <el-table-column
          prop="severity"
          :label="t('notificationCenter.column.severity')"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="severityTagType(row.severity)"
              size="small"
              effect="dark"
            >
              {{ row.severity }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          prop="title"
          :label="t('notificationCenter.column.title')"
        >
          <template #default="{ row }">
            <div class="flex flex-col gap-1">
              <span
                :class="row.status === 'UNREAD' ? 'font-semibold' : 'text-gray-700'"
              >
                {{ row.title }}
              </span>
              <span v-if="row.body" class="text-xs text-gray-500 line-clamp-2">
                {{ row.body }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          prop="type"
          :label="t('notificationCenter.column.type')"
          width="240"
        >
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ row.type }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column
          prop="status"
          :label="t('notificationCenter.column.status')"
          width="120"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusTagType(row.status)"
              size="small"
              effect="plain"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          prop="createdAt"
          :label="t('notificationCenter.column.createdAt')"
          width="180"
        >
          <template #default="{ row }">
            <span class="text-xs text-gray-600">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column
          :label="t('notificationCenter.column.actions')"
          width="200"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'UNREAD'"
              size="small"
              text
              @click.stop="onMarkRead(row.id)"
            >
              {{ t('notificationCenter.action.markRead') }}
            </el-button>
            <el-button
              v-if="row.status !== 'ARCHIVED'"
              size="small"
              text
              @click.stop="onArchive(row.id)"
            >
              {{ t('notificationCenter.action.archive') }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="py-10 text-center text-gray-500">
            {{ t('notificationCenter.empty') }}
          </div>
        </template>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="store.totalElements"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          :aria-label="t('a11y.pagination')"
          @change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
// SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-007 — 관리자 알림 통합 화면
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useNotificationCenterStore } from '@/stores/notificationCenter'
import { resolveNotificationDeepLink } from '@/router/notificationDeepLink'
import type {
  AdminNotificationDto,
  AdminNotificationSeverity,
  AdminNotificationStatus,
} from '@/api/adminNotifications'

const { t } = useI18n()
const router = useRouter()
const store = useNotificationCenterStore()

// ── 로컬 필터 (스토어로 위임) ───────────────────────────────────────────────
const statusFilter = ref<AdminNotificationStatus[]>([...store.filter.status])
const severityFilter = ref<AdminNotificationSeverity[]>([...store.filter.severity])
const dateRange = ref<[string, string] | null>(
  store.filter.from && store.filter.to ? [store.filter.from, store.filter.to] : null,
)

// el-pagination 은 1-base. store.page 는 0-base.
const currentPage = computed<number>({
  get: () => store.page + 1,
  set: (v) => store.setPage(Math.max(0, v - 1)),
})
const pageSize = computed<number>({
  get: () => store.size,
  set: (v) => store.setSize(v),
})

const loading = computed(() => store.loading)
const liveAnnouncement = ref('')

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
async function reload(): Promise<void> {
  await store.fetchNotifications()
  liveAnnouncement.value = t('notificationCenter.live.loaded', {
    count: store.totalElements,
  })
}

onMounted(() => {
  void reload()
  void store.fetchUnreadCount()
})

watch(() => [store.page, store.size], () => {
  void reload()
})

// ── 필터 적용 ────────────────────────────────────────────────────────────────
function applyFilter(): void {
  store.setFilter({
    status: statusFilter.value,
    severity: severityFilter.value,
    from: dateRange.value?.[0] ?? null,
    to: dateRange.value?.[1] ?? null,
  })
  void reload()
}

function onResetFilter(): void {
  statusFilter.value = ['UNREAD', 'READ']
  severityFilter.value = []
  dateRange.value = null
  store.resetFilter()
  void reload()
}

// ── 액션 ─────────────────────────────────────────────────────────────────────
async function onMarkRead(id: number): Promise<void> {
  try {
    await store.markRead(id)
    ElMessage.success(t('notificationCenter.toast.markReadSuccess'))
  } catch {
    ElMessage.error(t('notificationCenter.toast.markReadError'))
  }
}

async function onArchive(id: number): Promise<void> {
  try {
    await store.archive(id)
    ElMessage.success(t('notificationCenter.toast.archiveSuccess'))
  } catch {
    ElMessage.error(t('notificationCenter.toast.archiveError'))
  }
}

async function onMarkAllRead(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('notificationCenter.confirm.markAllReadMessage'),
      t('notificationCenter.confirm.markAllReadTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    // 사용자 취소
    return
  }

  try {
    const req = {
      severity: severityFilter.value.length > 0 ? severityFilter.value : undefined,
    }
    const updated = await store.markAllRead(req)
    ElMessage.success(t('notificationCenter.toast.markAllReadSuccess', { count: updated }))
    await reload()
  } catch {
    ElMessage.error(t('notificationCenter.toast.markAllReadError'))
  }
}

// REQ-NC-008 — 행 클릭 → 딥링크 + 자동 읽음 처리
async function onRowClick(row: AdminNotificationDto): Promise<void> {
  if (row.status === 'UNREAD') {
    // 백엔드 호출 실패해도 라우팅은 시도
    void store.markRead(row.id).catch(() => undefined)
  }
  const path = resolveNotificationDeepLink(row.refType, row.refId)
  if (path != null) {
    await router.push(path)
    return
  }
  if (!row.refType || row.refId == null) {
    ElMessage.info(t('notificationCenter.toast.noRefResource'))
  } else {
    // 매핑 미정의 → 콘솔 경고
    console.warn(`[NotificationCenter] Unknown refType: ${row.refType}`)
  }
}

function onPageChange(): void {
  void reload()
}

// ── 유틸 ─────────────────────────────────────────────────────────────────────
function severityTagType(s: AdminNotificationSeverity): 'info' | 'warning' | 'danger' {
  if (s === 'ERROR') return 'danger'
  if (s === 'WARN') return 'warning'
  return 'info'
}

function statusTagType(s: AdminNotificationStatus): 'primary' | 'success' | 'info' {
  if (s === 'UNREAD') return 'primary'
  if (s === 'READ') return 'success'
  return 'info'
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
