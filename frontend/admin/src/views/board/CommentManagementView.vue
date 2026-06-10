<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('comments.title') }}</h2>
    </div>

    <!-- 필터 영역 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-select
        v-model="filterBoardId"
        :placeholder="t('comments.filter.board')"
        clearable
        style="width: 200px"
        :aria-label="t('comments.filter.board')"
        @change="onSearch"
      >
        <el-option
          v-for="board in boards"
          :key="board.id"
          :label="board.name"
          :value="board.id"
        />
      </el-select>

      <el-select
        v-model="filterStatus"
        :placeholder="t('comments.filter.status')"
        style="width: 160px"
        :aria-label="t('comments.filter.status')"
        @change="onSearch"
      >
        <el-option :label="t('comments.status.ALL')" value="ALL" />
        <el-option :label="t('comments.status.VISIBLE')" value="VISIBLE" />
        <el-option :label="t('comments.status.HIDDEN')" value="HIDDEN" />
        <el-option :label="t('comments.status.DELETED')" value="DELETED" />
      </el-select>

      <el-input
        v-model="searchKeyword"
        :placeholder="t('comments.filter.keyword')"
        clearable
        style="width: 240px"
        :aria-label="t('comments.filter.keyword')"
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
      :data="comments"
      stripe
      :empty-text="t('comments.empty')"
      :aria-label="t('comments.title')"
      class="w-full"
    >
      <caption class="sr-only">{{ t('comments.title') }}</caption>

      <el-table-column
        prop="boardName"
        :label="t('comments.field.board')"
        width="150"
        show-overflow-tooltip
      />
      <el-table-column
        prop="postTitle"
        :label="t('comments.field.post')"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column
        prop="authorUsername"
        :label="t('comments.field.author')"
        width="140"
      >
        <template #default="{ row }">
          {{ row.authorUsername ?? t('comments.anonymous') }}
        </template>
      </el-table-column>
      <el-table-column
        prop="contentPreview"
        :label="t('comments.field.content')"
        min-width="260"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ truncate(row.contentPreview, 50) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        :label="t('comments.field.status')"
        width="110"
      >
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ t(`comments.status.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('comments.field.createdAt')"
        width="140"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column
        :label="t('common.actions')"
        width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="flex gap-1">
            <el-button
              v-if="row.status === 'VISIBLE'"
              size="small"
              type="warning"
              plain
              :aria-label="t('comments.action.hide')"
              @click="handleChangeStatus(row, 'HIDDEN')"
            >
              {{ t('comments.action.hide') }}
            </el-button>
            <el-button
              v-if="row.status === 'HIDDEN'"
              size="small"
              type="success"
              plain
              :aria-label="t('comments.action.show')"
              @click="handleChangeStatus(row, 'VISIBLE')"
            >
              {{ t('comments.action.show') }}
            </el-button>
            <el-button
              v-if="row.status !== 'DELETED'"
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
      v-if="!loading && comments.length === 0"
      :description="t('comments.empty')"
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
        @change="loadComments"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { boardApi } from '@/api/board'
import {
  listAdminComments,
  changeCommentStatus,
  deleteAdminComment,
  type CommentAdminSummary,
} from '@/api/comments'

const { t } = useI18n()

// ── 상태 ──────────────────────────────────────────────────────────────────
const comments = ref<CommentAdminSummary[]>([])
const boards = ref<{ id: number; name: string }[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const filterStatus = ref('ALL')
const filterBoardId = ref<number | undefined>(undefined)
const liveAnnouncement = ref('')

// @MX:ANCHOR: [AUTO] loadComments — onMounted, 검색, 페이지 변경, 상태변경/삭제 후 호출
// @MX:REASON: fan_in >= 3: 마운트, 필터/검색, 페이지 변경, 액션 후 갱신에서 사용
async function loadComments(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, string | number> = {
      page: currentPage.value - 1,
      size: pageSize.value,
      status: filterStatus.value,
    }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (filterBoardId.value != null) params.boardId = filterBoardId.value

    const res = await listAdminComments(params)
    comments.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = t('comments.loaded', { count: res.data.totalElements })
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

async function loadBoards(): Promise<void> {
  try {
    const res = await boardApi.listMasters()
    boards.value = res.data.map((b) => ({ id: b.id, name: b.name }))
  } catch {
    // 게시판 목록 로드 실패는 필터 비활성으로 graceful degrade
  }
}

function onSearch(): void {
  currentPage.value = 1
  loadComments()
}

async function handleChangeStatus(
  row: CommentAdminSummary,
  next: 'VISIBLE' | 'HIDDEN',
): Promise<void> {
  try {
    await changeCommentStatus(row.id, next)
    ElMessage.success(t('common.saveSuccess'))
    loadComments()
  } catch {
    ElMessage.error(t('common.saveError'))
  }
}

async function handleDelete(row: CommentAdminSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('comments.confirmDelete'),
      t('common.delete'),
      {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await deleteAdminComment(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadComments()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

function statusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    VISIBLE: 'success',
    HIDDEN: 'warning',
    DELETED: 'danger',
  }
  return map[status] ?? ''
}

function truncate(text: string, len: number): string {
  if (!text) return ''
  return text.length > len ? text.slice(0, len) + '…' : text
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

onMounted(() => {
  loadBoards()
  loadComments()
})
</script>
