<template>
  <div>
    <!-- 페이지 제목 -->
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('audit.personalDataAccess.title') }}
      </h2>
      <p class="mt-1 text-sm text-gray-500">
        {{ t('audit.personalDataAccess.description') }}
      </p>
    </div>

    <!-- 필터 영역 -->
    <el-card class="mb-4">
      <form
        role="search"
        :aria-label="t('audit.personalDataAccess.title')"
        @submit.prevent="onSearch"
      >
        <div class="flex flex-wrap gap-4">
          <!-- 대상 사용자 ID -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-target-user"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.personalDataAccess.filter.targetUser') }}
            </label>
            <el-input
              id="filter-target-user"
              v-model="filterTargetUserId"
              type="number"
              clearable
              style="width: 180px"
              :placeholder="t('audit.personalDataAccess.filter.targetUser')"
            />
          </div>

          <!-- 조회자 ID -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-viewer"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.personalDataAccess.filter.viewer') }}
            </label>
            <el-input
              id="filter-viewer"
              v-model="filterViewerId"
              type="number"
              clearable
              style="width: 180px"
              :placeholder="t('audit.personalDataAccess.filter.viewer')"
            />
          </div>

          <!-- 조회 목적 (multi-select) -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-purpose"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.personalDataAccess.filter.purpose') }}
            </label>
            <el-select
              id="filter-purpose"
              v-model="filterPurpose"
              clearable
              style="width: 200px"
              :placeholder="t('audit.personalDataAccess.filter.purpose')"
            >
              <el-option
                v-for="p in purposeOptions"
                :key="p.value"
                :label="p.label"
                :value="p.value"
              />
            </el-select>
          </div>

          <!-- 기간 -->
          <div class="flex flex-col gap-1">
            <label
              for="filter-date-range"
              class="text-sm font-medium text-gray-700"
            >
              {{ t('audit.personalDataAccess.filter.period') }}
            </label>
            <el-date-picker
              id="filter-date-range"
              v-model="filterDateRange"
              type="daterange"
              :start-placeholder="t('audit.personalDataAccess.filter.period')"
              :end-placeholder="t('audit.personalDataAccess.filter.period')"
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
            {{ t('audit.personalDataAccess.filter.search') }}
          </el-button>
          <el-button @click="onReset">
            {{ t('audit.personalDataAccess.filter.reset') }}
          </el-button>
        </div>
      </form>
    </el-card>

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
      :empty-text="t('audit.personalDataAccess.empty')"
      class="w-full"
      @row-click="openDetail"
    >
      <!-- KWCAG: caption을 sr-only로 제공 -->
      <caption class="sr-only">{{ t('audit.personalDataAccess.title') }}</caption>

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

      <!-- 조회자 역할 (badge) -->
      <el-table-column
        prop="viewerRole"
        :label="t('audit.personalDataAccess.field.viewerRole')"
        width="160"
      >
        <template #default="{ row }">
          <el-tag v-if="row.viewerRole" type="info" size="small">
            {{ row.viewerRole }}
          </el-tag>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>

      <!-- 대상 사용자 -->
      <el-table-column
        prop="targetUsername"
        :label="t('audit.personalDataAccess.field.targetUser')"
        min-width="130"
      >
        <template #default="{ row }">
          {{ row.targetUsername }}
        </template>
      </el-table-column>

      <!-- 접근 필드 (tag list) -->
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

      <!-- IP -->
      <el-table-column
        prop="ipAddress"
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

    <!-- 상세 모달 (focus trap: el-dialog 기본 제공) -->
    <el-dialog
      v-model="showDetail"
      :title="t('audit.personalDataAccess.title')"
      width="600px"
      destroy-on-close
      :aria-label="t('audit.personalDataAccess.title')"
    >
      <template v-if="selectedEntry">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('audit.personalDataAccess.field.accessedAt')">
            {{ formatDate(selectedEntry.accessedAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.personalDataAccess.field.viewer')">
            {{ selectedEntry.viewerUsername }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.personalDataAccess.field.viewerRole')">
            {{ selectedEntry.viewerRole ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.personalDataAccess.field.targetUser')">
            {{ selectedEntry.targetUsername }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.personalDataAccess.field.accessedFields')">
            <div class="flex flex-wrap gap-1">
              <el-tag
                v-for="field in selectedEntry.accessedFields"
                :key="field"
                size="small"
                type="warning"
              >
                {{ field }}
              </el-tag>
            </div>
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.personalDataAccess.field.purpose')">
            {{ t(`audit.personalDataAccess.purpose.${selectedEntry.purpose}`) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.personalDataAccess.field.ipAddress')">
            <span class="font-mono text-sm">{{ selectedEntry.ipAddress ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('audit.personalDataAccess.field.userAgent')">
            <span class="break-all text-xs text-gray-500">{{ selectedEntry.userAgent ?? '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
// @MX:ANCHOR: [AUTO] PersonalDataAccessLogView — 라우터, AdminLayout 사이드바에서 참조
// @MX:REASON: fan_in >= 3: 라우터 등록, AdminLayout 사이드바 메뉴, 테스트 mock에서 참조
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { auditApi } from '@/api/audit'
import type { PersonalDataAccessEntry, PersonalDataAccessPurpose } from '@iroum/shared/types/api'

const { t } = useI18n()

// ── 상태 ────────────────────────────────────────────────────────────────────
const entries = ref<PersonalDataAccessEntry[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filterTargetUserId = ref<string>('')
const filterViewerId = ref<string>('')
const filterPurpose = ref<string>('')
const filterDateRange = ref<[string, string] | null>(null)

const showDetail = ref(false)
const selectedEntry = ref<PersonalDataAccessEntry | null>(null)
const liveAnnouncement = ref('')

// ── 목적 옵션 ────────────────────────────────────────────────────────────────
const purposeOptions = computed(() => {
  const purposes: PersonalDataAccessPurpose[] = [
    'BUSINESS_INQUIRY', 'SUPPORT', 'AUDIT', 'SELF_VIEW',
    'ADMIN_USER_LIST', 'ADMIN_USER_EDIT', 'EXPORT',
  ]
  return purposes.map((p) => ({
    value: p,
    label: t(`audit.personalDataAccess.purpose.${p}`),
  }))
})

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
async function loadEntries(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: currentPage.value - 1,
      size: pageSize.value,
      sort: 'accessedAt,desc',
    }
    if (filterTargetUserId.value) params.targetUserId = Number(filterTargetUserId.value)
    if (filterViewerId.value) params.viewerId = Number(filterViewerId.value)
    if (filterPurpose.value) params.purpose = filterPurpose.value
    if (filterDateRange.value) {
      params.from = filterDateRange.value[0]
      params.to = filterDateRange.value[1]
    }

    const res = await auditApi.personalDataAccess(params)
    entries.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = `${t('audit.personalDataAccess.empty')} (${res.data.totalElements})`
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
  filterViewerId.value = ''
  filterPurpose.value = ''
  filterDateRange.value = null
  currentPage.value = 1
  loadEntries()
}

function openDetail(row: PersonalDataAccessEntry): void {
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
