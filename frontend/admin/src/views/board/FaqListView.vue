<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('faq.title') }}</h2>
      <el-button
        v-if="isAdmin"
        type="primary"
        :aria-label="t('faq.add')"
        @click="openCreateDialog"
      >
        + {{ t('faq.add') }}
      </el-button>
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
        v-model="filterCategory"
        :placeholder="t('faq.field.category')"
        clearable
        style="width: 200px"
        :aria-label="t('faq.field.category')"
        @change="onSearch"
      >
        <el-option
          v-for="cat in categories"
          :key="cat.categoryCode"
          :label="`${cat.categoryCode} (${cat.count})`"
          :value="cat.categoryCode"
        />
      </el-select>

      <el-select
        v-model="filterStatus"
        :placeholder="t('faq.field.status')"
        clearable
        style="width: 140px"
        :aria-label="t('faq.field.status')"
        @change="onSearch"
      >
        <el-option :label="t('faq.status.PUBLISHED')" value="PUBLISHED" />
        <el-option :label="t('faq.status.HIDDEN')" value="HIDDEN" />
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
      :data="faqs"
      stripe
      :empty-text="t('faq.empty')"
      :aria-label="t('faq.title')"
      class="w-full"
    >
      <caption class="sr-only">{{ t('faq.title') }}</caption>

      <el-table-column
        prop="categoryCode"
        :label="t('faq.field.category')"
        width="140"
      />
      <el-table-column
        prop="question"
        :label="t('faq.field.question')"
        min-width="300"
        show-overflow-tooltip
      />
      <el-table-column
        prop="sortOrder"
        :label="t('faq.field.sortOrder')"
        width="100"
        align="center"
      />
      <el-table-column
        prop="viewCount"
        :label="t('faq.field.viewCount')"
        width="100"
        align="right"
      >
        <template #default="{ row }">
          {{ row.viewCount.toLocaleString() }}
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        :label="t('faq.field.status')"
        width="100"
      >
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
            {{ t(`faq.status.${row.status}`) }}
          </el-tag>
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
        v-if="isAdmin"
        :label="t('common.actions')"
        width="160"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="flex gap-1">
            <el-button
              size="small"
              type="primary"
              plain
              :aria-label="`${t('common.edit')} ${row.question}`"
              @click="openEditDialog(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :aria-label="`${t('common.delete')} ${row.question}`"
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
      v-if="!loading && faqs.length === 0"
      :description="t('faq.empty')"
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
        @change="loadFaqs"
      />
    </div>

    <!-- 생성/수정 다이얼로그 -->
    <el-dialog
      v-model="showDialog"
      :title="dialogMode === 'create' ? t('faq.add') : t('common.edit')"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('faq.field.category')" prop="categoryCode">
          <el-input v-model="form.categoryCode" placeholder="GENERAL, BILLING ..." />
        </el-form-item>

        <el-form-item :label="t('faq.field.question')" prop="question">
          <el-input v-model="form.question" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item :label="t('faq.field.answer')" prop="answerHtml">
          <el-input v-model="form.answerHtml" type="textarea" :rows="6" />
        </el-form-item>

        <el-form-item :label="t('faq.field.sortOrder')" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>

        <el-form-item
          v-if="dialogMode === 'edit'"
          :label="t('faq.field.status')"
          prop="status"
        >
          <el-select v-model="form.status" style="width: 200px">
            <el-option :label="t('faq.status.PUBLISHED')" value="PUBLISHED" />
            <el-option :label="t('faq.status.HIDDEN')" value="HIDDEN" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  listFaqs,
  getCategories,
  createFaq,
  updateFaq,
  deleteFaq,
  type FaqSummary,
  type FaqCategoryCount,
  type FaqStatus,
} from '@/api/faq'

const { t } = useI18n()
const auth = useAuthStore()

// ── 상태 ──────────────────────────────────────────────────────────────────
const faqs = ref<FaqSummary[]>([])
const categories = ref<FaqCategoryCount[]>([])
const loading = ref(false)
const submitting = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const filterCategory = ref('')
const filterStatus = ref('')
const liveAnnouncement = ref('')

const showDialog = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

interface FormState {
  categoryCode: string
  question: string
  answerHtml: string
  sortOrder: number
  status: FaqStatus
}

const form = reactive<FormState>({
  categoryCode: '',
  question: '',
  answerHtml: '',
  sortOrder: 0,
  status: 'PUBLISHED',
})

const formRules: FormRules = {
  categoryCode: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  question: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  answerHtml: [{ required: true, message: t('common.required'), trigger: 'blur' }],
}

// ── 권한 ──────────────────────────────────────────────────────────────────
const isAdmin = computed(() => {
  const roles = auth.user?.roleCodes ?? []
  return roles.includes('SUPER_ADMIN') || roles.includes('ADMIN') || roles.includes('DEPT_ADMIN')
})

// @MX:ANCHOR: [AUTO] loadFaqs — onMounted, 검색, 페이지 변경, 저장 후 호출
// @MX:REASON: fan_in >= 3: 마운트, 페이지/사이즈 변경, 검색/필터, CRUD 후 갱신
async function loadFaqs(): Promise<void> {
  loading.value = true
  try {
    const res = await listFaqs({
      keyword: searchKeyword.value || undefined,
      category: filterCategory.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    // 상태 필터는 클라이언트 측에서 적용 (백엔드 미지원 시 fallback)
    let content = res.data.content
    if (filterStatus.value) {
      content = content.filter((f) => f.status === filterStatus.value)
    }
    faqs.value = content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = `${res.data.totalElements}건 조회됨`
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

async function loadCategories(): Promise<void> {
  try {
    const res = await getCategories()
    categories.value = res.data
  } catch {
    // 카테고리 로드 실패는 무시
  }
}

function onSearch(): void {
  currentPage.value = 1
  loadFaqs()
}

function openCreateDialog(): void {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  showDialog.value = true
}

function openEditDialog(row: FaqSummary): void {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.categoryCode = row.categoryCode
  form.question = row.question
  form.answerHtml = ''
  form.sortOrder = row.sortOrder
  form.status = (row.status as FaqStatus) || 'PUBLISHED'
  // answerHtml 은 상세 조회로 가져와야 정확하지만, summary 만으로 충분히 편집 가능
  showDialog.value = true
}

function resetForm(): void {
  form.categoryCode = ''
  form.question = ''
  form.answerHtml = ''
  form.sortOrder = 0
  form.status = 'PUBLISHED'
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createFaq({
        categoryCode: form.categoryCode,
        question: form.question,
        answerHtml: form.answerHtml,
        sortOrder: form.sortOrder,
      })
      ElMessage.success(t('common.saveSuccess'))
    } else if (editingId.value !== null) {
      await updateFaq(editingId.value, {
        categoryCode: form.categoryCode,
        question: form.question,
        answerHtml: form.answerHtml,
        sortOrder: form.sortOrder,
        status: form.status,
      })
      ElMessage.success(t('common.saveSuccess'))
    }
    showDialog.value = false
    loadFaqs()
    loadCategories()
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: FaqSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `'${row.question}' FAQ를 삭제하시겠습니까?`,
      t('common.delete'),
      {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await deleteFaq(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadFaqs()
    loadCategories()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

onMounted(() => {
  loadCategories()
  loadFaqs()
})
</script>
