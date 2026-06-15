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

      <!-- 본문 / 버전 히스토리 탭 -->
      <el-tabs v-model="activeTab" class="mb-2">
        <el-tab-pane :label="t('board.posts.content')" name="content" />
        <el-tab-pane
          :label="t('board.posts.postHistory.tab')"
          name="history"
        />
      </el-tabs>

      <!-- 게시글 본문 카드 -->
      <el-card v-show="activeTab === 'content'" class="mb-4">
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

        <!-- 태그 칩 (읽기 전용) — SPEC-CMS-AI-004 REQ-AI-TAG-015 -->
        <div v-if="post.tags && post.tags.length > 0" class="mb-4 flex flex-wrap items-center gap-1">
          <span class="mr-1 text-sm font-medium text-gray-500">{{ t('board.posts.field.tags') }}:</span>
          <el-tag
            v-for="tag in post.tags"
            :key="tag"
            size="small"
            type="info"
          >{{ tag }}</el-tag>
        </div>

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

      <!-- 버전 히스토리 탭 (read-only 뷰어 — 복원/편집/삭제 컨트롤 없음, REQ-PH-010) -->
      <div v-show="activeTab === 'history'" class="mb-4">
        <el-empty
          v-if="!historyLoading && historyItems.length === 0"
          :description="t('board.posts.postHistory.empty')"
          :image-size="100"
        />
        <template v-else>
          <el-table
            v-loading="historyLoading"
            :data="historyItems"
            border
            highlight-current-row
            :aria-label="t('board.posts.postHistory.tab')"
            @row-click="selectVersion"
          >
            <el-table-column
              prop="version"
              :label="t('board.posts.postHistory.field.version')"
              width="100"
            />
            <el-table-column :label="t('board.posts.postHistory.field.editor')">
              <template #default="{ row }">
                {{ row.editorName ?? t('board.posts.postHistory.unknownEditor') }}
              </template>
            </el-table-column>
            <el-table-column :label="t('board.posts.postHistory.field.editedAt')" width="180">
              <template #default="{ row }">{{ formatDate(row.editedAt) }}</template>
            </el-table-column>
            <el-table-column
              prop="editReason"
              :label="t('board.posts.postHistory.field.editReason')"
            />
          </el-table>

          <el-pagination
            v-if="historyTotal > historyPageSize"
            class="mt-3 justify-end"
            layout="prev, pager, next"
            :total="historyTotal"
            :page-size="historyPageSize"
            :current-page="historyPage + 1"
            @current-change="onHistoryPageChange"
          />

          <!-- 선택한 버전 본문 (읽기 전용) -->
          <el-card v-if="selectedVersion" class="mt-4">
            <template #header>
              <span class="text-sm font-medium text-gray-700">
                {{ t('board.posts.postHistory.versionContent', { version: selectedVersion.version }) }}
              </span>
            </template>
            <h2 class="mb-3 text-lg font-semibold text-gray-900">{{ selectedVersion.title }}</h2>
            <!-- @MX:WARN: [AUTO] v-html XSS 위험 — 백엔드 OWASP sanitize된 스냅샷만 렌더링 -->
            <!-- @MX:REASON: content_html은 저장 시점 서버측 Sanitizer 처리 후 적재됨 (SPEC-CMS-003 §3.1) -->
            <div
              class="prose prose-sm max-w-none text-gray-800"
              v-html="sanitize(selectedVersion.contentHtml)"
              role="article"
              :aria-label="t('board.posts.postHistory.content')"
            />
          </el-card>
        </template>
      </div>

      <!-- 댓글 섹션 — 게시판 마스터의 useComment 허용 시에만 표시 (본문 탭 한정) -->
      <PostCommentSection
        v-if="post.useComment && activeTab === 'content'"
        :post-id="post.id"
      />
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
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft as ElIconArrowLeft } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { boardApi } from '@/api/board'
import { useSafeHtml } from '@/composables/useSafeHtml'
import PostCommentSection from '@/components/PostCommentSection.vue'
import type {
  PostDetail,
  AttachmentSummary,
  PostHistoryItem,
  PostHistoryDetail,
} from '@iroum/shared/types/api'

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

// ── 버전 히스토리 (SPEC-CMS-POST-HISTORY-001, read-only) ──────────────────────
const activeTab = ref<'content' | 'history'>('content')
const historyItems = ref<PostHistoryItem[]>([])
const historyTotal = ref(0)
const historyPage = ref(0)
const historyPageSize = ref(20)
const historyLoading = ref(false)
const historyLoaded = ref(false)
const selectedVersion = ref<PostHistoryDetail | null>(null)

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

async function loadHistory(): Promise<void> {
  historyLoading.value = true
  try {
    const res = await boardApi.getPostHistory(Number(props.id), historyPage.value, historyPageSize.value)
    historyItems.value = res.data.content
    historyTotal.value = res.data.totalElements
    historyLoaded.value = true
  } catch {
    ElMessage.error(t('board.posts.postHistory.loadFailed'))
  } finally {
    historyLoading.value = false
  }
}

async function selectVersion(row: PostHistoryItem): Promise<void> {
  try {
    const res = await boardApi.getPostVersion(Number(props.id), row.version)
    selectedVersion.value = res.data
  } catch {
    ElMessage.error(t('board.posts.postHistory.loadFailed'))
  }
}

function onHistoryPageChange(oneBasedPage: number): void {
  historyPage.value = oneBasedPage - 1
  selectedVersion.value = null
  loadHistory()
}

// 히스토리 탭 최초 진입 시에만 목록 로드 (지연 로딩)
watch(activeTab, (tab) => {
  if (tab === 'history' && !historyLoaded.value) {
    loadHistory()
  }
})

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
