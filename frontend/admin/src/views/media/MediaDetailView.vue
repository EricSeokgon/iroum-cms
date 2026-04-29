<template>
  <div>
    <div class="mb-6 flex items-center gap-4">
      <el-button plain :aria-label="t('common.back')" @click="router.back()">
        {{ t('common.back') }}
      </el-button>
      <h2 class="text-xl font-semibold text-gray-800">{{ t('media.detail.title') }}</h2>
    </div>

    <div v-if="loading" class="flex justify-center py-16">
      <el-icon class="animate-spin text-4xl text-blue-500"><i-ep-loading /></el-icon>
    </div>

    <div v-else-if="asset" class="flex flex-col gap-6 lg:flex-row">
      <!-- 좌측: 미리보기 -->
      <div class="flex-shrink-0 lg:w-1/2">
        <div class="overflow-hidden rounded-lg border border-gray-200 bg-gray-100" style="min-height: 300px">
          <img
            v-if="asset.mediaType === 'IMAGE' && previewUrl"
            :src="previewUrl"
            :alt="asset.altText ?? asset.fileName"
            class="h-full w-full object-contain"
            style="max-height: 480px"
          />
          <div
            v-else
            class="flex h-72 flex-col items-center justify-center gap-4 text-gray-400"
            :aria-label="t(`media.type.${asset.mediaType}`)"
          >
            <el-icon class="text-6xl">
              <i-ep-video-camera v-if="asset.mediaType === 'VIDEO'" />
              <i-ep-headphone v-else-if="asset.mediaType === 'AUDIO'" />
              <i-ep-document v-else />
            </el-icon>
            <p class="text-sm">{{ t(`media.type.${asset.mediaType}`) }}</p>
          </div>
        </div>

        <!-- 액션 버튼 -->
        <div class="mt-4 flex flex-wrap gap-2">
          <el-button
            type="primary"
            plain
            :loading="downloadLoading"
            :aria-label="t('media.action.download')"
            @click="handleDownload"
          >
            <el-icon><i-ep-download /></el-icon>
            {{ t('media.action.download') }}
          </el-button>
          <el-button
            type="primary"
            :aria-label="t('media.action.edit')"
            @click="showEdit = true"
          >
            {{ t('media.action.edit') }}
          </el-button>
          <el-button
            type="danger"
            plain
            :disabled="(asset.usageCount ?? 0) > 0"
            :title="(asset.usageCount ?? 0) > 0 ? t('media.deleteBlockedHint') : ''"
            :aria-label="t('media.action.delete')"
            @click="handleDelete"
          >
            {{ t('media.action.delete') }}
          </el-button>
        </div>
        <!-- 사용 중 삭제 차단 안내 -->
        <p
          v-if="(asset.usageCount ?? 0) > 0"
          class="mt-2 text-xs text-orange-500"
          role="alert"
        >
          {{ t('media.deleteBlockedHint') }}
        </p>
      </div>

      <!-- 우측: 메타 + 사용처 -->
      <div class="flex-1 space-y-6">
        <!-- 메타데이터 -->
        <el-card :header="t('media.detail.meta')">
          <dl class="space-y-2 text-sm">
            <div class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.fileName') }}</dt>
              <dd class="break-all text-gray-800">{{ asset.fileName }}</dd>
            </div>
            <div class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.type') }}</dt>
              <dd class="text-gray-800">{{ t(`media.type.${asset.mediaType}`) }}</dd>
            </div>
            <div class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.mime') }}</dt>
              <dd class="text-gray-800">{{ asset.mimeType }}</dd>
            </div>
            <div class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.size') }}</dt>
              <dd class="text-gray-800">{{ formatBytes(asset.sizeBytes) }}</dd>
            </div>
            <div v-if="asset.width && asset.height" class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.resolution') }}</dt>
              <dd class="text-gray-800">{{ asset.width }} × {{ asset.height }} px</dd>
            </div>
            <div v-if="asset.durationSeconds" class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.duration') }}</dt>
              <dd class="text-gray-800">{{ formatDuration(asset.durationSeconds) }}</dd>
            </div>
            <div class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.checksum') }}</dt>
              <dd class="break-all font-mono text-xs text-gray-600">{{ asset.checksum }}</dd>
            </div>
            <div class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.uploadedBy') }}</dt>
              <dd class="text-gray-800">{{ asset.uploadedBy }}</dd>
            </div>
            <div class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.uploadedAt') }}</dt>
              <dd class="text-gray-800">{{ formatDate(asset.uploadedAt) }}</dd>
            </div>
            <div class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.license') }}</dt>
              <dd class="text-gray-800">{{ asset.licenseType }}</dd>
            </div>
            <div v-if="asset.altText" class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.altText') }}</dt>
              <dd class="text-gray-800">{{ asset.altText }}</dd>
            </div>
            <div v-if="asset.tags.length > 0" class="flex gap-2">
              <dt class="w-32 shrink-0 font-medium text-gray-600">{{ t('media.field.tags') }}</dt>
              <dd class="flex flex-wrap gap-1">
                <el-tag v-for="tag in asset.tags" :key="tag" size="small" type="info">
                  {{ tag }}
                </el-tag>
              </dd>
            </div>
          </dl>
        </el-card>

        <!-- 사용처 목록 -->
        <el-card :header="`${t('media.detail.usage')} (${usageList.length})`">
          <div v-if="usageLoading" class="text-center text-sm text-gray-400">
            {{ t('common.loading') }}
          </div>
          <ul v-else-if="usageList.length > 0" class="space-y-1 text-sm">
            <li
              v-for="entry in usageList"
              :key="`${entry.entityType}-${entry.entityId}`"
              class="flex items-center gap-2"
            >
              <el-tag size="small" type="info">{{ entry.entityType }}</el-tag>
              <a
                :href="entry.url"
                target="_blank"
                rel="noopener noreferrer"
                class="truncate text-blue-600 hover:underline focus-visible:outline focus-visible:outline-2"
              >
                {{ entry.entityTitle }}
              </a>
            </li>
          </ul>
          <el-empty
            v-else
            :description="t('media.detail.noUsage')"
            :image-size="60"
          />
        </el-card>
      </div>
    </div>

    <!-- 편집 다이얼로그 -->
    <el-dialog
      v-if="showEdit && asset"
      v-model="showEdit"
      :title="t('media.action.edit')"
      width="480px"
      :aria-label="t('media.action.edit')"
    >
      <el-form :model="editForm" label-width="100px">
        <el-form-item :label="t('media.field.altText')">
          <el-input v-model="editForm.altText" :aria-label="t('media.field.altText')" />
        </el-form-item>
        <el-form-item :label="t('media.field.description')">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            :aria-label="t('media.field.description')"
          />
        </el-form-item>
        <el-form-item :label="t('media.field.license')">
          <el-select v-model="editForm.licenseType" style="width: 100%" :aria-label="t('media.field.license')">
            <el-option v-for="opt in licenseOptions" :key="opt" :label="opt" :value="opt" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mediaApi } from '@/api/media'
