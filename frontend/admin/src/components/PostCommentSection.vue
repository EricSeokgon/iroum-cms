<template>
  <el-card>
    <template #header>
      <span class="font-semibold text-gray-700">
        {{ t('board.comments.title') }} ({{ totalCount }})
      </span>
    </template>

    <!-- 댓글 목록 -->
    <div v-if="comments.length > 0" class="space-y-4 mb-6">
      <template v-for="comment in comments" :key="comment.id">
        <div class="border-b border-gray-100 pb-3">
          <!-- 최상위 댓글 -->
          <div class="flex flex-col gap-1">
            <div class="flex items-center gap-2 text-sm">
              <span class="font-medium text-gray-700">{{ comment.authorUsername }}</span>
              <span class="text-xs text-gray-400">{{ formatDate(comment.createdAt) }}</span>
            </div>
            <!-- 수정 모드 -->
            <template v-if="editTargetId === comment.id">
              <textarea
                v-model="editContent"
                class="w-full rounded border border-gray-200 p-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400"
                rows="3"
              />
              <div class="flex gap-2 text-xs mt-1">
                <button
                  class="rounded bg-blue-600 px-3 py-1 text-white hover:bg-blue-700 disabled:opacity-50"
                  :disabled="saving"
                  @click="handleEditSubmit(comment.id)"
                >{{ t('common.save') }}</button>
                <button
                  class="rounded border border-gray-200 px-3 py-1 text-gray-600 hover:bg-gray-50"
                  @click="cancelEdit"
                >{{ t('common.cancel') }}</button>
              </div>
            </template>
            <!-- 일반 모드 -->
            <template v-else>
              <p class="text-sm text-gray-800 whitespace-pre-wrap">{{ comment.content }}</p>
              <div class="flex gap-2 text-xs mt-1">
                <button
                  class="text-blue-500 hover:underline"
                  @click="startReply(comment.id)"
                >{{ replyTargetId === comment.id ? t('board.comments.cancelReply') : t('board.comments.reply') }}</button>
                <button
                  v-if="canModify(comment)"
                  class="text-gray-500 hover:underline"
                  @click="startEdit(comment)"
                >{{ t('common.edit') }}</button>
                <button
                  v-if="canModify(comment)"
                  class="text-red-500 hover:underline"
                  @click="handleDeleteComment(comment.id)"
                >{{ t('common.delete') }}</button>
              </div>
            </template>
          </div>

          <!-- 대댓글 목록 (1단계) -->
          <div v-if="comment.children && comment.children.length > 0" class="ml-8 mt-3 space-y-3">
            <div v-for="child in comment.children" :key="child.id" class="flex flex-col gap-1">
              <div class="flex items-center gap-2 text-sm">
                <span class="text-gray-400" aria-hidden="true">↳</span>
                <span class="font-medium text-gray-700">{{ child.authorUsername }}</span>
                <span class="text-xs text-gray-400">{{ formatDate(child.createdAt) }}</span>
              </div>
              <!-- 대댓글 수정 모드 -->
              <template v-if="editTargetId === child.id">
                <textarea
                  v-model="editContent"
                  class="w-full rounded border border-gray-200 p-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400 ml-4"
                  rows="3"
                />
                <div class="flex gap-2 text-xs ml-4 mt-1">
                  <button
                    class="rounded bg-blue-600 px-3 py-1 text-white hover:bg-blue-700 disabled:opacity-50"
                    :disabled="saving"
                    @click="handleEditSubmit(child.id)"
                  >{{ t('common.save') }}</button>
                  <button
                    class="rounded border border-gray-200 px-3 py-1 text-gray-600 hover:bg-gray-50"
                    @click="cancelEdit"
                  >{{ t('common.cancel') }}</button>
                </div>
              </template>
              <!-- 대댓글 일반 모드 -->
              <template v-else>
                <p class="text-sm text-gray-800 whitespace-pre-wrap ml-4">{{ child.content }}</p>
                <div class="flex gap-2 text-xs ml-4 mt-1">
                  <button
                    v-if="canModify(child)"
                    class="text-gray-500 hover:underline"
                    @click="startEdit(child)"
                  >{{ t('common.edit') }}</button>
                  <button
                    v-if="canModify(child)"
                    class="text-red-500 hover:underline"
                    @click="handleDeleteComment(child.id)"
                  >{{ t('common.delete') }}</button>
                </div>
              </template>
            </div>
          </div>

          <!-- 대댓글 작성 폼 (해당 댓글에 답글 작성 시) -->
          <div v-if="replyTargetId === comment.id" class="ml-8 mt-3">
            <div class="flex flex-col gap-2">
              <textarea
                v-model="replyContent"
                :placeholder="t('board.comments.replyPlaceholder')"
                class="w-full rounded border border-gray-200 p-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400"
                :aria-label="t('board.comments.replyPlaceholder')"
                rows="3"
              />
              <div class="flex gap-2">
                <button
                  class="rounded bg-blue-600 px-3 py-1 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
                  :disabled="saving"
                  @click="handleReplySubmit(comment.id)"
                >{{ t('board.comments.submit') }}</button>
                <button
                  class="rounded border border-gray-200 px-3 py-1 text-sm text-gray-600 hover:bg-gray-50"
                  @click="cancelReply"
                >{{ t('common.cancel') }}</button>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 빈 상태 -->
    <p
      v-else-if="!loading"
      class="text-sm text-gray-400 text-center py-4"
      role="status"
    >
      {{ t('board.comments.empty') }}
    </p>

    <div v-if="loading" v-loading="true" class="h-16" />

    <!-- 새 댓글 작성 폼 (대댓글 모드가 아닐 때) -->
    <div v-if="!replyTargetId" class="border-t border-gray-100 pt-4">
      <p class="mb-2 text-sm font-medium text-gray-700">{{ t('board.comments.writeNew') }}</p>
      <div class="flex flex-col gap-2">
        <textarea
          v-model="newCommentContent"
          :placeholder="t('board.comments.placeholder')"
          class="w-full rounded border border-gray-200 p-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400"
          :aria-label="t('board.comments.placeholder')"
          rows="3"
        />
        <div class="flex gap-2">
          <button
            class="rounded bg-blue-600 px-3 py-1 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
            :disabled="saving"
            @click="submitNewComment"
          >{{ t('board.comments.submit') }}</button>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { boardApi } from '@/api/board'
