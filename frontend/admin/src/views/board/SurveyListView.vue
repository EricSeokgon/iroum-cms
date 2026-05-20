<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('survey.title') }}</h2>
      <el-button
        v-if="isAdmin"
        type="primary"
        :aria-label="t('survey.add')"
        @click="openCreateDialog"
      >
        + {{ t('survey.add') }}
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
        v-model="filterStatus"
        :placeholder="t('survey.field.status')"
        clearable
        style="width: 160px"
        :aria-label="t('survey.field.status')"
        @change="onSearch"
      >
        <el-option
          v-for="s in statusOptions"
          :key="s"
          :label="t(`survey.status.${s}`)"
          :value="s"
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
      :data="surveys"
      stripe
      :empty-text="t('survey.empty')"
      :aria-label="t('survey.title')"
      class="w-full"
    >
      <caption class="sr-only">{{ t('survey.title') }}</caption>

      <el-table-column
        prop="title"
        :label="t('survey.field.title')"
        min-width="280"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <a
            href="#"
            class="text-blue-600 hover:underline"
            :aria-label="`${t('survey.detail')}: ${row.title}`"
            @click.prevent="goDetail(row)"
          >
            {{ row.title }}
          </a>
        </template>
      </el-table-column>

      <el-table-column
        prop="status"
        :label="t('survey.field.status')"
        width="100"
      >
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ t(`survey.status.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        prop="isAnonymous"
        :label="t('survey.field.isAnonymous')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          {{ row.isAnonymous ? t('common.yes') : t('common.no') }}
        </template>
      </el-table-column>

      <el-table-column
        :label="t('survey.field.responseCount')"
        width="120"
        align="right"
      >
        <template #default="{ row }">
          {{ row.responseCount.toLocaleString() }}
          <span v-if="row.maxResponses !== null" class="text-gray-400">
            / {{ row.maxResponses.toLocaleString() }}
          </span>
        </template>
      </el-table-column>

      <el-table-column
        prop="startAt"
        :label="t('survey.field.startAt')"
        width="140"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.startAt) }}
        </template>
      </el-table-column>

      <el-table-column
        prop="endAt"
        :label="t('survey.field.endAt')"
        width="140"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.endAt) }}
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
        v-if="isAdmin"
        :label="t('common.actions')"
        width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="flex gap-1 flex-wrap">
            <!-- DRAFT → OPEN(게시), OPEN → CLOSED(마감) -->
            <el-button
              v-if="row.status === 'DRAFT'"
              size="small"
              type="success"
              plain
              :aria-label="`${t('survey.publish')} ${row.title}`"
              @click="handlePublish(row, 'OPEN')"
            >
              {{ t('survey.publish') }}
            </el-button>
            <el-button
              v-else-if="row.status === 'OPEN'"
              size="small"
              type="warning"
              plain
              :aria-label="`${t('survey.close')} ${row.title}`"
              @click="handlePublish(row, 'CLOSED')"
            >
              {{ t('survey.close') }}
            </el-button>
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
      v-if="!loading && surveys.length === 0"
      :description="t('survey.empty')"
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
        @change="loadSurveys"
      />
    </div>

    <!-- 생성/수정 다이얼로그 -->
    <el-dialog
      v-model="showDialog"
      :title="dialogMode === 'create' ? t('survey.add') : t('survey.edit')"
      width="860px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('survey.field.title')" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item :label="t('survey.field.period')" prop="period">
          <el-date-picker
            v-model="form.period"
            type="datetimerange"
            :range-separator="'~'"
            :start-placeholder="t('survey.field.startAt')"
            :end-placeholder="t('survey.field.endAt')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>

        <div class="flex gap-3">
          <el-form-item :label="t('survey.field.isAnonymous')" prop="isAnonymous" class="flex-1">
            <el-switch
              v-model="form.isAnonymous"
              active-text="익명"
              inactive-text="실명"
            />
          </el-form-item>

          <el-form-item :label="t('survey.field.maxResponses')" prop="maxResponses" class="flex-1">
            <el-input-number
              v-model="form.maxResponses"
              :min="1"
              :placeholder="t('common.optional')"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item :label="t('survey.field.descriptionHtml')" prop="descriptionHtml">
          <el-input
            v-model="form.descriptionHtml"
            type="textarea"
            :rows="4"
            :placeholder="t('common.optional')"
          />
        </el-form-item>

        <!-- 질문 목록 -->
        <el-divider>{{ t('survey.field.questions') }}</el-divider>

        <div
          v-for="(question, idx) in form.questions"
          :key="idx"
          class="mb-3 rounded border border-gray-200 bg-gray-50 p-3"
        >
          <div class="mb-2 flex items-center justify-between">
            <span class="text-sm font-semibold text-gray-700">
              {{ t('survey.field.questionText') }} #{{ idx + 1 }}
            </span>
            <el-button
              size="small"
              type="danger"
              plain
              :aria-label="t('common.delete')"
              @click="removeQuestion(idx)"
            >
              {{ t('common.delete') }}
            </el-button>
          </div>

          <div class="flex gap-3">
            <el-form-item :label="t('survey.field.questionText')" class="flex-1">
              <el-input v-model="question.questionText" maxlength="500" />
            </el-form-item>

            <el-form-item :label="t('survey.field.questionType')" style="width: 200px">
              <el-select v-model="question.questionType" style="width: 100%">
                <el-option
                  v-for="qt in questionTypeOptions"
                  :key="qt"
                  :label="t(`survey.questionType.${qt}`)"
                  :value="qt"
                />
              </el-select>
            </el-form-item>

            <el-form-item :label="t('survey.field.required')" style="width: 100px">
              <el-switch v-model="question.required" />
            </el-form-item>
          </div>

          <el-form-item
            v-if="question.questionType === 'SINGLE' || question.questionType === 'MULTI'"
            :label="t('survey.field.options')"
          >
            <div class="w-full space-y-2">
              <div
                v-for="(opt, optIdx) in optionLists[idx]"
                :key="optIdx"
                class="flex items-center gap-2"
              >
                <span class="min-w-[1.5rem] text-right text-sm font-medium text-gray-600">{{ optIdx + 1 }}.</span>
                <el-input
                  v-model="opt.label"
                  :placeholder="t('survey.field.optionLabel')"
                  class="flex-1"
                />
                <el-button
                  size="small"
                  type="danger"
                  plain
                  :aria-label="t('common.delete')"
                  @click="removeOption(idx, optIdx)"
                >
                  -
                </el-button>
              </div>
              <el-button type="primary" plain size="small" @click="addOption(idx)">
                + {{ t('survey.field.addOption') }}
              </el-button>
            </div>
          </el-form-item>
        </div>

        <el-button type="primary" plain @click="addQuestion">
          + {{ t('survey.field.questionText') }}
        </el-button>
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
  listSurveys,
  createSurvey,
  updateSurvey,
  deleteSurvey,
  type SurveySummary,
  type SurveyStatus,
  type QuestionType,
  type SurveyQuestionRequest,
} from '@/api/survey'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

// ── 상태 ──────────────────────────────────────────────────────────────────
const surveys = ref<SurveySummary[]>([])
const loading = ref(false)
const submitting = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const filterStatus = ref<SurveyStatus | ''>('')
const liveAnnouncement = ref('')

const showDialog = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const statusOptions: SurveyStatus[] = ['DRAFT', 'OPEN', 'CLOSED', 'HIDDEN']
const questionTypeOptions: QuestionType[] = ['SINGLE', 'MULTI', 'TEXT', 'RATING', 'DATE']
const optionLists = ref<{ value: string; label: string }[][]>([])

interface FormState {
  title: string
  period: [string, string] | null
  isAnonymous: boolean
  maxResponses: number | null
  descriptionHtml: string
  questions: SurveyQuestionRequest[]
}

const form = reactive<FormState>({
  title: '',
  period: null,
  isAnonymous: false,
  maxResponses: null,
  descriptionHtml: '',
  questions: [],
})

const formRules: FormRules = {
  title: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  period: [{ required: true, message: t('common.required'), trigger: 'change' }],
}

// ── 권한 ──────────────────────────────────────────────────────────────────
const isAdmin = computed(() => {
  const roles = auth.user?.roleCodes ?? []
  return roles.includes('SUPER_ADMIN') || roles.includes('ADMIN') || roles.includes('DEPT_ADMIN')
})

function statusTagType(status: SurveyStatus): 'info' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'DRAFT':
      return 'info'
    case 'OPEN':
      return 'success'
    case 'CLOSED':
      return 'warning'
    case 'HIDDEN':
      return 'danger'
  }
}

// @MX:ANCHOR: [AUTO] loadSurveys — onMounted, 검색, 페이지 변경, 저장 후 호출
// @MX:REASON: fan_in >= 3: 마운트, 페이지/사이즈 변경, 검색/필터, CRUD 후 갱신에서 사용
async function loadSurveys(): Promise<void> {
  loading.value = true
  try {
    const res = await listSurveys({
      keyword: searchKeyword.value || undefined,
      status: filterStatus.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    surveys.value = res.data.content
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
  loadSurveys()
}

function goDetail(row: SurveySummary): void {
  router.push({ name: 'board-survey-detail', params: { id: row.id } })
}

function openCreateDialog(): void {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  showDialog.value = true
}

function openEditDialog(row: SurveySummary): void {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.title = row.title
  form.period = [row.startAt, row.endAt]
  form.isAnonymous = row.isAnonymous
  form.maxResponses = row.maxResponses
  form.descriptionHtml = ''
  form.questions = []
  showDialog.value = true
}

function resetForm(): void {
  form.title = ''
  form.period = null
  form.isAnonymous = false
  form.maxResponses = null
  form.descriptionHtml = ''
  form.questions = []
  optionLists.value = []
}

function addQuestion(): void {
  form.questions.push({
    questionText: '',
    questionType: 'TEXT',
    required: false,
    sortOrder: form.questions.length + 1,
    options: null,
  })
  optionLists.value.push([])
}

function removeQuestion(idx: number): void {
  form.questions.splice(idx, 1)
  optionLists.value.splice(idx, 1)
  form.questions.forEach((q, i) => {
    q.sortOrder = i + 1
  })
}

function addOption(qIdx: number): void {
  const list = optionLists.value[qIdx]
  list.push({ value: String(list.length + 1), label: '' })
}

function removeOption(qIdx: number, optIdx: number): void {
  optionLists.value[qIdx].splice(optIdx, 1)
  optionLists.value[qIdx].forEach((opt, i) => {
    opt.value = String(i + 1)
  })
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.period) {
    ElMessage.warning(t('common.required'))
    return
  }

  submitting.value = true
  try {
    form.questions.forEach((q, i) => {
      if (q.questionType === 'SINGLE' || q.questionType === 'MULTI') {
        const opts = optionLists.value[i] ?? []
        q.options = opts.length > 0 ? JSON.stringify(opts) : null
      }
    })
    const payload = {
      title: form.title,
      descriptionHtml: form.descriptionHtml || undefined,
      startAt: new Date(form.period[0]).toISOString(),
      endAt: new Date(form.period[1]).toISOString(),
      isAnonymous: form.isAnonymous,
      maxResponses: form.maxResponses,
      questions: form.questions,
    }
    if (dialogMode.value === 'create') {
      await createSurvey(payload)
      ElMessage.success(t('survey.msg.created'))
    } else if (editingId.value !== null) {
      await updateSurvey(editingId.value, payload)
      ElMessage.success(t('survey.msg.updated'))
    }
    showDialog.value = false
    loadSurveys()
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    submitting.value = false
  }
}

async function handlePublish(row: SurveySummary, newStatus: SurveyStatus): Promise<void> {
  const labelKey = newStatus === 'OPEN' ? 'survey.confirm.publish' : 'survey.confirm.close'
  const titleKey = newStatus === 'OPEN' ? 'survey.confirm.publishTitle' : 'survey.confirm.closeTitle'
  try {
    await ElMessageBox.confirm(t(labelKey), t(titleKey), {
      type: 'warning',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
    })
    await updateSurvey(row.id, { status: newStatus })
    ElMessage.success(
      newStatus === 'OPEN' ? t('survey.msg.published') : t('survey.msg.closed'),
    )
    loadSurveys()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.saveError'))
  }
}

async function handleDelete(row: SurveySummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('survey.confirm.delete'),
      t('survey.confirm.deleteTitle'),
      {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await deleteSurvey(row.id)
    ElMessage.success(t('survey.msg.deleted'))
    loadSurveys()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

function formatDate(iso: string): string {
  if (!iso) return '-'
  return iso.slice(0, 10)
}

function formatDateTime(iso: string): string {
  if (!iso) return '-'
  // "YYYY-MM-DDTHH:mm:ss..." → "YYYY-MM-DD HH:mm"
  return `${iso.slice(0, 10)} ${iso.slice(11, 16)}`
}

onMounted(() => {
  loadSurveys()
})
</script>
