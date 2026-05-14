<!--
  SPEC-CMS-PUBLIC-001 T-009 — 미디어 갤러리 (D-06)
  - GET /api/v1/media?page=0&size=20
  - 타입 필터 (ALL/IMAGE/VIDEO/DOCUMENT)
  - MediaCard lazy load
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('media.title') }}</h1>
      <p class="mt-1 text-sm text-content-muted">{{ t('media.subtitle') }}</p>
    </header>

    <!-- 타입 필터 탭 -->
    <div
      role="tablist"
      :aria-label="t('media.title')"
      class="flex flex-wrap gap-1 border-b border-gray-200"
      data-testid="media-filter-tabs"
    >
      <button
        v-for="tab in typeTabs"
        :key="tab.value"
        type="button"
        role="tab"
        :aria-selected="currentType === tab.value"
        :data-testid="`media-tab-${tab.value}`"
        class="border-b-2 px-4 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-primary-600"
        :class="
          currentType === tab.value
            ? 'border-primary-600 text-primary-700'
            : 'border-transparent text-content-muted hover:text-content-DEFAULT'
        "
        @click="onTabChange(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <LoadingState v-if="loading" :rows="3" />
    <ErrorState v-else-if="error" @retry="loadMedia" />
    <ul
      v-else-if="items.length > 0"
      class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
      data-testid="media-grid"
    >
      <li v-for="item in items" :key="item.uuid">
        <MediaCard :item="item" />
      </li>
    </ul>
    <EmptyState v-else />
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { mediaApi } from '@/api/mediaApi'
import type { MediaAssetSummary } from '@iroum/shared/types/api'
import MediaCard from '@/components/media/MediaCard.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

type MediaTab = 'ALL' | 'IMAGE' | 'VIDEO' | 'DOCUMENT'

const { t } = useI18n()
const items = ref<MediaAssetSummary[]>([])
const loading = ref(false)
const error = ref(false)
const currentType = ref<MediaTab>('ALL')

const typeTabs = computed<Array<{ value: MediaTab; label: string }>>(() => [
  { value: 'ALL', label: t('media.typeAll') },
  { value: 'IMAGE', label: t('media.typeImage') },
  { value: 'VIDEO', label: t('media.typeVideo') },
  { value: 'DOCUMENT', label: t('media.typeDocument') },
])

async function loadMedia(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    const params: Parameters<typeof mediaApi.list>[0] = { page: 0, size: 20 }
    if (currentType.value !== 'ALL') {
      params.type = currentType.value as 'IMAGE' | 'VIDEO' | 'DOCUMENT'
    }
    const res = await mediaApi.list(params)
    items.value = res.content
  } catch {
    error.value = true
    items.value = []
  } finally {
    loading.value = false
  }
}

function onTabChange(next: MediaTab): void {
  currentType.value = next
  loadMedia()
}

onMounted(() => {
  loadMedia()
})
</script>
