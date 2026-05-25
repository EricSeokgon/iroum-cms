<template>
  <div v-loading="loading">
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <el-button :aria-label="t('common.back')" @click="goBack">
          <el-icon><i-ep-arrow-left /></el-icon>
          {{ t('common.back') }}
        </el-button>
        <h2 v-if="qna" class="text-xl font-semibold text-gray-800">{{ qna.title }}</h2>
      </div>
      <el-tag v-if="qna" :type="statusTagType(qna.status)" size="default">
        {{ t(`qna.status.${qna.status}`) }}
      </el-tag>
    </div>

    <div v-if="qna" class="space-y-6">
      <!-- 메타 정보 -->
      <div class="rounded border border-gray-200 bg-white p-4 text-sm text-gray-600">
        <div class="flex flex-wrap gap-6">
          <div>
            <span class="font-medium">{{ t('qna.field.questioner') }}:</span>
            <span class="ml-2">#{{ qna.questionerId }}</span>
          </div>
          <div>
            <span class="font-medium">{{ t('qna.field.isPrivate') }}:</span>
            <el-tag
              :type="qna.isPrivate ? 'warning' : 'info'"
              size="small"
              class="ml-2"
            >
              {{ qna.isPrivate ? t('qna.privacy.private') : t('qna.privacy.public') }}
            </el-tag>
          </div>
          <div>
            <span class="font-medium">{{ t('common.startDate') }}:</span>
            <span class="ml-2">{{ formatDate(qna.createdAt) }}</span>
          </div>
        </div>
      </div>

      <!-- 질문 영역 -->
      <section class="rounded border border-gray-200 bg-white p-6">
        <h3 class="mb-3 text-base font-semibold text-gray-800">
          {{ t('qna.field.question') }}
        </h3>
        <!-- v-html 사용: 백엔드에서 OWASP Java HTML Sanitizer 로 정화 후 전달됨 -->
        <div
          class="prose max-w-none text-sm leading-relaxed text-gray-800"
          v-html="sanitize(qna.questionHtml)"
        />
      </section>

      <!-- 답변 영역 -->
      <section
        v-if="qna.status === 'ANSWERED' || qna.status === 'CLOSED'"
        class="rounded border border-blue-200 bg-blue-50 p-6"
      >
        <h3 class="mb-3 text-base font-semibold text-gray-800">
          {{ t('qna.field.answer') }}
        </h3>
        <div class="mb-3 text-sm text-gray-600">
          <span class="font-medium">{{ t('qna.field.answerer') }}:</span>
          <span class="ml-2">#{{ qna.answererId }}</span>
          <span class="ml-4 font-medium">{{ t('qna.field.answeredAt') }}:</span>
          <span class="ml-2">{{ formatDate(qna.answeredAt) }}</span>
        </div>
        <!-- v-html 사용: 백엔드 sanitizer 로 정화된 안전한 HTML -->
        <div
          class="prose max-w-none text-sm leading-relaxed text-gray-800"
          v-html="sanitize(qna.answerHtml)"
        />
      </section>

      <!-- 답변 작성 폼 (PENDING 상태 + 관리자) -->
      <section
        v-if="(qna.status === 'PENDING' || editingAnswer) && isAdmin"
        class="rounded border border-gray-200 bg-white p-6"
      >
        <h3 class="mb-3 text-base font-semibold text-gray-800">
          {{ editingAnswer ? t('qna.editAnswer') : t('qna.answer') }}
        </h3>
        <el-form label-position="top">
          <el-form-item :label="t('qna.field.answer')">
            <TiptapEditor
              v-model="answerInput"
              :rows="6"
              :upload-image="uploadImage"
              :aria-label="t('qna.field.answer')"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="submitting"
              @click="handleAnswerSubmit"
            >
              {{ t('common.save') }}
            </el-button>
            <el-button v-if="editingAnswer" @click="cancelEditAnswer">
              {{ t('common.cancel') }}
            </el-button>
          </el-form-item>
        </el-form>
      </section>

      <!-- 액션 버튼들 -->
      <div class="flex justify-end gap-2">
        <el-button
          v-if="qna.status === 'ANSWERED' && isAdmin && !editingAnswer"
          type="primary"
          plain
          @click="startEditAnswer"
        >
          {{ t('qna.editAnswer') }}
        </el-button>
        <el-button
          v-if="qna.status !== 'CLOSED' && isAdmin"
          type="info"
          plain
          @click="handleClose"
        >
          {{ t('qna.close') }}
        </el-button>
        <el-button
          v-if="canDelete"
          type="danger"
          plain
          @click="handleDelete"
        >
          {{ t('common.delete') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  getQna,
  answerQna,
  closeQna,
  deleteQna,
  type QnaDetail,
} from '@/api/qna'
import { useSafeHtml } from '@/composables/useSafeHtml'
import { boardApi } from '@/api/board'
import TiptapEditor from '@/components/editor/TiptapEditor.vue'

const { sanitize } = useSafeHtml()

interface Props {
  id: string
}
const props = defineProps<Props>()

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const qna = ref<QnaDetail | null>(null)
const loading = ref(false)
const submitting = ref(false)
const answerInput = ref('')
const editingAnswer = ref(false)

const isAdmin = computed(() => {
  const roles = auth.user?.roleCodes ?? []
  return roles.includes('SUPER_ADMIN') || roles.includes('ADMIN') || roles.includes('DEPT_ADMIN')
})

const canDelete = computed(() => {
  if (!qna.value) return false
  if (isAdmin.value) return true
  // 작성자 본인이고 PENDING 상태인 경우만 삭제 가능
  return qna.value.questionerId === auth.user?.id && qna.value.status === 'PENDING'
})

async function loadQna(): Promise<void> {
  loading.value = true
  try {
    const res = await getQna(Number(props.id))
    qna.value = res.data
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

async function uploadImage(file: File): Promise<string> {
  const res = await boardApi.uploadAttachment(file)
  const urlRes = await boardApi.getAttachmentUrl(res.data.id)
  return urlRes.data.signedUrl
}

function startEditAnswer(): void {
  answerInput.value = qna.value?.answerHtml ?? ''
  editingAnswer.value = true
}

function cancelEditAnswer(): void {
  editingAnswer.value = false
  answerInput.value = ''
}

async function handleAnswerSubmit(): Promise<void> {
  if (!qna.value) return
  if (!answerInput.value.trim()) {
    ElMessage.warning(t('common.required'))
    return
  }
  submitting.value = true
  try {
    const res = await answerQna(qna.value.id, { answerHtml: answerInput.value })
    qna.value = res.data
    answerInput.value = ''
    editingAnswer.value = false
    ElMessage.success(t('common.saveSuccess'))
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    submitting.value = false
  }
}

async function handleClose(): Promise<void> {
  if (!qna.value) return
  try {
    await ElMessageBox.confirm(t('qna.close') + '?', t('qna.close'), {
      type: 'warning',
      confirmButtonText: t('qna.close'),
      cancelButtonText: t('common.cancel'),
    })
    const res = await closeQna(qna.value.id)
    qna.value = res.data
    ElMessage.success(t('common.saveSuccess'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.saveError'))
  }
}

async function handleDelete(): Promise<void> {
  if (!qna.value) return
  try {
    await ElMessageBox.confirm(
      `'${qna.value.title}' Q&A를 삭제하시겠습니까?`,
      t('common.delete'),
      {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await deleteQna(qna.value.id)
    ElMessage.success(t('common.deleteSuccess'))
    router.push({ name: 'board-qnas' })
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.deleteError'))
  }
}

function goBack(): void {
  router.push({ name: 'board-qnas' })
}

function statusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'warning',
    ANSWERED: 'success',
    CLOSED: 'info',
    HIDDEN: 'danger',
  }
  return map[status] ?? ''
}

function formatDate(iso: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

onMounted(() => {
  loadQna()
})
</script>
