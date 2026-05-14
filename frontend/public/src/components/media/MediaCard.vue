<!--
  SPEC-CMS-PUBLIC-001 T-009 — 미디어 카드 (D-06 lazy load)
  - IMAGE: <img loading="lazy" decoding="async" alt="...">
  - VIDEO: 썸네일 + 클릭 → modal with <video>
  - DOCUMENT: 다운로드 링크
-->
<template>
  <article
    class="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm hover:shadow-md focus-within:shadow-md"
    :data-testid="`media-card-${item.uuid}`"
  >
    <!-- IMAGE -->
    <template v-if="item.mediaType === 'IMAGE'">
      <img
        :src="item.thumbnailUrl ?? imageUrl"
        :alt="altLabel"
        loading="lazy"
        decoding="async"
        class="aspect-video w-full object-cover"
        data-testid="media-image"
      />
      <div class="px-3 py-2 text-sm text-content-DEFAULT">{{ item.fileName }}</div>
    </template>

    <!-- VIDEO -->
    <template v-else-if="item.mediaType === 'VIDEO'">
      <button
        type="button"
        class="block w-full text-left focus-visible:outline-2 focus-visible:outline-primary-600"
        :aria-label="t('media.openVideo', { name: item.fileName })"
        data-testid="media-video-open"
        @click="dialogOpen = true"
      >
        <div class="relative aspect-video w-full bg-gray-100">
          <img
            v-if="item.thumbnailUrl"
            :src="item.thumbnailUrl"
            :alt="altLabel"
            loading="lazy"
            decoding="async"
            class="h-full w-full object-cover"
            data-testid="media-video-thumbnail"
          />
          <div class="absolute inset-0 flex items-center justify-center">
            <span class="rounded-full bg-black/60 px-4 py-2 text-white" aria-hidden="true">▶</span>
          </div>
        </div>
        <div class="px-3 py-2 text-sm text-content-DEFAULT">{{ item.fileName }}</div>
      </button>

      <el-dialog
        v-model="dialogOpen"
        :title="item.fileName"
        :aria-label="item.fileName"
        width="min(90vw, 800px)"
        data-testid="media-video-modal"
      >
        <video
          v-if="dialogOpen"
          controls
          class="w-full"
          data-testid="media-video-player"
          :aria-label="altLabel"
        >
          <source :src="imageUrl" :type="item.mimeType" />
        </video>
      </el-dialog>
    </template>

    <!-- DOCUMENT -->
    <template v-else>
      <a
        :href="imageUrl"
        target="_blank"
        rel="noopener noreferrer"
        class="flex items-center gap-2 p-4 text-content-DEFAULT hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary-600"
        :aria-label="`${item.fileName} ${t('media.documentLink')}`"
        data-testid="media-document-link"
      >
        <span class="rounded bg-primary-100 px-2 py-1 text-xs font-bold text-primary-700">DOC</span>
        <span class="text-sm">{{ item.fileName }}</span>
      </a>
    </template>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDialog } from 'element-plus'
import type { MediaAssetSummary } from '@iroum/shared/types/api'

const props = defineProps<{
  item: MediaAssetSummary
}>()

const { t } = useI18n()
const dialogOpen = ref(false)

// 백엔드에서 url 필드를 따로 주지 않으므로 thumbnailUrl 을 기본으로 사용
// 실제 운영에선 별도 download API 호출 가능 — SPEC 단순화로 thumbnailUrl 폴백
const imageUrl = computed(() => props.item.thumbnailUrl ?? `/api/v1/media/${props.item.uuid}`)

const altLabel = computed(() => props.item.altText ?? props.item.fileName)
</script>
