<!--
  SPEC-CMS-PUBLIC-001 T-006 — Q&A 작성 화면
  AC: B-07 (인증 가드)

  - 라우트 가드(meta.requiresAuth=true)에 의해 미인증 시 /login 으로 자동 리다이렉트
  - POST /qnas { title, questionHtml, isPrivate } → 등록 후 상세로 이동
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('qna.create') }}</h1>
    </header>

    <form
      class="space-y-4 rounded-md border border-gray-200 bg-white p-6"
      :aria-label="t('qna.create')"
      data-testid="qna-create-form"
      @submit.prevent="onSubmit"
    >
      <div>
        <label for="qna-title" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('qna.titleLabel') }} <span class="text-red-600" aria-hidden="true">*</span>
        </label>
        <input
          id="qna-title"
          v-model.trim="form.title"
          type="text"
          :placeholder="t('qna.titlePlaceholder')"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          :aria-invalid="!!titleError"
          :aria-describedby="titleError ? 'qna-title-error' : undefined"
          data-testid="qna-title-input"
          required
          @blur="validateTitle"
        />
        <p v-if="titleError" id="qna-title-error" class="mt-1 text-xs text-red-600" role="alert">
          {{ titleError }}
        </p>
      </div>

      <div>
        <label for="qna-content" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('qna.contentLabel') }} <span class="text-red-600" aria-hidden="true">*</span>
        </label>
        <textarea
          id="qna-content"
          v-model.trim="form.content"
          rows="8"
          :placeholder="t('qna.contentPlaceholder')"
          class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          :aria-invalid="!!contentError"
          :aria-describedby="contentError ? 'qna-content-error' : undefined"
          data-testid="qna-content-input"
          required
          @blur="validateContent"
        />
        <p v-if="contentError" id="qna-content-error" class="mt-1 text-xs text-red-600" role="alert">
          {{ contentError }}
        </p>
      </div>

      <div class="flex items-center gap-2">
        <input
          id="qna-private"
          v-model="form.isPrivate"
          type="checkbox"
          class="h-4 w-4 rounded border-gray-300 text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="qna-private-input"
        />
        <label for="qna-private" class="text-sm text-content-DEFAULT">
          {{ t('qna.isPrivateLabel') }}
        </label>
      </div>

      <div class="flex items-center justify-end gap-2 border-t border-gray-200 pt-4">
        <router-link
          :to="{ name: 'qna-list' }"
          class="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary-600"
        >
          {{ t('common.cancel') }}
        </router-link>
        <button
          type="submit"
          :disabled="submitting"
          class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="qna-submit"
        >
          {{ t('qna.submit') }}
        </button>
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { qnaApi } from '@/api/qnaApi'

const { t } = useI18n()
const router = useRouter()

const form = reactive({
  title: '',
  content: '',
  isPrivate: false,
})

const titleError = ref('')
const contentError = ref('')
const submitting = ref(false)

function validateTitle(): boolean {
  if (!form.title.trim()) {
    titleError.value = t('qna.titleRequired')
    return false
  }
  titleError.value = ''
  return true
}

function validateContent(): boolean {
  if (form.content.trim().length < 10) {
    contentError.value = t('qna.contentRequired')
    return false
  }
  contentError.value = ''
  return true
}

async function onSubmit(): Promise<void> {
  const titleOk = validateTitle()
  const contentOk = validateContent()
  if (!titleOk || !contentOk) return

  submitting.value = true
  try {
    // 본문은 plain text → 줄바꿈을 <br> 로 변환해 questionHtml 로 전송
    const questionHtml = form.content.replace(/\n/g, '<br/>')
    const created = await qnaApi.create({
      title: form.title,
      questionHtml,
      isPrivate: form.isPrivate,
    })
    ElMessage.success(t('qna.submit'))
    await router.replace({ name: 'qna-detail', params: { id: created.id } })
  } catch {
    ElMessage.error(t('common.errorOccurred'))
  } finally {
    submitting.value = false
  }
}
</script>
