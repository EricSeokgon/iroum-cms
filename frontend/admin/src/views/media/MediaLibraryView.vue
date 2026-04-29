<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('media.title') }}</h2>
      <el-button type="primary" @click="showUpload = true">
        + {{ t('media.upload') }}
      </el-button>
    </div>

    <!-- 검색 및 필터 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-input
        v-model="searchQuery"
        :placeholder="t('media.search')"
        clearable
        style="width: 260px"
        :aria-label="t('media.search')"
      >
        <template #prefix>
          <el-icon><i-ep-search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="typeFilter"
        :placeholder="t('media.filterType')"
        clearable
        style="width: 150px"
        :aria-label="t('media.filterType')"
        @change="onFilterChange"
      >
        <el-option :label="t('media.type.IMAGE')" value="IMAGE" />
        <el-option :label="t('media.type.VIDEO')" value="VIDEO" />
        <el-option :label="t('media.type.DOCUMENT')" value="DOCUMENT" />
        <el-option :label="t('media.type.AUDIO')" value="AUDIO" />
      </el-select>

      <el-input
        v-model="tagQuery"
        :placeholder="t('media.filterTags')"
        clearable
        style="width: 180px"
        :aria-label="t('media.filterTags')"
        @change="onFilterChange"
      />
    </div>

    <!-- 그리드 영역 -->
    <div
      v-loading="loading"
      class="min-h-[200px]"
      role="region"
      :aria-label="t('media.gridRegion')"
      :aria-busy="loading"
    >
      <el-row v-if="assets.length > 0" :gutter="16">
        <el-col
          v-for="asset in assets"
          :key="asset.uuid"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
          class="mb-4"
        >
          <!-- @MX:NOTE: [AUTO] 카드는 네이티브 button으로 KWCAG 2.4.3 포커스 순서 보장 -->
          <button
            type="button"
            class="w-full text-left rounded-lg border border-gray-200 bg-white shadow-sm hover:shadow-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500 transition-shadow"
            :aria-label="`${asset.fileName} ${t('media.cardLabel')}`"
            @click="goDetail(asset.uuid)"
          >
            <!-- 썸네일 / 파일 아이콘 -->
            <div class="relative overflow-hidden rounded-t-lg bg-gray-100" style="height: 160px">
              <img
                v-if="asset.mediaType === 'IMAGE' && asset.thumbnailUrl"
                :src="asset.thumbnailUrl"
                :alt="asset.altText ?? asset.fileName"
                class="h-full w-full object-cover"
              />
              <div
                v-else
                class="flex h-full items-center justify-center"
                :aria-label="t(`media.type.${asset.mediaType}`)"
              >
                <el-icon class="text-5xl text-gray-400">
                  <i-ep-video-camera v-if="asset.mediaType === 'VIDEO'" />
                  <i-ep-headphone v-else-if="asset.mediaType === 'AUDIO'" />
                  <i-ep-document v-else />
                </el-icon>
              </div>

              <!-- 사용 수 뱃지 -->
              <span
                v-if="asset.usageCount > 0"
                class="absolute right-2 top-2 rounded bg-blue-600 px-2 py-0.5 text-xs font-medium text-white"
                :aria-label="`${t('media.usageCount')}: ${asset.usageCount}`"
              >
                {{ asset.usageCount }}
              </span>
            </div>

            <!-- 메타 정보 -->
            <div class="p-3">
              <p class="truncate text-sm font-medium text-gray-800" :title="asset.fileName">
                {{ asset.fileName }}
              </p>
              <p class="mt-1 text-xs text-gray-500">
                {{ formatBytes(asset.sizeBytes) }} · {{ formatDate(asset.uploadedAt) }}
              </p>
              <div v-if="asset.tags.length > 0" class="mt-2 flex flex-wrap gap-1">
                <el-tag
                  v-for="tag in asset.tags.slice(0, 3)"
                  :key="tag"
                  size="small"
                  type="info"
                >
                  {{ tag }}
                </el-tag>
              </div>
            </div>

            <!-- 액션 버튼 -->
            <div
              class="flex gap-1 border-t border-gray-100 px-3 py-2"
              role="group"
              :aria-label="`${asset.fileName} ${t('media.actions')}`"
              @click.stop
            >
              <el-button
                size="small"
                type="info"
                plain
                :aria-label="`${t('media.action.view')} ${asset.fileName}`"
                @click="goDetail(asset.uuid)"
              >
                {{ t('media.action.view') }}
              </el-button>
              <el-button
                size="small"
                type="danger"
                plain
                :aria-label="`${t('media.action.delete')} ${asset.fileName}`"
                @click="handleDelete(asset)"
              >
                {{ t('media.action.delete') }}
              </el-button>
            </div>
          </button>
        </el-col>
      </el-row>

      <!-- 빈 상태 -->
      <el-empty
        v-if="!loading && assets.length === 0"
        :description="t('media.empty')"
        :image-size="120"
        class="mt-8"
      />
    </div>

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="totalElements"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[20, 40, 80]"
        :aria-label="t('a11y.pagination')"
        @change="loadAssets"
      />
    </div>

    <!-- 업로드 다이얼로그 -->
    <MediaUploadDialog
      v-if="showUpload"
      @close="showUpload = false"
      @uploaded="onUploaded"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mediaApi } from '@/api/media'
import { useDebounce } from '@/composables/useDebounce'
import MediaUploadDialog from './MediaUploadDialog.vue'
import type { MediaAssetSummary, MediaType } from '@iroum/shared/types/api'

const { t } = useI18n()
const router = useRouter()

// ── 상태 ──────────────────────────────────────────────────────────────────────
const assets = ref<MediaAssetSummary[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchQuery = ref('')
const typeFilter = ref<MediaType | ''>('')
const tagQuery = ref('')
const showUpload = ref(false)

const debouncedSearch = useDebounce(searchQuery, 300)

// @MX:ANCHOR: [AUTO] loadAssets — onMounted, 페이지네이션, 필터, 검색 변경 시 호출
// @MX:REASON: fan_in >= 3: onMounted, onFilterChange, watch(debouncedSearch), onUploaded에서 공통 호출
async function loadAssets(): Promise<void> {
  loading.value = true
  try {
    const res = await mediaApi.list({
      page: currentPage.value - 1,
      size: pageSize.value,
      type: typeFilter.value || undefined,
      search: debouncedSearch.value || undefined,
      tags: tagQuery.value || undefined,
    })
    assets.value = res.data.content
    totalElements.value = res.data.totalElements
  } catch {
    ElMessage.error(t('media.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function onFilterChange(): void {
  currentPage.value = 1
  loadAssets()
}

watch(debouncedSearch, () => {
  currentPage.value = 1
  loadAssets()
})

function goDetail(uuid: string): void {
  router.push({ name: 'media-detail', params: { uuid } })
}

function onUploaded(): void {
  showUpload.value = false
  loadAssets()
}

async function handleDelete(asset: MediaAssetSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('media.confirm.delete', { name: asset.fileName }),
      t('media.action.delete'),
      { type: 'warning', confirmButtonText: t('media.action.delete'), cancelButtonText: t('common.cancel') },
    )
    await mediaApi.delete(asset.uuid)
    ElMessage.success(t('media.success.deleted'))
    loadAssets()
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
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  })
}

onMounted(loadAssets)
</script>
