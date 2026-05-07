<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('publication.title') }}</h2>
      <el-button
        v-if="isAdmin"
        type="primary"
        :aria-label="t('publication.add')"
        @click="openCreateDialog"
      >
        + {{ t('publication.add') }}
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
        v-model="filterYear"
        :placeholder="t('publication.field.year')"
        clearable
        style="width: 140px"
        :aria-label="t('publication.field.year')"
        @change="onSearch"
      >
        <el-option
          v-for="y in yearOptions"
          :key="y"
          :label="`${y}`"
          :value="y"
        />
      </el-select>

      <el-select
        v-model="filterMonth"
        :placeholder="t('publication.field.month')"
        clearable
        style="width: 120px"
        :aria-label="t('publication.field.month')"
        @change="onSearch"
      >
        <el-option
          v-for="m in monthOptions"
          :key="m"
          :label="`${m}월`"
          :value="m"
        />
      </el-select>

      <el-select
        v-model="filterDocumentType"
        :placeholder="t('publication.field.documentType')"
        clearable
        style="width: 160px"
        :aria-label="t('publication.field.documentType')"
        @change="onSearch"
      >
        <el-option
          v-for="dt in documentTypeOptions"
          :key="dt"
          :label="t(`publication.documentType.${dt}`)"
          :value="dt"
        />
      </el-select>

      <el-select
        v-model="filterCategoryId"
        :placeholder="t('publication.field.category')"
        clearable
        style="width: 200px"
        :aria-label="t('publication.field.category')"
        @change="onSearch"
      >
        <el-option
          v-for="c in flatCategories"
          :key="c.id"
          :label="c.label"
          :value="c.id"
        />
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
      :data="publications"
      stripe
      :empty-text="t('publication.empty')"
      :aria-label="t('publication.title')"
      class="w-full"
    >
      <caption class="sr-only">{{ t('publication.title') }}</caption>

      <el-table-column
        prop="publicationYear"
        :label="t('publication.field.year')"
        width="90"
        align="center"
      />
      <el-table-column
        prop="publicationMonth"
        :label="t('publication.field.month')"
        width="80"
        align="center"
      >
        <template #default="{ row }">
          {{ row.publicationMonth ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="documentType"
        :label="t('publication.field.documentType')"
        width="120"
      >
        <template #default="{ row }">
          <el-tag size="small">
            {{ t(`publication.documentType.${row.documentType}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="title"
        :label="t('publication.field.title')"
        min-width="280"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <a
            href="#"
            class="text-blue-600 hover:underline"
            :aria-label="`${t('publication.detail')}: ${row.title}`"
            @click.prevent="goDetail(row)"
          >
            {{ row.title }}
          </a>
        </template>
      </el-table-column>
      <el-table-column
        prop="categoryName"
        :label="t('publication.field.category')"
        width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.categoryName || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="publisher"
        :label="t('publication.field.publisher')"
        width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.publisher || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="fileCount"
        :label="t('publication.field.fileCount')"
        width="80"
        align="right"
      />
      <el-table-column
        prop="viewCount"
        :label="t('publication.field.viewCount')"
        width="90"
        align="right"
      >
        <template #default="{ row }">
          {{ row.viewCount.toLocaleString() }}
        </template>
      </el-table-column>
      <el-table-column
        prop="publishedAt"
        :label="t('publication.field.publishedAt')"
        width="120"
      >
        <template #default="{ row }">
          {{ formatDate(row.publishedAt) }}
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
              :aria-label="`${t('common.edit')} ${row.title}`"
              @click="openEditDialog(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :aria-label="`${t('common.delete')} ${row.title}`"
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
      v-if="!loading && publications.length === 0"
      :description="t('publication.empty')"
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
        @change="loadPublications"
      />
    </div>

    <!-- 생성/수정 다이얼로그 -->
    <el-dialog
      v-model="showDialog"
      :title="dialogMode === 'create' ? t('publication.add') : t('publication.edit')"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('publication.field.title')" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit />
        </el-form-item>

        <div class="flex gap-3">
          <el-form-item :label="t('publication.field.year')" prop="publicationYear" class="flex-1">
            <el-select v-model="form.publicationYear" style="width: 100%">
              <el-option
                v-for="y in yearOptions"
                :key="y"
                :label="`${y}`"
                :value="y"
              />
            </el-select>
          </el-form-item>

          <el-form-item :label="t('publication.field.month')" prop="publicationMonth" class="flex-1">
            <el-select
              v-model="form.publicationMonth"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="m in monthOptions"
                :key="m"
                :label="`${m}월`"
                :value="m"
              />
            </el-select>
          </el-form-item>

          <el-form-item :label="t('publication.field.documentType')" prop="documentType" class="flex-1">
            <el-select v-model="form.documentType" style="width: 100%">
              <el-option
                v-for="dt in documentTypeOptions"
                :key="dt"
                :label="t(`publication.documentType.${dt}`)"
                :value="dt"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item :label="t('publication.field.category')" prop="publicationCategoryId">
          <el-select
            v-model="form.publicationCategoryId"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="c in flatCategories"
              :key="c.id"
              :label="c.label"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <div class="flex gap-3">
          <el-form-item :label="t('publication.field.publisher')" prop="publisher" class="flex-1">
            <el-input v-model="form.publisher" maxlength="100" />
          </el-form-item>

          <el-form-item :label="t('publication.field.isbn')" prop="isbn" class="flex-1">
            <el-input v-model="form.isbn" maxlength="20" placeholder="ISBN" />
          </el-form-item>
        </div>

        <el-form-item :label="t('publication.field.content')" prop="contentHtml">
          <el-input v-model="form.contentHtml" type="textarea" :rows="6" />
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  listPublications,
  getCategories,
  createPublication,
  updatePublication,
  deletePublication,
  type PublicationSummary,
  type PublicationCategoryDto,
  type DocumentType,
} from '@/api/publication'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

// ── 상태 ──────────────────────────────────────────────────────────────────
const publications = ref<PublicationSummary[]>([])
const categoryTree = ref<PublicationCategoryDto[]>([])
const loading = ref(false)
const submitting = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const filterYear = ref<number | ''>('')
const filterMonth = ref<number | ''>('')
const filterDocumentType = ref<DocumentType | ''>('')
const filterCategoryId = ref<number | ''>('')
const liveAnnouncement = ref('')

const showDialog = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

interface FormState {
  title: string
  publicationYear: number
  publicationMonth: number | null
  documentType: DocumentType
  publicationCategoryId: number | null
  publisher: string
  isbn: string
  contentHtml: string
}

const documentTypeOptions: DocumentType[] = ['REPORT', 'BROCHURE', 'RESEARCH', 'GUIDE', 'OTHER']

const currentYear = new Date().getFullYear()
const yearOptions = computed<number[]>(() => {
  const years: number[] = []
  for (let y = currentYear + 1; y >= 2000; y--) years.push(y)
  return years
})
const monthOptions: number[] = Array.from({ length: 12 }, (_, i) => i + 1)

const form = reactive<FormState>({
  title: '',
  publicationYear: currentYear,
  publicationMonth: null,
  documentType: 'REPORT',
  publicationCategoryId: null,
  publisher: '',
  isbn: '',
  contentHtml: '',
})

const formRules: FormRules = {
  title: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  publicationYear: [{ required: true, message: t('common.required'), trigger: 'change' }],
  documentType: [{ required: true, message: t('common.required'), trigger: 'change' }],
}

// ── 카테고리 트리 평탄화 ─────────────────────────────────────────────────
interface FlatCategory {
  id: number
  label: string
}

const flatCategories = computed<FlatCategory[]>(() => {
  const result: FlatCategory[] = []
  const walk = (nodes: PublicationCategoryDto[], prefix: string): void => {
    for (const n of nodes) {
      const label = prefix ? `${prefix} / ${n.name}` : n.name
      result.push({ id: n.id, label })
      if (n.children && n.children.length > 0) walk(n.children, label)
    }
  }
  walk(categoryTree.value, '')
  return result
})

// ── 권한 ──────────────────────────────────────────────────────────────────
const isAdmin = computed(() => {
  const roles = auth.user?.roleCodes ?? []
  return roles.includes('SUPER_ADMIN') || roles.includes('ADMIN') || roles.includes('DEPT_ADMIN')
})

// @MX:ANCHOR: [AUTO] loadPublications — onMounted, 검색, 페이지 변경, 저장 후 호출
// @MX:REASON: fan_in >= 3: 마운트, 페이지/사이즈 변경, 검색/필터, CRUD 후 갱신에서 사용
async function loadPublications(): Promise<void> {
  loading.value = true
  try {
    const res = await listPublications({
      keyword: searchKeyword.value || undefined,
      year: filterYear.value === '' ? undefined : Number(filterYear.value),
      month: filterMonth.value === '' ? undefined : Number(filterMonth.value),
      documentType: filterDocumentType.value || undefined,
      categoryId: filterCategoryId.value === '' ? undefined : Number(filterCategoryId.value),
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    publications.value = res.data.content
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
    categoryTree.value = res.data
  } catch {
    // 카테고리 로드 실패는 무시
  }
}

function onSearch(): void {
  currentPage.value = 1
  loadPublications()
}

function goDetail(row: PublicationSummary): void {
  router.push({ name: 'board-publication-detail', params: { id: row.postId } })
}

function openCreateDialog(): void {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  showDialog.value = true
}

function openEditDialog(row: PublicationSummary): void {
  dialogMode.value = 'edit'
  editingId.value = row.postId
  form.title = row.title
  form.publicationYear = row.publicationYear
  form.publicationMonth = row.publicationMonth
  form.documentType = row.documentType
  form.publicationCategoryId = null // 상세에서 categoryId가 채워지지만 summary에는 없으므로 null
  form.publisher = row.publisher ?? ''
  form.isbn = row.isbn ?? ''
  form.contentHtml = ''
  showDialog.value = true
}

function resetForm(): void {
  form.title = ''
  form.publicationYear = currentYear
  form.publicationMonth = null
  form.documentType = 'REPORT'
  form.publicationCategoryId = null
  form.publisher = ''
  form.isbn = ''
  form.contentHtml = ''
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      title: form.title,
      contentHtml: form.contentHtml || undefined,
      publicationYear: form.publicationYear,
      publicationMonth: form.publicationMonth,
      documentType: form.documentType,
      publicationCategoryId: form.publicationCategoryId,
      isbn: form.isbn || undefined,
      publisher: form.publisher || undefined,
    }
    if (dialogMode.value === 'create') {
      await createPublication(payload)
      ElMessage.success(t('publication.msg.created'))
    } else if (editingId.value !== null) {
      await updatePublication(editingId.value, payload)
      ElMessage.success(t('publication.msg.updated'))
    }
    showDialog.value = false
    loadPublications()
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: PublicationSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('publication.confirm.delete'),
      t('publication.confirm.deleteTitle'),
      {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await deletePublication(row.postId)
    ElMessage.success(t('publication.msg.deleted'))
    loadPublications()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

function formatDate(iso: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

onMounted(() => {
  loadCategories()
  loadPublications()
})
</script>
