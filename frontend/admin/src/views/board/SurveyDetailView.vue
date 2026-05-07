<template>
  <div v-loading="loading">
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <el-button :aria-label="t('common.back')" @click="goBack">
          <el-icon><i-ep-arrow-left /></el-icon>
          {{ t('common.back') }}
        </el-button>
        <h2 v-if="survey" class="text-xl font-semibold text-gray-800">
          {{ survey.title }}
        </h2>
      </div>
      <el-tag v-if="survey" :type="statusTagType(survey.status)" size="default">
        {{ t(`survey.status.${survey.status}`) }}
      </el-tag>
    </div>

    <div v-if="survey" class="space-y-6">
      <!-- 메타 정보 -->
      <div class="rounded border border-gray-200 bg-white p-4 text-sm text-gray-600">
        <div class="flex flex-wrap gap-6">
          <div>
            <span class="font-medium">{{ t('survey.field.startAt') }}:</span>
            <span class="ml-2">{{ formatDateTime(survey.startAt) }}</span>
          </div>
          <div>
            <span class="font-medium">{{ t('survey.field.endAt') }}:</span>
            <span class="ml-2">{{ formatDateTime(survey.endAt) }}</span>
          </div>
          <div>
            <span class="font-medium">{{ t('survey.field.isAnonymous') }}:</span>
            <span class="ml-2">{{ survey.isAnonymous ? t('common.yes') : t('common.no') }}</span>
          </div>
          <div>
            <span class="font-medium">{{ t('survey.field.responseCount') }}:</span>
            <span class="ml-2">
              {{ survey.responseCount.toLocaleString() }}
              <span v-if="survey.maxResponses !== null" class="text-gray-400">
                / {{ survey.maxResponses.toLocaleString() }}
              </span>
            </span>
          </div>
          <div>
            <span class="font-medium">{{ t('common.createdAt') }}:</span>
            <span class="ml-2">{{ formatDate(survey.createdAt) }}</span>
          </div>
        </div>
      </div>

      <!-- 본문 영역 -->
      <section
        v-if="survey.descriptionHtml"
        class="rounded border border-gray-200 bg-white p-6"
      >
        <h3 class="mb-3 text-base font-semibold text-gray-800">
          {{ t('survey.field.descriptionHtml') }}
        </h3>
        <!-- v-html 사용: 백엔드에서 OWASP Java HTML Sanitizer 로 정화 후 전달됨 -->
        <div
          class="prose max-w-none text-sm leading-relaxed text-gray-800"
          v-html="survey.descriptionHtml"
        />
      </section>

      <!-- 질문 목록 -->
      <section class="rounded border border-gray-200 bg-white p-6">
        <h3 class="mb-3 text-base font-semibold text-gray-800">
          {{ t('survey.field.questions') }} ({{ survey.questions.length }})
        </h3>
        <div v-if="survey.questions.length === 0" class="text-sm text-gray-400">
          {{ t('common.empty') }}
        </div>
        <el-card
          v-for="(q, idx) in survey.questions"
          :key="q.id"
          class="mb-3"
          shadow="never"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="flex-1">
              <div class="mb-1 text-sm text-gray-500">Q{{ idx + 1 }}</div>
              <div class="text-base font-medium text-gray-800">
                {{ q.questionText }}
              </div>
              <div v-if="q.options" class="mt-2 text-xs text-gray-500">
                {{ q.options }}
              </div>
            </div>
            <div class="flex flex-col items-end gap-1">
              <el-tag size="small">
                {{ t(`survey.questionType.${q.questionType}`) }}
              </el-tag>
              <el-tag v-if="q.required" size="small" type="danger" effect="plain">
                {{ t('survey.field.required') }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </section>

      <!-- 액션 버튼 -->
      <div class="flex justify-end gap-2">
        <template v-if="isAdmin">
          <el-button type="info" plain @click="openResultDialog">
            {{ t('survey.results') }}
          </el-button>
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
      :title="t('survey.edit')"
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
            <el-input
              v-model="question.options"
              type="textarea"
              :rows="3"
              :placeholder="optionsPlaceholder"
            />
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

    <!-- 결과 보기 다이얼로그 -->
    <el-dialog
      v-model="showResultDialog"
      :title="t('survey.results')"
      width="720px"
    >
      <div v-loading="resultLoading">
        <div v-if="result" class="space-y-4">
          <div class="rounded border border-gray-200 bg-gray-50 p-3 text-sm">
            <span class="font-semibold">{{ t('survey.field.totalResponses') }}:</span>
            <span class="ml-2">{{ result.totalResponses.toLocaleString() }}</span>
          </div>

          <div
            v-for="q in result.questions"
            :key="q.questionId"
            class="rounded border border-gray-200 bg-white p-4"
          >
            <div class="mb-2 flex items-start justify-between">
              <div class="text-sm font-medium text-gray-800">
                {{ q.questionText }}
              </div>
              <el-tag size="small">
                {{ t(`survey.questionType.${q.questionType}`) }}
              </el-tag>
            </div>
            <div class="mb-2 text-xs text-gray-500">
              {{ t('survey.field.totalAnswers') }}: {{ q.totalAnswers.toLocaleString() }}
            </div>

            <div v-if="q.distribution.length === 0" class="text-xs text-gray-400">
              {{ t('common.empty') }}
            </div>

            <div
              v-for="(item, i) in q.distribution"
              :key="i"
              class="mb-2"
            >
              <div class="mb-1 flex items-center justify-between text-xs">
                <span class="text-gray-700">{{ item.label }}</span>
                <span class="text-gray-500">
                  {{ item.count.toLocaleString() }} ({{ item.percentage.toFixed(1) }}%)
                </span>
              </div>
              <el-progress :percentage="item.percentage" :show-text="false" />
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="showResultDialog = false">{{ t('common.close') }}</el-button>
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
  getSurvey,
  updateSurvey,
  deleteSurvey,
  getSurveyResults,
  type SurveyDetail,
  type SurveyStatus,
  type QuestionType,
  type SurveyQuestionRequest,
  type SurveyResultDto,
} from '@/api/survey'

