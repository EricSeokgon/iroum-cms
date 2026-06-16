<template>
  <div data-testid="email-template-list">
    <!-- 페이지 제목 -->
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">이메일 템플릿 관리</h2>
      <p class="mt-1 text-sm text-gray-500">
        시스템에서 발송되는 이메일 템플릿을 등록·수정하고 미리보기/테스트 발송할 수 있습니다.
      </p>
    </div>

    <!-- 필터 바 -->
    <el-card class="mb-4" data-testid="email-template-filter">
      <div class="flex flex-wrap items-end gap-4">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium text-gray-700" for="filter-type">유형</label>
          <el-select
            id="filter-type"
            v-model="filter.templateType"
            clearable
            placeholder="전체"
            style="width: 200px"
            data-testid="filter-type"
          >
            <el-option
              v-for="opt in TEMPLATE_TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium text-gray-700" for="filter-lang">언어</label>
          <el-select
            id="filter-lang"
            v-model="filter.language"
            clearable
            placeholder="전체"
            style="width: 140px"
            data-testid="filter-language"
          >
            <el-option label="한국어" value="ko" />
            <el-option label="English" value="en" />
          </el-select>
        </div>

        <div class="flex flex-col gap-1">
          <span class="text-sm font-medium text-gray-700">사용 여부</span>
          <el-radio-group v-model="filter.isActive" data-testid="filter-active">
            <el-radio-button :value="undefined">전체</el-radio-button>
            <el-radio-button :value="true">활성</el-radio-button>
            <el-radio-button :value="false">비활성</el-radio-button>
          </el-radio-group>
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium text-gray-700" for="filter-keyword">검색어</label>
          <el-input
            id="filter-keyword"
            v-model="filter.keyword"
            clearable
            placeholder="코드 또는 이름"
            style="width: 220px"
            data-testid="filter-keyword"
            @keyup.enter="onSearch"
          />
        </div>

        <div class="ml-auto flex gap-2">
          <el-button data-testid="reset-btn" @click="onReset">초기화</el-button>
          <el-button type="primary" data-testid="search-btn" @click="onSearch">검색</el-button>
        </div>
      </div>
    </el-card>

    <!-- 목록 -->
    <el-card>
      <div class="mb-3 flex justify-end">
        <el-button
          v-if="canWrite"
          type="primary"
          data-testid="create-btn"
          @click="openCreate"
        >
          템플릿 등록
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        data-testid="email-template-table"
        aria-label="이메일 템플릿 목록"
        row-key="id"
        stripe
      >
        <el-table-column prop="code" label="코드" width="180" />
        <el-table-column prop="name" label="이름" min-width="180" />
        <el-table-column prop="templateType" label="유형" width="160">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">
              {{ templateTypeLabel(row.templateType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="language" label="언어" width="90">
          <template #default="{ row }">{{ row.language === 'en' ? 'English' : '한국어' }}</template>
        </el-table-column>
        <el-table-column prop="isActive" label="상태" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? '활성' : '비활성' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="수정일" width="170">
          <template #default="{ row }">
            <span class="text-xs text-gray-600">{{ formatDate(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="openPreview(row.id)">미리보기</el-button>
            <el-button size="small" text @click="openSendLogs(row)">발송 이력</el-button>
            <el-button
              v-if="canWrite"
              size="small"
              text
              type="primary"
              @click="openEdit(row.id)"
            >
              수정
            </el-button>
            <el-button
              v-if="canWrite"
              size="small"
              text
              type="danger"
              @click="onDelete(row)"
            >
              삭제
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="py-10 text-center text-gray-500">등록된 템플릿이 없습니다.</div>
        </template>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="totalElements"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          aria-label="페이지 이동"
          @change="reload"
        />
      </div>
    </el-card>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '템플릿 수정' : '템플릿 등록'"
      width="760px"
      data-testid="email-template-form-dialog"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="110px"
        data-testid="email-template-form"
      >
        <el-form-item label="코드" prop="code">
          <el-input
            v-model="form.code"
            :disabled="isEdit"
            placeholder="예: otp-default"
            data-testid="form-code"
          />
        </el-form-item>
        <el-form-item label="이름" prop="name">
          <el-input v-model="form.name" data-testid="form-name" />
        </el-form-item>
        <el-form-item label="유형" prop="templateType">
          <el-select v-model="form.templateType" style="width: 100%" data-testid="form-type">
            <el-option
              v-for="opt in TEMPLATE_TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="언어" prop="language">
          <el-select v-model="form.language" style="width: 100%" data-testid="form-language">
            <el-option label="한국어" value="ko" />
            <el-option label="English" value="en" />
          </el-select>
        </el-form-item>
        <el-form-item label="제목" prop="subject">
          <el-input v-model="form.subject" data-testid="form-subject" />
        </el-form-item>
        <el-form-item label="HTML 본문" prop="bodyHtml">
          <el-input
            v-model="form.bodyHtml"
            type="textarea"
            :rows="10"
            data-testid="form-body-html"
          />
        </el-form-item>
        <el-form-item label="텍스트 본문">
          <el-input
            v-model="form.bodyText"
            type="textarea"
            :rows="4"
            placeholder="선택 사항"
            data-testid="form-body-text"
          />
        </el-form-item>
        <el-form-item label="활성">
          <el-switch v-model="form.isActive" data-testid="form-active" />
        </el-form-item>

        <!-- 변수 정의 -->
        <el-form-item label="변수">
          <div class="w-full" data-testid="form-variables">
            <div
              v-for="(v, idx) in form.variables"
              :key="idx"
              class="mb-2 flex items-center gap-2"
            >
              <el-input v-model="v.name" placeholder="변수명" style="width: 160px" />
              <el-input v-model="v.description" placeholder="설명" style="flex: 1" />
              <el-checkbox v-model="v.required">필수</el-checkbox>
              <el-button
                size="small"
                text
                type="danger"
                data-testid="remove-variable-btn"
                @click="removeVariable(idx)"
              >
                삭제
              </el-button>
            </div>
            <el-button size="small" data-testid="add-variable-btn" @click="addVariable">
              변수 추가
            </el-button>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">취소</el-button>
        <el-button
          type="primary"
          :loading="saving"
          data-testid="form-submit-btn"
          @click="onSubmit"
        >
          저장
        </el-button>
      </template>
    </el-dialog>

    <!-- 미리보기 다이얼로그 -->
    <el-dialog
      v-model="previewVisible"
      title="템플릿 미리보기"
      width="720px"
      data-testid="preview-dialog"
    >
      <div v-if="previewTemplate">
        <!-- 변수 입력 -->
        <div
          v-if="previewTemplate.variables && previewTemplate.variables.length > 0"
          class="mb-4"
        >
          <h4 class="mb-2 text-sm font-semibold text-gray-700">변수 값 입력</h4>
          <el-form label-width="160px">
            <el-form-item
              v-for="v in previewTemplate.variables"
              :key="v.name"
              :label="`${v.name}${v.required ? ' *' : ''}`"
            >
              <el-input
                v-model="previewVars[v.name]"
                :placeholder="v.description"
                data-testid="preview-var-input"
              />
            </el-form-item>
          </el-form>
          <el-button
            size="small"
            type="primary"
            :loading="previewing"
            data-testid="render-preview-btn"
            @click="renderPreview"
          >
            미리보기 렌더링
          </el-button>
        </div>

        <!-- 렌더 결과 -->
        <div v-if="previewResult" data-testid="preview-result">
          <el-divider />
          <p class="mb-2 text-sm">
            <span class="font-semibold text-gray-700">제목:</span>
            {{ previewResult.subject }}
          </p>
          <div class="rounded border border-gray-200 p-3" v-html="safePreviewHtml" />
        </div>
      </div>

      <template #footer>
        <el-button @click="previewVisible = false">닫기</el-button>
        <el-button
          type="success"
          :loading="testSending"
          data-testid="test-send-btn"
          @click="onTestSend"
        >
          테스트 발송
        </el-button>
      </template>
    </el-dialog>

    <!-- 발송 이력 드로어 -->
    <el-drawer
      v-model="logDrawerVisible"
      title="발송 이력"
      direction="rtl"
      size="560px"
      data-testid="send-log-drawer"
    >
      <div v-if="logTemplate" class="mb-3 text-sm text-gray-600">
        템플릿: <span class="font-medium">{{ logTemplate.name }}</span>
        ({{ logTemplate.code }})
      </div>
      <el-table
        v-loading="logLoading"
        :data="sendLogs"
        aria-label="발송 이력 목록"
        data-testid="send-log-table"
        stripe
      >
        <el-table-column prop="status" label="상태" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '성공' : '실패' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sentAt" label="발송 시각" width="170">
          <template #default="{ row }">
            <span class="text-xs text-gray-600">{{ formatDate(row.sentAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="오류 메시지" min-width="160">
          <template #default="{ row }">
            <span class="text-xs text-red-600">{{ row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="py-8 text-center text-gray-500">발송 이력이 없습니다.</div>
        </template>
      </el-table>
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="logPage"
          :page-size="logSize"
          :total="logTotal"
          layout="prev, pager, next"
          small
          @change="loadSendLogs"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
// SPEC-CMS-EMAIL-TEMPLATE-001 — 이메일 템플릿 관리 화면
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { usePermission } from '@/composables/usePermission'
import { useSafeHtml } from '@/composables/useSafeHtml'
import {
  createEmailTemplate,
  deleteEmailTemplate,
  getEmailTemplate,
  getSendLogs,
  listEmailTemplates,
  previewEmailTemplate,
  testSendEmailTemplate,
  updateEmailTemplate,
  type CreateRequest,
  type EmailTemplateDetail,
  type EmailTemplateSummary,
  type PreviewResult,
  type SendLogEntry,
  type TemplateType,
} from '@/api/email-template'

const { hasPermission } = usePermission()
const { sanitize } = useSafeHtml()

const canWrite = computed(() => hasPermission('EMAIL_TEMPLATE:WRITE'))

const TEMPLATE_TYPE_OPTIONS: Array<{ label: string; value: TemplateType }> = [
  { label: 'OTP', value: 'OTP' },
  { label: 'Q&A 답변', value: 'QNA_ANSWER' },
  { label: '비밀번호 재설정', value: 'PASSWORD_RESET' },
  { label: '관리자 알림', value: 'ADMIN_NOTIFICATION' },
  { label: '사용자 정의', value: 'CUSTOM' },
]

function templateTypeLabel(type: TemplateType): string {
  return TEMPLATE_TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type
}

// ── 목록/필터 ────────────────────────────────────────────────────────────────
const rows = ref<EmailTemplateSummary[]>([])
const totalElements = ref(0)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)

const filter = reactive<{
  templateType?: TemplateType
  language?: string
  isActive?: boolean
  keyword?: string
}>({})

async function reload(): Promise<void> {
  loading.value = true
  try {
    const { data } = await listEmailTemplates({
      page: currentPage.value - 1,
      size: pageSize.value,
      templateType: filter.templateType,
      language: filter.language,
      isActive: filter.isActive,
      keyword: filter.keyword || undefined,
    })
    rows.value = data.content
    totalElements.value = data.totalElements
  } catch {
    ElMessage.error('템플릿 목록을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  currentPage.value = 1
  void reload()
}

function onReset(): void {
  filter.templateType = undefined
  filter.language = undefined
  filter.isActive = undefined
  filter.keyword = undefined
  currentPage.value = 1
  void reload()
}

onMounted(() => {
  void reload()
})

// ── 등록/수정 폼 ──────────────────────────────────────────────────────────────
const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

function emptyForm(): CreateRequest {
  return {
    code: '',
    name: '',
    templateType: 'CUSTOM',
    language: 'ko',
    subject: '',
    bodyHtml: '',
    bodyText: '',
    variables: [],
    isActive: true,
  }
}

const form = reactive<CreateRequest>(emptyForm())

const formRules: FormRules<CreateRequest> = {
  code: [{ required: true, message: '코드를 입력하세요.', trigger: 'blur' }],
  name: [{ required: true, message: '이름을 입력하세요.', trigger: 'blur' }],
  templateType: [{ required: true, message: '유형을 선택하세요.', trigger: 'change' }],
  language: [{ required: true, message: '언어를 선택하세요.', trigger: 'change' }],
  subject: [{ required: true, message: '제목을 입력하세요.', trigger: 'blur' }],
  bodyHtml: [{ required: true, message: 'HTML 본문을 입력하세요.', trigger: 'blur' }],
}

function applyForm(src: CreateRequest): void {
  form.code = src.code
  form.name = src.name
  form.templateType = src.templateType
  form.language = src.language
  form.subject = src.subject
  form.bodyHtml = src.bodyHtml
  form.bodyText = src.bodyText ?? ''
  form.variables = src.variables ? src.variables.map((v) => ({ ...v })) : []
  form.isActive = src.isActive
}

function resetForm(): void {
  applyForm(emptyForm())
  formRef.value?.clearValidate()
}

function openCreate(): void {
  isEdit.value = false
  editingId.value = null
  applyForm(emptyForm())
  formVisible.value = true
}

async function openEdit(id: number): Promise<void> {
  try {
    const { data } = await getEmailTemplate(id)
    isEdit.value = true
    editingId.value = id
    applyForm(data)
    formVisible.value = true
  } catch {
    ElMessage.error('템플릿 정보를 불러오지 못했습니다.')
  }
}

function addVariable(): void {
  form.variables = [...(form.variables ?? []), { name: '', description: '', required: false }]
}

function removeVariable(idx: number): void {
  form.variables = (form.variables ?? []).filter((_, i) => i !== idx)
}

async function onSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload: CreateRequest = {
      ...form,
      bodyText: form.bodyText || undefined,
      variables: (form.variables ?? []).filter((v) => v.name.trim() !== ''),
    }
    if (isEdit.value && editingId.value != null) {
      await updateEmailTemplate(editingId.value, payload)
      ElMessage.success('템플릿이 수정되었습니다.')
    } else {
      await createEmailTemplate(payload)
      ElMessage.success('템플릿이 등록되었습니다.')
    }
    formVisible.value = false
    await reload()
  } catch {
    ElMessage.error('저장에 실패했습니다.')
  } finally {
    saving.value = false
  }
}

// ── 삭제 ─────────────────────────────────────────────────────────────────────
async function onDelete(row: EmailTemplateSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `"${row.name}" 템플릿을 삭제하시겠습니까?`,
      '템플릿 삭제',
      { confirmButtonText: '삭제', cancelButtonText: '취소', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteEmailTemplate(row.id)
    ElMessage.success('템플릿이 삭제되었습니다.')
    await reload()
  } catch {
    ElMessage.error('삭제에 실패했습니다.')
  }
}

// ── 미리보기 / 테스트 발송 ────────────────────────────────────────────────────
const previewVisible = ref(false)
const previewTemplate = ref<EmailTemplateDetail | null>(null)
const previewVars = reactive<Record<string, string>>({})
const previewResult = ref<PreviewResult | null>(null)
const previewing = ref(false)
const testSending = ref(false)

const safePreviewHtml = computed(() => sanitize(previewResult.value?.bodyHtml))

async function openPreview(id: number): Promise<void> {
  try {
    const { data } = await getEmailTemplate(id)
    previewTemplate.value = data
    previewResult.value = null
    // 변수 입력 초기화
    Object.keys(previewVars).forEach((k) => delete previewVars[k])
    ;(data.variables ?? []).forEach((v) => {
      previewVars[v.name] = ''
    })
    previewVisible.value = true
    // 변수가 없으면 즉시 렌더링
    if (!data.variables || data.variables.length === 0) {
      await renderPreview()
    }
  } catch {
    ElMessage.error('템플릿 정보를 불러오지 못했습니다.')
  }
}

async function renderPreview(): Promise<void> {
  if (!previewTemplate.value) return
  previewing.value = true
  try {
    const { data } = await previewEmailTemplate(previewTemplate.value.id, { ...previewVars })
    previewResult.value = data
  } catch {
    ElMessage.error('미리보기 렌더링에 실패했습니다.')
  } finally {
    previewing.value = false
  }
}

async function onTestSend(): Promise<void> {
  if (!previewTemplate.value) return
  testSending.value = true
  try {
    await testSendEmailTemplate(previewTemplate.value.id, { ...previewVars })
    ElMessage.success('테스트 메일이 발송되었습니다.')
  } catch {
    ElMessage.error('테스트 발송에 실패했습니다.')
  } finally {
    testSending.value = false
  }
}

// ── 발송 이력 드로어 ──────────────────────────────────────────────────────────
const logDrawerVisible = ref(false)
const logTemplate = ref<EmailTemplateSummary | null>(null)
const sendLogs = ref<SendLogEntry[]>([])
const logLoading = ref(false)
const logPage = ref(1)
const logSize = ref(20)
const logTotal = ref(0)

function openSendLogs(row: EmailTemplateSummary): void {
  logTemplate.value = row
  logPage.value = 1
  logDrawerVisible.value = true
  void loadSendLogs()
}

async function loadSendLogs(): Promise<void> {
  if (!logTemplate.value) return
  logLoading.value = true
  try {
    const { data } = await getSendLogs(logTemplate.value.id, {
      page: logPage.value - 1,
      size: logSize.value,
    })
    sendLogs.value = data.content
    logTotal.value = data.totalElements
  } catch {
    ElMessage.error('발송 이력을 불러오지 못했습니다.')
  } finally {
    logLoading.value = false
  }
}

// ── 유틸 ─────────────────────────────────────────────────────────────────────
function formatDate(iso: string | null | undefined): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
