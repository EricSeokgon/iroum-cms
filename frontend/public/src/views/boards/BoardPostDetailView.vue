<!--
  SPEC-CMS-PUBLIC-001 T-006 — 게시판 게시글 상세 화면
  - GET /posts/{id} (boardApi.post)
  - 본문 sanitize (NoticeContent 재사용)
  - 첨부 다운로드 (NoticeDetailView와 동일 패턴)
-->
<template>
  <section class="space-y-6">
    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadPost" />
    <article v-else-if="post" class="space-y-4">
      <header class="border-b border-gray-200 pb-4">
        <h1 class="text-2xl font-bold text-content-DEFAULT">{{ post.title }}</h1>
        <dl class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-content-muted">
          <div class="flex items-center gap-1">
            <dt class="sr-only">{{ t('common.author') }}</dt>
            <dd>{{ post.authorUsername }}</dd>
          </div>
          <div class="flex items-center gap-1">
            <dt class="sr-only">{{ t('common.createdAt') }}</dt>
            <dd>{{ formattedDate }}</dd>
          </div>
          <div class="flex items-center gap-1">
            <dt class="sr-only">{{ t('common.viewCount') }}</dt>
            <dd>{{ t('common.viewCount') }} {{ post.viewCount }}</dd>
          </div>
        </dl>
      </header>

      <NoticeContent :html="post.contentHtml" />

      <section
        v-if="post.attachments && post.attachments.length > 0"
        :aria-label="t('common.attachments')"
        class="rounded-md border border-gray-200 bg-surface-muted p-4"
        data-testid="attachment-section"
      >
        <h2 class="mb-2 text-sm font-bold text-content-DEFAULT">{{ t('common.attachments') }}</h2>
        <ul class="space-y-2">
          <li v-for="att in post.attachments" :key="att.id" class="flex items-center gap-2">
            <button
              type="button"
              class="flex items-center gap-2 text-sm text-primary-600 underline hover:text-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
              :disabled="downloadingId === att.id"
              data-testid="attachment-download"
              @click="downloadAttachment(att.id)"
            >
              {{ att.fileName }}
              <span class="text-xs text-content-muted">({{ formatSize(att.sizeBytes) }})</span>
            </button>
          </li>
        </ul>
      </section>

      <footer class="flex justify-end border-t border-gray-200 pt-4">
        <router-link
          :to="{ name: 'board-post-list', params: { code: routeCode } }"
          class="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary-600"
        >
          {{ t('common.back') }}
        </router-link>
      </footer>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { boardApi } from '@/api/boardApi'
import { apiClient } from '@/api/client'
import type { PostDetail, AttachmentDownloadUrl } from '@iroum/shared/types/api'
import NoticeContent from '@/components/notice/NoticeContent.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'

const { t } = useI18n()
const route = useRoute()

const post = ref<PostDetail | null>(null)
const loading = ref(false)
const error = ref(false)
const downloadingId = ref<number | null>(null)

const routeCode = computed(() => route.params.code as string)

const formattedDate = computed(() => {
  if (!post.value) return ''
  const raw = post.value.publishedAt ?? post.value.createdAt
  return raw ? raw.slice(0, 10) : ''
})

async function loadPost(): Promise<void> {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    error.value = true
    return
  }
  loading.value = true
  error.value = false
  try {
    post.value = await boardApi.post(id)
  } catch {
    error.value = true
    post.value = null
  } finally {
    loading.value = false
  }
}

function formatSize(bytes: number): string {
  if (!bytes || bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function downloadAttachment(attachmentId: number): Promise<void> {
  if (downloadingId.value !== null) return
  downloadingId.value = attachmentId
  try {
    const res = await apiClient.post<AttachmentDownloadUrl>(
      `/attachments/${attachmentId}/download-url`,
    )
    window.location.href = res.data.signedUrl
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const status = err.response?.status
      const code = err.response?.data?.code
      if (status === 403) {
        ElMessage.error(t('attachment.permissionError'))
      } else if (status === 423 || code === 'FILE_NOT_READY') {
        ElMessage.error(t('attachment.notReady'))
      } else {
        ElMessage.error(t('attachment.downloadError'))
      }
    } else {
      ElMessage.error(t('attachment.downloadError'))
    }
  } finally {
    downloadingId.value = null
  }
}

watch(() => route.params.id, loadPost)

onMounted(() => {
  loadPost()
})
</script>
