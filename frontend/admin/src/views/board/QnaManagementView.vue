<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('qnaAdmin.title') }}</h2>
    </div>

    <!-- 필터 영역 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-select
        v-model="filterStatus"
        :placeholder="t('qnaAdmin.filter.status')"
        style="width: 160px"
        :aria-label="t('qnaAdmin.filter.status')"
        @change="onSearch"
      >
        <el-option :label="t('qnaAdmin.status.ALL')" value="" />
        <el-option :label="t('qnaAdmin.status.PENDING')" value="PENDING" />
        <el-option :label="t('qnaAdmin.status.ANSWERED')" value="ANSWERED" />
        <el-option :label="t('qnaAdmin.status.CLOSED')" value="CLOSED" />
        <el-option :label="t('qnaAdmin.status.HIDDEN')" value="HIDDEN" />
      </el-select>

      <el-input
        v-model="searchKeyword"
        :placeholder="t('qnaAdmin.filter.keyword')"
        clearable
        style="width: 240px"
        :aria-label="t('qnaAdmin.filter.keyword')"
        @keyup.enter="onSearch"
      >
        <template #prefix>
          <el-icon><i-ep-search /></el-icon>
        </template>
      </el-input>

      <el-button type="primary" plain @click="onSearch">
        {{ t('common.search') }}
      </el-button>
    </div>

    <!-- aria-live 알림 -->
    <div aria-live="polite" aria-atomic="true" class="sr-only">{{ liveAnnouncement }}</div>

    <!-- 테이블 -->
    <el-table
      v-loading="loading"
      :data="qnas"
      stripe
      :empty-text="t('qnaAdmin.empty')"
      :aria-label="t('qnaAdmin.title')"
      class="w-full"
    >
      <caption class="sr-only">{{ t('qnaAdmin.title') }}</caption>

      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column
        prop="title"
        :label="t('qnaAdmin.field.title')"
        min-width="260"
        show-overflow-tooltip
      />
      <el-table-column
        prop="questionerId"
        :label="t('qnaAdmin.field.questioner')"
        width="120"
      />
      <el-table-column
        prop="isPrivate"
        :label="t('qnaAdmin.field.isPrivate')"
        width="80"
      >
        <template #default="{ row }">
          {{ row.isPrivate ? t('qnaAdmin.private') : t('qnaAdmin.public') }}
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        :label="t('qnaAdmin.field.status')"
        width="110"
      >
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ t(`qnaAdmin.status.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('qnaAdmin.field.createdAt')"
        width="140"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column
        :label="t('common.actions')"
        width="260"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="flex gap-1 flex-wrap">
            <el-button
              v-if="row.status !== 'HIDDEN'"
              size="small"
              type="warning"
              plain
              :aria-label="t('qnaAdmin.action.hide')"
              @click="handleChangeStatus(row, 'HIDDEN')"
            >
              {{ t('qnaAdmin.action.hide') }}
            </el-button>
            <el-button
              v-if="row.status === 'HIDDEN'"
              size="small"
              type="success"
              plain
              :aria-label="t('qnaAdmin.action.restore')"
              @click="handleChangeStatus(row, 'PENDING')"
            >
              {{ t('qnaAdmin.action.restore') }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :aria-label="t('common.delete')"
              @click="handleDelete(row)"
            >
              {{ t('common.delete') }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 빈 상태 -->
    <el-empty
      v-if="!loading && qnas.length === 0"
      :description="t('qnaAdmin.empty')"
      :image-size="120"
      class="mt-8"
    />

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="totalElements"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        :aria-label="t('a11y.pagination')"
        @change="loadQnas"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listAdminQnas,
  changeQnaStatus,
  deleteAdminQna,
  type QnaAdminSummary,
  type QnaStatus,
} from '@/api/qnaAdmin'

const { t } = useI18n()

// ── 상태 ──────────────────────────────────────────────────────────────────
const qnas = ref<QnaAdminSummary[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const filterStatus = ref('')
const liveAnnouncement = ref('')

// @MX:ANCHOR: [AUTO] loadQnas — onMounted, 검색, 페이지 변경, 상태변경/삭제 후 호출
// @MX:REASON: fan_in >= 3: 마운트, 필터/검색, 페이지 변경, 액션 후 갱신에서 사용
async function loadQnas(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, string | number> = {
      page: currentPage.value - 1,
      size: pageSize.value,
    }
    if (filterStatus.value) params.status = filterStatus.value
    if (searchKeyword.value) params.keyword = searchKeyword.value

    const res = await listAdminQnas(params)
    qnas.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = t('qnaAdmin.loaded', { count: res.data.totalElements })
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  currentPage.value = 1
  loadQnas()
}

async function handleChangeStatus(row: QnaAdminSummary, next: QnaStatus): Promise<void> {
  try {
    await changeQnaStatus(row.id, next)
    ElMessage.success(t('common.saveSuccess'))
    loadQnas()
  } catch {
    ElMessage.error(t('common.saveError'))
  }
}

async function handleDelete(row: QnaAdminSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('qnaAdmin.confirmDelete'),
      t('common.delete'),
      {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await deleteAdminQna(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadQnas()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

function statusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'info',
    ANSWERED: 'success',
    CLOSED: '',
    HIDDEN: 'warning',
  }
  return map[status] ?? ''
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

onMounted(() => {
  loadQnas()
})
</script>
