<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('qna.title') }}</h2>
    </div>

    <!-- 검색 영역 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-input
        v-model="searchKeyword"
        :placeholder="t('common.search')"
        clearable
        style="width: 240px"
        :aria-label="t('common.search')"
        @keyup.enter="onSearch"
      >
        <template #prefix>
          <el-icon><i-ep-search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="filterStatus"
        :placeholder="t('qna.field.status')"
        clearable
        style="width: 160px"
        :aria-label="t('qna.field.status')"
        @change="onSearch"
      >
        <el-option :label="t('qna.status.PENDING')" value="PENDING" />
        <el-option :label="t('qna.status.ANSWERED')" value="ANSWERED" />
        <el-option :label="t('qna.status.CLOSED')" value="CLOSED" />
        <el-option :label="t('qna.status.HIDDEN')" value="HIDDEN" />
      </el-select>

      <el-select
        v-model="filterPrivacy"
        :placeholder="t('qna.field.isPrivate')"
        clearable
        style="width: 140px"
        :aria-label="t('qna.field.isPrivate')"
        @change="onSearch"
      >
        <el-option :label="t('qna.privacy.all')" value="" />
        <el-option :label="t('qna.privacy.public')" value="false" />
        <el-option :label="t('qna.privacy.private')" value="true" />
      </el-select>

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
      :empty-text="t('qna.empty')"
      :aria-label="t('qna.title')"
      class="w-full cursor-pointer"
      @row-click="goDetail"
    >
      <caption class="sr-only">{{ t('qna.title') }}</caption>

      <el-table-column
        prop="title"
        :label="t('qna.field.title')"
        min-width="320"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <div class="flex items-center gap-2">
            <el-tag
              v-if="row.isPrivate"
              size="small"
              type="warning"
              :aria-label="t('qna.privacy.private')"
            >
              {{ t('qna.privacy.private') }}
            </el-tag>
            <span>{{ row.title }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        prop="questionerId"
        :label="t('qna.field.questioner')"
        width="120"
        align="center"
      />
      <el-table-column
        prop="status"
        :label="t('qna.field.status')"
        width="120"
      >
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ t(`qna.status.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="isPrivate"
        :label="t('qna.field.isPrivate')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <span :aria-label="row.isPrivate ? t('qna.privacy.private') : t('qna.privacy.public')">
            {{ row.isPrivate ? t('qna.privacy.private') : t('qna.privacy.public') }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('common.startDate')"
        width="140"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column
        :label="t('common.actions')"
        width="240"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="flex gap-1">
            <el-button
              v-if="row.status === 'PENDING' && isAdmin"
              size="small"
              type="primary"
              plain
              :aria-label="t('qna.answer')"
              @click.stop="openAnswerDialog(row)"
            >
              {{ t('qna.answer') }}
            </el-button>
            <el-button
              v-if="row.status !== 'CLOSED' && isAdmin"
              size="small"
              type="info"
              plain
              :aria-label="t('qna.close')"
              @click.stop="handleClose(row)"
            >
              {{ t('qna.close') }}
            </el-button>
            <el-button
              v-if="isAdmin"
              size="small"
              type="danger"
              plain
              :aria-label="t('common.delete')"
              @click.stop="handleDelete(row)"
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
      :description="t('qna.empty')"
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

    <!-- 답변 다이얼로그 -->
    <el-dialog
      v-model="showAnswerDialog"
      :title="t('qna.answer')"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item :label="t('qna.field.title')">
          <div class="text-sm text-gray-700">{{ answeringQna?.title }}</div>
        </el-form-item>
        <el-form-item :label="t('qna.field.answer')">
          <el-input
            v-model="answerHtml"
            type="textarea"
            :rows="8"
            :placeholder="t('qna.field.answer')"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showAnswerDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAnswerSubmit">
          {{ t('qna.answer') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  listQnas,
  answerQna,
  closeQna,
  deleteQna,
  type QnaSummary,
} from '@/api/qna'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

// ── 상태 ──────────────────────────────────────────────────────────────────
const qnas = ref<QnaSummary[]>([])
const loading = ref(false)
const submitting = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const filterStatus = ref('')
const filterPrivacy = ref<'' | 'true' | 'false'>('')
const liveAnnouncement = ref('')

const showAnswerDialog = ref(false)
const answeringQna = ref<QnaSummary | null>(null)
const answerHtml = ref('')

// ── 권한 ──────────────────────────────────────────────────────────────────
const isAdmin = computed(() => {
  const roles = auth.user?.roleCodes ?? []
  return roles.includes('SUPER_ADMIN') || roles.includes('ADMIN') || roles.includes('DEPT_ADMIN')
})

// @MX:ANCHOR: [AUTO] loadQnas — onMounted, 검색, 페이지 변경, 답변/종결/삭제 후 호출
// @MX:REASON: fan_in >= 3: 마운트, 검색/필터, 페이지 변경, 액션 후 갱신에서 사용
async function loadQnas(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, string | number | boolean> = {
      page: currentPage.value - 1,
      size: pageSize.value,
    }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (filterStatus.value) params.status = filterStatus.value
    if (filterPrivacy.value !== '') params.isPrivate = filterPrivacy.value === 'true'

    const res = await listQnas(params)
    qnas.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = `${res.data.totalElements}건 조회됨`
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

function goDetail(row: QnaSummary): void {
  router.push({ name: 'board-qna-detail', params: { id: row.id } })
}

function openAnswerDialog(row: QnaSummary): void {
  answeringQna.value = row
  answerHtml.value = ''
  showAnswerDialog.value = true
}

async function handleAnswerSubmit(): Promise<void> {
  if (!answeringQna.value) return
  if (!answerHtml.value.trim()) {
    ElMessage.warning(t('common.required'))
    return
  }
  submitting.value = true
  try {
    await answerQna(answeringQna.value.id, { answerHtml: answerHtml.value })
    ElMessage.success(t('common.saveSuccess'))
    showAnswerDialog.value = false
    loadQnas()
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    submitting.value = false
  }
}

async function handleClose(row: QnaSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `'${row.title}' Q&A를 종결하시겠습니까?`,
      t('qna.close'),
      {
        type: 'warning',
        confirmButtonText: t('qna.close'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await closeQna(row.id)
    ElMessage.success(t('common.saveSuccess'))
    loadQnas()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.saveError'))
  }
}

async function handleDelete(row: QnaSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `'${row.title}' Q&A를 삭제하시겠습니까?`,
      t('common.delete'),
      {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await deleteQna(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadQnas()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

function statusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'warning',
    ANSWERED: 'success',
    CLOSED: 'info',
    HIDDEN: 'danger',
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
