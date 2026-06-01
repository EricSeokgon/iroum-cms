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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile, UploadRawFile } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { boardApi } from '@/api/board'
import type { PostCreateRequest } from '@iroum/shared/types/api'
import TiptapEditor from '@/components/editor/TiptapEditor.vue'

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

  saving.value = true
  try {
    if (isEdit.value && props.id) {
      await boardApi.updatePost(Number(props.id), {
        title: form.title,
        contentHtml: form.contentHtml,
        categoryCode: form.categoryCode || undefined,
        isNotice: form.isNotice,
      })
      await saveEnTranslation(Number(props.id))
      ElMessage.success(t('board.posts.success.updated'))
      router.push({ name: 'board-post-detail', params: { id: props.id } })
    } else {
      const res = await boardApi.createPost(Number(props.bbsId), {
        title: form.title,
        contentHtml: form.contentHtml,
        categoryCode: form.categoryCode || undefined,
        isNotice: form.isNotice,
      })
      await saveEnTranslation(res.data.id)
      ElMessage.success(t('board.posts.success.created'))
      router.push({ name: 'board-post-detail', params: { id: res.data.id } })
    }
  } catch {
    ElMessage.error(t('board.posts.error.saveFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadPost()
})
</script>
