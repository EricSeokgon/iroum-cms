<template>
  <div v-loading="loading">
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <el-button :aria-label="t('common.back')" @click="goBack">
          <el-icon><i-ep-arrow-left /></el-icon>
          {{ t('common.back') }}
        </el-button>
        <h2 v-if="publication" class="text-xl font-semibold text-gray-800">
          {{ publication.title }}
        </h2>
      </div>
      <el-tag v-if="publication" size="default">
        {{ t(`publication.documentType.${publication.documentType}`) }}
      </el-tag>
    </div>

    <div v-if="publication" class="space-y-6">
      <!-- 메타 정보 -->
      <div class="rounded border border-gray-200 bg-white p-4 text-sm text-gray-600">
        <div class="flex flex-wrap gap-6">
          <div>
            <span class="font-medium">{{ t('publication.field.year') }}:</span>
            <span class="ml-2">{{ publication.publicationYear }}</span>
          </div>
          <div>
            <span class="font-medium">{{ t('publication.field.month') }}:</span>
            <span class="ml-2">{{ publication.publicationMonth ?? '-' }}</span>
          </div>
          <div v-if="publication.categoryName">
            <span class="font-medium">{{ t('publication.field.category') }}:</span>
            <span class="ml-2">{{ publication.categoryName }}</span>
          </div>
          <div v-if="publication.isbn">
            <span class="font-medium">{{ t('publication.field.isbn') }}:</span>
            <span class="ml-2">{{ publication.isbn }}</span>
          </div>
          <div v-if="publication.publisher">
            <span class="font-medium">{{ t('publication.field.publisher') }}:</span>
            <span class="ml-2">{{ publication.publisher }}</span>
          </div>
          <div>
            <span class="font-medium">{{ t('publication.field.viewCount') }}:</span>
            <span class="ml-2">{{ publication.viewCount.toLocaleString() }}</span>
          </div>
          <div>
            <span class="font-medium">{{ t('publication.field.publishedAt') }}:</span>
            <span class="ml-2">{{ formatDate(publication.publishedAt) }}</span>
          </div>
        </div>
      </div>

      <!-- 본문 영역 -->
      <section class="rounded border border-gray-200 bg-white p-6">
        <h3 class="mb-3 text-base font-semibold text-gray-800">
          {{ t('publication.field.content') }}
        </h3>
        <!-- v-html 사용: 백엔드에서 OWASP Java HTML Sanitizer 로 정화 후 전달됨 -->
        <div
          class="prose max-w-none text-sm leading-relaxed text-gray-800"
          v-html="publication.contentHtml"
        />
      </section>

      <!-- 액션 버튼들 -->
      <div class="flex justify-end gap-2">
        <el-button
          type="success"
          plain
          :loading="zipRequesting"
          @click="handleZipDownload"
        >
          {{ t('publication.field.downloadZip') }}
        </el-button>
        <template v-if="isAdmin">
          <el-button type="primary" plain @click="openEditDialog">
            {{ t('common.edit') }}
          </el-button>
          <el-button type="danger" plain @click="handleDelete">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </div>
    </div>

    <!-- 수정 다이얼로그 -->
    <el-dialog
      v-model="showDialog"
      :title="t('publication.edit')"
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
            <el-select v-model="form.publicationMonth" clearable style="width: 100%">
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
          <el-select v-model="form.publicationCategoryId" clearable style="width: 100%">
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
            <el-input v-model="form.isbn" maxlength="20" />
          </el-form-item>
        </div>

        <el-form-item :label="t('publication.field.content')" prop="contentHtml">
          <el-input v-model="form.contentHtml" type="textarea" :rows="8" />
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
  getPublication,
  getCategories,
  updatePublication,
  deletePublication,
  requestZipDownload,
  type PublicationDetail,
  type PublicationCategoryDto,
  type DocumentType,
} from '@/api/publication'

interface Props {
  id: string
}
const props = defineProps<Props>()

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const publication = ref<PublicationDetail | null>(null)
const categoryTree = ref<PublicationCategoryDto[]>([])
const loading = ref(false)
const submitting = ref(false)
const zipRequesting = ref(false)

const showDialog = ref(false)
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

const isAdmin = computed(() => {
  const roles = auth.user?.roleCodes ?? []
  return roles.includes('SUPER_ADMIN') || roles.includes('ADMIN') || roles.includes('DEPT_ADMIN')
})

async function loadPublication(): Promise<void> {
  loading.value = true
  try {
    const res = await getPublication(Number(props.id))
    publication.value = res.data
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

function openEditDialog(): void {
  if (!publication.value) return
  form.title = publication.value.title
  form.publicationYear = publication.value.publicationYear
  form.publicationMonth = publication.value.publicationMonth
  form.documentType = publication.value.documentType
  form.publicationCategoryId = publication.value.categoryId
  form.publisher = publication.value.publisher ?? ''
  form.isbn = publication.value.isbn ?? ''
  form.contentHtml = publication.value.contentHtml ?? ''
  showDialog.value = true
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value || !publication.value) return
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
    const res = await updatePublication(publication.value.postId, payload)
    publication.value = res.data
    ElMessage.success(t('publication.msg.updated'))
    showDialog.value = false
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    submitting.value = false
  }
}

async function handleDelete(): Promise<void> {
  if (!publication.value) return
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
    await deletePublication(publication.value.postId)
    ElMessage.success(t('publication.msg.deleted'))
    router.push({ name: 'board-publications' })
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

async function handleZipDownload(): Promise<void> {
  if (!publication.value) return
  zipRequesting.value = true
  try {
    const res = await requestZipDownload(publication.value.postId, { assetUuids: [] })
    if (res.data.mode === 'SYNC') {
      ElMessage.success(t('publication.msg.zipSync'))
    } else {
      ElMessage.info(t('publication.msg.zipAsync'))
    }
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    zipRequesting.value = false
  }
}

function goBack(): void {
  router.push({ name: 'board-publications' })
}

function formatDate(iso: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

onMounted(() => {
  loadCategories()
  loadPublication()
})
</script>
