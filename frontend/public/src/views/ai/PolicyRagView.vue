<!--
  SPEC-CMS-AI-003 — RAG 자연어 질의응답 (시민 SPA, 익명 가능)
  AC-RAG-010 — 질문 입력·답변·출처 목록·HELPFUL/UNHELPFUL 피드백, i18n ko/en
  degraded=true 응답 시 "간소 검색 결과" 안내 배너 표시

  meta.requiresAuth 미설정(공개). 401은 axios 인터셉터에서 무시(리다이렉트 안함).
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('rag.title') }}</h1>
      <p class="mt-1 text-sm text-content-muted">{{ t('rag.subtitle') }}</p>
    </header>

    <form
      data-testid="rag-form"
      class="space-y-3"
      @submit.prevent="onSubmit"
    >
      <label for="rag-q" class="block text-sm font-medium text-content-DEFAULT">
        {{ t('rag.questionLabel') }}
      </label>
      <textarea
        id="rag-q"
        v-model="question"
        data-testid="rag-question-input"
        rows="3"
        maxlength="1000"
        :placeholder="t('rag.questionPlaceholder')"
        class="w-full rounded border border-gray-300 px-3 py-2 text-sm"
      ></textarea>
      <p
        v-if="inputError"
        data-testid="rag-input-error"
        class="text-sm text-red-600"
      >
        {{ inputError }}
      </p>
      <button
        type="submit"
        data-testid="rag-submit"
        :disabled="submitting"
        class="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
      >
        {{ t('rag.submit') }}
      </button>
    </form>

    <section v-if="submitted" class="space-y-4">
      <LoadingState v-if="submitting" />
      <ErrorState v-else-if="error" @retry="retry" />
      <template v-else-if="result">
        <p
          v-if="result.degraded"
          data-testid="rag-degraded-banner"
          class="rounded bg-amber-50 px-3 py-2 text-sm text-amber-700"
        >
          {{ t('rag.degraded') }}
        </p>

        <article class="space-y-2">
          <h2 class="text-lg font-semibold text-content-DEFAULT">
            {{ t('rag.answerTitle') }}
          </h2>
          <p
            data-testid="rag-answer"
            class="whitespace-pre-line rounded bg-gray-50 px-3 py-3 text-sm text-gray-800"
          >
            {{ result.answer }}
          </p>
        </article>

        <section v-if="result.sources.length" class="space-y-2">
          <h3 class="text-base font-semibold text-content-DEFAULT">
            {{ t('rag.sourcesTitle') }}
          </h3>
          <ul class="divide-y divide-gray-100" data-testid="rag-sources">
            <li
              v-for="src in result.sources"
              :key="src.id"
              class="flex items-center justify-between py-2"
            >
              <button
                type="button"
                class="text-left font-medium text-blue-700 hover:underline"
                :data-testid="`rag-source-${src.id}`"
                @click="goToPolicy(src.id)"
              >
                {{ src.title }}
              </button>
              <span class="rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-700">
                {{ t('rag.relevance') }} {{ pct(src.relevance) }}
              </span>
            </li>
          </ul>
        </section>
        <EmptyState v-else :message="t('rag.empty')" />

        <div
          v-if="!feedbackSent"
          class="flex items-center gap-3"
          data-testid="rag-feedback"
        >
          <button
            type="button"
            data-testid="rag-helpful"
            class="rounded border border-green-600 px-3 py-1 text-sm text-green-700 hover:bg-green-50"
            @click="sendFeedback('HELPFUL')"
          >
            {{ t('rag.helpful') }}
          </button>
          <button
            type="button"
            data-testid="rag-unhelpful"
            class="rounded border border-gray-400 px-3 py-1 text-sm text-gray-600 hover:bg-gray-50"
            @click="sendFeedback('UNHELPFUL')"
          >
            {{ t('rag.unhelpful') }}
          </button>
        </div>
        <p
          v-else
          data-testid="rag-feedback-thanks"
          class="text-sm text-green-700"
        >
          {{ t('rag.feedbackThanks') }}
        </p>
      </template>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  policyApi,
  type RagQueryResponse,
  type RagFeedbackValue,
} from '@/api/policyApi'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()
const router = useRouter()

const question = ref('')
const submitting = ref(false)
const submitted = ref(false)
const error = ref(false)
const inputError = ref('')
const result = ref<RagQueryResponse | null>(null)
const feedbackSent = ref(false)

function pct(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

async function onSubmit(): Promise<void> {
  inputError.value = ''
  const q = question.value.trim()
  if (!q) {
    inputError.value = t('rag.questionRequired')
    return
  }
  if (q.length > 1000) {
    inputError.value = t('rag.questionTooLong')
    return
  }
  submitting.value = true
  submitted.value = true
  error.value = false
  feedbackSent.value = false
  result.value = null
  try {
    result.value = await policyApi.ragQuery({ question: q })
  } catch {
    // 401 익명 호출은 client 인터셉터에서 무시 — 에러 상태로 표시
    error.value = true
  } finally {
    submitting.value = false
  }
}

async function sendFeedback(value: RagFeedbackValue): Promise<void> {
  if (!result.value) return
  try {
    await policyApi.ragFeedback({
      queryRef: result.value.queryRef,
      feedback: value,
    })
    feedbackSent.value = true
  } catch {
    // 피드백 실패는 사용자 흐름을 막지 않음 (best-effort)
    feedbackSent.value = true
  }
}

function goToPolicy(policyId: number): void {
  router.push({ name: 'policy-detail', params: { id: policyId } })
}

function retry(): void {
  onSubmit()
}

defineExpose({ result, question, onSubmit, sendFeedback, pct })
</script>
