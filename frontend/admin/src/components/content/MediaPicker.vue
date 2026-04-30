<template>
  <!-- 미디어 라이브러리 선택 모달 — SPEC-CMS-004 ContentBlock IMAGE 지원 -->
  <el-dialog
    v-model="visible"
    :title="t('content.mediaPicker.title')"
    width="860px"
    :close-on-click-modal="false"
    :aria-label="t('content.mediaPicker.title')"
  >
    <!-- 검색 -->
    <div class="mb-4 flex gap-2">
      <el-input
        v-model="search"
        :placeholder="t('content.mediaPicker.search')"
        clearable
        @keyup.enter="loadMedia"
        class="flex-1"
      />
      <el-button @click="loadMedia">{{ t('common.search') }}</el-button>
    </div>

    <!-- 미디어 그리드 -->
    <div v-loading="loading" class="grid grid-cols-4 gap-3 min-h-48">
      <div
        v-for="item in mediaList"
        :key="item.uuid"
        class="cursor-pointer rounded border-2 p-1 hover:border-blue-500 transition-colors"
        :class="selected?.uuid === item.uuid ? 'border-blue-500 bg-blue-50' : 'border-gray-200'"
        @click="selected = item"
        :aria-label="`${t('content.mediaPicker.select')}: ${item.originalName}`"
        tabindex="0"
        @keyup.enter="selected = item"
        role="option"
        :aria-selected="selected?.uuid === item.uuid"
      >
        <img
          v-if="item.mimeType?.startsWith('image/')"
          :src="item.url"
          :alt="item.altText || item.originalName"
          class="h-20 w-full object-cover rounded"
        />
        <div v-else class="flex h-20 items-center justify-center bg-gray-100 rounded text-xs text-gray-500">
          {{ item.mimeType }}
        </div>
        <p class="mt-1 truncate text-xs text-gray-600">{{ item.originalName }}</p>
      </div>

      <div v-if="!loading && mediaList.length === 0" class="col-span-4 flex items-center justify-center text-gray-400 py-8">
        {{ t('content.mediaPicker.empty') }}
      </div>
    </div>

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-center">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadMedia"
        :aria-label="t('a11y.pagination')"
        small
      />
    </div>

    <template #footer>
      <div class="flex items-center justify-between">
        <span v-if="selected" class="text-sm text-gray-600">
          {{ t('content.mediaPicker.selected') }}: {{ selected.originalName }}
        </span>
        <div class="flex gap-2 ml-auto">
          <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :disabled="!selected" @click="confirm">
            {{ t('content.mediaPicker.confirm') }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { mediaApi } from '@/api/media'

const { t } = useI18n()

interface MediaItem {
  uuid: string
  originalName: string
  url: string
  mimeType?: string
  altText?: string
}

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'select', item: { url: string; alt: string; name: string }): void
}>()

const visible = ref(props.modelValue)
const mediaList = ref<MediaItem[]>([])
const selected = ref<MediaItem | null>(null)
const search = ref('')
const page = ref(1)
const pageSize = 12
const total = ref(0)
const loading = ref(false)

watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => emit('update:modelValue', val))

async function loadMedia(): Promise<void> {
  loading.value = true
  try {
    const res = await mediaApi.list({ search: search.value, page: page.value - 1, size: pageSize })
    const data = res.data
    // PageResponse 구조 처리
    if (Array.isArray(data)) {
      mediaList.value = data as unknown as MediaItem[]
      total.value = (data as unknown as MediaItem[]).length
    } else {
      const pr = data as unknown as { content: MediaItem[]; totalElements: number }
      mediaList.value = pr.content ?? []
      total.value = pr.totalElements ?? 0
    }
  } catch {
    mediaList.value = []
  } finally {
    loading.value = false
  }
}

watch(visible, (val) => {
  if (val) {
    selected.value = null
    loadMedia()
  }
})

function confirm(): void {
  if (!selected.value) return
  emit('select', {
    url: selected.value.url,
    alt: selected.value.altText || selected.value.originalName,
    name: selected.value.originalName,
  })
  visible.value = false
}
</script>