import type { CommentSummary } from '@iroum/shared/types/api'

interface Props {
  postId: number
}
const props = defineProps<Props>()

const { t } = useI18n()
const auth = useAuthStore()

const comments = ref<CommentSummary[]>([])
const loading = ref(false)
const saving = ref(false)
const replyTargetId = ref<number | null>(null)
const newCommentContent = ref('')
const replyContent = ref('')
const editTargetId = ref<number | null>(null)
const editContent = ref('')

const totalCount = computed(() => {
  let n = comments.value.length
  comments.value.forEach((c) => { n += c.children?.length ?? 0 })
  return n
})

const isAdmin = computed(() =>
  auth.user?.roleCodes?.includes('SUPER_ADMIN') || auth.user?.roleCodes?.includes('DEPT_ADMIN'),
)

function canModify(comment: CommentSummary): boolean {
  return comment.authorUsername === auth.user?.username || !!isAdmin.value
}

function formatDate(val: string | undefined): string {
  if (!val) return ''
  return new Date(val).toLocaleString('ko-KR')
}

// @MX:ANCHOR: [AUTO] loadComments — onMounted, submitComment, handleDeleteComment에서 호출
// @MX:REASON: fan_in >= 3: 마운트, 댓글 등록, 댓글 삭제 후 갱신에서 공통 호출
async function loadComments(): Promise<void> {
  loading.value = true
  try {
    const res = await boardApi.listComments(props.postId)
    comments.value = res.data
  } catch {
    ElMessage.error(t('board.comments.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function submitComment(content: string, parentCommentId: number | null): Promise<void> {
  if (!content.trim()) return
  saving.value = true
  try {
    await boardApi.createComment(props.postId, { content: content.trim(), parentCommentId })
    ElMessage.success(t('board.comments.success.created'))
    replyTargetId.value = null
    replyContent.value = ''
    newCommentContent.value = ''
    await loadComments()
  } catch {
    ElMessage.error(t('board.comments.error.saveFailed'))
  } finally {
    saving.value = false
  }
}

function submitNewComment(): void {
  submitComment(newCommentContent.value, null)
}

function handleReplySubmit(parentCommentId: number): void {
  submitComment(replyContent.value, parentCommentId)
}

function startReply(commentId: number): void {
  if (replyTargetId.value === commentId) {
    cancelReply()
  } else {
    replyTargetId.value = commentId
    replyContent.value = ''
  }
}

function cancelReply(): void {
  replyTargetId.value = null
  replyContent.value = ''
}

function startEdit(comment: CommentSummary): void {
  editTargetId.value = comment.id
  editContent.value = comment.content
}

function cancelEdit(): void {
  editTargetId.value = null
  editContent.value = ''
}

async function handleEditSubmit(id: number): Promise<void> {
  if (!editContent.value.trim()) return
  saving.value = true
  try {
    await boardApi.updateComment(id, editContent.value.trim())
    ElMessage.success(t('board.comments.success.updated'))
    cancelEdit()
    await loadComments()
  } catch {
    ElMessage.error(t('board.comments.error.updateFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDeleteComment(id: number): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('board.comments.confirm.delete'),
      t('board.comments.delete'),
      { type: 'warning', confirmButtonText: t('board.comments.delete'), cancelButtonText: t('common.cancel') },
    )
    await boardApi.deleteComment(id)
    ElMessage.success(t('board.comments.success.deleted'))
    await loadComments()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('board.comments.error.deleteFailed'))
  }
}

onMounted(() => {
  loadComments()
})
</script>
