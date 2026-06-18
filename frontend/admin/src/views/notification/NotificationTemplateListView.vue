<template>
  <div data-testid="notification-template-list">
    <!-- 페이지 제목 -->
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">알림 템플릿 관리</h2>
      <p class="mt-1 text-sm text-gray-500">
        인앱/이메일 알림 발송에 사용되는 템플릿을 등록·수정하고 미리보기할 수 있습니다.
      </p>
    </div>

    <!-- 필터 바 -->
    <el-card class="mb-4" data-testid="notification-template-filter">
      <div class="flex flex-wrap items-end gap-4">
        <div class="flex flex-col gap-1">
          <span class="text-sm font-medium text-gray-700">사용 여부</span>
          <el-radio-group v-model="filter.isActive" data-testid="filter-active">
            <el-radio-button :value="undefined">전체</el-radio-button>
            <el-radio-button :value="true">활성</el-radio-button>
            <el-radio-button :value="false">비활성</el-radio-button>
          </el-radio-group>
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
          새 템플릿 추가
        </el-button>
      </div>

      <el-table
        v-loading="store.loading"
        :data="store.templates"
        data-testid="notification-template-table"
        aria-label="알림 템플릿 목록"
        row-key="id"
        stripe
      >
        <el-table-column prop="code" label="코드" width="200" />
        <el-table-column prop="name" label="이름" min-width="180">
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="channel" label="채널" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.channel" size="small" type="info" effect="plain">
              {{ channelLabel(row.channel) }}
            </el-tag>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="language" label="언어" width="90">
          <template #default="{ row }">{{ row.language === 'en' ? 'English' : '한국어' }}</template>
        </el-table-column>
        <el-table-column prop="isActive" label="활성" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.isActive"
              :disabled="!canWrite"
              data-testid="row-active-switch"
              @change="onToggleActive(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="수정일" width="170">
          <template #default="{ row }">
            <span class="text-xs text-gray-600">{{ formatDate(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="openPreview(row.id)">미리보기</el-button>
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
              v-if="canDelete"
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
          <div class="py-10 text-center text-gray-500">등록된 알림 템플릿이 없습니다.</div>
        </template>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="store.totalCount"
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
      :title="isEdit ? '알림 템플릿 수정' : '알림 템플릿 등록'"
      width="720px"
      data-testid="notification-template-form-dialog"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="120px"
        data-testid="notification-template-form"
      >
        <el-form-item label="코드" prop="code">
          <el-input
            v-model="form.code"
            :disabled="isEdit"
            placeholder="예: policy-match-default"
            data-testid="form-code"
          />
        </el-form-item>
        <el-form-item label="이름">
          <el-input v-model="form.name" data-testid="form-name" />
        </el-form-item>
        <el-form-item label="채널">
          <el-select
            v-model="form.channel"
            clearable
            style="width: 100%"
            placeholder="채널 선택"
            data-testid="form-channel"
          >
            <el-option
              v-for="opt in CHANNEL_OPTIONS"
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
        <el-form-item label="제목">
          <el-input v-model="form.subject" data-testid="form-subject" />
        </el-form-item>
        <el-form-item label="HTML 본문">
          <el-input
            v-model="form.bodyHtml"
            type="textarea"
            :rows="10"
            data-testid="form-body-html"
          />
        </el-form-item>
        <el-form-item label="변수">
          <el-input
            v-model="form.variables"
            type="textarea"
            :rows="3"
            placeholder="변수 정의 (JSON 등 자유 형식)"
            data-testid="form-variables"
          />
        </el-form-item>
        <el-form-item label="이메일 템플릿 ID">
          <el-input-number
            v-model="form.emailTemplateId"
            :min="1"
            controls-position="right"
            style="width: 200px"
            data-testid="form-email-template-id"
          />
        </el-form-item>
        <el-form-item label="활성">
          <el-switch v-model="form.isActive" data-testid="form-active" />
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
      title="알림 템플릿 미리보기"
      width="720px"
      data-testid="preview-dialog"
    >
      <div v-if="previewResult" data-testid="preview-result">
        <p v-if="previewResult.subject" class="mb-2 text-sm">
          <span class="font-semibold text-gray-700">제목:</span>
          {{ previewResult.subject }}
        </p>
        <div class="rounded border border-gray-200 p-3" v-html="safePreviewHtml" />
      </div>
      <div v-else class="py-8 text-center text-gray-500">미리보기 내용이 없습니다.</div>

      <template #footer>
        <el-button @click="previewVisible = false">닫기</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
// SPEC-CMS-NOTI-EXT-001 — 알림 템플릿 관리 화면
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { usePermission } from '@/composables/usePermission'
import { useSafeHtml } from '@/composables/useSafeHtml'
import { useNotificationTemplateStore } from '@/stores/notificationTemplate'
import type {
  NotificationTemplateCreateRequest,
  NotificationTemplatePreviewResult,
  NotificationTemplateResponse,
} from '@/api/notificationTemplate'

const { hasPermission } = usePermission()
const { sanitize } = useSafeHtml()
const store = useNotificationTemplateStore()

const canWrite = computed(() => hasPermission('NOTIFICATION_TEMPLATE:WRITE'))
const canDelete = computed(() => hasPermission('NOTIFICATION_TEMPLATE:DELETE'))

const CHANNEL_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '이메일', value: 'EMAIL' },
  { label: '인앱', value: 'INAPP' },
]

function channelLabel(channel: string): string {
  return CHANNEL_OPTIONS.find((o) => o.value === channel)?.label ?? channel
}

// ── 목록/필터 ────────────────────────────────────────────────────────────────
const currentPage = ref(1)
const pageSize = ref(20)

const filter = reactive<{ isActive?: boolean }>({})

async function reload(): Promise<void> {
  try {
    await store.fetchTemplates({
      page: currentPage.value - 1,
      size: pageSize.value,
      isActive: filter.isActive,
    })
  } catch {
    ElMessage.error('템플릿 목록을 불러오지 못했습니다.')
  }
}

function onSearch(): void {
  currentPage.value = 1
  void reload()
}

function onReset(): void {
  filter.isActive = undefined
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

interface TemplateForm {
  code: string
  name: string
  channel?: string
  subject: string
  bodyHtml: string
  variables: string
  language: string
  isActive: boolean
  emailTemplateId?: number
}

function emptyForm(): TemplateForm {
  return {
    code: '',
    name: '',
    channel: undefined,
    subject: '',
    bodyHtml: '',
    variables: '',
    language: 'ko',
    isActive: true,
    emailTemplateId: undefined,
  }
}

const form = reactive<TemplateForm>(emptyForm())

const formRules: FormRules<TemplateForm> = {
  code: [{ required: true, message: '코드를 입력하세요.', trigger: 'blur' }],
  language: [{ required: true, message: '언어를 선택하세요.', trigger: 'change' }],
}

function applyForm(src: NotificationTemplateResponse): void {
  form.code = src.code
  form.name = src.name ?? ''
  form.channel = src.channel || undefined
  form.subject = src.subject ?? ''
  form.bodyHtml = src.bodyHtml ?? ''
  form.variables = src.variables ?? ''
  form.language = src.language
  form.isActive = src.isActive
  form.emailTemplateId = src.emailTemplateId
}

function resetForm(): void {
  Object.assign(form, emptyForm())
  formRef.value?.clearValidate()
}

function openCreate(): void {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, emptyForm())
  formVisible.value = true
}

async function openEdit(id: number): Promise<void> {
  try {
    const data = await store.fetchTemplate(id)
    isEdit.value = true
    editingId.value = id
    applyForm(data)
    formVisible.value = true
  } catch {
    ElMessage.error('템플릿 정보를 불러오지 못했습니다.')
  }
}

function buildPayload(): NotificationTemplateCreateRequest {
  return {
    code: form.code,
    name: form.name || undefined,
    channel: form.channel || undefined,
    subject: form.subject || undefined,
    bodyHtml: form.bodyHtml || undefined,
    variables: form.variables || undefined,
    language: form.language,
    isActive: form.isActive,
    emailTemplateId: form.emailTemplateId,
  }
}

async function onSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value && editingId.value != null) {
      await store.updateTemplate(editingId.value, payload)
      ElMessage.success('템플릿이 수정되었습니다.')
    } else {
      await store.createTemplate(payload)
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

// ── 활성 토글 ─────────────────────────────────────────────────────────────────
async function onToggleActive(row: NotificationTemplateResponse): Promise<void> {
  try {
    await store.updateTemplate(row.id, { isActive: !row.isActive })
    ElMessage.success('활성 상태가 변경되었습니다.')
    await reload()
  } catch {
    ElMessage.error('상태 변경에 실패했습니다.')
  }
}

// ── 삭제 ─────────────────────────────────────────────────────────────────────
async function onDelete(row: NotificationTemplateResponse): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `"${row.name || row.code}" 템플릿을 삭제하시겠습니까?`,
      '템플릿 삭제',
      { confirmButtonText: '삭제', cancelButtonText: '취소', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await store.deleteTemplate(row.id)
    ElMessage.success('템플릿이 삭제되었습니다.')
    await reload()
  } catch {
    ElMessage.error('삭제에 실패했습니다.')
  }
}

// ── 미리보기 ─────────────────────────────────────────────────────────────────
const previewVisible = ref(false)
const previewResult = ref<NotificationTemplatePreviewResult | null>(null)

const safePreviewHtml = computed(() => sanitize(previewResult.value?.bodyHtml))

async function openPreview(id: number): Promise<void> {
  try {
    previewResult.value = await store.previewTemplate(id)
    previewVisible.value = true
  } catch {
    ElMessage.error('미리보기를 불러오지 못했습니다.')
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
</script>