import type { MediaAssetDetail, MediaUsageEntry, LicenseType } from '@iroum/shared/types/api'

const props = defineProps<{ uuid: string }>()
const { t } = useI18n()
const router = useRouter()

// ── 상태 ──────────────────────────────────────────────────────────────────────
const asset = ref<MediaAssetDetail | null>(null)
const loading = ref(false)
const usageList = ref<MediaUsageEntry[]>([])
const usageLoading = ref(false)
const previewUrl = ref<string | null>(null)
const downloadLoading = ref(false)
const showEdit = ref(false)
const saving = ref(false)

const editForm = ref<{
  altText: string
  description: string
  licenseType: LicenseType
}>({
  altText: '',
  description: '',
  licenseType: 'ALL_RIGHTS_RESERVED',
})

const licenseOptions: LicenseType[] = [
  'ALL_RIGHTS_RESERVED', 'CC_BY', 'CC_BY_SA', 'CC_BY_NC', 'CC0', 'PUBLIC_DOMAIN',
]

// @MX:ANCHOR: [AUTO] loadAsset — onMounted, handleSave 후 재로드 시 호출
// @MX:REASON: fan_in >= 3: onMounted, handleSave에서 공통 호출되는 진입점
async function loadAsset(): Promise<void> {
  loading.value = true
  try {
    const res = await mediaApi.get(props.uuid)
    asset.value = res.data
    editForm.value = {
      altText: res.data.altText ?? '',
      description: res.data.description ?? '',
      licenseType: res.data.licenseType,
    }
    // 이미지인 경우 서명 URL로 미리보기
    if (res.data.mediaType === 'IMAGE') {
      const urlRes = await mediaApi.signedUrl(props.uuid)
      previewUrl.value = urlRes.data.signedUrl
    }
  } catch {
    ElMessage.error(t('media.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadUsage(): Promise<void> {
  usageLoading.value = true
  try {
    const res = await mediaApi.usage(props.uuid)
    usageList.value = res.data
  } catch {
    // 사용처 로드 실패는 비치명적
  } finally {
    usageLoading.value = false
  }
}

async function handleDownload(): Promise<void> {
  downloadLoading.value = true
  try {
    const res = await mediaApi.signedUrl(props.uuid)
    window.open(res.data.signedUrl, '_blank', 'noopener,noreferrer')
  } catch {
    ElMessage.error(t('media.error.downloadFailed'))
  } finally {
    downloadLoading.value = false
  }
}

async function handleSave(): Promise<void> {
  if (!asset.value) return
  saving.value = true
  try {
    await mediaApi.update(props.uuid, {
      altText: editForm.value.altText || null,
      description: editForm.value.description || null,
      licenseType: editForm.value.licenseType,
    })
    ElMessage.success(t('media.success.updated'))
    showEdit.value = false
    await loadAsset()
  } catch {
    ElMessage.error(t('media.error.updateFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDelete(): Promise<void> {
  if (!asset.value) return
  if ((asset.value.usageCount ?? 0) > 0) {
    ElMessage.warning(t('media.deleteBlockedHint'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('media.confirm.delete', { name: asset.value.fileName }),
      t('media.action.delete'),
      { type: 'warning', confirmButtonText: t('media.action.delete'), cancelButtonText: t('common.cancel') },
    )
    await mediaApi.delete(props.uuid)
    ElMessage.success(t('media.success.deleted'))
    router.replace({ name: 'media-library' })
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('media.error.deleteFailed'))
  }
}

// ── 유틸 ──────────────────────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

onMounted(() => {
  loadAsset()
  loadUsage()
})
</script>
