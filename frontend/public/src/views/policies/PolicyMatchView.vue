<!--
  SPEC-CMS-PUBLIC-001 T-007 / SPEC-CMS-AI-002 — AI 하이브리드 정책 추천 (익명 가능)
  AC-PM-001/008/009 — hybrid 점수 배지 + 추천 사유 + 클릭/신청 피드백 전송
  AC-PM-013 — 클릭/신청 시 POST /ai/policy-match/feedback (CLICKED/APPLIED)

  meta.requiresAuth=false (기본). 비회원 세션은 X-Session-Ref 헤더로 추적되며
  401 응답은 axios 인터셉터에서 무시(리다이렉트 안함) — 빈 결과 처리.
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
      <EmptyState v-else-if="items.length === 0" :message="t('policy.matchEmpty')" />
      <template v-else>
        <p
          v-if="degraded"
          data-testid="ai-degraded-banner"
          class="rounded bg-amber-50 px-3 py-2 text-sm text-amber-700"
        >
          {{ t('policy.aiDegraded') }}
        </p>
        <ul class="divide-y divide-gray-100" data-testid="policy-match-results">
          <li
            v-for="item in items"
            :key="item.policyId"
            class="space-y-2 py-4"
          >
            <div class="flex items-center justify-between">
              <button
                type="button"
                class="text-left font-semibold text-blue-700 hover:underline"
                :data-testid="`policy-link-${item.policyId}`"
                @click="onPolicyClick(item.policyId)"
              >
                {{ t('policy.policyId') }} #{{ item.policyId }}
              </button>
              <div class="flex items-center gap-2">
                <span
                  class="rounded bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700"
                  :data-testid="`hybrid-badge-${item.policyId}`"
                >
                  {{ t('policy.hybridScore') }} {{ pct(item.hybridScore) }}
                </span>
                <span
                  class="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600"
                  :title="t('policy.ruleScore')"
                >
                  R {{ pct(item.ruleScore) }}
                </span>
                <span
                  v-if="item.explanation.semanticAvailable"
                  class="rounded bg-purple-100 px-2 py-0.5 text-xs text-purple-700"
                  :title="t('policy.semanticScore')"
                >
                  S {{ pct(item.semanticScore) }}
                </span>
              </div>
            </div>

            <!-- 추천 사유 (AC-PM-008/009) -->
            <details class="text-sm text-gray-600">
              <summary
                class="cursor-pointer select-none text-gray-500"
                :data-testid="`explain-toggle-${item.policyId}`"
              >
                {{ t('policy.whyRecommended') }}
              </summary>
              <p class="mt-1">{{ item.explanation.rationale }}</p>
              <p
                v-if="item.explanation.matchedTerms.length"
                class="mt-1 flex flex-wrap gap-1"
              >
                <span
                  v-for="term in item.explanation.matchedTerms"
                  :key="term"
                  class="rounded bg-gray-50 px-2 py-0.5 text-xs text-gray-500"
                >
                  {{ term }}
                </span>
              </p>
            </details>

            <button
              type="button"
              class="rounded bg-green-600 px-3 py-1 text-sm font-medium text-white hover:bg-green-700"
              :data-testid="`apply-btn-${item.policyId}`"
              @click="onPolicyApply(item.policyId)"
            >
              {{ t('policy.applyAction') }}
            </button>
          </li>
        </ul>
      </template>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  policyApi,
  type PolicyMatchRequest,
  type AiPolicyMatchRequest,
  type AiPolicyMatchItem,
  type PolicyFeedbackType,
} from '@/api/policyApi'
import PolicyMatchForm from '@/components/policy/PolicyMatchForm.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()

const items = ref<AiPolicyMatchItem[]>([])
const degraded = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const error = ref(false)
const lastRequest = ref<PolicyMatchRequest>({})

// 비회원 세션 식별자 (서버에서 SHA-256 해시 후 저장 — 평문 미저장)
const sessionRef = ref<string>(resolveSessionRef())

function resolveSessionRef(): string {
  const KEY = 'iroum-pm-session'
  let v = window.localStorage.getItem(KEY)
  if (!v) {
    v = `pm-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    window.localStorage.setItem(KEY, v)
  }
  return v
}

function pct(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

/** PolicyMatchForm 입력 → AI 화이트리스트 companyProfile 매핑 (PII 제외). */
function toCompanyProfile(req: PolicyMatchRequest): Record<string, unknown> {
  const profile: Record<string, unknown> = {}
  if (req.industry) profile.ksic_code = req.industry
  if (req.employeeCount != null) profile.employee_count = req.employeeCount
  if (req.region) profile.region_code = req.region
  if (req.revenueAmount != null) profile.annual_revenue = req.revenueAmount
  return profile
}

async function onSubmit(payload: PolicyMatchRequest): Promise<void> {
  lastRequest.value = payload
  submitting.value = true
  submitted.value = true
  error.value = false
  try {
    const aiReq: AiPolicyMatchRequest = {
      companyProfile: toCompanyProfile(payload),
      topK: 10,
    }
    const res = await policyApi.aiMatch(aiReq)
    items.value = res.items
    degraded.value = res.degraded
  } catch {
    // 401 익명 호출은 client 인터셉터에서 무시(리다이렉트 안함) — 빈 결과 처리
    error.value = true
    items.value = []
  } finally {
    submitting.value = false
  }
}

async function sendFeedback(
  policyId: number,
  interactionType: PolicyFeedbackType,
): Promise<void> {
  try {
    await policyApi.sendFeedback({
      sessionRef: sessionRef.value,
      interactionType,
      policyId,
    })
  } catch {
    // 피드백 실패는 사용자 흐름을 막지 않음 (best-effort)
  }
}

function onPolicyClick(policyId: number): void {
  sendFeedback(policyId, 'CLICKED')
}

function onPolicyApply(policyId: number): void {
  sendFeedback(policyId, 'APPLIED')
}

function retry(): void {
  onSubmit(lastRequest.value)
}

defineExpose({ items, degraded, onPolicyClick, onPolicyApply, pct })
</script>
