<!--
  SPEC-CMS-PUBLIC-001 T-007 — 정책 매칭 (익명 가능)
  AC: C-03 — POST /api/v1/policies/match, 401 시에도 /login 으로 리다이렉트하지 않음

  meta.requiresAuth=false (기본). 401 응답은 axios 인터셉터에서 무시되며 카탈로그 상에 빈 결과로 표시.
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('policy.matchTitle') }}</h1>
      <p class="mt-1 text-sm text-content-muted">{{ t('policy.matchSubtitle') }}</p>
    </header>

    <PolicyMatchForm :submitting="submitting" @submit="onSubmit" />

    <section v-if="submitted" class="space-y-4">
      <LoadingState v-if="submitting" />
      <ErrorState v-else-if="error" @retry="retry" />
      <EmptyState v-else-if="results.length === 0" :message="t('policy.matchEmpty')" />
      <ul v-else class="divide-y divide-gray-100" data-testid="policy-match-results">
        <li v-for="result in results" :key="result.policyId">
          <PolicyCard
            :policy="result.policy"
            :match-score="result.score"
            :match-reason="result.reason"
          />
        </li>
      </ul>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  policyApi,
  type PolicyMatchRequest,
  type PolicyMatchResult,
} from '@/api/policyApi'
import PolicyMatchForm from '@/components/policy/PolicyMatchForm.vue'
import PolicyCard from '@/components/policy/PolicyCard.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()

const results = ref<PolicyMatchResult[]>([])
const submitting = ref(false)
const submitted = ref(false)
const error = ref(false)
const lastRequest = ref<PolicyMatchRequest>({})

async function onSubmit(payload: PolicyMatchRequest): Promise<void> {
  lastRequest.value = payload
  submitting.value = true
  submitted.value = true
  error.value = false
  try {
    results.value = await policyApi.match(payload)
  } catch {
    // 401 익명 호출은 client 인터셉터에서 무시(리다이렉트 안함) — 빈 결과 처리
    error.value = true
    results.value = []
  } finally {
    submitting.value = false
  }
}

function retry(): void {
  onSubmit(lastRequest.value)
}
</script>
