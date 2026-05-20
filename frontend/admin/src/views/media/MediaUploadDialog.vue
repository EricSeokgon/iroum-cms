<template>
  <el-dialog
    v-model="visible"
    :title="t('media.upload')"
    width="600px"
    :aria-label="t('media.upload')"
    @close="emit('close')"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      :aria-label="t('media.uploadForm')"
    >
      <!-- 파일 선택 (drag-drop) -->
      <el-form-item :label="t('media.field.file')" prop="files">
        <el-upload
          ref="uploadRef"
          v-model:file-list="fileList"
          drag
          multiple
          :auto-upload="false"
          :limit="10"
          :on-exceed="onExceed"
          :on-change="onFileChange"
          :aria-label="t('media.uploadArea')"
          class="w-full"
        >
          <el-icon class="text-4xl text-gray-400"><i-ep-upload-filled /></el-icon>
          <p class="mt-2 text-sm text-gray-600">{{ t('media.uploadDrag') }}</p>
          <p class="text-xs text-gray-400">{{ t('media.uploadOr') }}</p>
          <el-button type="primary" plain size="small" class="mt-2">
            {{ t('media.uploadBrowse') }}
          </el-button>
          <template #tip>
            <p class="mt-1 text-xs text-gray-400" role="note">
              {{ t('media.uploadTip') }}
            </p>
          </template>
        </el-upload>
      </el-form-item>

      <!-- alt_text — 이미지 파일이 있으면 필수 -->
      <el-form-item
        :label="t('media.field.altText')"
        prop="altText"
      >
        <el-input
          v-model="form.altText"
          :placeholder="t('media.field.altTextPlaceholder')"
          :aria-label="t('media.field.altText')"
          aria-required="true"
        />
        <p v-if="hasImageFile" class="mt-1 text-xs text-orange-500" role="note">
          {{ t('media.field.altTextRequired') }}
        </p>
      </el-form-item>

      <!-- 라이선스 -->
      <el-form-item :label="t('media.field.license')" prop="licenseType">
        <el-select
          v-model="form.licenseType"
          style="width: 100%"
          :aria-label="t('media.field.license')"
        >
          <el-option
            v-for="opt in licenseOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <!-- 라이선스 텍스트 라벨 (색상 외 텍스트로도 구분) KWCAG 1.4.1 -->
        <p class="mt-1 text-xs text-gray-500" role="note">
          {{ licenseDescription }}
        </p>
      </el-form-item>

      <!-- 태그 -->
      <el-form-item :label="t('media.field.tags')">
        <el-input
          v-model="tagInput"
          :placeholder="t('media.field.tagsPlaceholder')"
          :aria-label="t('media.field.tags')"
          @keyup.enter="addTag"
          @blur="addTag"
        />
        <div v-if="form.tags.length > 0" class="mt-2 flex flex-wrap gap-1">
          <el-tag
            v-for="tag in form.tags"
            :key="tag"
            closable
            @close="removeTag(tag)"
          >
            {{ tag }}
          </el-tag>
        </div>
      </el-form-item>
    </el-form>

    <!-- 업로드 진행률 -->
    <div v-if="uploading" class="mt-4 space-y-2">
      <div
        v-for="item in uploadQueue"
        :key="item.name"
        role="status"
        :aria-label="`${item.name} ${item.percent}%`"
      >
        <p class="truncate text-sm text-gray-700">{{ item.name }}</p>
        <el-progress
          :percentage="item.percent"
          :status="item.status"
          role="progressbar"
          :aria-valuenow="item.percent"
          :aria-valuemin="0"
          :aria-valuemax="100"
          :aria-label="`${item.name} ${t('media.uploadProgress')}`"
        />
      </div>
    </div>

    <template #footer>
      <el-button :disabled="uploading" @click="emit('close')">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="uploading"
        :disabled="fileList.length === 0"
        @click="startUpload"
      >
        {{ t('media.action.upload') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadFiles } from 'element-plus'
import { mediaApi } from '@/api/media'
import type { LicenseType } from '@iroum/shared/types/api'

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'uploaded'): void
}>()

const { t } = useI18n()

const visible = ref(true)
const uploading = ref(false)
const fileList = ref<UploadFile[]>([])
const tagInput = ref('')

interface UploadQueueItem {
  name: string
  percent: number
  status: '' | 'success' | 'exception'
}

const uploadQueue = ref<UploadQueueItem[]>([])

const form = ref({
  altText: '',
  licenseType: 'PROPRIETARY' as LicenseType,
  tags: [] as string[],
})

const rules = computed(() => ({
  altText: hasImageFile.value
    ? [{ required: true, message: t('media.field.altTextRequired'), trigger: 'blur' }]
    : [],
}))

// 이미지 파일 포함 여부 판별
const hasImageFile = computed(() =>
  fileList.value.some((f) => f.raw?.type.startsWith('image/')),
)

const licenseOptions: { value: LicenseType; label: string; description: string }[] = [
  { value: 'PROPRIETARY', label: '저작권 보유', description: '모든 권리 보유 (기본값)' },
  { value: 'INTERNAL', label: '내부 용도', description: '내부 사용 전용' },
  { value: 'CC_BY', label: 'CC BY 4.0', description: '저작자 표시' },
  { value: 'CC_BY_NC', label: 'CC BY-NC 4.0', description: '저작자 표시-비영리' },
  { value: 'CC0', label: 'CC0 1.0', description: '퍼블릭 도메인 기증' },
]

const licenseDescription = computed(
  () => licenseOptions.find((o) => o.value === form.value.licenseType)?.description ?? '',
)

function onFileChange(_file: UploadFile, files: UploadFiles): void {
  fileList.value = files
}

function onExceed(): void {
  ElMessage.warning(t('media.error.fileLimit'))
}

function addTag(): void {
  const tag = tagInput.value.trim()
  if (tag && !form.value.tags.includes(tag)) {
    form.value.tags.push(tag)
  }
  tagInput.value = ''
}

function removeTag(tag: string): void {
  form.value.tags = form.value.tags.filter((t) => t !== tag)
}

// @MX:WARN: [AUTO] startUpload — 여러 파일을 순차 업로드, 실패 시 부분 업로드 발생 가능
// @MX:REASON: Promise 체인 실패 시 일부 파일만 업로드될 수 있으므로 개별 결과를 수집 후 보고
async function startUpload(): Promise<void> {
  if (fileList.value.length === 0) return

  uploading.value = true
  uploadQueue.value = fileList.value.map((f) => ({
    name: f.name,
    percent: 0,
    status: '' as const,
  }))

  let successCount = 0

  for (let i = 0; i < fileList.value.length; i++) {
    const uploadFile = fileList.value[i]
    const queueItem = uploadQueue.value[i]

    if (!uploadFile.raw) {
      queueItem.status = 'exception'
      continue
    }

    try {
      await mediaApi.upload(
        uploadFile.raw,
        {
          altText: form.value.altText || undefined,
          licenseType: form.value.licenseType,
          tags: form.value.tags,
        },
        (percent) => {
          queueItem.percent = percent
        },
      )
      queueItem.percent = 100
      queueItem.status = 'success'
      successCount++
    } catch {
      queueItem.status = 'exception'
      ElMessage.error(t('media.error.uploadFailed', { name: uploadFile.name }))
    }
  }

  uploading.value = false

  if (successCount > 0) {
    ElMessage.success(t('media.success.uploaded', { count: successCount }))
    emit('uploaded')
  }
}
</script>
