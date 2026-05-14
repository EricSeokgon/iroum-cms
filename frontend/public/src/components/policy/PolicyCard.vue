<!--
  SPEC-CMS-PUBLIC-001 T-007 — 정책 목록 카드
  - PolicySummary 또는 PolicyMatchResult 양쪽을 지원
  - 매칭 결과인 경우 score 와 reason 을 함께 표시
-->
<template>
  <article
    class="border-b border-gray-200 px-2 py-4 hover:bg-surface-muted focus-within:bg-surface-muted"
    data-testid="policy-card"
  >
    <router-link
      :to="{ name: 'policy-detail', params: { id: policy.id } }"
      class="block focus-visible:outline-2 focus-visible:outline-primary-600"
    >
      <header class="flex flex-wrap items-center gap-2">
        <span
          class="rounded-md bg-primary-100 px-2 py-0.5 text-xs font-bold text-primary-700"
          data-testid="policy-type-badge"
        >
          {{ policy.type }}
        </span>
        <h3 class="text-base font-semibold text-content-DEFAULT hover:text-primary-600">
          {{ policy.title }}
        </h3>
        <span
          v-if="matchScore !== undefined"
          class="ml-auto rounded-md bg-green-100 px-2 py-0.5 text-xs font-bold text-green-700"
          data-testid="policy-match-score"
        >
          {{ t('policy.matchScore') }}: {{ Math.round(matchScore * 100) }}%
        </span>
      </header>
      <dl class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-content-muted">
        <div class="flex items-center gap-1">
          <dt class="sr-only">{{ t('policy.industry') }}</dt>
          <dd>{{ policy.industry }}</dd>
        </div>
        <div class="flex items-center gap-1">
          <dt class="sr-only">{{ t('policy.region') }}</dt>
          <dd>{{ policy.region }}</dd>
        </div>
        <div v-if="policy.supportAmount" class="flex items-center gap-1">
          <dt class="sr-only">{{ t('policy.supportAmount') }}</dt>
          <dd>{{ policy.supportAmount }}</dd>
        </div>
        <div v-if="policy.deadline" class="flex items-center gap-1">
          <dt class="sr-only">{{ t('policy.deadline') }}</dt>
          <dd>~ {{ policy.deadline.slice(0, 10) }}</dd>
        </div>
      </dl>
      <p v-if="matchReason" class="mt-2 text-sm text-content-muted" data-testid="policy-match-reason">
        {{ matchReason }}
      </p>
    </router-link>
  </article>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PolicySummary } from '@/api/policyApi'

defineProps<{
  policy: PolicySummary
  matchScore?: number
  matchReason?: string
}>()

const { t } = useI18n()
</script>
