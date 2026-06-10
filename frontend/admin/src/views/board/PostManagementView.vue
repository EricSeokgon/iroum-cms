<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('postAdmin.title') }}</h2>
    </div>

    <!-- 필터 영역 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-select
        v-model="filterStatus"
        :placeholder="t('postAdmin.filter.status')"
        style="width: 160px"
        :aria-label="t('postAdmin.filter.status')"
        @change="onSearch"
      >
        <el-option :label="t('postAdmin.status.ALL')" value="" />
        <el-option :label="t('postAdmin.status.DRAFT')" value="DRAFT" />
        <el-option :label="t('postAdmin.status.SCHEDULED')" value="SCHEDULED" />
        <el-option :label="t('postAdmin.status.PUBLISHED')" value="PUBLISHED" />
        <el-option :label="t('postAdmin.status.HIDDEN')" value="HIDDEN" />
      </el-select>

      <el-input
        v-model="searchKeyword"
        :placeholder="t('postAdmin.filter.keyword')"
        clearable
        style="width: 240px"
        :aria-label="t('postAdmin.filter.keyword')"
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
      :data="posts"
      stripe
      :empty-text="t('postAdmin.empty')"
      :aria-label="t('postAdmin.title')"
      class="w-full"
    >
      <caption class="sr-only">{{ t('postAdmin.title') }}</caption>

      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column
        prop="bbsName"
        :label="t('postAdmin.field.bbsName')"
        width="140"
        show-overflow-tooltip
      />
      <el-table-column
        prop="title"
        :label="t('postAdmin.field.title')"
        min-width="240"
        show-overflow-tooltip
      />
      <el-table-column
        prop="authorName"
        :label="t('postAdmin.field.author')"
        width="120"
      />
      <el-table-column
        prop="status"
        :label="t('postAdmin.field.status')"
        width="110"
      >
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ t(`postAdmin.status.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('postAdmin.field.createdAt')"
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
          <div class="flex gap-1 flex-wrap">
            <el-button
              v-if="row.status !== 'HIDDEN'"
              size="small"
              type="warning"
              plain
              :aria-label="t('postAdmin.action.hide')"
              @click="handleChangeStatus(row, 'HIDDEN')"
            >
              {{ t('postAdmin.action.hide') }}
            </el-button>
            <el-button
              v-if="row.status === 'HIDDEN'"
              size="small"
              type="success"
              plain
              :aria-label="t('postAdmin.action.restore')"
              @click="handleChangeStatus(row, 'PUBLISHED')"
            >
              {{ t('postAdmin.action.restore') }}
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
      v-if="!loading && posts.length === 0"
      :description="t('postAdmin.empty')"
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
        @change="loadPosts"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listAdminPosts,
  changePostStatus,
  deleteAdminPost,
  type PostAdminSummary,
  type PostStatus,
} from '@/api/postAdmin'

const { t } = useI18n()

// ── 상태 ──────────────────────────────────────────────────────────────────
const posts = ref<PostAdminSummary[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const filterStatus = ref('')
const liveAnnouncement = ref('')

// @MX:ANCHOR: [AUTO] loadPosts — onMounted, 검색, 페이지 변경, 상태변경/삭제 후 호출
// @MX:REASON: fan_in >= 3: 마운트, 필터/검색, 페이지 변경, 액션 후 갱신에서 사용
async function loadPosts(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, string | number> = {
      page: currentPage.value - 1,
      size: pageSize.value,
    }
    if (filterStatus.value) params.status = filterStatus.value
    if (searchKeyword.value) params.keyword = searchKeyword.value

    const res = await listAdminPosts(params)
    posts.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = t('postAdmin.loaded', { count: res.data.totalElements })
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  currentPage.value = 1
  loadPosts()
}

async function handleChangeStatus(row: PostAdminSummary, next: PostStatus): Promise<void> {
  try {
    await changePostStatus(row.id, next)
    ElMessage.success(t('common.saveSuccess'))
    loadPosts()
  } catch {
    ElMessage.error(t('common.saveError'))
  }
}

async function handleDelete(row: PostAdminSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('postAdmin.confirmDelete'),
      t('common.delete'),
      {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await deleteAdminPost(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadPosts()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

function statusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    DRAFT: 'info',
    SCHEDULED: '',
    PUBLISHED: 'success',
    HIDDEN: 'warning',
    DELETED: 'danger',
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
  loadPosts()
})
</script>
