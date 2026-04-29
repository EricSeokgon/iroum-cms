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
        <!-- 최상위 댓글 -->
        <div class="border-b border-gray-100 pb-3">
          <CommentItem
            :comment="comment"
            :current-username="auth.user?.username"
            :is-admin="isAdmin"
            @reply="startReply(comment.id)"
            @edit="startEdit(comment)"
            @delete="handleDeleteComment(comment.id)"
          />

          <!-- 대댓글 목록 (1단계) -->
          <div v-if="comment.children && comment.children.length > 0" class="ml-8 mt-3 space-y-3">
            <CommentItem
              v-for="child in comment.children"
              :key="child.id"
              :comment="child"
              :current-username="auth.user?.username"
              :is-admin="isAdmin"
              :is-reply="true"
              @edit="startEdit(child)"
              @delete="handleDeleteComment(child.id)"
            />
          </div>

          <!-- 대댓글 작성 폼 (해당 댓글에 답글 작성 시) -->
          <div v-if="replyTargetId === comment.id" class="ml-8 mt-3">
            <CommentForm
              :placeholder="t('board.comments.replyPlaceholder')"
              :saving="saving"
              @submit="(content) => submitComment(content, comment.id)"
              @cancel="replyTargetId = null"
            />
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
      <CommentForm
        :placeholder="t('board.comments.placeholder')"
        :saving="saving"
        @submit="(content) => submitComment(content, null)"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { boardApi } from '@/api/board'
import type { CommentSummary } from '@iroum/shared/types/api'

// 내부 서브 컴포넌트 (인라인 정의)
const CommentItem = defineAsyncComponent(() =>
  Promise.resolve({
    props: {
      comment: Object as () => CommentSummary,
      currentUsername: String,
      isAdmin: Boolean,
      isReply: Boolean,
    },
    emits: ['reply', 'edit', 'delete'],
    template: `
      <div class="flex flex-col gap-1">
        <div class="flex items-center gap-2 text-sm">
          <span v-if="isReply" class="text-gray-400" aria-hidden="true">↳</span>
          <span class="font-medium text-gray-700">{{ comment.authorUsername }}</span>
          <span class="text-xs text-gray-400">{{ new Date(comment.createdAt).toLocaleString('ko-KR') }}</span>
        </div>
        <p class="text-sm text-gray-800">{{ comment.content }}</p>
        <div class="flex gap-2 text-xs">
          <button
            v-if="!isReply"
            class="text-blue-500 hover:underline"
            @click="$emit('reply')"
          >답글</button>
          <button
            v-if="currentUsername === comment.authorUsername || isAdmin"
            class="text-gray-500 hover:underline"
            @click="$emit('edit', comment)"
          >수정</button>
          <button
            v-if="currentUsername === comment.authorUsername || isAdmin"
            class="text-red-500 hover:underline"
            @click="$emit('delete')"
          >삭제</button>
        </div>
      </div>
    `,
  })
)

const CommentForm = defineAsyncComponent(() =>
  Promise.resolve({
    props: { placeholder: String, saving: Boolean },
    emits: ['submit', 'cancel'],
    setup(props: { placeholder?: string; saving?: boolean }, { emit }: { emit: (event: string, ...args: unknown[]) => void }) {
      const content = ref('')
      function onSubmit() {
        if (!content.value.trim()) return
        emit('submit', content.value.trim())
        content.value = ''
      }
      return { content, onSubmit }
    },
    template: `
      <div class="flex flex-col gap-2">
        <textarea
          v-model="content"
          :placeholder="placeholder"
          class="w-full rounded border border-gray-200 p-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400"
          rows="3"
          :aria-label="placeholder"
        />
        <div class="flex gap-2">
          <button
            class="rounded bg-blue-600 px-3 py-1 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
            :disabled="saving"
            @click="onSubmit"
          >등록</button>
          <button
            v-if="$attrs.onCancel"
            class="rounded border border-gray-200 px-3 py-1 text-sm text-gray-600 hover:bg-gray-50"
            @click="$emit('cancel')"
          >취소</button>
        </div>
      </div>
    `,
  })
)

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
const totalCount = computed(() => {
  let n = comments.value.length
  comments.value.forEach((c) => { n += c.children?.length ?? 0 })
  return n
})

const isAdmin = computed(() =>
  auth.user?.roleCodes?.includes('SUPER_ADMIN') || auth.user?.roleCodes?.includes('DEPT_ADMIN'),
)

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
  saving.value = true
  try {
    await boardApi.createComment(props.postId, { content, parentCommentId })
    ElMessage.success(t('board.comments.success.created'))
    replyTargetId.value = null
    await loadComments()
  } catch {
    ElMessage.error(t('board.comments.error.saveFailed'))
  } finally {
    saving.value = false
  }
}

function startReply(commentId: number): void {
  replyTargetId.value = replyTargetId.value === commentId ? null : commentId
}

function startEdit(_comment: CommentSummary): void {
  // @MX:TODO: [AUTO] 댓글 인라인 수정 UI 구현
  ElMessage.info(t('board.comments.editNotImplemented'))
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
