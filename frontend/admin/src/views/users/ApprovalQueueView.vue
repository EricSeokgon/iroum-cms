<!--
  가입 승인 대기열 화면 — SPEC-CMS-USER-APPROVAL-001 T8
  목록/검색/단건·일괄 승인·거절(거절 사유 입력). SUPER_ADMIN/DEPT_ADMIN 전용 화면.
-->
<template>
  <div class="approval-queue">
    <div class="header">
      <h2>가입 승인 관리</h2>
    </div>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="이름 또는 아이디 검색"
        clearable
        style="width: 260px"
        data-testid="approval-search"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button type="primary" data-testid="approval-search-btn" @click="reload">검색</el-button>
      <div class="bulk-actions">
        <el-button
          type="success"
          :disabled="selected.length === 0"
          data-testid="bulk-approve-btn"
          @click="onBulkApprove"
        >
          선택 승인 ({{ selected.length }})
        </el-button>
        <el-button
          type="danger"
          :disabled="selected.length === 0"
          data-testid="bulk-reject-btn"
          @click="openRejectDialog(null)"
        >
          선택 거절 ({{ selected.length }})
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="rows"
      data-testid="approval-table"
      empty-text="승인 대기 중인 가입자가 없습니다"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column prop="name" label="이름" min-width="120" />
      <el-table-column prop="email" label="이메일" min-width="200" />
      <el-table-column prop="username" label="아이디" min-width="200" />
      <el-table-column label="가입신청일" min-width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="처리" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            type="success"
            :data-testid="`approve-${row.userId}`"
            @click="onApprove(row)"
          >
            승인
          </el-button>
          <el-button
            size="small"
            type="danger"
            :data-testid="`reject-${row.userId}`"
            @click="openRejectDialog(row)"
          >
            거절
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="totalElements > 0"
      class="pagination"
      layout="prev, pager, next, total"
      :total="totalElements"
      :page-size="pageSize"
      :current-page="currentPage"
      @current-change="onPageChange"
    />

    <!-- 거절 사유 입력 다이얼로그 -->
    <el-dialog v-model="rejectDialogVisible" title="거절 사유 입력" width="480px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="4"
        placeholder="거절 사유를 입력하세요 (필수)"
        data-testid="reject-reason-input"
      />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">취소</el-button>
        <el-button
          type="danger"
          :disabled="!rejectReason.trim()"
          data-testid="reject-confirm-btn"
          @click="confirmReject"
        >
          거절 확정
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApprovalsApi, type PendingUser } from '@/api/userApprovals'

const rows = ref<PendingUser[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const selected = ref<PendingUser[]>([])

// 거절 다이얼로그: target 이 null 이면 일괄 거절, 아니면 단건 거절.
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref<PendingUser | null>(null)

function formatDate(iso: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleString('ko-KR')
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await userApprovalsApi.list({
      page: currentPage.value - 1,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    rows.value = res.data.content
    totalElements.value = res.data.totalElements
  } catch {
    ElMessage.error('승인 대기열을 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

function reload(): void {
  currentPage.value = 1
  loadData()
}

function onPageChange(page: number): void {
  currentPage.value = page
  loadData()
}

function onSelectionChange(sel: PendingUser[]): void {
  selected.value = sel
}

async function onApprove(row: PendingUser): Promise<void> {
  try {
    await userApprovalsApi.approve(row.userId)
    ElMessage.success('승인되었습니다')
    await loadData()
  } catch {
    ElMessage.error('승인에 실패했습니다')
  }
}

async function onBulkApprove(): Promise<void> {
  try {
    const ids = selected.value.map((u) => u.userId)
    const res = await userApprovalsApi.bulkApprove(ids)
    ElMessage.success(`승인 ${res.data.successCount}건, 실패 ${res.data.failureCount}건`)
    await loadData()
  } catch {
    ElMessage.error('일괄 승인에 실패했습니다')
  }
}

function openRejectDialog(row: PendingUser | null): void {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function confirmReject(): Promise<void> {
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('거절 사유는 필수입니다')
    return
  }
  try {
    if (rejectTarget.value) {
      await userApprovalsApi.reject(rejectTarget.value.userId, reason)
      ElMessage.success('거절되었습니다')
    } else {
      const ids = selected.value.map((u) => u.userId)
      const res = await userApprovalsApi.bulkReject(ids, reason)
      ElMessage.success(`거절 ${res.data.successCount}건, 실패 ${res.data.failureCount}건`)
    }
    rejectDialogVisible.value = false
    await loadData()
  } catch {
    ElMessage.error('거절에 실패했습니다')
  }
}

onMounted(loadData)
</script>

<style scoped>
.approval-queue {
  padding: 16px;
}
.header {
  margin-bottom: 16px;
}
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}
.bulk-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
