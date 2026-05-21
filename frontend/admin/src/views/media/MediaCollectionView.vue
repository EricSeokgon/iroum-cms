<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('media.collections.title') }}</h2>
      <el-button type="primary" @click="showCreate = true">
        + {{ t('media.collections.create') }}
      </el-button>
    </div>

    <div v-loading="loading" class="min-h-[200px]">
      <el-table
        :data="collections"
        stripe
        :empty-text="t('media.collections.empty')"
        :aria-label="t('media.collections.title')"
        class="w-full"
      >
        <el-table-column prop="name" :label="t('media.collections.field.name')" min-width="180" />
        <el-table-column prop="description" :label="t('media.collections.field.description')" min-width="260">
          <template #default="{ row }">
            {{ row.description ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="itemCount" :label="t('media.collections.field.itemCount')" width="100" />
        <el-table-column prop="createdAt" :label="t('media.collections.field.createdAt')" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              plain
              :aria-label="`${t('media.collections.manage')}: ${row.name}`"
              @click="openDetail(row)"
            >
              {{ t('media.collections.manage') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 컬렉션 생성 다이얼로그 -->
    <el-dialog
      v-model="showCreate"
      :title="t('media.collections.create')"
      width="420px"
      :aria-label="t('media.collections.create')"
    >
      <el-form :model="createForm" label-width="100px">
        <el-form-item :label="t('media.collections.field.name')" required>
          <el-input v-model="createForm.name" :aria-label="t('media.collections.field.name')" aria-required="true" />
        </el-form-item>
        <el-form-item :label="t('media.collections.field.description')">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="2"
            :aria-label="t('media.collections.field.description')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 컬렉션 상세 편집 다이얼로그 -->
    <el-dialog
      v-model="showDetail"
      :title="detailCollection?.name ?? ''"
      width="760px"
      :aria-label="t('media.collections.manage')"
      @close="detailCollection = null"
    >
      <div v-loading="detailLoading" class="min-h-[200px]">
        <!-- 아이템 그리드 -->
        <div
          v-if="detailCollection && detailCollection.items.length > 0"
          class="grid grid-cols-4 gap-3 mb-4"
        >
          <div
            v-for="item in detailCollection.items"
            :key="item.uuid"
            class="relative group rounded border border-gray-200 p-1"
          >
            <img
              v-if="item.mimeType?.startsWith('image/')"
              :src="item.thumbnailUrl ?? ''"
              :alt="item.altText ?? item.fileName"
              class="h-20 w-full object-cover rounded"
            />
            <div
              v-else
              class="h-20 flex items-center justify-center bg-gray-100 rounded text-xs text-gray-500 text-center px-1"
            >
              {{ item.fileName }}
            </div>
            <el-button
              size="small"
              type="danger"
              class="absolute top-1 right-1 opacity-0 group-hover:opacity-100 transition-opacity"
              :aria-label="`${t('media.collections.removeItem')}: ${item.fileName}`"
              @click="handleRemoveItem(item.uuid)"
            >
              ×
            </el-button>
          </div>
        </div>

        <el-empty
          v-else-if="detailCollection && detailCollection.items.length === 0"
          :description="t('media.collections.empty')"
          :image-size="80"
        />
      </div>

      <template #footer>
        <div class="flex justify-between">
          <el-popconfirm
            :title="t('media.collections.confirmDelete')"
            :confirm-button-text="t('common.delete')"
            :cancel-button-text="t('common.cancel')"
            @confirm="handleDeleteCollection"
          >
            <template #reference>
              <el-button type="danger" plain :aria-label="t('media.collections.delete')">
                {{ t('media.collections.delete') }}
              </el-button>
            </template>
          </el-popconfirm>
          <div class="flex gap-2">
            <el-button @click="showDetail = false">{{ t('common.close') }}</el-button>
            <el-button
              type="primary"
              :loading="addingItems"
              :aria-label="t('media.collections.addItems')"
              @click="openMediaPickerForAdd"
            >
              {{ t('media.collections.addItems') }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 미디어 선택 다이얼로그 (아이템 추가용) -->
    <el-dialog
      v-model="showMediaPicker"
      :title="t('media.collections.selectMedia')"
      width="900px"
      :aria-label="t('media.collections.selectMedia')"
    >
      <div v-loading="pickerLoading" class="min-h-[300px]">
        <div class="mb-3 flex gap-2">
          <el-input
            v-model="pickerSearch"
            :placeholder="t('common.search')"
            clearable
            class="flex-1"
            @keyup.enter="loadPickerMedia"
          />
          <el-button @click="loadPickerMedia">{{ t('common.search') }}</el-button>
        </div>
        <div class="grid grid-cols-4 gap-3">
          <div
            v-for="asset in pickerAssets"
            :key="asset.uuid"
            class="cursor-pointer rounded border-2 p-1 hover:border-blue-500 transition-colors"
            :class="pickerSelected.includes(asset.uuid) ? 'border-blue-500 bg-blue-50' : 'border-gray-200'"
            :aria-label="`${t('media.collections.selectMedia')}: ${asset.fileName}`"
            tabindex="0"
            role="option"
            :aria-selected="pickerSelected.includes(asset.uuid)"
            @click="togglePickerSelect(asset.uuid)"
            @keyup.enter="togglePickerSelect(asset.uuid)"
          >
            <img
              v-if="asset.mimeType?.startsWith('image/')"
              :src="asset.thumbnailUrl ?? ''"
              :alt="asset.altText ?? asset.fileName"
              class="h-20 w-full object-cover rounded"
            />
            <div
              v-else
              class="h-20 flex items-center justify-center bg-gray-100 rounded text-xs text-gray-500 text-center px-1"
            >
              {{ asset.fileName }}
            </div>
            <div
              v-if="pickerSelected.includes(asset.uuid)"
              class="text-xs text-center text-blue-600 mt-1 font-medium"
            >
              ✓
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="text-sm text-gray-500 mr-2">
          {{ t('media.collections.selectedCount', { count: pickerSelected.length }) }}
        </span>
        <el-button @click="showMediaPicker = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="addingItems"
          :disabled="pickerSelected.length === 0"
          @click="handleAddItems"
        >
          {{ t('media.collections.addItems') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { mediaApi } from '@/api/media'
import type {
  MediaCollectionSummary,
  MediaCollectionDetail,
  MediaAssetSummary,
} from '@iroum/shared/types/api'

const { t } = useI18n()

// ── 목록 ────────────────────────────────────────────────────────────────────
const collections = ref<MediaCollectionSummary[]>([])
const loading = ref(false)
const showCreate = ref(false)
const creating = ref(false)

const createForm = ref({ name: '', description: '' })

async function loadCollections(): Promise<void> {
  loading.value = true
  try {
    const res = await mediaApi.listCollections()
    collections.value = res.data
  } catch {
    ElMessage.error(t('media.collections.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function handleCreate(): Promise<void> {
  if (!createForm.value.name.trim()) return
  creating.value = true
  try {
    await mediaApi.createCollection(createForm.value.name, createForm.value.description || undefined)
    ElMessage.success(t('media.collections.success.created'))
    showCreate.value = false
    createForm.value = { name: '', description: '' }
    await loadCollections()
  } catch {
    ElMessage.error(t('media.collections.error.createFailed'))
  } finally {
    creating.value = false
  }
}

// ── 상세 편집 ────────────────────────────────────────────────────────────────
const showDetail = ref(false)
const detailLoading = ref(false)
const detailCollection = ref<MediaCollectionDetail | null>(null)

async function openDetail(row: MediaCollectionSummary): Promise<void> {
  showDetail.value = true
  detailLoading.value = true
  try {
    const res = await mediaApi.getCollection(row.id)
    detailCollection.value = res.data
  } catch {
    ElMessage.error(t('media.collections.error.loadFailed'))
    showDetail.value = false
  } finally {
    detailLoading.value = false
  }
}

async function handleRemoveItem(assetUuid: string): Promise<void> {
  if (!detailCollection.value) return
  try {
    await mediaApi.removeFromCollection(detailCollection.value.id, assetUuid)
    // 로컬 상태에서 아이템 제거 (서버 재조회 불필요)
    detailCollection.value = {
      ...detailCollection.value,
      items: detailCollection.value.items.filter((i) => i.uuid !== assetUuid),
    }
    await loadCollections()
  } catch {
    ElMessage.error(t('media.collections.error.removeFailed'))
  }
}

async function handleDeleteCollection(): Promise<void> {
  if (!detailCollection.value) return
  try {
    await mediaApi.deleteCollection(detailCollection.value.id)
    ElMessage.success(t('media.collections.success.deleted'))
    showDetail.value = false
    detailCollection.value = null
    await loadCollections()
  } catch {
    ElMessage.error(t('media.collections.error.deleteFailed'))
  }
}

// ── 미디어 피커 (아이템 추가) ─────────────────────────────────────────────
const showMediaPicker = ref(false)
const pickerLoading = ref(false)
const pickerSearch = ref('')
const pickerAssets = ref<MediaAssetSummary[]>([])
const pickerSelected = ref<string[]>([])
const addingItems = ref(false)

function openMediaPickerForAdd(): void {
  pickerSelected.value = []
  pickerSearch.value = ''
  pickerAssets.value = []
  showMediaPicker.value = true
  loadPickerMedia()
}

async function loadPickerMedia(): Promise<void> {
  pickerLoading.value = true
  try {
    const res = await mediaApi.list({ search: pickerSearch.value || undefined, size: 24 })
    pickerAssets.value = res.data.content
  } catch {
    ElMessage.error(t('media.collections.error.loadFailed'))
  } finally {
    pickerLoading.value = false
  }
}

function togglePickerSelect(uuid: string): void {
  const idx = pickerSelected.value.indexOf(uuid)
  if (idx === -1) {
    pickerSelected.value.push(uuid)
  } else {
    pickerSelected.value.splice(idx, 1)
  }
}

async function handleAddItems(): Promise<void> {
  if (!detailCollection.value || pickerSelected.value.length === 0) return
  addingItems.value = true
  try {
    await mediaApi.addToCollection(detailCollection.value.id, pickerSelected.value)
    // 상세 다시 로드 (추가된 아이템 메타데이터 동기화)
    const res = await mediaApi.getCollection(detailCollection.value.id)
    detailCollection.value = res.data
    showMediaPicker.value = false
    await loadCollections()
    ElMessage.success(t('media.collections.success.itemsAdded'))
  } catch {
    ElMessage.error(t('media.collections.error.addFailed'))
  } finally {
    addingItems.value = false
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

onMounted(loadCollections)
</script>
