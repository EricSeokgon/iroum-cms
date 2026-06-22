<!--
  SPEC-CMS-REVIEW-001 C2 — 관리자 리뷰 모더레이션 화면

  - 전체 리뷰 목록(페이지네이션 + 상태/게시물ID 필터)
  - 숨김(PATCH /admin/reviews/{id}/hide): VISIBLE 일 때만 가능
  - 삭제(DELETE /admin/reviews/{id}): DELETED 가 아닐 때만 가능 (비가역 — REQ-REV-006)
  - CommentManagementView 모더레이션 패턴 준용. 권한: REVIEW:READ(조회) / REVIEW:DELETE(액션)
  - 사용자 노출 텍스트는 한국어 하드코딩 (locale JSON 미변경 제약)
-->
<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">리뷰 관리</h2>
    </div>

    <!-- 필터 영역 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-input
        v-model="filterPostId"
        placeholder="게시물 ID"
        clearable
        style="width: 160px"
        aria-label="게시물 ID 필터"
        @keyup.enter="onSearch"
      >
        <template #prefix>
          <el-icon><i-ep-search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="filterStatus"
        placeholder="상태"
        style="width: 160px"
        aria-label="상태 필터"
        @change="onSearch"
      >
        <el-option label="전체" value="ALL" />
        <el-option label="노출" value="VISIBLE" />
        <el-option label="숨김" value="HIDDEN" />
        <el-option label="삭제" value="DELETED" />
      </el-select>

      <el-button type="primary" plain @click="onSearch">검색</el-button>
    </div>

    <!-- aria-live 알림 -->
    <div aria-live="polite" aria-atomic="true" class="sr-only">{{ liveAnnouncement }}</div>

    <!-- 테이블 -->
    <el-table
      v-loading="loading"
      :data="reviews"
      stripe
      empty-text="리뷰가 없습니다"
      aria-label="리뷰 관리"
      class="w-full"
    >
      <caption class="sr-only">리뷰 관리</caption>

      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="postId" label="게시물ID" width="100" />
      <el-table-column prop="authorName" label="작성자" width="140">
        <template #default="{ row }">
          {{ row.authorName ?? '익명' }}
        </template>
      </el-table-column>
      <el-table-column prop="rating" label="별점" width="150">
        <template #default="{ row }">
          <el-rate :model-value="row.rating" disabled size="small" text-color="#f59e0b" />
        </template>
      </el-table-column>
      <el-table-column
        prop="content"
        label="내용"
        min-width="260"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ truncate(row.content, 50) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="상태" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP" width="140">
        <template #default="{ row }">
          {{ row.ipAddress ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="작성일" width="130">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="작업" width="180" fixed="right">
        <template #default="{ row }">
          <div class="flex gap-1">
            <el-button
              size="small"
              type="warning"
              plain
              :disabled="row.status !== 'VISIBLE'"
              aria-label="숨김"
              @click="handleHide(row)"
            >
              숨김
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="row.status === 'DELETED'"
              aria-label="삭제"
              @click="handleDelete(row)"
            >
              삭제
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 빈 상태 -->
    <el-empty
      v-if="!loading && reviews.length === 0"
      description="리뷰가 없습니다"
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
        aria-label="페이지네이션"
        @change="loadReviews"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listAdminReviews,
  hideAdminReview,
  deleteAdminReview,
  type AdminReviewResponse,
} from '@/api/reviews'

// ── 상태 ──────────────────────────────────────────────────────────────────
const reviews = ref<AdminReviewResponse[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const filterStatus = ref('ALL')
const filterPostId = ref('')
const liveAnnouncement = ref('')

// @MX:ANCHOR: [AUTO] loadReviews — onMounted, 검색, 페이지 변경, 숨김/삭제 후 호출
// @MX:REASON: fan_in >= 3: 마운트, 필터/검색, 페이지 변경, 액션 후 갱신에서 사용
async function loadReviews(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, string | number> = {
      page: currentPage.value - 1,
      size: pageSize.value,
      status: filterStatus.value,
    }
    const postId = Number(filterPostId.value)
    if (filterPostId.value && !Number.isNaN(postId)) params.postId = postId

    const res = await listAdminReviews(params)
    reviews.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = `리뷰 ${res.data.totalElements}건을 불러왔습니다`
  } catch {
    ElMessage.error('목록을 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  currentPage.value = 1
  loadReviews()
}

async function handleHide(row: AdminReviewResponse): Promise<void> {
  try {
    await ElMessageBox.confirm('이 리뷰를 숨김 처리하시겠습니까?', '숨김', {
      type: 'warning',
      confirmButtonText: '숨김',
      cancelButtonText: '취소',
    })
    await hideAdminReview(row.id)
    ElMessage.success('숨김 처리되었습니다')
    loadReviews()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('처리에 실패했습니다')
  }
}

async function handleDelete(row: AdminReviewResponse): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '이 리뷰를 삭제하시겠습니까? 삭제는 복구할 수 없습니다.',
      '삭제',
      {
        type: 'warning',
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
      },
    )
    await deleteAdminReview(row.id)
    ElMessage.success('삭제되었습니다')
    loadReviews()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('삭제에 실패했습니다')
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

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    VISIBLE: '노출',
    HIDDEN: '숨김',
    DELETED: '삭제',
  }
  return map[status] ?? status
}

function truncate(text: string | null, len: number): string {
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

onMounted(loadReviews)
</script>
