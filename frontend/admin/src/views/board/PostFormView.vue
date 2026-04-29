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

        <!-- 본문 — 1차: textarea, 추후 Tiptap 통합 -->
        <!-- @MX:TODO: [AUTO] Tiptap WYSIWYG 에디터 통합 — SPEC-CMS-003 추후 단계 -->
        <el-form-item :label="t('board.posts.field.content')" prop="contentHtml">
          <el-input
            id="post-content"
            v-model="form.contentHtml"
            type="textarea"
            :rows="15"
            :placeholder="t('board.posts.field.contentPlaceholder')"
            aria-required="true"
            aria-multiline="true"
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
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadFile, UploadRawFile } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { boardApi } from '@/api/board'
import type { PostCreateRequest } from '@iroum/shared/types/api'

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
    { required: true, message: t('board.posts.error.contentRequired'), trigger: 'blur' },
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
      ElMessage.success(t('board.posts.success.updated'))
      router.push({ name: 'board-post-detail', params: { id: props.id } })
    } else {
      const res = await boardApi.createPost(Number(props.bbsId), {
        title: form.title,
        contentHtml: form.contentHtml,
        categoryCode: form.categoryCode || undefined,
        isNotice: form.isNotice,
      })
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