interface Props {
  id: string
}
const props = defineProps<Props>()

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const survey = ref<SurveyDetail | null>(null)
const loading = ref(false)
const submitting = ref(false)

const showDialog = ref(false)
const formRef = ref<FormInstance>()

const showResultDialog = ref(false)
const resultLoading = ref(false)
const result = ref<SurveyResultDto | null>(null)

const questionTypeOptions: QuestionType[] = ['SINGLE', 'MULTI', 'TEXT', 'RATING', 'DATE']
const optionsPlaceholder = 'JSON 배열 직접 입력, 예: [{"value":"A","label":"선택1"}]'

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

async function loadSurvey(): Promise<void> {
  loading.value = true
  try {
    const res = await getSurvey(Number(props.id))
    survey.value = res.data
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

function openEditDialog(): void {
  if (!survey.value) return
  form.title = survey.value.title
  form.period = [survey.value.startAt, survey.value.endAt]
  form.isAnonymous = survey.value.isAnonymous
  form.maxResponses = survey.value.maxResponses
  form.descriptionHtml = survey.value.descriptionHtml ?? ''
  form.questions = survey.value.questions.map((q) => ({
    questionText: q.questionText,
    questionType: q.questionType,
    required: q.required,
    sortOrder: q.sortOrder,
    options: q.options,
  }))
  showDialog.value = true
}

function addQuestion(): void {
  form.questions.push({
    questionText: '',
    questionType: 'TEXT',
    required: false,
    sortOrder: form.questions.length + 1,
    options: null,
  })
}

function removeQuestion(idx: number): void {
  form.questions.splice(idx, 1)
  form.questions.forEach((q, i) => {
    q.sortOrder = i + 1
  })
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value || !survey.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.period) {
    ElMessage.warning(t('common.required'))
    return
  }

  submitting.value = true
  try {
    const payload = {
      title: form.title,
      descriptionHtml: form.descriptionHtml || undefined,
      startAt: form.period[0],
      endAt: form.period[1],
      isAnonymous: form.isAnonymous,
      maxResponses: form.maxResponses,
      questions: form.questions,
    }
    const res = await updateSurvey(survey.value.id, payload)
    survey.value = res.data
    ElMessage.success(t('survey.msg.updated'))
    showDialog.value = false
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    submitting.value = false
  }
}

async function handleDelete(): Promise<void> {
  if (!survey.value) return
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
    await deleteSurvey(survey.value.id)
    ElMessage.success(t('survey.msg.deleted'))
    router.push({ name: 'board-surveys' })
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

async function openResultDialog(): Promise<void> {
  if (!survey.value) return
  showResultDialog.value = true
  resultLoading.value = true
  try {
    const res = await getSurveyResults(survey.value.id)
    result.value = res.data
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    resultLoading.value = false
  }
}

function goBack(): void {
  router.push({ name: 'board-surveys' })
}

function formatDate(iso: string): string {
  if (!iso) return '-'
  return iso.slice(0, 10)
}

function formatDateTime(iso: string): string {
  if (!iso) return '-'
  return `${iso.slice(0, 10)} ${iso.slice(11, 16)}`
}

onMounted(() => {
  loadSurvey()
})
</script>
