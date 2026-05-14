<template>
  <div>
    <!-- 로딩 상태 -->
    <div v-if="loading" v-loading="true" class="h-48" />

    <!-- 게시글 콘텐츠 -->
    <template v-else-if="post">
      <!-- 뒤로가기 + 액션 -->
      <div class="mb-4 flex items-center justify-between">
        <el-button
          :icon="ElIconArrowLeft"
          plain
          :aria-label="t('common.back')"
          @click="router.back()"
        >
          {{ t('common.back') }}
        </el-button>
        <div v-if="canEdit" class="flex gap-2">
          <el-button
            type="primary"
            plain
            :aria-label="t('board.posts.edit')"
            @click="goEdit"
          >
            {{ t('board.posts.edit') }}
          </el-button>
          <el-button
            type="danger"
            plain
            :aria-label="t('board.posts.delete')"
            @click="handleDelete"
          >
            {{ t('board.posts.delete') }}
          </el-button>
        </div>
      </div>

      <!-- 게시글 본문 카드 -->
      <el-card class="mb-4">
        <!-- 제목 -->
        <div class="mb-3 border-b border-gray-100 pb-3">
          <div class="flex items-center gap-2">
            <el-tag v-if="post.isNotice" type="danger" size="small">
              {{ t('board.posts.notice') }}
            </el-tag>
            <h1 class="text-xl font-semibold text-gray-900">{{ post.title }}</h1>
          </div>
        </div>

        <!-- 메타 정보 -->
        <dl class="mb-4 flex flex-wrap gap-x-6 gap-y-1 text-sm text-gray-500">
          <div class="flex gap-1">
            <dt class="font-medium">{{ t('board.posts.field.author') }}:</dt>
            <dd>{{ post.authorUsername }}</dd>
          </div>
          <div class="flex gap-1">
            <dt class="font-medium">{{ t('board.posts.field.viewCount') }}:</dt>
            <dd>{{ post.viewCount.toLocaleString() }}</dd>
          </div>
          <div class="flex gap-1">
            <dt class="font-medium">{{ t('board.posts.field.createdAt') }}:</dt>
            <dd>{{ formatDate(post.createdAt) }}</dd>
          </div>
        </dl>

        <!-- 첨부파일 목록 -->
        <div v-if="post.attachments.length > 0" class="mb-4">
          <p class="mb-2 text-sm font-medium text-gray-700">
            {{ t('board.posts.field.attachments') }} ({{ post.attachments.length }})
          </p>
          <ul class="space-y-1">
            <li
              v-for="att in post.attachments"
              :key="att.id"
              class="flex items-center gap-2 text-sm"
            >
              <el-icon class="text-blue-500"><i-ep-paperclip /></el-icon>
              <span class="text-gray-700">{{ att.fileName }}</span>
              <span class="text-xs text-gray-400">({{ formatBytes(att.sizeBytes) }})</span>
              <el-button
                size="small"
                type="primary"
                plain
                :aria-label="`${t('board.posts.download')} ${att.fileName}`"
                :loading="downloadingId === att.id"
                @click="downloadAttachment(att)"
              >
                {{ t('board.posts.download') }}
              </el-button>
            </li>
          </ul>
        </div>

        <!-- 본문 — v-html: 백엔드에서 OWASP HTML Sanitizer 처리됨 -->
        <!-- @MX:WARN: [AUTO] v-html XSS 위험 — 반드시 백엔드 sanitize된 콘텐츠만 렌더링 -->
        <!-- @MX:REASON: contentHtml은 서버측 OWASP Sanitizer 처리 후 응답됨 (SPEC-CMS-003 §3.1) -->
        <div
          class="prose prose-sm max-w-none text-gray-800"
          v-html="sanitize(post.contentHtml)"
          role="article"
          :aria-label="t('board.posts.content')"
        />
      </el-card>

      <!-- 댓글 섹션 -->
      <PostCommentSection :post-id="post.id" />
    </template>

    <!-- 404 상태 -->
    <el-empty
      v-else
      :description="t('board.posts.error.notFound')"
      :image-size="120"
      class="mt-8"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft as ElIconArrowLeft } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { boardApi } from '@/api/board'
import { useSafeHtml } from '@/composables/useSafeHtml'
import PostCommentSection from '@/components/PostCommentSection.vue'
import type { PostDetail, AttachmentSummary } from '@iroum/shared/types/api'

const { sanitize } = useSafeHtml()

interface Props {
  id: string
}
const props = defineProps<Props>()

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const post = ref<PostDetail | null>(null)
const loading = ref(false)
const downloadingId = ref<number | null>(null)

const canEdit = computed(() => {
  if (!post.value || !auth.user) return false
  const isAdmin = auth.user.roleCodes?.includes('SUPER_ADMIN') || auth.user.roleCodes?.includes('DEPT_ADMIN')
  return isAdmin || post.value.authorUsername === auth.user.username
})

async function loadPost(): Promise<void> {
  loading.value = true
  try {
    const res = await boardApi.getPost(Number(props.id))
    post.value = res.data
  } catch {
    ElMessage.error(t('board.posts.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function downloadAttachment(att: AttachmentSummary): Promise<void> {
  downloadingId.value = att.id
  try {
    const res = await boardApi.getAttachmentUrl(att.id)
    window.open(res.data.signedUrl, '_blank', 'noopener')
  } catch {
    ElMessage.error(t('board.posts.error.downloadFailed'))
  } finally {
    downloadingId.value = null
  }
}

function goEdit(): void {
  router.push({ name: 'board-post-edit', params: { id: props.id } })
}

async function handleDelete(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('board.posts.confirm.delete'),
      t('board.posts.delete'),
      { type: 'warning', confirmButtonText: t('board.posts.delete'), cancelButtonText: t('common.cancel') },
    )
    await boardApi.deletePost(Number(props.id))
    ElMessage.success(t('board.posts.success.deleted'))
    router.back()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('board.posts.error.deleteFailed'))
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

onMounted(() => {
  loadPost()
})
</script>
