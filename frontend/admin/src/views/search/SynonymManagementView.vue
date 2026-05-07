<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('synonyms.title') }}</h2>
      <el-button
        type="primary"
        :aria-label="t('synonyms.add')"
        @click="openCreateDialog"
      >
        + {{ t('synonyms.add') }}
      </el-button>
    </div>

    <!-- 필터 영역 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-select
        v-model="filterLocale"
        :placeholder="t('synonyms.locale')"
        style="width: 140px"
        :aria-label="t('synonyms.locale')"
        @change="onFilterChange"
      >
        <el-option label="한국어 (ko)" value="ko" />
        <el-option label="English (en)" value="en" />
      </el-select>
    </div>

    <!-- aria-live 알림 -->
    <div aria-live="polite" aria-atomic="true" class="sr-only">{{ liveAnnouncement }}</div>

    <!-- 테이블 -->
    <el-table
      v-loading="loading"
      :data="synonyms"
      stripe
      :empty-text="t('synonyms.empty')"
      :aria-label="t('synonyms.title')"
      class="w-full"
    >
      <caption class="sr-only">{{ t('synonyms.title') }}</caption>

      <el-table-column
        prop="term"
        :label="t('synonyms.term')"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="synonym"
        :label="t('synonyms.synonym')"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column
        prop="locale"
        :label="t('synonyms.locale')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          {{ row.locale === 'ko' ? '한국어' : 'English' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        :label="t('synonyms.status')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'ACTIVE' ? 'success' : 'warning'"
            size="small"
          >
            {{ t(`synonyms.statusValue.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="description"
        :label="t('synonyms.description')"
        min-width="180"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.description || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('common.createdAt')"
        width="120"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column
        :label="t('common.actions')"
        width="180"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="flex gap-1">
            <el-button
              size="small"
              type="primary"
              plain
              :aria-label="`${t('common.edit')} ${row.term}`"
              @click="openEditDialog(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-popconfirm
              :title="t('synonyms.deleteConfirm')"
              :confirm-button-text="t('common.delete')"
              :cancel-button-text="t('common.cancel')"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  :aria-label="`${t('common.delete')} ${row.term}`"
                >
                  {{ t('common.delete') }}
                </el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="totalElements"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        :aria-label="t('a11y.pagination')"
        @change="loadSynonyms"
      />
    </div>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="showDialog"
      :title="dialogMode === 'create' ? t('synonyms.add') : t('synonyms.edit')"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('synonyms.term')" prop="term">
          <el-input v-model="form.term" maxlength="100" show-word-limit />
        </el-form-item>

        <el-form-item :label="t('synonyms.synonym')" prop="synonym">
          <el-input
            v-model="form.synonym"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="t('synonyms.synonymPlaceholder')"
          />
        </el-form-item>

        <el-form-item :label="t('synonyms.locale')" prop="locale">
          <el-select v-model="form.locale" style="width: 100%">
            <el-option label="한국어 (ko)" value="ko" />
            <el-option label="English (en)" value="en" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('synonyms.description')" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
          />
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
import { ref, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listSynonyms,
  createSynonym,
  updateSynonym,
  deleteSynonym,
  type SynonymItem,
} from '@/api/search'

const { t } = useI18n()

// ── 상태 ──────────────────────────────────────────────────────────────────────
const synonyms = ref<SynonymItem[]>([])
const loading = ref(false)
const submitting = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const filterLocale = ref<string>('ko')
const liveAnnouncement = ref('')

const showDialog = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

interface FormState {
  term: string
  synonym: string
  locale: string
  description: string
}

const form = reactive<FormState>({
  term: '',
  synonym: '',
  locale: 'ko',
  description: '',
})

const formRules: FormRules = {
  term: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  synonym: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  locale: [{ required: true, message: t('common.required'), trigger: 'change' }],
}

// @MX:ANCHOR: [AUTO] loadSynonyms — onMounted, 필터, 페이지 변경, 저장 후 호출
// @MX:REASON: fan_in >= 3: 마운트, 페이지/사이즈 변경, 필터 변경, CRUD 후 갱신
async function loadSynonyms(): Promise<void> {
  loading.value = true
  try {
    const res = await listSynonyms(filterLocale.value, currentPage.value, pageSize.value)
    synonyms.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = `${res.data.totalElements}건 조회됨`
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

function onFilterChange(): void {
  currentPage.value = 1
  loadSynonyms()
}

function openCreateDialog(): void {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  showDialog.value = true
}

function openEditDialog(row: SynonymItem): void {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.term = row.term
  form.synonym = row.synonym
  form.locale = row.locale
  form.description = row.description ?? ''
  showDialog.value = true
}

function resetForm(): void {
  form.term = ''
  form.synonym = ''
  form.locale = filterLocale.value
  form.description = ''
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      term: form.term,
      synonym: form.synonym,
      locale: form.locale,
      description: form.description || undefined,
    }
    if (dialogMode.value === 'create') {
      await createSynonym(payload)
    } else if (editingId.value !== null) {
      await updateSynonym(editingId.value, payload)
    }
    ElMessage.success(t('synonyms.saved'))
    showDialog.value = false
    loadSynonyms()
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SynonymItem): Promise<void> {
  try {
    await deleteSynonym(row.id)
    ElMessage.success(t('synonyms.deleted'))
    loadSynonyms()
  } catch {
    ElMessage.error(t('common.deleteError'))
  }
}

function formatDate(iso?: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

onMounted(() => {
  loadSynonyms()
})
</script>
