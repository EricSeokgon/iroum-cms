<!--
  SPEC-CMS-PUBLIC-001 T-007 — 사고사례 카드 (공개 목록용)
-->
<template>
  <article
    class="border-b border-gray-200 px-2 py-4 hover:bg-surface-muted focus-within:bg-surface-muted"
    data-testid="incident-card"
  >
    <h3 class="text-base font-semibold text-content-DEFAULT">{{ incident.title ?? incident.summary }}</h3>
    <dl class="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-content-muted">
      <div class="flex items-center gap-1">
        <dt class="sr-only">{{ t('safety.industryCode') }}</dt>
        <dd>{{ incident.industryCode }}</dd>
      </div>
      <div class="flex items-center gap-1">
        <dt class="sr-only">{{ t('safety.occurredAt') }}</dt>
        <dd>{{ incident.occurredAt.slice(0, 10) }}</dd>
      </div>
    </dl>
    <p class="mt-2 text-sm text-content-muted">{{ incident.summary }}</p>
  </article>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { SafetyIncidentSummary } from '@/api/safetyApi'

defineProps<{
  incident: SafetyIncidentSummary
}>()

const { t } = useI18n()
</script>
