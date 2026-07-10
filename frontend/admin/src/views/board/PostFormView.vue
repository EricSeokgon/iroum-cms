<template>
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ isEdit ? t('board.posts.edit') : t('board.posts.write') }}
      </h2>
      <el-button
        plain
        :aria-label="t('common.back')"
        @click="router.back()"
      >
        {{ t('common.cancel') }}
      </el-button>
    </div>

    <el-card>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        :aria-label="isEdit ? t('board.posts.edit') : t('board.posts.write')"
      >
        <el-tabs v-model="activeTab" class="mb-2">
          <el-tab-pane label="한국어" name="ko">
        <!-- 제목 -->
        <el-form-item :label="t('board.posts.field.title')" prop="title">
          <el-input
            id="post-title"
            v-model="form.title"
            :placeholder="t('board.posts.field.titlePlaceholder')"
            maxlength="500"
            show-word-limit
            aria-required="true"
          />
        </el-form-item>

        <!-- 공지 체크박스 (관리자만) -->
        <el-form-item
          v-if="isAdmin"
          :label="t('board.posts.field.isNotice')"
          prop="isNotice"
        >
          <el-checkbox
            v-model="form.isNotice"
            :aria-label="t('board.posts.field.isNotice')"
          >
            {{ t('board.posts.field.isNoticeLabel') }}
          </el-checkbox>
        </el-form-item>

        <!-- 발행 방식: 즉시 / 예약 (SPEC-CMS-POST-SCHEDULE-001) -->
        <el-form-item :label="t('board.posts.field.publishMode')" prop="publishMode">
          <el-radio-group
            v-model="publishMode"
            :aria-label="t('board.posts.field.publishMode')"
          >
            <el-radio value="NOW">{{ t('board.posts.publishMode.now') }}</el-radio>
            <el-radio value="SCHEDULE">{{ t('board.posts.publishMode.schedule') }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 예약 발행 일시 picker (예약 선택 시) -->
        <el-form-item
          v-if="publishMode === 'SCHEDULE'"
          :label="t('board.posts.field.scheduledAt')"
          prop="scheduledAt"
        >
          <el-date-picker
            v-model="scheduledAt"
            type="datetime"
            :placeholder="t('board.posts.field.scheduledAtPlaceholder')"
            :disabled-date="disablePastDate"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            :aria-label="t('board.posts.field.scheduledAt')"
          />
          <el-button
            v-if="isEdit && isScheduled"
            type="warning"
            plain
            size="small"
            class="ml-2"
            @click="handleCancelSchedule"
          >
            {{ t('board.posts.cancelSchedule') }}
          </el-button>
        </el-form-item>

        <!-- 카테고리 (옵션) -->
        <el-form-item :label="t('board.posts.field.categoryCode')" prop="categoryCode">
          <el-input
            id="post-category"
            v-model="form.categoryCode"
            :placeholder="t('board.posts.field.categoryPlaceholder')"
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item :label="t('board.posts.field.content')" prop="contentHtml">
          <TiptapEditor
            v-model="form.contentHtml"
            :placeholder="t('board.posts.field.contentPlaceholder')"
            :rows="15"
            :aria-label="t('board.posts.field.content')"
          />
        </el-form-item>

        <!-- AI 스마트 태그 추천 (SPEC-CMS-AI-004) -->
        <el-form-item :label="t('board.posts.field.tags')">
          <TagRecommendationInput
            v-model="tags"
            :recommendations="recommendations"
            :loading="recommendLoading"
            @accept="acceptTag"
            @reject="rejectTag"
          />
        </el-form-item>

        <!-- 첨부파일 업로드 -->
        <el-form-item :label="t('board.posts.field.attachments')">
          <el-upload
            v-model:file-list="fileList"
            :http-request="handleUpload"
            multiple
            :limit="5"
            :on-exceed="onExceed"
            :aria-label="t('board.posts.field.attachments')"
          >
            <el-button type="primary" plain>
              {{ t('board.posts.uploadFile') }}
            </el-button>
            <template #tip>
              <p class="text-xs text-gray-400 mt-1">
                {{ t('board.posts.uploadTip') }}
              </p>
            </template>
          </el-upload>
        </el-form-item>
          </el-tab-pane>

          <!-- English 번역 탭 (선택) -->
          <el-tab-pane label="English" name="en">
            <div class="space-y-4">
              <div>
                <label class="mb-1 block text-sm text-gray-600">Title (English)</label>
                <el-input
                  v-model="enTitle"
                  placeholder="English title (optional)"
                  maxlength="500"
                  show-word-limit
                  aria-label="English title"
                />
              </div>
              <div>
                <label class="mb-1 block text-sm text-gray-600">Content (English)</label>
                <el-input
                  v-model="enContentHtml"
                  type="textarea"
                  :rows="10"
                  placeholder="English content (optional)"
                  aria-label="English content"
                />
              </div>
              <el-button
                v-if="isEdit && hasEnTranslation"
                type="danger"
                plain
                size="small"
                @click="deleteEnTranslation"
              >
                영어 번역 삭제
              </el-button>
            </div>
          </el-tab-pane>
        </el-tabs>

        <!-- 저장 버튼 -->
        <el-form-item>
          <div class="flex gap-2">
            <el-button
              type="primary"
              :loading="saving"
              :aria-label="t('common.save')"
              @click="handleSave"
            >
              {{ t('common.save') }}
            </el-button>
            <el-button @click="router.back()">{{ t('common.cancel') }}</el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 편집 충돌 모달 (SPEC-CMS-CONTENT-REVISION-001) -->
    <ConflictModal
      :visible="conflictVisible"
      :current-version="conflictVersion"
      @reload="reloadAfterConflict"
      @dismiss="conflictVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile, UploadRawFile } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { boardApi } from '@/api/board'
import type { PostCreateRequest } from '@iroum/shared/types/api'
import type { RevisionConflictPayload } from '@/types/revision'
import TiptapEditor from '@/components/editor/TiptapEditor.vue'
import ConflictModal from '@/components/revision/ConflictModal.vue'
import TagRecommendationInput from '@/components/TagRecommendationInput.vue'
import { useTagRecommendation } from '@/composables/useTagRecommendation'

interface Props {
  bbsId?: string
  id?: string
}
const props = defineProps<Props>()

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const saving = ref(false)
const fileList = ref<UploadFile[]>([])

// 다국어 번역 (English) 상태 — SPEC-CMS-NOTICE-I18N-001
const activeTab = ref('ko')
const enTitle = ref('')
const enContentHtml = ref('')
const hasEnTranslation = ref(false)

// 예약 발행 상태 — SPEC-CMS-POST-SCHEDULE-001
const publishMode = ref<'NOW' | 'SCHEDULE'>('NOW')
const scheduledAt = ref<string>('')
const isScheduled = ref(false)

// 낙관적 락(편집 충돌) 상태 — SPEC-CMS-CONTENT-REVISION-001
const expectedVersion = ref<number | undefined>(undefined)
const conflictVisible = ref(false)
const conflictVersion = ref(0)

// 과거 날짜 선택 차단 (오늘 이전 비활성화)
function disablePastDate(date: Date): boolean {
  return date.getTime() < Date.now() - 24 * 60 * 60 * 1000
}

const isEdit = computed(() => Boolean(props.id))
const isAdmin = computed(() =>
  auth.user?.roleCodes?.includes('SUPER_ADMIN') || auth.user?.roleCodes?.includes('DEPT_ADMIN'),
)

const form = reactive<PostCreateRequest & { categoryCode?: string }>({
  title: '',
  contentHtml: '',
  categoryCode: '',
  isNotice: false,
})

// AI 스마트 태그 추천 (SPEC-CMS-AI-004)
const tags = ref<string[]>([])
// 본문 HTML에서 태그를 제거한 plain text — ML 추천 입력
const contentText = computed(() => form.contentHtml.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim())
const {
  recommendations,
  loading: recommendLoading,
  acceptTag,
  rejectTag,
} = useTagRecommendation(contentText, tags, 'POST')

const rules: FormRules = {
  title: [
    { required: true, message: t('board.posts.error.titleRequired'), trigger: 'blur' },
    { max: 500, message: t('board.posts.error.titleLength'), trigger: 'blur' },
  ],
  contentHtml: [
    { required: true, message: t('board.posts.error.contentRequired'), trigger: 'change' },
  ],
}

async function loadPost(): Promise<void> {
  if (!props.id) return
  try {
    const res = await boardApi.getPost(Number(props.id))
    const p = res.data
    form.title = p.title
    form.contentHtml = p.contentHtml
    form.categoryCode = p.categoryCode ?? ''
    form.isNotice = p.isNotice
    expectedVersion.value = p.version
    tags.value = p.tags ?? []
    expectedVersion.value = p.version
    // 예약 상태로 로드되면 picker 초기값 + 예약 모드 표시 (REQ-POST-SCHEDULE-006-2)
    if (p.status === 'SCHEDULED' && p.scheduledAt) {
      publishMode.value = 'SCHEDULE'
      scheduledAt.value = p.scheduledAt
      isScheduled.value = true
    }
  } catch {
    ElMessage.error(t('board.posts.error.loadFailed'))
  }
  await loadEnTranslation()
}

// 편집 모드에서 기존 영어 번역을 불러와 English 탭을 채운다.
async function loadEnTranslation(): Promise<void> {
  if (!props.id) return
  try {
    const res = await boardApi.getTranslation(Number(props.id), 'en')
    enTitle.value = res.data.title
    enContentHtml.value = res.data.contentHtml ?? ''
    hasEnTranslation.value = true
  } catch {
    // 번역 없음(404) 또는 조회 실패는 무시 — 영어 번역은 선택 사항
    hasEnTranslation.value = false
  }
}

// 영어 번역 저장 (제목이 입력된 경우에만 upsert)
async function saveEnTranslation(postId: number): Promise<void> {
  if (!enTitle.value.trim()) return
  await boardApi.upsertTranslation(postId, {
    language: 'en',
    title: enTitle.value,
    contentHtml: enContentHtml.value || undefined,
  })
  hasEnTranslation.value = true
}

async function deleteEnTranslation(): Promise<void> {
  if (!props.id) return
  try {
    await ElMessageBox.confirm('영어 번역을 삭제하시겠습니까?', '확인', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await boardApi.deleteTranslation(Number(props.id), 'en')
    enTitle.value = ''
    enContentHtml.value = ''
    hasEnTranslation.value = false
    ElMessage.success('영어 번역이 삭제되었습니다.')
  } catch {
    ElMessage.error(t('board.posts.error.saveFailed'))
  }
}

// @MX:WARN: [AUTO] 파일 업로드 비동기 처리 — 각 파일 독립적으로 업로드
// @MX:REASON: multipart 업로드는 개별 실패 가능성 있음, 에러 핸들링 필수
async function handleUpload({ file }: { file: UploadRawFile }): Promise<void> {
  try {
    await boardApi.uploadAttachment(file)
    ElMessage.success(`${file.name} ${t('board.posts.success.uploaded')}`)
  } catch {
    ElMessage.error(`${file.name} ${t('board.posts.error.uploadFailed')}`)
    throw new Error('upload failed')
  }
}

function onExceed(): void {
  ElMessage.warning(t('board.posts.error.fileLimit'))
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 예약 발행 선택 시 일시 필수 검증 (SPEC-CMS-POST-SCHEDULE-001)
  if (publishMode.value === 'SCHEDULE' && !scheduledAt.value) {
    ElMessage.warning(t('board.posts.error.scheduledAtRequired'))
    return
  }

  saving.value = true
  try {
    let postId: number
    if (isEdit.value && props.id) {
      await boardApi.updatePost(Number(props.id), {
        title: form.title,
        contentHtml: form.contentHtml,
        categoryCode: form.categoryCode || undefined,
        isNotice: form.isNotice,
        tags: tags.value,
      }, expectedVersion.value)
      await saveEnTranslation(Number(props.id))
      postId = Number(props.id)
    } else {
      const res = await boardApi.createPost(Number(props.bbsId), {
        title: form.title,
        contentHtml: form.contentHtml,
        categoryCode: form.categoryCode || undefined,
        isNotice: form.isNotice,
        tags: tags.value,
      })
      await saveEnTranslation(res.data.id)
      postId = res.data.id
    }

    // 예약 발행 선택 시 schedule API 호출 (REQ-POST-SCHEDULE-006-1)
    if (publishMode.value === 'SCHEDULE') {
      await boardApi.schedulePost(postId, scheduledAt.value)
      ElMessage.success(t('board.posts.success.scheduled'))
    } else {
      ElMessage.success(isEdit.value ? t('board.posts.success.updated') : t('board.posts.success.created'))
    }
    router.push({ name: 'board-post-detail', params: { id: postId } })
  } catch (e) {
    // 409 REVISION_CONFLICT → 충돌 모달 표시 (저장 실패 메시지 대신)
    if (isRevisionConflict(e)) {
      conflictVersion.value = (e.response!.data as RevisionConflictPayload).currentVersion
      conflictVisible.value = true
      return
    }
    ElMessage.error(t('board.posts.error.saveFailed'))
  } finally {
    saving.value = false
  }
}

// axios 409 + REVISION_CONFLICT 코드 판별
function isRevisionConflict(e: unknown): e is import('axios').AxiosError {
  return (
    axios.isAxiosError(e) &&
    e.response?.status === 409 &&
    (e.response.data as RevisionConflictPayload | undefined)?.code === 'REVISION_CONFLICT'
  )
}

// 충돌 모달의 "최신 버전 불러오기" → 게시글 재로드 후 expectedVersion 갱신
async function reloadAfterConflict(): Promise<void> {
  conflictVisible.value = false
  await loadPost()
  ElMessage.info(t('revision.conflict.reload'))
}

// 예약 취소 → DRAFT 복귀 (REQ-POST-SCHEDULE-006-2)
async function handleCancelSchedule(): Promise<void> {
  if (!props.id) return
  try {
    await ElMessageBox.confirm(t('board.posts.cancelScheduleConfirm'), t('common.confirm'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await boardApi.cancelSchedule(Number(props.id))
    publishMode.value = 'NOW'
    scheduledAt.value = ''
    isScheduled.value = false
    ElMessage.success(t('board.posts.success.scheduleCancelled'))
  } catch {
    ElMessage.error(t('board.posts.error.saveFailed'))
  }
}

onMounted(() => {
  loadPost()
})
</script>
