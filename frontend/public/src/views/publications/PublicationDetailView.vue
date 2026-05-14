<!--
  SPEC-CMS-PUBLIC-001 T-007 — 발간자료 상세 (다중 첨부 zip 다운로드)
  AC: C-08

  - GET /api/v1/publications/{id}
  - 첨부 체크박스 다중 선택 → POST /api/v1/posts/{id}/download-zip { attachmentIds: [...] }
    - 응답 < 50MB: blob 으로 즉시 다운로드 (window.URL.createObjectURL → click → revoke)
    - 응답 jobId 보유: 비동기 처리 — "준비 중입니다. 완료 시 알림이 발송됩니다" 토스트
    - 400 응답: "500MB를 초과합니다" 토스트
-->
<template>
  <section class="space-y-6">
    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadDetail" />
    <article v-else-if="publication" class="space-y-4">
      <header class="border-b border-gray-200 pb-4">
        <h1 class="text-2xl font-bold text-content-DEFAULT">{{ publication.title }}</h1>
        <dl class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-content-muted">
          <div class="flex items-center gap-1">
            <dt>{{ t('publication.publicationYear') }}:</dt>
            <dd>{{ publication.publicationYear }}</dd>
          </div>
          <div class="flex items-center gap-1">
            <dt>{{ t('publication.documentType') }}:</dt>
            <dd>{{ publication.documentType }}</dd>
          </div>
          <div class="flex items-center gap-1">
            <dt>{{ t('publication.downloadCount') }}:</dt>
            <dd>{{ publication.downloadCount }}</dd>
          </div>
        </dl>
      </header>

      <NoticeContent v-if="publication.descriptionHtml" :html="publication.descriptionHtml" />

      <section
        v-if="publication.attachments.length > 0"
        :aria-label="t('publication.selectAttachments')"
        class="rounded-md border border-gray-200 bg-surface-muted p-4"
        data-testid="publication-attachments"
      >
        <h2 class="mb-3 text-sm font-bold text-content-DEFAULT">
          {{ t('publication.selectAttachments') }}
        </h2>
        <ul class="space-y-2">
          <li v-for="att in publication.attachments" :key="att.id" class="flex items-center gap-2">
            <input
              :id="`att-${att.id}`"
              type="checkbox"
              :value="att.id"
              v-model="selectedIds"
              class="h-4 w-4 rounded border-gray-300 text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
              :data-testid="`publication-attachment-${att.id}`"
            />
            <label :for="`att-${att.id}`" class="flex flex-1 items-center gap-2 text-sm">
              <span>{{ att.fileName }}</span>
              <span class="text-xs text-content-muted">({{ formatSize(att.sizeBytes) }})</span>
            </label>
          </li>
        </ul>

        <div class="mt-4 flex justify-end">
          <button
            type="button"
            class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-primary-600"
            data-testid="publication-download-zip"
            :disabled="downloading || selectedIds.length === 0"
            @click="onDownloadZip"
          >
            {{ t('publication.downloadSelected') }}
          </button>
        </div>
      </section>
      <p v-else class="text-sm text-content-muted">{{ t('publication.noAttachments') }}</p>

      <footer class="flex justify-end border-t border-gray-200 pt-4">
        <router-link
          :to="{ name: 'publication-list' }"
          class="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary-600"
        >
          {{ t('common.back') }}
        </router-link>
      </footer>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { apiClient } from '@/api/client'
import type { PublicationDetail } from '@/api/publicationTypes'
import NoticeContent from '@/components/notice/NoticeContent.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'

const { t } = useI18n()
const route = useRoute()

const publication = ref<PublicationDetail | null>(null)
const loading = ref(false)
const error = ref(false)
const downloading = ref(false)
const selectedIds = ref<number[]>([])

async function loadDetail(): Promise<void> {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    error.value = true
    return
  }
  loading.value = true
  error.value = false
  try {
    const res = await apiClient.get<PublicationDetail>(`/publications/${id}`)
    publication.value = res.data
  } catch {
    error.value = true
    publication.value = null
  } finally {
    loading.value = false
  }
}

function formatSize(bytes: number): string {
  if (!bytes || bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

// Blob 에서 텍스트를 안전하게 읽는 헬퍼 (jsdom 호환 — Blob.text() 가 없을 수 있음)
async function readBlobAsText(blob: Blob): Promise<string> {
  if (typeof (blob as Blob & { text?: () => Promise<string> }).text === 'function') {
    return (blob as Blob & { text: () => Promise<string> }).text()
  }
  // 폴백: FileReader (브라우저 및 jsdom 모두 지원)
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '')
    reader.onerror = () => reject(reader.error)
    reader.readAsText(blob)
  })
}

// @MX:NOTE: [AUTO] zip 다운로드 — blob 응답과 비동기 jobId 응답을 분기 처리
async function onDownloadZip(): Promise<void> {
  if (!publication.value) return
  if (selectedIds.value.length === 0) {
    ElMessage.warning(t('publication.selectAtLeastOne'))
    return
  }
  const publicationId = publication.value.id
  downloading.value = true
  try {
    const res = await apiClient.post(
      `/posts/${publicationId}/download-zip`,
      { attachmentIds: selectedIds.value },
      { responseType: 'blob' },
    )
    // 응답 타입 분기: Blob 인 경우 즉시 다운로드, JSON(jobId) 인 경우 비동기 안내
    const data: unknown = res.data
    if (data instanceof Blob) {
      // application/json 타입 Blob 은 비동기 jobId 응답일 수 있음 — 텍스트로 파싱 시도
      const isJsonBlob = data.type === 'application/json'
      if (isJsonBlob) {
        const text = await readBlobAsText(data)
        let parsed: { jobId?: string } | null = null
        try {
          parsed = JSON.parse(text)
        } catch {
          parsed = null
        }
        if (parsed && typeof parsed.jobId === 'string') {
          ElMessage.info(t('publication.downloadAsyncPending'))
        } else {
          ElMessage.error(t('publication.downloadError'))
        }
      } else {
        // 실제 zip 바이너리 — 다운로드 트리거
        const url = window.URL.createObjectURL(data)
        const link = document.createElement('a')
        link.href = url
        link.download = `publication-${publicationId}.zip`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
      }
    } else if (data && typeof data === 'object' && 'jobId' in (data as Record<string, unknown>)) {
      ElMessage.info(t('publication.downloadAsyncPending'))
    } else {
      // 알 수 없는 응답 — 에러 처리
      ElMessage.error(t('publication.downloadError'))
    }
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const status = err.response?.status
      if (status === 400) {
        ElMessage.error(t('publication.downloadTooLarge'))
      } else {
        ElMessage.error(t('publication.downloadError'))
      }
    } else {
      ElMessage.error(t('publication.downloadError'))
    }
  } finally {
    downloading.value = false
  }
}

watch(() => route.params.id, loadDetail)

onMounted(() => {
  loadDetail()
})
</script>
