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

      <!-- AI 스마트 태그 추천 (SPEC-CMS-AI-004) — 비회원 포함 사용 가능 -->
      <div>
        <label for="qna-tag-input" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          {{ t('qna.tagsLabel') }}
        </label>
        <!-- 선택된 태그 + 자유 입력 -->
        <div class="flex flex-wrap items-center gap-1">
          <span
            v-for="tag in tags"
            :key="tag"
            class="inline-flex items-center gap-1 rounded bg-primary-50 px-2 py-0.5 text-xs text-primary-700"
          >
            {{ tag }}
            <button
              type="button"
              class="text-primary-500 hover:text-primary-700"
              :aria-label="`${tag} 태그 삭제`"
              @click="removeTag(tag)"
            >&times;</button>
          </span>
          <input
            id="qna-tag-input"
            v-model.trim="tagInput"
            type="text"
            :placeholder="t('qna.tagPlaceholder')"
            class="min-w-[140px] flex-1 rounded-md border border-gray-300 px-2 py-1 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
            data-testid="qna-tag-input"
            @keyup.enter.prevent="addTag"
            @keydown="onKeydown"
          />
        </div>
        <!-- AI 추천 칩 -->
        <div v-if="filteredRecommendations.length > 0" class="mt-2 flex flex-wrap items-center gap-1">
          <span class="mr-1 text-xs text-gray-500">{{ t('qna.aiRecommend') }}</span>
          <button
            v-for="tag in filteredRecommendations"
            :key="tag"
            type="button"
            class="rounded border border-dashed border-primary-300 bg-white px-2 py-0.5 text-xs text-primary-600 hover:bg-primary-50"
            data-testid="qna-tag-recommend"
            @click="acceptRecommendation(tag)"
          >+ {{ tag }}</button>
        </div>
        <p v-else-if="recommendLoading" class="mt-1 text-xs text-gray-400">
          {{ t('qna.aiAnalyzing') }}
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
import { reactive, ref, computed, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { qnaApi } from '@/api/qnaApi'
import { useTagRecommendation } from '@/composables/useTagRecommendation'

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

// AI 스마트 태그 추천 (SPEC-CMS-AI-004) — 비회원 포함 사용 가능
const tags = ref<string[]>([])
const tagInput = ref('')
const {
  recommendations,
  loading: recommendLoading,
  acceptTag,
} = useTagRecommendation(toRef(form, 'content'), tags, 'QNA')

// 이미 선택된 태그는 추천 목록에서 제외
const filteredRecommendations = computed(() =>
  recommendations.value.filter((tg) => !tags.value.includes(tg)),
)

// 콤마 키 처리 (vue/valid-v-on에서 .comma modifier 미지원 → 직접 처리)
function onKeydown(e: KeyboardEvent): void {
  if (e.key === ',') {
    e.preventDefault()
    addTag()
  }
}

function addTag(): void {
  const tag = tagInput.value.trim().replace(/,$/, '').trim()
  if (tag && !tags.value.includes(tag)) {
    tags.value = [...tags.value, tag]
  }
  tagInput.value = ''
}

function removeTag(tag: string): void {
  tags.value = tags.value.filter((tg) => tg !== tag)
}

function acceptRecommendation(tag: string): void {
  if (!tags.value.includes(tag)) {
    tags.value = [...tags.value, tag]
    void acceptTag(tag)
  }
}

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
      tags: tags.value,
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
